package mil.nga.giat.geowave.datastore.hbase.operations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.filter.FilterList;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter.RowRange;
import org.apache.log4j.Logger;

import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.index.ByteArrayRange;
import mil.nga.giat.geowave.core.index.IndexUtils;
import mil.nga.giat.geowave.core.index.MultiDimensionalCoordinateRangesArray;
import mil.nga.giat.geowave.core.index.StringUtils;
import mil.nga.giat.geowave.core.store.entities.GeoWaveRow;
import mil.nga.giat.geowave.core.store.filter.DistributableQueryFilter;
import mil.nga.giat.geowave.core.store.operations.Reader;
import mil.nga.giat.geowave.core.store.operations.ReaderParams;
import mil.nga.giat.geowave.datastore.hbase.HBaseRow;
import mil.nga.giat.geowave.datastore.hbase.query.FixedCardinalitySkippingFilter;
import mil.nga.giat.geowave.datastore.hbase.query.HBaseDistributableFilter;
import mil.nga.giat.geowave.datastore.hbase.query.HBaseNumericIndexStrategyFilter;
import mil.nga.giat.geowave.mapreduce.splits.GeoWaveRowRange;
import mil.nga.giat.geowave.mapreduce.splits.RecordReaderParams;

public class HBaseReader implements
		Reader
{
	private final static Logger LOGGER = Logger.getLogger(
			HBaseReader.class);

	private final ReaderParams readerParams;
	private final RecordReaderParams recordReaderParams;
	private final HBaseOperations operations;

	private final ResultScanner scanner;
	private final Iterator<Result> it;

	private final boolean wholeRowEncoding;
	private final int partitionKeyLength;

	public HBaseReader(
			final ReaderParams readerParams,
			final HBaseOperations operations ) {
		this.readerParams = readerParams;
		this.recordReaderParams = null;
		this.operations = operations;

		this.partitionKeyLength = readerParams.getIndex().getIndexStrategy().getPartitionKeyLength();
		this.wholeRowEncoding = readerParams.isMixedVisibility() && !readerParams.isServersideAggregation();

		this.scanner = initScanner();
		it = scanner.iterator();
	}

	public HBaseReader(
			final RecordReaderParams recordReaderParams,
			final HBaseOperations operations ) {
		this.readerParams = null;
		this.recordReaderParams = recordReaderParams;
		this.operations = operations;

		this.partitionKeyLength = recordReaderParams.getIndex().getIndexStrategy().getPartitionKeyLength();
		this.wholeRowEncoding = recordReaderParams.isMixedVisibility() && !recordReaderParams.isServersideAggregation();

		this.scanner = initRecordScanner();
		it = scanner.iterator();
	}

	@Override
	public void close()
			throws Exception {
		scanner.close();
	}

	@Override
	public boolean hasNext() {
		return it.hasNext();
	}

	@Override
	public GeoWaveRow next() {
		final Result entry = it.next();

		return new HBaseRow(
				entry,
				partitionKeyLength);
	}

	protected ResultScanner initRecordScanner() {
		final FilterList filterList = new FilterList();
		final GeoWaveRowRange range = recordReaderParams.getRowRange();
		final String tableName = StringUtils.stringFromBinary(
				recordReaderParams.getIndex().getId().getBytes());

		final Scan scanner = createStandardScanner();
		scanner.setStartRow(
				range.getStartSortKey());
		scanner.setStopRow(
				range.getEndSortKey());

		if (operations.isEnableCustomFilters()) {
			// Add skipping filter if requested
			if (readerParams.getMaxResolutionSubsamplingPerDimension() != null) {
				if (readerParams.getMaxResolutionSubsamplingPerDimension().length != readerParams
						.getIndex()
						.getIndexStrategy()
						.getOrderedDimensionDefinitions().length) {
					LOGGER.warn(
							"Unable to subsample for table '" + readerParams.getIndex().getId().getString()
									+ "'. Subsample dimensions = "
									+ readerParams.getMaxResolutionSubsamplingPerDimension().length
									+ " when indexed dimensions = " + readerParams
											.getIndex()
											.getIndexStrategy()
											.getOrderedDimensionDefinitions().length);
				}
				else {
					final int cardinalityToSubsample = IndexUtils.getBitPositionFromSubsamplingArray(
							readerParams.getIndex().getIndexStrategy(),
							readerParams.getMaxResolutionSubsamplingPerDimension());

					final FixedCardinalitySkippingFilter skippingFilter = new FixedCardinalitySkippingFilter(
							cardinalityToSubsample);
					filterList.addFilter(
							skippingFilter);
				}
			}

			// Add distributable filters if requested, this has to be last
			// in the filter list for the dedupe filter to work correctly

			if (readerParams.getFilter() != null) {
				final HBaseDistributableFilter hbdFilter = new HBaseDistributableFilter();
				if (wholeRowEncoding) {
					hbdFilter.setWholeRowFilter(
							true);
				}

				final List<DistributableQueryFilter> distFilters = new ArrayList();
				distFilters.add(
						readerParams.getFilter());
				hbdFilter.init(
						distFilters,
						readerParams.getIndex().getIndexModel(),
						readerParams.getAdditionalAuthorizations());

				filterList.addFilter(
						hbdFilter);
			}
			else {
				final List<MultiDimensionalCoordinateRangesArray> coords = readerParams.getCoordinateRanges();
				if ((coords != null) && !coords.isEmpty()) {
					final HBaseNumericIndexStrategyFilter numericIndexFilter = new HBaseNumericIndexStrategyFilter(
							readerParams.getIndex().getIndexStrategy(),
							coords.toArray(
									new MultiDimensionalCoordinateRangesArray[] {}));
					filterList.addFilter(
							numericIndexFilter);
				}
			}
		}

		if (!filterList.getFilters().isEmpty()) {
			scanner.setFilter(
					filterList);
		}

		try {
			final ResultScanner resultScanner = operations.getScannedResults(
					scanner,
					readerParams.getIndex().getId().getString(),
					readerParams.getAdditionalAuthorizations());

			return resultScanner;
		}
		catch (final IOException e) {
			LOGGER.warn(
					"Could not get the results from scanner",
					e);
		}
		return null;
	}

	protected ResultScanner initScanner() {
		final FilterList filterList = new FilterList();

		final Scan multiScanner = getMultiScanner(
				filterList,
				readerParams.getLimit(),
				readerParams.getMaxResolutionSubsamplingPerDimension());

		if (operations.isEnableCustomFilters()) {
			// Add skipping filter if requested
			if (readerParams.getMaxResolutionSubsamplingPerDimension() != null) {
				if (readerParams.getMaxResolutionSubsamplingPerDimension().length != readerParams
						.getIndex()
						.getIndexStrategy()
						.getOrderedDimensionDefinitions().length) {
					LOGGER.warn(
							"Unable to subsample for table '" + readerParams.getIndex().getId().getString()
									+ "'. Subsample dimensions = "
									+ readerParams.getMaxResolutionSubsamplingPerDimension().length
									+ " when indexed dimensions = " + readerParams
											.getIndex()
											.getIndexStrategy()
											.getOrderedDimensionDefinitions().length);
				}
				else {
					final int cardinalityToSubsample = IndexUtils.getBitPositionFromSubsamplingArray(
							readerParams.getIndex().getIndexStrategy(),
							readerParams.getMaxResolutionSubsamplingPerDimension());

					final FixedCardinalitySkippingFilter skippingFilter = new FixedCardinalitySkippingFilter(
							cardinalityToSubsample);
					filterList.addFilter(
							skippingFilter);
				}
			}

			// Add distributable filters if requested, this has to be last
			// in the filter list for the dedupe filter to work correctly

			if (readerParams.getFilter() != null) {
				final HBaseDistributableFilter hbdFilter = new HBaseDistributableFilter();
				if (wholeRowEncoding) {
					hbdFilter.setWholeRowFilter(
							true);
				}

				final List<DistributableQueryFilter> distFilters = new ArrayList();
				distFilters.add(
						readerParams.getFilter());
				hbdFilter.init(
						distFilters,
						readerParams.getIndex().getIndexModel(),
						readerParams.getAdditionalAuthorizations());

				filterList.addFilter(
						hbdFilter);
			}
			else {
				final List<MultiDimensionalCoordinateRangesArray> coords = readerParams.getCoordinateRanges();
				if ((coords != null) && !coords.isEmpty()) {
					final HBaseNumericIndexStrategyFilter numericIndexFilter = new HBaseNumericIndexStrategyFilter(
							readerParams.getIndex().getIndexStrategy(),
							coords.toArray(
									new MultiDimensionalCoordinateRangesArray[] {}));
					filterList.addFilter(
							numericIndexFilter);
				}
			}
		}

		if (!filterList.getFilters().isEmpty()) {
			multiScanner.setFilter(
					filterList);
		}

		try {
			final ResultScanner resultScanner = operations.getScannedResults(
					multiScanner,
					readerParams.getIndex().getId().getString(),
					readerParams.getAdditionalAuthorizations());

			return resultScanner;
		}
		catch (final IOException e) {
			LOGGER.warn(
					"Could not get the results from scanner",
					e);
		}
		return null;
	}

	protected Scan getMultiScanner(
			final FilterList filterList,
			final Integer limit,
			final double[] maxResolutionSubsamplingPerDimension ) {
		// Single scan w/ multiple ranges
		final Scan multiScanner = createStandardScanner();

		final List<ByteArrayRange> ranges = readerParams.getQueryRanges().getCompositeQueryRanges();

		final MultiRowRangeFilter filter = getMultiRowRangeFilter(
				ranges);
		if (filter != null) {
			filterList.addFilter(
					filter);

			final List<RowRange> rowRanges = filter.getRowRanges();
			multiScanner.setStartRow(
					rowRanges.get(
							0).getStartRow());

			final RowRange stopRowRange = rowRanges.get(
					rowRanges.size() - 1);
			byte[] stopRowExclusive;
			if (stopRowRange.isStopRowInclusive()) {
				// because the end is always exclusive, to make an inclusive
				// stop row into exlusive all we need to do is add a traling 0
				stopRowExclusive = new byte[stopRowRange.getStopRow().length + 1];

				System.arraycopy(
						stopRowRange.getStopRow(),
						0,
						stopRowExclusive,
						0,
						stopRowExclusive.length - 1);
			}
			else {
				stopRowExclusive = stopRowRange.getStopRow();
			}
			multiScanner.setStopRow(
					stopRowExclusive);
		}

		return multiScanner;
	}

	protected Scan createStandardScanner() {
		final Scan scanner = new Scan();

		// Performance tuning per store options
		scanner.setCaching(
				operations.getScanCacheSize());
		scanner.setCacheBlocks(
				operations.isEnableBlockCache());

		// Only return the most recent version
		scanner.setMaxVersions(
				1);

		if ((readerParams.getAdapterIds() != null) && !readerParams.getAdapterIds().isEmpty()) {
			for (final ByteArrayId adapterId : readerParams.getAdapterIds()) {
				scanner.addFamily(
						adapterId.getBytes());
			}
		}

		if ((readerParams.getLimit() != null) && (readerParams.getLimit() > 0)
				&& (readerParams.getLimit() < scanner.getBatch())) {
			scanner.setBatch(
					readerParams.getLimit());
		}

		return scanner;
	}

	protected MultiRowRangeFilter getMultiRowRangeFilter(
			final List<ByteArrayRange> ranges ) {
		// create the multi-row filter
		final List<RowRange> rowRanges = new ArrayList<RowRange>();
		if ((ranges == null) || ranges.isEmpty()) {
			rowRanges.add(
					new RowRange(
							HConstants.EMPTY_BYTE_ARRAY,
							true,
							HConstants.EMPTY_BYTE_ARRAY,
							false));
		}
		else {
			for (final ByteArrayRange range : ranges) {
				if (range.getStart() != null) {
					final byte[] startRow = range.getStart().getBytes();
					byte[] stopRow;
					if (!range.isSingleValue()) {
						stopRow = range.getEndAsNextPrefix().getBytes();
					}
					else {
						stopRow = range.getStart().getNextPrefix();
					}

					final RowRange rowRange = new RowRange(
							startRow,
							true,
							stopRow,
							false);

					rowRanges.add(
							rowRange);
				}
			}
		}

		// Create the multi-range filter
		try {
			return new MultiRowRangeFilter(
					rowRanges);
		}
		catch (final IOException e) {
			LOGGER.error(
					"Error creating range filter.",
					e);
		}
		return null;
	}
}
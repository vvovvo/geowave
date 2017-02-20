package mil.nga.giat.geowave.core.index;

import java.util.List;

public class SinglePartitionQueryRanges
{
	private final ByteArrayId partitionKey;

	private final List<ByteArrayRange> sortKeyRanges;

	public SinglePartitionQueryRanges(
			final ByteArrayId partitionKey,
			final List<ByteArrayRange> sortKeyRanges ) {
		this.partitionKey = partitionKey;
		this.sortKeyRanges = sortKeyRanges;
	}

	public SinglePartitionQueryRanges(
			final ByteArrayId partitionKey ) {
		this.partitionKey = partitionKey;
		sortKeyRanges = null;
	}

	public SinglePartitionQueryRanges(
			final List<ByteArrayRange> sortKeyRanges ) {
		this.sortKeyRanges = sortKeyRanges;
		partitionKey = null;
	}
	public ByteArrayId getPartitionKey() {
		return partitionKey;
	}

	public List<ByteArrayRange> getSortKeyRanges() {
		return sortKeyRanges;
	}
}
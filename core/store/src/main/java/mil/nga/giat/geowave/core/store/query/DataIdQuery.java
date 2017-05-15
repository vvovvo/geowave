/*******************************************************************************
 * Copyright (c) 2013-2017 Contributors to the Eclipse Foundation
 * 
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License,
 * Version 2.0 which accompanies this distribution and is available at
 * http://www.apache.org/licenses/LICENSE-2.0.txt
 ******************************************************************************/
package mil.nga.giat.geowave.core.store.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.core.index.NumericIndexStrategy;
import mil.nga.giat.geowave.core.index.sfc.data.MultiDimensionalNumericData;
import mil.nga.giat.geowave.core.store.filter.DataIdQueryFilter;
import mil.nga.giat.geowave.core.store.filter.QueryFilter;
import mil.nga.giat.geowave.core.store.index.CommonIndexModel;
import mil.nga.giat.geowave.core.store.index.Index;

public class DataIdQuery implements
		Query
{
	private ByteArrayId adapterId;
	private List<ByteArrayId> dataIds;

	public DataIdQuery(
			ByteArrayId adapterId,
			ByteArrayId dataId ) {
		this.adapterId = adapterId;
		this.dataIds = Collections.singletonList(dataId);
	}

	public DataIdQuery(
			ByteArrayId adapterId,
			List<ByteArrayId> dataIds ) {
		this.adapterId = adapterId;
		this.dataIds = new ArrayList<ByteArrayId>(
				dataIds);
	}

	public ByteArrayId getAdapterId() {
		return adapterId;
	}

	public List<ByteArrayId> getDataIds() {
		return dataIds;
	}

	@Override
	public List<QueryFilter> createFilters(
			CommonIndexModel indexModel ) {
		List<QueryFilter> filters = new ArrayList<QueryFilter>();
		filters.add(new DataIdQueryFilter(
				adapterId,
				dataIds));
		return filters;
	}

	@Override
	public boolean isSupported(
			Index index ) {
		return true;
	}

	@Override
	public List<MultiDimensionalNumericData> getIndexConstraints(
			final NumericIndexStrategy indexStrategy ) {
		return Collections.emptyList();
	}

}

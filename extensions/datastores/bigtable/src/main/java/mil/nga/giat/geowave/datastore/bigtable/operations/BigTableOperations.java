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
package mil.nga.giat.geowave.datastore.bigtable.operations;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;

import mil.nga.giat.geowave.core.index.ByteArrayId;
import mil.nga.giat.geowave.datastore.bigtable.BigTableConnectionPool;
import mil.nga.giat.geowave.datastore.bigtable.operations.config.BigTableOptions;
import mil.nga.giat.geowave.datastore.hbase.operations.HBaseOperations;

public class BigTableOperations extends
		HBaseOperations
{
	public BigTableOperations(
			final BigTableOptions options )
			throws IOException {
		super(
				BigTableConnectionPool.getInstance().getConnection(
						options.getProjectId(),
						options.getInstanceId()),
				options.getGeowaveNamespace(),
				options.getHBaseOptions());
	}
	
	@Override
	protected boolean verifyColumnFamilies(
			final String[] columnFamilies,
			final TableName tableName,
			final boolean addIfNotExist )
			throws IOException {
		return true;
	}

	@Override
	public ResultScanner getScannedResults(
			Scan scanner,
			String tableName,
			String... authorizations )
			throws IOException {

		if (indexExists(
				new ByteArrayId(
						tableName))) {
			// TODO Cache locally b/c numerous checks can be expensive
			return super.getScannedResults(
					scanner,
					tableName,
					authorizations);
		}

		return new ResultScanner() {
			@Override
			public Iterator<Result> iterator() {
				return Collections.emptyIterator();
			}

			@Override
			public Result[] next(
					int nbRows )
					throws IOException {
				return null;
			}

			@Override
			public Result next()
					throws IOException {
				return null;
			}

			@Override
			public void close() {}
		};
	}

	public static BigTableOperations createOperations(
			final BigTableOptions options )
			throws IOException {
		return new BigTableOperations(
				options);
	}

}

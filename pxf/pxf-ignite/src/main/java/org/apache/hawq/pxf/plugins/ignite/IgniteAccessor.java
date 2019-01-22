package org.apache.hawq.pxf.plugins.ignite;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.hawq.pxf.api.OneRow;
import org.apache.hawq.pxf.api.ReadAccessor;
import org.apache.hawq.pxf.api.WriteAccessor;
import org.apache.hawq.pxf.api.UserDataException;
import org.apache.hawq.pxf.api.utilities.ColumnDescriptor;
import org.apache.hawq.pxf.api.utilities.InputData;
import org.apache.hawq.pxf.plugins.ignite.IgnitePlugin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Semaphore;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;

/**
 * PXF-Ignite accessor class
 */
public class IgniteAccessor extends IgnitePlugin implements ReadAccessor, WriteAccessor {
    private static final Log LOG = LogFactory.getLog(IgniteAccessor.class);

    // A pattern to cut extra parameters from 'InputData.dataSource' when write operation is performed. See {@link openForWrite()} for the details
    private static final Pattern writeAddressPattern = Pattern.compile("/(.*)/[0-9]*-[0-9]*_[0-9]*");

    // Protecting Semapthore for Ignite thick client variables
    private static Semaphore igniteSem = new Semaphore(1, true);
    // An instance of Ignite thick client and its configure path
    private static Ignite ignite = null;
    private static String currentConfigPath = null;

    // An instance of Ignite cache. Used by both write and read pipelines
    private IgniteCache<Object, Object> cache = null;

    // Read pipeline variables
    private FieldsQueryCursor<List<?>> readCursor = null;
    private Iterator<List<?>> readIterator = null;

    // Write pipeline variables
    private SqlFieldsQuery writeQuery = null;


    /**
     * Class constructor
     */
    public IgniteAccessor(InputData inputData) throws Exception {
        super(inputData);
        try {
            igniteSem.acquire();

            if ((ignite == null) || (!currentConfigPath.equals(configPath))) {
                if (ignite == null) {
                    if (configPath == null) {
                        throw new UserDataException("'CONFIG' parameter (path to Ignite configuration file) is required to connect to Ignite");
                    }
                }
                else {
                    LOG.info("Closing Ignite thick client with config at '" + currentConfigPath + "'...");
                    ignite.close();
                    ignite = null;
                    currentConfigPath = null;
                    LOG.info("Ignite thick client closed");
                    if (configPath == null) {
                        throw new RuntimeException("Ignite thick client closed successfully, query execution is prevented");
                    }
                }
                LOG.info("Launching Ignite thick client with config at '" + configPath + "'...");
                ignite = Ignition.start(configPath);
                currentConfigPath = configPath;
                LOG.info("Ignite thick client launched");
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Constructed IgniteAccessor");
            }
        }
        finally {
            igniteSem.release();
        }
    }

    /**
     * openForRead() implementation
     */
    @Override
    public boolean openForRead() throws Exception {
        // Instantiate cache
        try {
            igniteSem.acquire();
            cache = ignite.cache(cacheName);
            if (cache == null) {
                throw new RuntimeException("Ignite returned 'null' when creating cache '" + cacheName + "'");
            }
        }
        finally {
            igniteSem.release();
        }

        // Build SELECT query
        SqlFieldsQuery query = buildSelectQuery();
        query.setSchema(schema);
        query.setLazy(flagLazy);
        query.setReplicatedOnly(flagReplicatedOnly);

        // Get cursor
        if (LOG.isDebugEnabled()) {
            LOG.debug("openForRead(): making a request to Ignite. Query: '" + query.getSql() + "'");
        }
        readCursor = cache.query(query);
        readIterator = readCursor.iterator();
        if (LOG.isDebugEnabled()) {
            LOG.debug("openForRead() finished successfully");
        }

        return true;
    }

    /**
     * readNextObject() implementation
     */
    @Override
    public OneRow readNextObject() throws Exception {
        try {
            return new OneRow(readIterator.next());
        }
        catch(NoSuchElementException e) {
            return null;
        }
    }

    /**
     * closeForRead() implementation
     *
     * Any number of resources, each of which are to be closed, can be closed by this procedure. Only one exception will be thrown - the last that happened
     */
    @Override
    public void closeForRead() throws Exception {
        Exception exception = null;

        if (readCursor != null) {
            try {
                readCursor.close();
                readCursor = null;
            }
            catch(Exception e) {
                exception = e;
            }
        }
        if (cache != null) {
            try {
                cache.close();
                cache = null;
            }
            catch(Exception e) {
                exception = e;
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    /**
     * openForWrite() implementation
     */
    @Override
    public boolean openForWrite() throws Exception {
        // Instantiate cache
        try {
            igniteSem.acquire();
            cache = ignite.cache(cacheName);
            if (cache == null) {
                throw new RuntimeException("Ignite returned 'null' when creating cache '" + cacheName + "'");
            }
        }
        finally {
            igniteSem.release();
        }

        // Build INSERT query
        writeQuery = buildInsertQuery();
        writeQuery.setSchema(schema);

        return true;
    }

    /**
     * writeNextObject() implementation
     */
    @Override
    public boolean writeNextObject(OneRow currentRow) throws Exception {
        Object[] args = (Object[])currentRow.getData();
        writeQuery.setArgs(args);

        cache.query(writeQuery).getAll();

        return true;
    }

    /**
     * closeForWrite() implementation
     */
    @Override
    public void closeForWrite() throws Exception {
        Exception exception = null;

        if (cache != null) {
            try {
                cache.close();
                cache = null;
            }
            catch(Exception e) {
                exception = e;
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    /**
     * Build a SELECT query using the existing plugin parameters
     * @return a {@link SqlFieldsQuery} (with default settings) containing a SQL query
     * @throws Exception in case plugin parameters are incorrect or {@link WhereSQLBuilder} failed
     */
    private SqlFieldsQuery buildSelectQuery() throws Exception {
        StringBuilder sb = new StringBuilder();

        // Insert a list of fields to be selected
        ArrayList<ColumnDescriptor> columns = inputData.getTupleDescription();
        if (columns == null) {
            throw new UserDataException("Tuple description must be present.");
        }
        sb.append("SELECT ");
        for (int i = 0; i < columns.size(); i++) {
            ColumnDescriptor column = columns.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(column.columnName());
        }

        // Insert the name of the table to select values from
        sb.append(" FROM ");
        String tableName = inputData.getDataSource();
        if (tableName == null) {
            throw new UserDataException("Table name must be set as DataSource.");
        }
        sb.append(tableName);

        // Insert query constraints
        if (inputData.hasFilter()) {
            WhereSQLBuilder filterBuilder = new WhereSQLBuilder(inputData);
            String whereSql = filterBuilder.buildWhereSQL();

            if (whereSql != null) {
                sb.append(" WHERE ").append(whereSql);
            }
        }

        // Insert partition constraints
        IgnitePartitionFragmenter.buildFragmenterSql(inputData, sb);

        return new SqlFieldsQuery(sb.toString());
    }

    /**
     * Build an INSERT query using the existing plugin parameters
     * @return a {@link SqlFieldsQuery} (with default settings) containing a SQL query with placeholders
     * @throws UserDataException in case plugin parameters are incorrect
     *
     * This function also checks the external data source (target table name) against a regexp. At the moment there is no other way (except for the usage of user-defined parameters) to get correct name of that table: GPDB inserts extra data into the address, because it is required required by Hadoop.
     * If no extra data is present, the source is left unchanged
     */
    private SqlFieldsQuery buildInsertQuery() throws UserDataException {
        // Check data source
        String definedSource = inputData.getDataSource();
        Matcher matcher = writeAddressPattern.matcher(definedSource);
        if (matcher.find()) {
            inputData.setDataSource(matcher.group(1));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ");

        // Insert the table name
        String tableName = inputData.getDataSource();
        if (tableName == null) {
            throw new UserDataException("Table name must be set as DataSource.");
        }
        sb.append(tableName);

        // Insert the column names
        sb.append("(");
        ArrayList<ColumnDescriptor> columns = inputData.getTupleDescription();
        if (columns == null) {
            throw new UserDataException("Tuple description must be present.");
        }
        String fieldDivisor = "";
        for (int i = 0; i < columns.size(); i++) {
            sb.append(fieldDivisor);
            fieldDivisor = ", ";
            sb.append(columns.get(i).columnName());
        }
        sb.append(")");

        sb.append(" VALUES ");

        sb.append("(");
        fieldDivisor = "";
        for (int i = 0; i < columns.size(); i++) {
            sb.append(fieldDivisor);
            fieldDivisor = ", ";
            sb.append("?");
        }
        sb.append(")");

        return new SqlFieldsQuery(sb.toString());
    }
}

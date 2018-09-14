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

import org.apache.hawq.pxf.api.UserDataException;
import org.apache.hawq.pxf.api.utilities.InputData;
import org.apache.hawq.pxf.api.utilities.Plugin;

/**
 * PXF-Ignite base class.
 * This class manages the user-defined parameters provided in the query from PXF.
 * Implemented subclasses: {@link IgniteAccessor}, {@link IgniteResolver}.
 */
public class IgnitePlugin extends Plugin {
    /**
     * Class constructor. Parses and checks 'InputData'
     * @param inputData
     * @throws UserDataException if the request parameter is malformed
     */
    public IgnitePlugin(InputData inputData) throws UserDataException, NumberFormatException {
        super(inputData);

        String hostParameter = inputData.getUserProperty("HOST");
        String hostsParameter = inputData.getUserProperty("HOSTS");
        if (hostsParameter != null) {
            hosts = hostsParameter;
        }
        else if (hostParameter != null) {
            hosts = hostParameter;
        }
        else {
            throw new UserDataException("HOST or HOSTS parameter must be provided");
        }

        // This is not a required parameter, and null is a valid default value
        igniteCache = inputData.getUserProperty("IGNITE_CACHE");

        // This is not a required parameter, and null is a valid default value
        user = inputData.getUserProperty("USER");
        if (user != null) {
            // This is not a required parameter, and null is a valid default value
            password = inputData.getUserProperty("PASSWORD");
        }

        // This is not a required parameter
        String bufferSizeParameter = inputData.getUserProperty("BUFFER_SIZE");
        if (bufferSizeParameter != null) {
            bufferSize = Integer.parseInt(bufferSizeParameter);
            if (bufferSize <= 0) {
                throw new NumberFormatException("BUFFER_SIZE must be a positive integer");
            }
        }

        String lazyParameter = inputData.getUserProperty("LAZY");
        if (lazyParameter != null) {
            lazy = true;
        }

        String tcpNoDelayParameter = inputData.getUserProperty("I_TCP_NO_DELAY");
        if (tcpNoDelayParameter != null) {
            tcpNoDelay = true;
        }

        String replicatedOnlyParameter = inputData.getUserProperty("I_REPLICATED_ONLY");
        if (replicatedOnlyParameter != null) {
            replicatedOnly = true;
        }
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    // Connection parameters
    protected String hosts = null;
    protected String igniteCache = null;
    protected String user = null;
    protected String password = null;

    // ReceiveBufferSize or SendBufferSize (depends on type of query)
    protected int bufferSize = 0;

    // Lazy SELECTs
    protected boolean lazy = false;

    protected boolean tcpNoDelay = false;
    protected boolean replicatedOnly = false;
}

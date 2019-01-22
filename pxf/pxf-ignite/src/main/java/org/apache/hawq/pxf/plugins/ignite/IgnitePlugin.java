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
    public IgnitePlugin(InputData inputData) throws UserDataException {
        super(inputData);

        // Connection parameters
        configPath = inputData.getUserProperty("CONFIG");
        cacheName = inputData.getUserProperty("CACHE") != null ? inputData.getUserProperty("CACHE") : "DEFAULT";

        // Schema name
        schema = inputData.getUserProperty("SCHEMA");

        // Query execution flags
        flagLazy = inputData.getUserProperty("LAZY") != null ? true : false;
        flagReplicatedOnly = inputData.getUserProperty("REPLICATED_ONLY") != null ? true : false;
    }

    @Override
    public boolean isThreadSafe() {
        return true;
    }

    // Ignite configuration parameters
    protected String configPath = null;
    protected String cacheName = null;

    // Query parameters
    protected String schema = null;
    protected boolean flagReplicatedOnly;
    protected boolean flagLazy;
}

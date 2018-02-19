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

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.mockito.internal.util.reflection.Whitebox;
import org.apache.hawq.pxf.api.io.DataType;
import org.apache.hawq.pxf.api.utilities.ColumnDescriptor;
import org.apache.hawq.pxf.api.utilities.InputData;


@RunWith(PowerMockRunner.class)
@PrepareForTest({IgniteAccessor.class})
public class IgniteAccessorTest {
    IgniteAccessor readAccessor = null;
    IgniteAccessor writeAccessor = null;

    @Before
    public void prepareTestAccessors() throws Exception {
        InputData inputData = mock(InputData.class);
        ArrayList<ColumnDescriptor> columns = new ArrayList<>();
        columns.add(new ColumnDescriptor("id", DataType.INTEGER.getOID(), 0, "int4", null));
        columns.add(new ColumnDescriptor("cdate", DataType.DATE.getOID(), 1, "date", null));
        columns.add(new ColumnDescriptor("amt", DataType.FLOAT8.getOID(), 2, "float8", null));
        columns.add(new ColumnDescriptor("grade", DataType.TEXT.getOID(), 3, "text", null));
        when(inputData.getTupleDescription()).thenReturn(columns);
        when(inputData.getColumn(0)).thenReturn(columns.get(0));
        when(inputData.getColumn(1)).thenReturn(columns.get(1));
        when(inputData.getColumn(2)).thenReturn(columns.get(2));
        when(inputData.getColumn(3)).thenReturn(columns.get(3));
        Whitebox.setInternalState(inputData, "columns", columns);

        readAccessor = PowerMockito.spy(new IgniteAccessor(inputData));

    }

    @Test(expected = Exception.class)
    public void testReadAccess() throws Exception {

    }
}

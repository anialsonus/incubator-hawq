# Accessing Ignite database using PXF

The PXF Ignite plug-in enables to access the [Apache Ignite database](https://ignite.apache.org/) (both read and write operations are supported) via Ignite thin connector for Java.


## Prerequisites

Check the following before using the plug-in:

* The Ignite plug-in is installed on all PXF nodes;

* The Apache Ignite client is installed and running.


## Syntax

```
CREATE [READABLE | WRITABLE] EXTERNAL TABLE <table_name> (
    <column_name> <data_type>[, <column_name> <data_type>, ...] | LIKE <other_table>
)
LOCATION ('pxf://<ignite_table_name>?PROFILE=Ignite[&<extra-parameter>&<extra-parameter>&...]')
FORMAT 'CUSTOM' (formatter='pxfwritable_import');
```
where each `<extra-parameter>` is one of the following:
* `CONFIG=<path>`. Path to Ignite thick client configuration. If not present, close the last opened client;
* `CACHE=<cache_name>`. Name of a cache in Ignite to use. If not present, `"Default"` is used;
* `SCHEMA=<schema_name>`. Name of a schema in Ignite to use. If not present, current cache schema is used;
* `LAZY=<any_value>`. If this parameter is present, tell Ignite to perform lazy SELECTs (the Ignite will not store data in thick client; instead, it will send it in portions to PXF over time). This may increase query execution time, but will prevent some out-of-memory crashes on Ignite side;
* `REPLICATED_ONLY=<any_value>`. If this parameter is present, tell Ignite the query is over "replicated" tables. This is a hint for potentially more effective execution;
* `PARTITION_BY=<column>:<column_type>`. See below;
* `RANGE=<start_value>:<end_value>`. See below;
* `INTERVAL=<value>[:<unit>]`. See below.


## Write access

PXF Ignite plugin supports `INSERT` queries. However, due to the usage of REST API, this function has a technical limit: URL can not be longer than approx. 2000 characters. This makes the `INSERT` of very long tuples of data impossible.

Due to this limitation, the recommended value of `BUFFER_SIZE` for `INSERT` queries is `1`. Note that this slightly decreases the perfomance.


## Partitioning
### Introduction

PXF Ignite plugin supports simultaneous **read** access to the Apache Ignite database from multiple PXF segments. *Partitioning* should be used in order to perform such operation.

This feature is optional. If partitioning is not used, all the data will be retrieved by a single PXF segment.


### Mechanism

Partitioning in PXF Ignite plug-in works just like in PXF JDBC plug-in.

If partitioning is activated (a valid set of the required parameters is present in the `EXTERNAL TABLE` description; see syntax below), the SELECT query is split into a set of small queries, each of which is called a *fragment*. All the fragments are processed by separate PXF instances simultaneously. If there are more fragments than PXF instances, some instances will process more than one fragment; if only one PXF instance is available, it will process all the fragments.

Extra constraints (`WHERE` expressions) are automatically added to each fragment to guarantee that every tuple of data is retrieved from the Apache Ignite database exactly once.


### Syntax

To use partitions, add a set of `<ignite-parameter>`s:
```
&PARTITION_BY=<column>:<column_type>&RANGE=<start_value>:<end_value>[&INTERVAL=<value>[:<unit>]]
```

* The `PARTITION_BY` parameter indicates which column to use as the partition column. Only one column can be used as a partition column.
    * The `<column>` is the name of a partition column;
    * The `<column_type>` is the datatype of a partition column. At the moment, the **supported types** are `INT`, `DATE` and `ENUM`. The `DATE` format is `yyyy-MM-dd`.

* The `RANGE` parameter indicates the range of data to be queried. If the partition type is `ENUM`, the `RANGE` parameter must be a list of values, each of which forms its own fragment. In case of `INT` and `DATE` partitions, this parameter must be a finite left-closed range ("infinity" values are not supported):
    * `[ <start_value> ; <end_value> )`
    * `... >= start_value AND ... < end_value`;

* The `INTERVAL` parameter is **required** for `INT` and `DATE` partitions. This parameter is ignored if `<column_type>` is `ENUM`.
    * The `<value>` is the size of each fragment (the last one may be smaller). Note that by default PXF does not support more than 100 fragments;
    * The `<unit>` **must** be provided if `<column_type>` is `DATE`. At the moment, only `year`, `month` and `day` are supported. This parameter is ignored in case of any other `<column_type>`.

Example partitions:
* `&PARTITION_BY=id:int&RANGE=42:142&INTERVAL=2`
* `&PARTITION_BY=createdate:date&RANGE=2008-01-01:2010-01-01&INTERVAL=1:month`
* `&PARTITION_BY=grade:enum&RANGE=excellent:good:general:bad`

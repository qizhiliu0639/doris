// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

suite("test_nereids_dup_tab_decimal_nullable") {

    sql "SET enable_vectorized_engine=true"
    sql "SET enable_nereids_planner=true"
    sql "SET enable_fallback_to_original_planner=false"

    def table1 = "test_dup_tab_decimal_nullable"

    sql "drop table if exists ${table1}"

    sql """
        CREATE TABLE IF NOT EXISTS `${table1}` (
          `siteid` decimal(10, 5) NULL COMMENT "",
          `citycode` decimal(10, 5) NULL COMMENT "",
          `userid` decimal(10, 5) NULL COMMENT "",
          `pv` decimal(10, 5) NULL COMMENT ""
        ) ENGINE=OLAP
        DUPLICATE KEY(`siteid`)
        COMMENT "OLAP"
        DISTRIBUTED BY HASH(`siteid`) BUCKETS 1
        PROPERTIES (
        "replication_allocation" = "tag.location.default: 1",
        "in_memory" = "false",
        "storage_format" = "V2"
        )
    """

    sql "set enable_vectorized_engine = false"

    sql """insert into ${table1} values(1.1,1.2,1.3,1.4),
        (1.1,2.2,2.3,3.4),
        (2.1,2.2,2.3,2.4),
        (3.1,3.2,3.3,3.4),
        (4.1,4.2,4.3,4.4),
        (null,2,null,4)
    """

    sql "set enable_vectorized_engine = true"

    // query decimal
    test {
        sql "select siteid from ${table1} order by siteid"
        result([[null],[1.10000],[1.10000],[2.10000],[3.10000],[4.10000]])
    }
    // FIXME(wb) this may failed because of regression framework
//    test {
//        sql "select siteid,citycode from ${table1} order by siteid,citycode"
//        result([[null,2],[1.1,1.2],[1.1,2.2],[2.1,2.2],[3.1,3.2],[4.1,4.2]])
//    }

    // pred is decimal
    test {
        sql "select siteid from ${table1} where siteid=4.1"
        result([[4.10000]])
    }
    test {
        sql "select siteid from ${table1} where siteid=1.1"
        result([[1.10000],[1.10000]])
    }

    // pred not key
    test {
        sql "select citycode from ${table1} where citycode=2.2"
        result([[2.20000],[2.20000]])
    }
    test {
        sql "select citycode from ${table1} where citycode=4.2"
        result([[4.20000]])
    }

    // pred column not same with read column
    test {
        sql "select citycode from ${table1} where siteid=4.1"
        result([[4.20000]])
    }

    test {
        sql "select citycode from ${table1} where siteid=1.1 order by citycode"
        result([[1.20000],[2.20000]])
    }

    // pred column not same with read column;pred is not key
    test {
        sql "select siteid from ${table1} where citycode=2.2 order by siteid"
        result([[1.10000],[2.10000]])
    }

    test {
        sql "select siteid from ${table1} where citycode=4.2 order by siteid"
        result([[4.10000]])
    }

    // int pred
    test {
        sql "select userid from ${table1} where userid in(2.3)"
        result([[2.30000],[2.30000]])
    }

    test {
        sql "select userid from ${table1} where userid not in(2.3) order by userid"
        result([[1.30000],[3.30000],[4.30000]])
    }

    // is null
    test {
        sql "select userid from  ${table1} where userid is null"
        result([[null]])
    }
    // is not null
    test {
        sql "select userid from  ${table1} where userid is not null order by userid"
        result([[1.30000],[2.30000],[2.30000],[3.30000],[4.30000]])
    }

    sql "drop table if exists ${table1}"

}

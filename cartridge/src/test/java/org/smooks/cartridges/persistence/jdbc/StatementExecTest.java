/*-
 * ========================LICENSE_START=================================
 * smooks-persistence-cartridge
 * %%
 * Copyright (C) 2020 Smooks
 * %%
 * Licensed under the terms of the Apache License Version 2.0, or
 * the GNU Lesser General Public License version 3.0 or later.
 *
 * SPDX-License-Identifier: Apache-2.0 OR LGPL-3.0-or-later
 *
 * ======================================================================
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ======================================================================
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * =========================LICENSE_END==================================
 */
package org.smooks.cartridges.persistence.jdbc;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.smooks.testkit.HsqlServer;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class StatementExecTest {

    private HsqlServer hsqlServer;

    @Before
    public void before() throws Exception {
        hsqlServer = new HsqlServer(9995);
        hsqlServer.execScript(getClass().getResourceAsStream("test.script"));
    }

    @After
    public void after() throws Exception {
        hsqlServer.stop();
    }

    @Test
    public void test_unjoined() throws SQLException {
        Connection connection = hsqlServer.getConnection();
        StatementExec exec1 = new StatementExec("select * from CUSTOMERS where CUSTOMERNUMBER = ?");
        StatementExec exec2 = new StatementExec("insert into CUSTOMERS (CUSTOMERNUMBER, CUSTOMERNAME) values (?, ?)");
        List<Map<String, Object>> resultSet;

        resultSet = exec1.executeUnjoinedQuery(connection, 1234);
        assertEquals(0, resultSet.size());

        exec2.executeUnjoinedUpdate(connection, 1234, "Tom Fennelly");
        resultSet = exec1.executeUnjoinedQuery(connection, 1234);
        assertEquals("[{CUSTOMERNUMBER=1234, CUSTOMERNAME=Tom Fennelly}]", resultSet.toString());
    }

    @Test
    public void test_joined_and_merged() throws SQLException {
        Connection connection = hsqlServer.getConnection();
        StatementExec exec1 = new StatementExec("select * from ORDERS");
        StatementExec exec2 = new StatementExec("select * from CUSTOMERS where CUSTOMERNUMBER = ${CUSTOMERNUMBER}");
        List<Map<String, Object>> resultSet;

        resultSet = exec1.executeUnjoinedQuery(connection);
        assertEquals("[{ORDERNUMBER=1, CUSTOMERNUMBER=1, PRODUCTCODE=123}, {ORDERNUMBER=2, CUSTOMERNUMBER=2, PRODUCTCODE=456}, {ORDERNUMBER=3, CUSTOMERNUMBER=1, PRODUCTCODE=789}]", resultSet.toString());
        for (Map<String, Object> row : resultSet) {
            exec2.executeJoinedQuery(connection, row);
        }
        assertEquals("[{ORDERNUMBER=1, CUSTOMERNUMBER=1, PRODUCTCODE=123, CUSTOMERNAME=Tom Fennelly}, {ORDERNUMBER=2, CUSTOMERNUMBER=2, PRODUCTCODE=456, CUSTOMERNAME=Mike Fennelly}, {ORDERNUMBER=3, CUSTOMERNUMBER=1, PRODUCTCODE=789, CUSTOMERNAME=Tom Fennelly}]", resultSet.toString());
    }

    @Test
    public void test_joined_and_unmerged() throws SQLException {
        Connection connection = hsqlServer.getConnection();
        StatementExec exec1 = new StatementExec("select * from ORDERS");
        StatementExec exec2 = new StatementExec("select * from CUSTOMERS where CUSTOMERNUMBER = ${CUSTOMERNUMBER}");
        List<Map<String, Object>> resultSet;

        resultSet = exec1.executeUnjoinedQuery(connection);
        assertEquals("[{ORDERNUMBER=1, CUSTOMERNUMBER=1, PRODUCTCODE=123}, {ORDERNUMBER=2, CUSTOMERNUMBER=2, PRODUCTCODE=456}, {ORDERNUMBER=3, CUSTOMERNUMBER=1, PRODUCTCODE=789}]", resultSet.toString());

        testRow(connection, exec2, resultSet.get(0), "[{CUSTOMERNUMBER=1, CUSTOMERNAME=Tom Fennelly}]");
        testRow(connection, exec2, resultSet.get(1), "[{CUSTOMERNUMBER=2, CUSTOMERNAME=Mike Fennelly}]");
        testRow(connection, exec2, resultSet.get(2), "[{CUSTOMERNUMBER=1, CUSTOMERNAME=Tom Fennelly}]");
        assertEquals("[{ORDERNUMBER=1, CUSTOMERNUMBER=1, PRODUCTCODE=123}, {ORDERNUMBER=2, CUSTOMERNUMBER=2, PRODUCTCODE=456}, {ORDERNUMBER=3, CUSTOMERNUMBER=1, PRODUCTCODE=789}]", resultSet.toString());
    }

    private void testRow(Connection connection, StatementExec exec2, Map<String, Object> row, String expected) throws SQLException {
        List<Map<String, Object>> resultSet2 = new ArrayList<Map<String, Object>>();
        exec2.executeJoinedQuery(connection, row, resultSet2);
        assertEquals(expected, resultSet2.toString());
    }

    @Test
    public void test_joined_insert_update() throws SQLException {
        Connection connection = hsqlServer.getConnection();
        Map<String, Object> beanMap = new HashMap<String, Object>();
        Map<String, Object> orderBean = new HashMap<String, Object>();

        beanMap.put("order", orderBean);
        orderBean.put("id", 10);
        orderBean.put("cust", 2);
        orderBean.put("prod", 4444);
        StatementExec exec1 = new StatementExec("select * from ORDERS");
        StatementExec exec2 = new StatementExec("insert into ORDERS (ORDERNUMBER, CUSTOMERNUMBER, PRODUCTCODE) values (${order.id}, ${order.cust}, ${order.prod})");
        StatementExec exec3 = new StatementExec("update ORDERS set PRODUCTCODE = 5555 where ORDERNUMBER = ${order.id} and CUSTOMERNUMBER = ${order.cust}");

        assertEquals(3, exec1.executeUnjoinedQuery(connection).size());
        exec2.executeJoinedStatement(connection, beanMap);
        assertEquals(4, exec1.executeUnjoinedQuery(connection).size());
        assertEquals("{ORDERNUMBER=10, CUSTOMERNUMBER=2, PRODUCTCODE=4444}", exec1.executeUnjoinedQuery(connection).get(3).toString());

        orderBean.put("prod", 5555);
        exec3.executeJoinedStatement(connection, beanMap);
        assertEquals("{ORDERNUMBER=10, CUSTOMERNUMBER=2, PRODUCTCODE=5555}", exec1.executeUnjoinedQuery(connection).get(3).toString());
    }

    @Test
    public void test_bulk_insert() throws SQLException {
        Connection connection = hsqlServer.getConnection();
        List<Map<String, Object>> orders = new ArrayList<Map<String, Object>>();
        Map<String, Object> beanMap = new HashMap<String, Object>();

        addOrder(orders, 10, 2, 444);
        addOrder(orders, 11, 1, 555);
        addOrder(orders, 12, 2, 666);
        beanMap.put("orders", orders);

        StatementExec exec1 = new StatementExec("select * from ORDERS");
        StatementExec exec2 = new StatementExec("insert into ORDERS (ORDERNUMBER, CUSTOMERNUMBER, PRODUCTCODE) values (${id}, ${cust}, ${prod})");

        assertEquals(3, exec1.executeUnjoinedQuery(connection).size());
        exec2.executeJoinedStatement(connection, orders);
        assertEquals(6, exec1.executeUnjoinedQuery(connection).size());
        assertEquals("{ORDERNUMBER=10, CUSTOMERNUMBER=2, PRODUCTCODE=444}", exec1.executeUnjoinedQuery(connection).get(3).toString());
        assertEquals("{ORDERNUMBER=11, CUSTOMERNUMBER=1, PRODUCTCODE=555}", exec1.executeUnjoinedQuery(connection).get(4).toString());
        assertEquals("{ORDERNUMBER=12, CUSTOMERNUMBER=2, PRODUCTCODE=666}", exec1.executeUnjoinedQuery(connection).get(5).toString());
    }

    private void addOrder(List<Map<String, Object>> orders, int id, int customerId, int productId) {
        Map<String, Object> orderBean = new HashMap<String, Object>();

        orders.add(orderBean);
        orderBean.put("id", id);
        orderBean.put("cust", customerId);
        orderBean.put("prod", productId);
    }
}

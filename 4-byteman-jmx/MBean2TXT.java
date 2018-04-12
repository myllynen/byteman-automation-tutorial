/*
 * Copyright (C) 2018 Marko Myllynen <myllynen@redhat.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is subject to the "Classpath" exception as provided
 * by the authors in the LICENSE file that accompanied this code.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

public class MBean2TXT {
    private static String JVM_TARGET = "service:jmx:rmi:///jndi/rmi://localhost:9875/jmxrmi";

    public static void main(String[] args) {
        try {
            JMXServiceURL url = new JMXServiceURL(JVM_TARGET);
            JMXConnector connector = JMXConnectorFactory.connect(url);
            MBeanServerConnection connection = connector.getMBeanServerConnection();
            while (true) {
                fetchAndPrintItems(connection);
                Thread.sleep(5000);
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private static void fetchAndPrintItems(MBeanServerConnection connection) {
        System.out.print("ProfTest statistics [JMX] - ");
        System.out.print(new java.sql.Timestamp(System.currentTimeMillis()));
        System.out.println(":\n");

        try {
            ObjectName query = new ObjectName("proftest:type=Statistics");
            Set<ObjectName> mbeanNames = connection.queryNames(query, null);
            for (ObjectName mbean: mbeanNames) {
                try {
                    for (MBeanAttributeInfo attr: connection.getMBeanInfo(mbean).getAttributes()) {
                        if (!attr.isReadable()) {
                            System.err.println("Skipping unreadable attribute: " + attr.getName());
                            continue;
                        }
                        try {
                            Object value = connection.getAttribute(mbean, attr.getName());
                            printItem(mbean, attr, value.toString());
                        } catch (Exception ex) { ex.printStackTrace(); }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private static void printItem(ObjectName mbean, MBeanAttributeInfo attr, String value) {
        System.out.println(attr.getDescription() + ": " + value);
        if (!attr.getDescription().contains(" a") &&
            !attr.getDescription().contains(" b")) {
            System.out.println();
        }
    }
}

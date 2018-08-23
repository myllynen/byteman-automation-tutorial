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

import java.util.Properties;
import com.sun.tools.attach.VirtualMachine;

public class JMXEnabler {
    private static String[] params = {
        //"com.sun.management.jmxremote",
        "com.sun.management.jmxremote.access.file",
        "com.sun.management.jmxremote.authenticate",
        //"com.sun.management.jmxremote.config.file",
        "com.sun.management.jmxremote.host",
        //"com.sun.management.jmxremote.local.only",
        "com.sun.management.jmxremote.login.config",
        "com.sun.management.jmxremote.password.file",
        //"com.sun.management.jmxremote.password.toHashes",
        "com.sun.management.jmxremote.port",
        "com.sun.management.jmxremote.registry.ssl",
        //"com.sun.management.jmxremote.serial.filter.pattern",
        "com.sun.management.jmxremote.ssl",
        "com.sun.management.jmxremote.ssl.config.file",
        "com.sun.management.jmxremote.ssl.enabled.cipher.suites",
        "com.sun.management.jmxremote.ssl.enabled.protocols",
        "com.sun.management.jmxremote.ssl.need.client.auth",
    };

    private static void usage() {
        System.err.println("Usage: java <options> JMXEnabler <pid>");
        System.err.println("Supported options:");
        for (String param: params) {
            System.err.println("  -D" + param);
        }
        System.err.println("Target port will be set to com.sun.management.jmxremote.port + 1.");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 1 || args[0].substring(0, 1).equals("-")) {
            usage();
        }

        VirtualMachine vm = VirtualMachine.attach(args[0]);
        Properties props = new Properties();

        for (String param: params) {
            if (System.getProperty(param) != null) {
                // No way to stop our JMX so need a different port to bind to
                if (param.equals("com.sun.management.jmxremote.port")) {
                    int port = Integer.parseInt(System.getProperty(param)) + 1;
                    props.put(param, port);
                    continue;
                }
                props.put(param, System.getProperty(param));
            }
        }

        if (props.isEmpty()) {
            usage();
        }

        System.out.println(props);
        vm.startManagementAgent(props);
        vm.detach();
    }
}

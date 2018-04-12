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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.jboss.byteman.contrib.dtest.RuleConstructor;

public class RuleCreator {
    // Help text program name
    private static final String programName            = "RuleCreator";

    // Command line options
    private static final String OPT_HELP_SHORT         = "-h";
    private static final String OPT_HELP               = "-help";
    private static final String OPT_HELP_LONG          = "--help";
    private static final String OPT_INPUT_FILE         = "--input-file";
    private static final String OPT_OUTPUT_FILE        = "--output-file";
    private static final String OPT_HELPER_CLASS       = "--helper-class";
    private static final String OPT_REGISTER_CLASS     = "--register-class";
    private static final String OPT_REGISTER_METHOD    = "--register-method";
    private static final String OPT_REGISTER_ACTION    = "--register-action";
    private static final String OPT_REGISTER_OBJECT    = "--register-object";
    private static final String OPT_INSTANCE_COUNTS    = "--instance-counts";
    private static final String OPT_INSTANCE_LIFETIMES = "--instance-lifetimes";
    private static final String OPT_CALL_COUNTS        = "--call-counts";
    private static final String OPT_CALL_EXECTIMES     = "--call-exectimes";

    // Defaults
    private static final String DFL_INPUT_FILE         = "targets.txt";
    private static final String DFL_OUTPUT_FILE        = "rules.btm";
    private static final String DFL_HELPER_CLASS       = "proftest.JMXHelper";
    private static final String DFL_REGISTER_CLASS     = "proftest.ProfTest";
    private static final String DFL_REGISTER_METHOD    = "main";
    private static final String DFL_REGISTER_ACTION    = "registerMBean";
    private static final String DFL_REGISTER_OBJECT    = "byteman:type=Statistics";

    // Parameters
    private String inputFile                           = DFL_INPUT_FILE;
    private String outputFile                          = DFL_OUTPUT_FILE;
    private String helperClass                         = DFL_HELPER_CLASS;
    private String registerClass                       = DFL_REGISTER_CLASS;
    private String registerMethod                      = DFL_REGISTER_METHOD;
    private String registerAction                      = DFL_REGISTER_ACTION;
    private String registerObject                      = DFL_REGISTER_OBJECT;
    private boolean instanceCounts                     = false;
    private boolean instanceLifetimes                  = false;
    private boolean callCounts                         = false;
    private boolean callExectimes                      = false;

    public RuleCreator(String[] args) {
        parseArguments(args);
    }

    public void parseArguments(final String[] args) throws IllegalArgumentException {
        List<String> argsList = Arrays.asList(args);
        if (argsList.contains(OPT_HELP_SHORT) || argsList.contains(OPT_HELP) || argsList.contains(OPT_HELP_LONG)) {
            usage();
            System.exit(0);
        }
        if (argsList.contains(OPT_INSTANCE_COUNTS))    instanceCounts = true;
        if (argsList.contains(OPT_INSTANCE_LIFETIMES)) instanceLifetimes = true;
        if (argsList.contains(OPT_CALL_COUNTS))        callCounts = true;
        if (argsList.contains(OPT_CALL_EXECTIMES))     callExectimes = true;
        for (Iterator<String> iter = argsList.iterator(); iter.hasNext(); ) {
            String arg = iter.next();
            if (arg.equals(OPT_INSTANCE_COUNTS) || arg.equals(OPT_INSTANCE_LIFETIMES) ||
                arg.equals(OPT_CALL_COUNTS) || arg.equals(OPT_CALL_EXECTIMES)) {
                continue;
            }
            try {
                if (arg.equals(OPT_INPUT_FILE)) {
                    inputFile = iter.next();
                } else if (arg.equals(OPT_OUTPUT_FILE)) {
                    outputFile = iter.next();
                } else if (arg.equals(OPT_HELPER_CLASS)) {
                    helperClass = iter.next();
                } else if (arg.equals(OPT_REGISTER_CLASS)) {
                    registerClass = iter.next();
                } else if (arg.equals(OPT_REGISTER_METHOD)) {
                    registerMethod = iter.next();
                } else if (arg.equals(OPT_REGISTER_ACTION)) {
                    registerAction = iter.next();
                } else if (arg.equals(OPT_REGISTER_OBJECT)) {
                    registerObject = iter.next();
                } else {
                    throw new IllegalArgumentException("Unrecognized option: " + arg);
                }
            } catch (NoSuchElementException ex) {
                throw new IllegalArgumentException("Option requires an argument: " + arg);
            }
        }
    }

    private RuleConstructor createRegisterMBeanRule(String clazz, String method, String action, String objectName) {
        return RuleConstructor.createRule("Register dynamic MBean")
            .onClass(clazz)
            .inMethod(method)
            .helper(helperClass)
            .atEntry()
            .ifTrue()
            .doAction(action + "(\"" + objectName + "\")");
    }

    private RuleConstructor createEntryRule(String ruleName, String clazz, String method, String action) {
        return RuleConstructor.createRule(ruleName)
            .onClass(clazz)
            .inMethod(method)
            .helper(helperClass)
            .atEntry()
            .ifTrue()
            .doAction(action);
    }

    private RuleConstructor createExitRule(String ruleName, String clazz, String method, String action) {
        return RuleConstructor.createRule(ruleName)
            .onClass(clazz)
            .inMethod(method)
            .helper(helperClass)
            .atExit()
            .ifTrue()
            .doAction(action);
    }

    public StringBuilder createRules() throws FileNotFoundException, IOException {
        StringBuilder ruleScriptBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {

            ruleScriptBuilder.append(createRegisterMBeanRule(registerClass, registerMethod, registerAction, registerObject).build());

            String prevClazz = null;
            for (String line; (line = br.readLine()) != null; ) {
                String clazz = line.substring(0, line.indexOf("#"));
                String method = line.substring(line.indexOf("#") + 1);

                if (prevClazz == null || !prevClazz.equals(clazz)) {
                    if (instanceCounts) {
                        String ruleName = "Increment instance count: " + clazz;
                        String action = "incrementInstanceCount($CLASS);";
                        ruleScriptBuilder.append(createExitRule(ruleName, clazz, "<init>", action).build());
                    }
                    if (instanceLifetimes) {
                        String ruleName = "Record instance creation time: " + clazz;
                        String action = ("recordInstanceCreationTime($CLASS, $0);");
                        ruleScriptBuilder.append(createEntryRule(ruleName, clazz, "<init>", action).build());

                        ruleName = "Record instance lifetime: " + clazz;
                        action = "recordInstanceLifetime($CLASS, $0);";
                        ruleScriptBuilder.append(createExitRule(ruleName, clazz, "run", action).build());
                    }
                }
                prevClazz = clazz;

                if (callCounts) {
                    String ruleName = "Increment call count: " + clazz + " - " + method;
                    String action = "incrementMethodCallCount($CLASS, $METHOD);";
                    ruleScriptBuilder.append(createEntryRule(ruleName, clazz, method, action).build());
                }
                if (callExectimes) {
                    String ruleName = "Record call time of method: " + clazz + " - " + method;
                    String action = "recordMethodCallTime($CLASS, $METHOD);";
                    ruleScriptBuilder.append(createEntryRule(ruleName, clazz, method, action).build());

                    ruleName = "Record execution time of method: " + clazz + " - " + method;
                    action = "recordMethodExecTime($CLASS, $METHOD);";
                    ruleScriptBuilder.append(createExitRule(ruleName, clazz, method, action).build());
                }
            }
        }
        return ruleScriptBuilder;
    }

    public void writeRules(StringBuilder ruleScriptBuilder) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFile)))) {
            writer.write(ruleScriptBuilder.toString());
        }
    }

    private static void usage() {
        PrintStream out = System.out;
        out.println("Usage:");
        out.println("  " + programName + " [options]");
        out.println("where [options] include:");
        out.println("  " + OPT_HELP + "                  Print this message");
        out.println("  " + OPT_INPUT_FILE + "            Specify input file to read monitored targets from");
        out.println("  " + OPT_OUTPUT_FILE + "           Specify output file to write created rule script to");
        out.println("  " + OPT_HELPER_CLASS + "          Specify Byteman helper class to use in created script");
        out.println("  " + OPT_REGISTER_CLASS + "        Specify class to register dynamic MBean on");
        out.println("  " + OPT_REGISTER_METHOD + "       Specify method to register dynamic MBean on");
        out.println("  " + OPT_REGISTER_ACTION + "       Specify action (method) to register dynamic MBean with");
        out.println("  " + OPT_REGISTER_OBJECT + "       Specify object (name) to register dynamic MBean with");
        out.println("  " + OPT_INSTANCE_COUNTS + "       Write rules for monitoring instance counts");
        out.println("  " + OPT_INSTANCE_LIFETIMES + "    Write rules for monitoring instance lifetimes");
        out.println("  " + OPT_CALL_COUNTS + "           Write rules for monitoring method call counts");
        out.println("  " + OPT_CALL_EXECTIMES + "        Write rules for monitoring method call execution times");
    }

    public static void main(String[] args) {
        try {
            RuleCreator rc = new RuleCreator(args);
            rc.writeRules(rc.createRules());
        } catch (Exception ex) {
            System.err.println(programName + " error: " + ex.getMessage());
            usage();
            System.exit(1);
        }
    }
}

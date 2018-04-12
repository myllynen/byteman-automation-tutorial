# Byteman Automation Tool

[![License: GPLv2](https://img.shields.io/badge/license-GPLv2-brightgreen.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.en.html)

## Introduction

This page introduces a [Byteman](http://byteman.jboss.org/) automation
tool that allows custom, on-the-fly instrumentation and monitoring of
_unmodified_ Java applications, and exposing data to any external tool
using the standard JMX technology.

Although using _the tool does not necessarily require Byteman
knowledge_, it is still recommended to check the
[Byteman Automation Guide](https://github.com/myllynen/byteman-automation-tutorial)
for basic Byteman introduction and to understand the basic operation of
this tool.

The tool can be customized and extended as needed, this initial version
provides statistics from Java applications for the following metrics:

* method average execution time
* number of live instances of a class
* number of objects instantiated from a class
* number of calls for any selected instance methods
* instance average lifetime (for classes which implement
  [Runnable](https://docs.oracle.com/javase/10/docs/api/java/lang/Runnable.html))

Additional Byteman capabilities which could be utilized to customize and
extend the tool include triggers for variable/object updates, running
custom code at any point of application code, coordinating application
threads, evaluating conditions, and so forth. For more details, see
[Byteman Programmer's Guide](http://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html).

## Implementation Overview

The tool reads a plain text configuration file listing classes and
methods where to install the instrumentation selected on command line.
It then creates a Byteman script to leverage Byteman's bytecode
manipulation capabilities to transform starting or running Java
applications. Byteman itself uses the
[ASM framework](http://asm.ow2.org/) and is loaded as a Java agent
using the
[Instrumentation API](https://docs.oracle.com/javase/10/docs/api/java/lang/instrument/package-summary.html)
at application startup or at any point of application lifecycle with
Byteman convenience scripts leveraging the
[Attach API](https://docs.oracle.com/javase/10/docs/api/com/sun/tools/attach/package-summary.html)
(which since Java 9 does not require the separate _tools.jar_ anymore).

Once running, the application has been transformed to use methods of a
helper class part of the tool converting application events into
statistics available over JMX. Unlike with, for example,
[Proxy](https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html)
or
[Prometheus instrumentation](https://prometheus.io/docs/practices/instrumentation/),
tracing can be extended to new areas of application or libraries
on-the-fly without any code changes or even without restarting the
application.

Byteman or the tool do not rely on
[JNI](https://en.wikipedia.org/wiki/Java_Native_Interface) or
[JVM TI](https://en.wikipedia.org/wiki/Java_Virtual_Machine_Tools_Interface)
so they are platform agnostic and supported by all modern JVMs.

This approach provides the following benefits and options:

* no application code changes needed
* can be applied to components for which source is not available
* can be enabled on class/method level, no one giant switch
* can be enabled and disabled on-the-fly as needed
* allows for customization and extensibility
* results available over standard JMX
* fully open source solution

Obviously, the best approach is highly dependent on each use case.
For example, the following aspects might need to be considered:

* using containers or not
* test or production environment
* easily reproducible or once-a-month issue
* possible to use other troubleshooting tools or not
* are application/component developers reachable or not
* previously added instrumentation present in application code or not

In-depth considerations of these aspects are out of scope for this
document and are merely mentioned as a base for discussions which of the
many available methodologies and tools to use in any particular
situation; this tool is often not the right choice but may be very
helpful under certain circumstances.

## Byteman Installation

Byteman is packaged for many distributions but installing the latest
version locally without additional privileges is easy (copypasting the
below snippet into a terminal is enough):

```
vers=4.0.2
wget http://downloads.jboss.org/byteman/$vers/byteman-download-$vers-bin.zip
unzip byteman-download-$vers-bin.zip
export BYTEMAN_HOME=$(pwd)/byteman-download-$vers
export PATH=$BYTEMAN_HOME/bin:$PATH
```

Optionally, after BYTEMAN_HOME has been set, _.bat_ files can be removed
and _.sh_ suffixes can be removed:

```
find $BYTEMAN_HOME/bin -name '*.bat' -print | xargs rm -f
find $BYTEMAN_HOME/bin -name '*.sh' -print | sed 'p;s/\.sh//' | xargs -n 2 mv
```

## Byteman Automation Example

After cloning this repository we launch
[a simple test program](../tutorial/1-example-stdout/src/main/java/org/jboss/byteman/automate/proftest/ProfTest.java)
later to be used as a guinea pig in the later example:

```
$ cd tutorial/1-example-stdout
$ mvn package
$ java \
    -Dcom.sun.management.jmxremote=true \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.local.only=true \
    -Dcom.sun.management.jmxremote.port=9875 \
    -Dcom.sun.management.jmxremote.ssl=false \
    -jar ./target/proftest-01-example-stdout-1.0.jar
```

The program does nothing else than print some statistics periodically.

Next we use [a trivial shell script](jarp.sh) to list all methods from
the jar to create the initial configuration file containing
instrumentation points for the test application, customize these targets
(for real applications these configurations could be predefined, have
different target sets for different scenarios, be integrated with higher
level tools, and so forth), then use the
[tool](src/main/java/org/jboss/byteman/automate/proftool)
to generate the corresponding Byteman script, and finally check the
generated script:

```
$ cd byteman-automation-tool
$ ./jarp.sh ../tutorial/1-example-stdout/target/proftest-01-example-stdout-1.0.jar > targets.txt
$ vi targets.txt
$ cat targets.txt
org.jboss.byteman.automate.proftest.TestUnit#a
org.jboss.byteman.automate.proftest.TestUnit#b
org.jboss.byteman.automate.proftest.TestUnit#c
$ mvn package
$ java -jar ./target/proftool-1.0.jar \
    --input-file targets.txt \
    --register-class proftest.TestUnit \
    --register-method '<init>' \
    --instance-counts \
    --instance-lifetimes \
    --call-counts \
    --call-exectimes \
    --output-file rules.btm
$ appdir=../tutorial/1-example-stdout/target
$ tooljar=./target/proftool-1.0.jar
$ bmcheck -cp $appdir:$tooljar -v rules.btm
```

It is worth explaining the _-register-class_ and _-register-method_
parameters here: they define the class and the method of which
invocation causes
[the Byteman helper class](src/main/java/org/jboss/byteman/automate/proftool/JMXHelper.java)
of the tool responsible for transforming application events into metrics
available over JMX getting registered. In case the Byteman agent is
installed during application startup, the method could be _main_. Here,
where we will install the agent and the script while the application is
already running, we need to know any one method that gets invoked to
have the helper registered, we use the constructor of the test program's
_proftest.TestUnit_ class for this purpose.

(Note that depending how Byteman was installed, its scripts may or may
not have the _.sh_ suffix. NB. The test target is _proftest_, the tool
is _proftool_. Apologies for the confusion.)

The configuration file [targets.txt](targets.txt) and the Byteman script
[rules.btm](rules.btm) are included for reference in this
repository.

Now that we have a test application running and the Byteman script
generated, we install the Byteman agent to provide us application
metrics from the selected targets with no interruption for the running
application (prior Java 9, attaching to a JVM requires the _tools.jar_
to be available):

```
$ bminstall $(jps -l | awk '/ProfTest/ {print $1}')
$ bmsubmit -s $(pwd)/target/ProfTool-1.0.jar
$ bmsubmit -c
$ bmsubmit -l rules.btm
$ bmsubmit -l
$ sleep 5
$ echo "RULE Register dynamic MBean" > uninstall.btm
$ bmsubmit -u uninstall.btm
$ bmsubmit -l
```

Here we use the Byteman convenience scripts to install the Byteman agent
into the target JVM, load the helper tool, and submit the previously
generated Byteman script. After that we pause for a moment to make sure
the helper has been registered and then we uninstall the rule that was
only used for registering the helper, the actual instrumentation is left
active.

To verify all the previous steps, we use a simple
[MBean2TXT](MBean2TXT.java) helper utility to retrieve all the available
metrics over JMX:

```
$ javac MBean2TXT.java
$ java MBean2TXT
Application statistics [JMX] - 2018-03-21 16:41:22.152:

Instance count of proftest.TestUnit [proftest.TestUnit.instances.count] : 24
Live instances of proftest.TestUnit [proftest.TestUnit.instances.live] : 12
Average instance lifetime of proftest.TestUnit [proftest.TestUnit.lifetime.average] : 8006
Call count of proftest.TestUnit.a_int_long_void [proftest.TestUnit.a_int_long_void.calls] : 140
Call count of proftest.TestUnit.c__void [proftest.TestUnit.c__void.calls] : 8
Call count of proftest.TestUnit.b_int_void [proftest.TestUnit.b_int_void.calls] : 37
Average execution time of proftest.TestUnit.a_int_long_void [proftest.TestUnit.a_int_long_void.exectime.average] : 0
Average execution time of proftest.TestUnit.c__void [proftest.TestUnit.c__void.exectime.average] : 0
Average execution time of proftest.TestUnit.b_int_void [proftest.TestUnit.b_int_void.exectime.average] : 0
```

Since our test program does not do anything concrete, method average
execution time is (correctly) reported being zero.

## Summary

This page introduced an automated toolset for instrumenting and
monitoring unmodified Java applications on-the-fly. The tool can be
customized and extended and can be considered as complementary to other
available alternatives and may greatly increase observability of
applications under certain circumstances.

## Limitations

* Some statistics supported by tools accessing JVM using native JNI /
  JVM TI interfaces are not available using bytecode instrumentation
* Creating dynamic MBeans on the fly may prevent some JMX metrics
  collectors started as javaagent to detect and retrieve them
  * At least PCP/Parfait fails in this regard, see
    https://github.com/performancecopilot/parfait/issues/32

## Additional Resources

* http://byteman.jboss.org/
* https://developer.jboss.org/wiki/ABytemanTutorial
* http://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html
* https://prometheus.io/
* http://pcp.io/

## License

This project is licensed under the GNU GPLv2 with Classpath Exception,
which is the same license as OpenJDK itself. Byteman is licensed under
the LGPLv2.1.

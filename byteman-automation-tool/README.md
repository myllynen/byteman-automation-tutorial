# Byteman Automation Tool

[![License: GPLv2](https://img.shields.io/badge/license-GPLv2-brightgreen.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.en.html)

## Introduction

This page introduces a [Byteman](https://byteman.jboss.org/) automation
tool that allows custom, on-the-fly instrumentation and monitoring of
_unmodified_ Java applications, and exposing the captured data to any
external tool using the standard JMX technology.

Although using _the tool does not necessarily require Byteman
knowledge_, it is still recommended to check the
[Byteman Automation Tutorial](https://github.com/myllynen/byteman-automation-tutorial)
for basic Byteman introduction and to understand the basic operation of
this tool.

The tool can be customized and extended as needed, this initial version
provides statistics from unmodified Java applications for the following
metrics:

* number of calls for any selected methods
* min/avg/max execution time for any selected methods
* number of exits via exceptions for any selected methods
* number of live/total instances of a class (live only for classes implementing
  [Runnable](https://docs.oracle.com/javase/10/docs/api/java/lang/Runnable.html))
* instance min/avg/max lifetime (for classes implementing
  [Runnable](https://docs.oracle.com/javase/10/docs/api/java/lang/Runnable.html))

Additional Byteman capabilities which could be utilized to customize and
extend the tool include triggers for variable/object updates, running
custom code at any point of application code, evaluating conditions, and
so forth. For more details, see
[Byteman Programmer's Guide](https://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html).

## Implementation Overview

The tool reads a plain text configuration file listing classes and
methods where to install the instrumentation selected with command line
options. It then creates a Byteman script to leverage Byteman's bytecode
manipulation capabilities to transform Java applications. Byteman itself
uses the [ASM framework](https://projects.ow2.org/view/asm/) and is
loaded as a Java agent using the
[Instrumentation API](https://docs.oracle.com/javase/10/docs/api/java/lang/instrument/package-summary.html)
at application startup or at any point of application lifecycle with
Byteman convenience scripts leveraging the
[Attach API](https://docs.oracle.com/javase/10/docs/api/com/sun/tools/attach/package-summary.html)
(which since Java 9 does not require the separate _tools.jar_ anymore).

Once the created Byteman script has been loaded, the application has
been transformed to use methods of a helper class part of the tool
converting application events into statistics available over JMX. Unlike
with, for example,
[Proxy class](https://docs.oracle.com/javase/10/docs/api/java/lang/reflect/Proxy.html)
or
[Prometheus instrumentation](https://prometheus.io/docs/practices/instrumentation/),
tracing can be extended to new areas of an application or libraries
on-the-fly without any code changes or even without restarting the
application.

Byteman or the tool do not rely on
[JNI](https://en.wikipedia.org/wiki/Java_Native_Interface) or
[JVM TI](https://en.wikipedia.org/wiki/Java_Virtual_Machine_Tools_Interface)
so they are platform agnostic and supported by all modern JVMs.

The above approach provides the following benefits and options:

* no application code changes needed to monitor application internals
* can be applied to components for which source is not available
* can be enabled on class/method level, no one global switch
* can be enabled and disabled on-the-fly as needed
* allows for customization and extensibility
* results available over standard JMX
* fully open source solution

Obviously, the best approach is highly dependent on each use case.
For example, the following aspects might need to be considered:

* using containers or not
* test or production environment
* easily reproducible or a "once-a-month" issue
* possible to use other troubleshooting tools or not
* are application/component developers reachable or not
* built-in instrumentation support in application available or not

In-depth considerations of these aspects are out of scope for this
document and they are merely mentioned as a base for further discussions
which of the many available methodologies and tools to use in any
particular situation; this tool is not always the best choice but may be
very helpful under certain circumstances.

## Byteman Installation

Byteman is packaged for many distributions but installing the latest
version locally without additional privileges is easy (copypasting the
below snippet into a terminal is enough):

```
vers=4.0.4
wget https://downloads.jboss.org/byteman/$vers/byteman-download-$vers-bin.zip
unzip byteman-download-$vers-bin.zip
export BYTEMAN_HOME=$(pwd)/byteman-download-$vers
export PATH=$BYTEMAN_HOME/bin:$PATH
```

Optionally, after BYTEMAN_HOME has been set, _.bat_ files and _.sh_
suffixes can be removed:

```
find $BYTEMAN_HOME/bin -name '*.bat' -print | xargs rm -f
find $BYTEMAN_HOME/bin -name '*.sh' -print | sed 'p;s/\.sh//' | xargs -n 2 mv
```

## Byteman Automation Example

After cloning this repository we start
[a simple test program](../tutorial/1-example-stdout/src/main/java/com/example/proftest/ProfTest.java)
to be used as a guinea pig in the later example:

```
$ cd tutorial/1-example-stdout
$ mvn package
(output omitted)
$ java \
    -Dcom.sun.management.jmxremote=true \
    -Dcom.sun.management.jmxremote.authenticate=false \
    -Dcom.sun.management.jmxremote.local.only=true \
    -Dcom.sun.management.jmxremote.port=9875 \
    -Dcom.sun.management.jmxremote.ssl=false \
    -jar ./target/proftest-01-example-stdout-1.0.jar
```

The program does nothing else except print some statistics of its
internals periodically. This repo also contains a little helper
utility [JMXEnabler](JMXEnabler.java) which can be used to enable
JMX on the target JVM if it was started without JMX being enabled.

Next we use [a trivial shell script](jarp.sh) to list all the methods
from the jar file to create the initial configuration file containing
instrumentation points for the test application, customize these targets
(for real applications these configurations could be predefined, have
different target sets for different scenarios, be integrated with higher
level tools, and so forth), then use the
[tool](src/main/java/org/jboss/byteman/automate/proftool)
to create the Byteman script to instrument the application, and finally
check the generated script for correctness:

```
$ cd byteman-automation-tool
$ ./jarp.sh ../tutorial/1-example-stdout/target/proftest-01-example-stdout-1.0.jar > targets.txt
$ vi targets.txt
$ cat targets.txt
com.example.proftest.TestUnit#a
com.example.proftest.TestUnit#b
com.example.proftest.TestUnit#c
$ mvn package
(output omitted)
$ java \
    -jar ./target/proftool-1.0.jar \
      --input-file targets.txt \
      --register-class com.example.proftest.TestUnit \
      --register-method '<init>' \
      --inst-lifetimes-min \
      --inst-lifetimes-avg \
      --inst-lifetimes-max \
      --call-counts \
      --call-exectimes-min \
      --call-exectimes-avg \
      --call-exectimes-max \
      --call-exit-except \
      --output-file rules.btm
$ appjar=../tutorial/1-example-stdout/target/proftest-01-example-stdout-1.0.jar
$ tooljar=./target/proftool-1.0.jar
$ bmcheck -cp $appjar:$tooljar -v rules.btm
```

While the other command lines options are pretty obvious in what they do
(and described briefly below), it is worth explaining the
_-register-class_ and _-register-method_ options in detail here: they
define the class and the method of which invocation causes
[the Byteman helper class](src/main/java/org/jboss/byteman/automate/proftool/JMXHelper.java)
of the tool responsible for transforming application events into metrics
available over JMX getting registered. In case the Byteman agent is
installed during application startup, the method could be _main_. Here,
where we will install the agent and the script when the application is
already running, we need to know any one method that is called to have
the helper registered. We use the constructor of the test program's
_com.example.proftest.TestUnit_ class for this purpose.

It is recommended to read more about used-defined rule helpers from the
[tutorial](https://github.com/myllynen/byteman-automation-tutorial) and
Byteman Programmer's Guide:
https://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html#user-defined-rule-helpers.

(Note that depending on how Byteman was installed, its scripts may or
may not have the _.sh_ suffix. NB. The test target is _proftest_, the
tool is _proftool_. Apologies for the confusion.)

The configuration file [targets.txt](targets.txt) and the generated
Byteman script [rules.btm](rules.btm) are included for reference in
this repository.

To control more precisely Byteman script generation the tool provides
several command line options, use the _--help_ option to display them:

```
$ java -jar ./target/proftool-1.0.jar --help
Usage:
  ProfTool [options]
where [options] include:
  --help                  Print this message
  --input-file            Specify input file to read monitored targets from
  --output-file           Specify output file to write created rule script to
  --helper-class          Specify Byteman helper class to use in created script
  --register-class        Specify class to register dynamic MBean on
  --register-method       Specify method to register dynamic MBean on
  --register-action       Specify action (method) to register dynamic MBean with
  --register-object       Specify object (name) to register dynamic MBean with
  --instance-counts       Write rules for monitoring instance counts
  --inst-lifetimes-min    Write rules for monitoring instance min lifetimes
  --inst-lifetimes-avg    Write rules for monitoring instance avg lifetimes
  --inst-lifetimes-max    Write rules for monitoring instance max lifetimes
  --call-counts           Write rules for monitoring method call counts
  --call-exectimes-min    Write rules for monitoring method call min exec times
  --call-exectimes-avg    Write rules for monitoring method call avg exec times
  --call-exectimes-max    Write rules for monitoring method call max exec times
  --call-exit-except      Write rules for monitoring method exits via exceptions
```

Now that we have a test application running and the Byteman script
generated, we install the Byteman agent to provide us application
metrics from the selected targets with no interruption for the running
application (prior Java 9, attaching to a JVM requires the separate
_tools.jar_ to be available):

```
$ bminstall $(jps -l | awk '/proftest-01-example-stdout/ {print $1}')
$ bmsubmit -s $(pwd)/target/proftool-1.0.jar
$ bmsubmit -c
$ bmsubmit -l rules.btm
$ bmsubmit -l
$ sleep 3
$ echo "RULE Register dynamic MBean" > uninstall.btm
$ bmsubmit -u uninstall.btm
$ bmsubmit -l
```

Here we use the Byteman convenience scripts to install the Byteman agent
into the target JVM, load the helper tool, and submit the previously
generated Byteman script. After that we pause for a moment to make sure
the helper has been registered and then we uninstall the rule that was
only used for registering the helper, the actual instrumentation is
otherwise left active. The above commands are also available as script
[submit.sh](submit.sh).

To verify all the previous steps, we use a simple
[MBean2TXT](MBean2TXT.java) utility (completely unrelated to the actual
automation tool) to retrieve all the available metrics over JMX:

```
$ javac MBean2TXT.java
$ java MBean2TXT
Application statistics [JMX] - 2018-03-21 16:41:22.152:

Total instances of com.example.proftest.TestUnit [com.example.proftest.TestUnit.instances.total] : 23
Live instances of com.example.proftest.TestUnit [com.example.proftest.TestUnit.instances.live] : 13
Minimum instance lifetime of com.example.proftest.TestUnit [com.example.proftest.TestUnit.lifetime.minimum] : 3001
Average instance lifetime of com.example.proftest.TestUnit [com.example.proftest.TestUnit.lifetime.average] : 8605
Maximum instance lifetime of com.example.proftest.TestUnit [com.example.proftest.TestUnit.lifetime.maximum] : 15012
Call count of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.calls] : 162
Call count of com.example.proftest.TestUnit.c__void [com.example.proftest.TestUnit.c__void.calls] : 12
Call count of com.example.proftest.TestUnit.b_int_void [com.example.proftest.TestUnit.b_int_void.calls] : 44
Minimum execution time of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.exectime.minimum] : 0
Average execution time of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.exectime.average] : 0
Maximum execution time of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.exectime.maximum] : 1
Minimum execution time of com.example.proftest.TestUnit.c__void [com.example.proftest.TestUnit.c__void.exectime.minimum] : 0
Average execution time of com.example.proftest.TestUnit.c__void [com.example.proftest.TestUnit.c__void.exectime.average] : 0
Maximum execution time of com.example.proftest.TestUnit.c__void [com.example.proftest.TestUnit.c__void.exectime.maximum] : 1
Minimum execution time of com.example.proftest.TestUnit.b_int_void [com.example.proftest.TestUnit.b_int_void.exectime.minimum] : 0
Average execution time of com.example.proftest.TestUnit.b_int_void [com.example.proftest.TestUnit.b_int_void.exectime.average] : 0
Maximum execution time of com.example.proftest.TestUnit.b_int_void [com.example.proftest.TestUnit.b_int_void.exectime.maximum] : 1
```

(Note that since our test program does not do anything meaningful,
method average execution times are (correctly) reported being zero or
near zero.)

Without modifying a target Java application in any way or even
restarting it, with only a few commands using the Byteman Automation
Tool introduced above we are now able to provide application internal
statistics over JMX!

With real applications monitoring different aspects could be enabled and
disabled on-the-fly by loading and unloading different Byteman helper
scripts. This allows, for example, first gathering overall understanding
of application behavior and health and then investigating more relevant
looking areas of the application in more detail. After uninstalling all
the loaded rules, the application is again working as if no Byteman
instrumentation ever would have been applied.

## Summary

This page introduced Byteman Automation Tool for instrumenting and
monitoring _unmodified_ Java applications on-the-fly over JMX. The tool
can be customized and extended as needed. It can be considered as a
complementary option to other available alternatives and may greatly
increase observability of applications under certain circumstances.

## Limitations

* Some statistics supported by tools accessing JVM internals using the
  native JNI / JVM TI interfaces are not available when using bytecode
  transformation based instrumentation
* Creating dynamic MBean based metrics on-the-fly may prevent some JMX
  metric collectors started as javaagent to detect and retrieve them
  * At least PCP/Parfait fails in this regard, see
    https://github.com/performancecopilot/parfait/issues/32

## Additional Resources

* https://github.com/myllynen/byteman-automation-tutorial
* https://byteman.jboss.org/
* https://developer.jboss.org/wiki/ABytemanTutorial
* https://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html
* https://prometheus.io/
* https://pcp.io/

## Credits

The following people have provided invaluable help and suggestions:

* Andrew Dinn
* Ondra Chaloupka
* Timo Friman

## License

This project is licensed under the GNU GPLv2 with Classpath Exception,
which is the same license as OpenJDK itself. Byteman is licensed under
the LGPLv2.1.

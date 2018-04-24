# Byteman Automation Tutorial

[![License: GPLv2](https://img.shields.io/badge/license-GPLv2-brightgreen.svg)](https://www.gnu.org/licenses/old-licenses/gpl-2.en.html)

## Introduction

This repository provides a Tutorial Introduction to Byteman, using it to
develop an Automation Tool which supports custom, on-the-fly
instrumentation and monitoring of _unmodified_ Java applications. The
tool exposes the captured data to any external tool using the standard
JMX technology.

The tutorial starts with a simple Hello World like example and proceeds
to eventually introduce a low overhead, easily customizable tool to
provide statistics from Java applications supporting the following
metrics over JMX (the actual tool supports additional metrics):

* method average execution time
* number of live instances of a class (for classes implementing
  [Runnable](https://docs.oracle.com/javase/10/docs/api/java/lang/Runnable.html))
* number of objects instantiated from a class
* number of calls for any selected instance methods
* instance average lifetime (for classes implementing
  [Runnable](https://docs.oracle.com/javase/10/docs/api/java/lang/Runnable.html))

Basic Java, JMX, JVM, Linux, Maven, shell, and XML knowledge is
expected.

The impatient and those with Byteman experience may jump directly to the
actual tool page: [byteman-automation-tool](byteman-automation-tool).

## Byteman

From http://byteman.jboss.org/:

> Byteman is a tool which makes it easy to trace, monitor and test the behaviour of Java application and JDK runtime code. It injects Java code into your application methods or into Java runtime methods without the need for you to recompile, repackage or even redeploy your application. Injection can be performed at JVM startup or after startup while the application is still running. Injected code can access any of your data and call any application methods, including where they are private.

## Overview

The [Byteman Programmer's Guide](http://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html)
lists four main areas where Byteman could be used:

* tracing execution of specific code paths and displaying application or JVM state
* subverting normal execution by changing state, making unscheduled method calls or forcing an unexpected return or throw
* orchestrating the timing of activities performed by independent application threads
* monitoring and gathering statistics summarising application and JVM operation

This tutorial concentrates on the last item to allow readers to get
familiar with Byteman, automate its use, and evaluate its suitability
for their projects.

The first section will introduce a trivial Java program that will be
used unmodified in later sections to monitor the number of objects
instantiated from a class, how many times different methods have been
called, and instance average lifetime. As usual with compact tutorials,
proper error handling, unit tests, and other such aspects crucial in
real-world programming are omitted from the examples to keep the code
clear and to-the-point.

## Example Program

The example program used throughout the rest of the tutorial is
available in the directory
[tutorial/1-example-stdout](tutorial/1-example-stdout), more
specially it consists of
[ProfTest.java](tutorial/1-example-stdout/src/main/java/com/example/proftest/ProfTest.java).
The program will create objects from the _TestUnit_ class indefinitely
once per second. The objects will have a lifetime between 1 to 20
seconds during which time they periodically call the class methods _a_,
_b_, and _c_.

The test program will print every 10 seconds statistics how many objects
have been created, how many calls to different methods have been made,
and the average lifetime of created objects.

Note that these statistics are provided to allow verifying the later
results when using Byteman, with real programs these kinds of counters
and support routines are *not* needed in the application code when using
the Byteman automation presented here.

Below is a screenshot of compiling, running, and seeing the first update
from the example program - note the _APP_ marker indicating the source
of the data on the first line:

```
$ cd tutorial/1-example-stdout
$ mvn package
(output omitted)
$ java -jar ./target/proftest-01-example-stdout-1.0.jar
ProfTest statistics [APP] - 2018-03-21 11:30:14.555:

Objects instantiated from TestUnit: 10

Calls to method a: 24
Calls to method b: 4
Calls to method c: 0

Average lifetime: 8
```

Now that we have a test application with easy to understand behavior
running, we can start learning and using Byteman for monitoring and
gathering statistics from the application.

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

Optionally, after BYTEMAN_HOME has been set, _.bat_ files and _.sh_
suffixes can be removed:

```
find $BYTEMAN_HOME/bin -name '*.bat' -print | xargs rm -f
find $BYTEMAN_HOME/bin -name '*.sh' -print | sed 'p;s/\.sh//' | xargs -n 2 mv
```

## Basic Byteman Example

First we create a simple Byteman script that already provides the same
statistics as the above example program provides. Remember, the program
being traced could be doing anything and not collect any statistics by
itself. Byteman would allow extracting this information without any help
from the program by manipulating the bytecode running on the JVM.

This basic [rules.btm](tutorial/2-byteman-stdout/rules.btm) Byteman
script defines a set of rules which increment statistics counters at the
start and end of different methods and periodically print results to the
standard output. When we start the JVM below, the Byteman Java agent
will inject bytecode into the application bytecode based on this script.
Both the application and the Byteman script printing the same results
allows us to verify that the statistics printed by the program and
the example Byteman script are similar and correct.

[Byteman Programmer's Guide](http://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html)
provides complete description of Byteman script syntax.

From this initial script we see two downsides with this basic approach
which will be addressed in the next sections: first, for lifetime we are
relying on a method argument which in a real use case might not be
present. Second, merely printing statistics on a terminal is not
feasible approach with larger applications.

Here we start the example program and Byteman with it at the same time
as a Java agent:

```
$ cd tutorial/2-byteman-stdout
$ mvn package
(output omitted)
$ java \
  -javaagent:$BYTEMAN_HOME/lib/byteman.jar=script:rules.btm \
  -jar ./target/proftest-02-byteman-stdout-1.0.jar
ProfTest statistics [BTM] - 2018-03-21 11:33:03.437:

Objects instantiated from TestUnit: 10

Calls to method a: 22
Calls to method b: 5
Calls to method c: 0

Average lifetime: 10

ProfTest statistics [APP] - 2018-03-21 11:33:03.445:

Objects instantiated from TestUnit: 10

Calls to method a: 22
Calls to method b: 5
Calls to method c: 0

Average lifetime: 10
```

Above we now see statistics both from Byteman (marked with _BTM_) and
from the example program (marker with _APP_). Both statistics are as
expected and correct. (Minor variations in results over different test
runs may occur due to slight timing differences.)

In case there were any issues with Byteman, consider the
`org.jboss.byteman.debug` and/or `org.jboss.byteman.verbose` environment
settings for more verbosity (see
http://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html#environment-settings
for all the supported settings).

## Byteman Rule Helpers

One thing that makes Byteman very powerful is that it supports
user-defined rule helpers which allow running custom Java code at any
location of application code. Here we use this capability to allow us
writing the results into a file using JSON notation. We also avoid
relying on method arguments and move the Byteman script to be part of
the built jar file for easier packaging.

It is recommended to read more about used-defined rule helpers from the
Byteman Programmer's Guide:
http://downloads.jboss.org/byteman/latest/byteman-programmers-guide.html#user-defined-rule-helpers.

Our example program is unchanged, but we add a custom helper code
[JSONHelper.java](tutorial/3-byteman-json/src/main/java/com/example/proftest/JSONHelper.java)
that is relatively straightforward, it is the
[rules.btm](tutorial/3-byteman-json/src/main/resources/rules.btm) script
that connects the events in the application execution flow to our custom
helper code. Any time a location of application code specified in the
Byteman script is reached, Byteman injected bytecode invokes a method
from our custom helper class. Thus, the script and the helper class
should be investigated together to fully see what is going on.

(Note that in these examples we are bundling the application and the
rule helper in the same package - this would be inconvinient with real
applications and indeed with the actual tool no such bundling is used.)

Below we see an example run with slightly changed command line options:

```
$ cd tutorial/3-byteman-json
$ mvn package
(output omitted)
$ java \
  -javaagent:$BYTEMAN_HOME/lib/byteman.jar=resourcescript:rules.btm \
  -jar ./target/proftest-03-byteman-json-1.0.jar
ProfTest statistics [APP] - 2018-03-21 11:34:49.606:

Objects instantiated from TestUnit: 10

Calls to method a: 28
Calls to method b: 5
Calls to method c: 0

Average lifetime: 12
```

The example program still prints out its statistics but now we also see
the newly created _data.json_ file written by the helper:

```
$ cat data.json
{
  "instances": 10,
  "count_a": 28,
  "count_b": 5,
  "count_c": 0,
  "average_lifetime": 12
}
```

While the rules to call the helper as expected, writing a JSON file on
application events could cause too much overhead and in general is not a
standard way to provide metrics for Java applications. Here we used
this approach merely to illustrate how custom code can be run with
Byteman rule helpers, in the next section we address these issues and
provide Byteman generated metrics properly over JMX.

## Byteman Data over JMX

Now that we know how Byteman user-defined rule helpers work, it is easy
to convert our above Byteman-to-JSON example as Byteman-to-JMX. This
allows any tool (like
[JConsole](https://docs.oracle.com/javase/10/management/using-jconsole.htm),
[Prometheus](https://prometheus.io/) or
[Performance Co-Pilot](http://pcp.io/), PCP) consuming metrics over the
standard JMX interface to retrieve data provided by the Byteman helper.

The example program still unchanged, our new custom helper code is
[JMXHelper.java](tutorial/4-byteman-jmx/src/main/java/com/example/proftest/JMXHelper.java).
It is similar than the previous example but instead of writing a JSON
file it defines a dynamic MBean providing the previous statistics as
MBean attributes.
[rules.btm](tutorial/4-byteman-jmx/src/main/resources/rules.btm) script
connects the events in the application execution flow to our JMX helper.

For easy testing, a simple standalone
[MBean2TXT](tutorial/4-byteman-jmx/MBean2TXT.java) utility (unrelated to
the actual Byteman example code) is used in the below example. For this,
we need to start the JVM with JMX enabled:

```
$ cd tutorial/4-byteman-jmx
$ mvn package
(output omitted)
$ java \
  -javaagent:$BYTEMAN_HOME/lib/byteman.jar=resourcescript:rules.btm \
  -Dcom.sun.management.jmxremote=true \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.local.only=true \
  -Dcom.sun.management.jmxremote.port=9875 \
  -Dcom.sun.management.jmxremote.ssl=false \
  -jar ./target/proftest-04-byteman-jmx-1.0.jar
```

The example program will still print out statistics as before but now we
can access those statistics also with the standalone utility (or, as said,
any other JMX consuming tool, like [Prometheus](https://prometheus.io/)
or [Performance Co-Pilot](http://pcp.io/), PCP):

```
$ javac MBean2TXT.java
$ java MBean2TXT
ProfTest statistics [JMX] - 2018-03-21 13:50:04.661:

Objects instantiated from TestUnit: 27

Calls to method a: 144
Calls to method b: 36
Calls to method c: 10

Average lifetime: 8
```

### Byteman and PCP Example (optional)

For the sake of a more complete (command line) example, here is a
screenshot from a patched Fedora 28 VM where [PCP](http://pcp.io/) is
enabled and its plugin for JMX metrics,
[Parfait](https://github.com/performancecopilot/parfait), is configured:

```
# yum install pcp pcp-system-tools pcp-parfait-agent
# systemctl start pmcd
# cd tutorial/4-byteman-jmx
# cp pcp/proftest.json /etc/parfait/jvm.json
# parfait --name byteman --connect localhost:9875
```

```
$ pmrep --interval 10 --samples 2 --separate-header --width 5 mmv.byteman
[ 1] - mmv.byteman.com.example.proftest.instances - count
[ 2] - mmv.byteman.com.example.proftest.count_c - count
[ 3] - mmv.byteman.com.example.proftest.count_b - count
[ 4] - mmv.byteman.com.example.proftest.count_a - count
[ 5] - mmv.byteman.com.example.proftest.average_lifetime - count

      1      2      3      4      5
     10      0      8     37      5
     20      8     27    106      7
```

From here, we could use PCP to write the available metrics to an archive
log, use [pmrep(1)](http://man7.org/linux/man-pages/man1/pmrep.1.html)
for more elaborate reporting (possibly by combining these application
metrics with JVM, DB, OS, and other PCP provided metrics), or perhaps
use PCP tools like
[pcp2elasticsearch(1)](http://man7.org/linux/man-pages/man1/pcp2elasticsearch.1.html),
[pcp2graphite(1)](http://man7.org/linux/man-pages/man1/pcp2graphite.1.html),
or [pcp2zabbix(1)](http://man7.org/linux/man-pages/man1/pcp2zabbix.1.html)
to forward Byteman provided data to external systems for visualization
and further analysis.

## Byteman Generic JMX Helper

The earlier example already makes it possible to provide helpful metrics
from unmodified Java applications over JMX. However, the approach so far
is not scalable as application implementation details are coded in the
Byteman script and the JMXHelper.

To address these issues,
[JMXHelper.java](tutorial/5-byteman-generic/src/main/java/com/example/proftest/JMXHelper.java)
is made generic so that it can be used with any application, the metrics
(MBean attributes) published over JMX are now dynamic. Also the Byteman
script [rules.btm](tutorial/5-byteman-generic/src/main/resources/rules.btm)
is adjusted to call these generic methods of the helper, no application
related details are included in the script anymore either.

To guarantee reliability of this approach, we enable automatic Byteman
script correctness checking as part of Maven packaging phase in
[pom.xml](tutorial/5-byteman-generic/pom.xml). Also the simple
[MBean2TXT](tutorial/5-byteman-generic/MBean2TXT.java) utility is made
slightly more generic by adjusting its output format.

Testing can be done just as in the previous example, first we start the
example application and Byteman with the generic helper and script:

```
$ cd tutorial/5-byteman-generic
$ mvn package
(output omitted)
$ java \
  -javaagent:$BYTEMAN_HOME/lib/byteman.jar=resourcescript:rules.btm \
  -Dcom.sun.management.jmxremote=true \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.local.only=true \
  -Dcom.sun.management.jmxremote.port=9875 \
  -Dcom.sun.management.jmxremote.ssl=false \
  -jar ./target/proftest-05-byteman-generic-1.0.jar
```

And then verify the availability of the metrics over JMX:

```
$ javac MBean2TXT.java
$ java MBean2TXT
Application statistics [JMX] - 2018-03-21 16:10:32.168:

Total instances of com.example.proftest.TestUnit [com.example.proftest.TestUnit.instances.total] : 16
Live instances of com.example.proftest.TestUnit [com.example.proftest.TestUnit.instances.live] : 11
Average instance lifetime of com.example.proftest.TestUnit [com.example.proftest.TestUnit.lifetime.average] : 5806
Call count of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.calls] : 70
Call count of com.example.proftest.TestUnit.c__void [com.example.proftest.TestUnit.c__void.calls] : 3
Call count of com.example.proftest.TestUnit.b_int_void [com.example.proftest.TestUnit.b_int_void.calls] : 19
Average execution time of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.exectime.average] : 0

```

Since our simple test program does not do anything useful, method
average execution time is (correctly) reported being zero. The actual
tool supports also reporting minimum and maximum method execution times.

We now have a generic tool to publish any statistics we choose from
unmodified Java applications over JMX! To push things even further, in
the next section we will address the remaining scalability issue, namely
the manual creation of Byteman scripts.

## Byteman Automation

The previous example introduced a generic Byteman tool to gather custom
statistics from unmodified Java applications. While the solution is
already generic, manually creating and updating potentially a large set
of Byteman scripts would be tedious and error prone. It would also
impose a requirent for anyone wanting to use the tool for tracing to be
rather familiar with Byteman, ideally only overall knowledge of the
application under investigation would be needed.

The Byteman JMX helper from the previous example is unchanged but a new
standalone helper tool to automatically create Byteman scripts is added.
This [RuleCreator](tutorial/6-byteman-automate/RuleCreator.java) reads
in a text file listing target classes and methods and based on given
parameters writes out a new Byteman script.

While there are several ways to determine the most relevant target
classes and methods (the tool page discusses this in more detail), here
we use a quick and simple
[jarp.sh](tutorial/6-byteman-automate/jarp.sh)
shell script to generate a list of all methods found in a given jar
file. This list can then easily be adjusted as needed. In this example,
we include the earlier monitored classes and methods.

Since the example program is unchanged we can use the earlier compiled
jar from the first example to create the initial list of target classes
and methods:

```
$ cd tutorial/6-byteman-automate
$ ./jarp.sh ../1-example-stdout/target/proftest-01-example-stdout-1.0.jar > targets.txt
$ vi targets.txt
$ cat targets.txt
com.example.proftest.TestUnit#a
com.example.proftest.TestUnit#b
com.example.proftest.TestUnit#c
```

Now we compile the new standalone helper tool and generate a new Byteman
script based on the above input (use the _--help_ option for a quick
help):

```
$ javac \
  -cp .:$BYTEMAN_HOME/lib/byteman.jar:$BYTEMAN_HOME/contrib/dtest/byteman-dtest.jar \
  RuleCreator.java
$ java \
  -cp .:$BYTEMAN_HOME/lib/byteman.jar:$BYTEMAN_HOME/contrib/dtest/byteman-dtest.jar \
  RuleCreator \
    --input-file targets.txt \
    --instance-counts \
    --instance-lifetimes \
    --call-counts \
    --call-exectimes \
    --output-file ./src/main/resources/rules.btm
```

This generated script is included here for reference:
[rules.btm](tutorial/6-byteman-automate/src/main/resources/rules.btm).

We are now ready to run the usual test, first the application is
started:

```
$ mvn package
(output omitted)
$ java \
  -javaagent:$BYTEMAN_HOME/lib/byteman.jar=resourcescript:rules.btm \
  -Dcom.sun.management.jmxremote=true \
  -Dcom.sun.management.jmxremote.authenticate=false \
  -Dcom.sun.management.jmxremote.local.only=true \
  -Dcom.sun.management.jmxremote.port=9875 \
  -Dcom.sun.management.jmxremote.ssl=false \
  -jar ./target/proftest-06-byteman-automate-1.0.jar
```

And then availability of the metrics based on the automatically created
Byteman script is verified:

```
$ javac MBean2TXT.java
$ java MBean2TXT
Application statistics [JMX] - 2018-03-21 16:34:38.371:

Total instances of com.example.proftest.TestUnit [com.example.proftest.TestUnit.instances.total] : 24
Live instances of com.example.proftest.TestUnit [com.example.proftest.TestUnit.instances.live] : 12
Average instance lifetime of com.example.proftest.TestUnit [com.example.proftest.TestUnit.lifetime.average] : 8006
Call count of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.calls] : 140
Call count of com.example.proftest.TestUnit.c__void [com.example.proftest.TestUnit.c__void.calls] : 8
Call count of com.example.proftest.TestUnit.b_int_void [com.example.proftest.TestUnit.b_int_void.calls] : 37
Average execution time of com.example.proftest.TestUnit.a_int_long_void [com.example.proftest.TestUnit.a_int_long_void.exectime.average] : 0
Average execution time of com.example.proftest.TestUnit.c__void [com.example.proftest.TestUnit.c__void.exectime.average] : 0
Average execution time of com.example.proftest.TestUnit.b_int_void [com.example.proftest.TestUnit.b_int_void.exectime.average] : 0
```

In addition being able to publish any statistics we choose from
unmodified Java applications over JMX, we now can also create the needed
Byteman scripts for any selected target methods with the utility!

This concludes the tutorial, the actual Byteman Automation Tool (which
has more features and proper packaging) will be presented in its own
page, see the link below.

## Byteman Automation Tool

The Byteman Automation Tool is introduced at
https://github.com/myllynen/byteman-automation-tutorial/tree/master/byteman-automation-tool.

The tool is based on the same appoarch as the above example but supports
additional metrics (with additional command line parameters), takes into
account exits via exceptions, the helper to create Byteman scripts is
properly packaged, an example to attach to an already running Java
application is provided, and some additional details about Byteman
internals are explained.

NB. The actual tool will use more formal naming than
_com.example.proftest_ used above.

## Credits

The following people have provided invaluable help and suggestions:

* Andrew Dinn
* Ondra Chaloupka
* Timo Friman

## License

This project is licensed under the GNU GPLv2 with Classpath Exception,
which is the same license as OpenJDK itself. Byteman is licensed under
the LGPLv2.1.

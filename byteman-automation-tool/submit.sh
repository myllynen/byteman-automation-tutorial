#!/bin/sh

set -x
set -e

bminstall $(jps -l | awk '/proftest-01-example-stdout/ {print $1}')
bmsubmit -s $(pwd)/target/proftool-1.0.jar
bmsubmit -c
bmsubmit -l rules.btm
bmsubmit -l
sleep 3
echo "RULE Register dynamic MBean" > uninstall.btm
bmsubmit -u uninstall.btm
bmsubmit -l

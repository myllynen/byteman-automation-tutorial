#!/bin/sh

if [ $# -ne 1 -o ! -f "$1" -o ! -r "$1" ]; then
  echo "Usage: $0 <file.jar>" 1>&2
  exit 1
fi

classes=$(unzip -l "$1" | awk '/\.class$/ {print $4}' | sed -e 's,/,.,g' -e 's,\.class,,g' | sort)

javap -classpath "$1" -p $classes | grep -v lambda |
  while IFS= read -r line; do
     line=" $line"
     method=
     case $line in
       *\ class\ *) class=${line/* class }; class=${class/ *}; ;;
       *\(*) method=${line/(*}; method=${method/* } ; method=${method/*.} ;;
     esac
     if [ -n "$class" -a -n "$method" ]; then
       echo $class\#$method
     fi
  done

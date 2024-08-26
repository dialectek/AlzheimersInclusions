#!/bin/bash
javac -classpath ../lib/vecmath-1.5.2.jar -d . ../src/*.java
jar cvfm ../bin/AlzheimersInclusions.jar AlzheimersInclusions.mf *.class
rm *.class


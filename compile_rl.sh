#!/bin/bash
rm -rf beans
mkdir -p beans
javac -cp "jars/*" -sourcepath src -d beans $(find src -name "*.java")

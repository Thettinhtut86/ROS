#!/bin/bash
cd /opt/ros

# Compile
javac -cp ".:lib/*" Robot_test.java

# Run
java -cp ".:lib/*" Robot_test
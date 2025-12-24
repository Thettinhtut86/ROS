#!/bin/bash
cd /opt/ros

# Compile
javac -cp ".:lib/*" Robot.java

# Run
java -cp ".:lib/*" Robot
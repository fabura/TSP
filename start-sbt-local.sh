#!/bin/bash


env JAVA_OPTS="${TSP_JAVA_OPTS:--Xms2G -Xmx4G}" \
    JAVA_TOOL_OPTIONS="${TSP_JAVA_TOOL_OPTS:--Dfile.encoding=UTF8}" \
    sbt "${TSP_LAUNCHER:-http/runMain ru.itclover.tsp.http.Launcher}" $TSP_LAUNCHER_ARGS

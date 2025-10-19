#!/bin/sh


export JDT_LAUNCHER_PATH=$(find /opt/jdt-ls -name "org.eclipse.equinox.launcher_*.jar")


echo "JDT Launcher Path set to: ${JDT_LAUNCHER_PATH}"


exec "$@"
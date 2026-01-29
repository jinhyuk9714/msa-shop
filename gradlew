#!/bin/sh

#
# Gradle wrapper start script (POSIX).
# Wrapper JAR missing? Run: gradle wrapper
#

set -e

app_path=$0
while [ -h "$app_path" ]; do
  ls=$(ls -ld "$app_path")
  link=${ls#*' -> '}
  case $link in
    /*) app_path=$link ;;
    *) app_path=$(dirname "$app_path")/$link ;;
  esac
done
APP_HOME=$(cd -P "$(dirname "$app_path")" >/dev/null && pwd) || exit 1

WRAPPER_JAR=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ ! -f "$WRAPPER_JAR" ]; then
  echo "gradle-wrapper.jar not found. Run: gradle wrapper" >&2
  exit 1
fi

exec java -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"

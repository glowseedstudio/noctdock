#!/bin/sh

APP_HOME=$(cd "${0%/*}" && pwd -P) || exit
APP_NAME=${0##*/}
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
elif [ -x "/usr/lib/jvm/temurin-17-jdk/bin/java" ]; then
    JAVA_HOME="/usr/lib/jvm/temurin-17-jdk"
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=$(command -v java)
fi

if [ ! -x "$JAVACMD" ]; then
    echo "ERROR: JAVA_HOME is not set and no java command could be found." >&2
    exit 1
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

eval "set -- $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \"-Dorg.gradle.appname=$APP_NAME\" -classpath \"$CLASSPATH\" org.gradle.wrapper.GradleWrapperMain \"\$@\""
exec "$JAVACMD" "$@"

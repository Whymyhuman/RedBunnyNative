#!/usr/bin/env sh
DEFAULT_JVM_OPTS="-Xmx64m -Xms64m"
APP_BASE_NAME="gradle"
APP_HOME="$(
  cd "$(dirname "$0")" >/dev/null || exit
  pwd
)"

if [ -z "$JAVA_HOME" ]; then
  if [ -r "/etc/os-release" ]; then
    . /etc/os-release
    if [ "$ID" = "alpine" ]; then
      if [ -x "/usr/bin/java" ]; then
        JAVA_HOME="/usr/lib/jvm/default-jvm"
      fi
    fi
  fi
fi

if [ -z "$JAVA_HOME" ]; then
  if [ -d "/usr/lib/jvm/default-java" ]; then
    JAVA_HOME="/usr/lib/jvm/default-java"
  elif [ -d "/usr/lib/jvm/java-8-openjdk-amd64" ]; then # Debian/Ubuntu
    JAVA_HOME="/usr/lib/jvm/java-8-openjdk-amd64"
  elif [ -d "/usr/lib/jvm/java-11-openjdk-amd64" ]; then # Debian/Ubuntu
    JAVA_HOME="/usr/lib/jvm/java-11-openjdk-amd64"
  elif [ -d "/usr/lib/jvm/java-17-openjdk-amd64" ]; then # Debian/Ubuntu
    JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
  elif [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then # Debian/Ubuntu
    JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
  fi
fi

# Determine the Java command to run.
if [ -n "$JAVA_HOME" ]; then
  if [ -x "$JAVA_HOME/jre/bin/java" ]; then
    JAVA_EXE="$JAVA_HOME/jre/bin/java"
  elif [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_EXE="$JAVA_HOME/bin/java"
  fi
fi

if [ -z "$JAVA_EXE" ]; then
  JAVA_EXE="java"
fi

if [ ! -x "$JAVA_EXE" ]; then
  echo "Error: JAVA_HOME is not set and no 'java' command can be found in your PATH."
  echo "Please set the JAVA_HOME variable or add Java to your PATH."
  exit 1
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVA_EXE" $DEFAULT_JVM_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"

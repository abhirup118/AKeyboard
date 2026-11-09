#!/bin/sh
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

GRADLE_BIN="$HOME/.cache/alarm-clock-build/gradle-8.13/bin/gradle"
if [ ! -f "$GRADLE_BIN" ]; then
  GRADLE_BIN="$(which gradle 2>/dev/null)"
fi

if [ -z "$GRADLE_BIN" ] || [ ! -x "$GRADLE_BIN" ]; then
  echo "Error: Gradle binary not found at $GRADLE_BIN." >&2
  exit 1
fi

exec "$GRADLE_BIN" "$@"


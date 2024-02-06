#!/usr/bin/env bash

if [ $# -ne "2" ]; then
  echo "need <path to project directory> <output file>"
  exit 1
fi

# https://stackoverflow.com/a/246128
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null && pwd)"
if uname -s | grep -iq cygwin; then
  DIR=$(cygpath -w "$DIR")
  PWD=$(cygpath -w "$PWD")
fi


#"$DIR/gradlew" -p "$DIR" busFactor
"$DIR/gradlew" -p "$DIR" headless -Pprj=$1 -Pout=$2
#!/bin/bash

set -e

./gradlew -p tests \
  clean \
  deployment \
  test -Dtags=stateful \
  shutdown


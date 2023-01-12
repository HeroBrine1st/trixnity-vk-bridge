#!/bin/bash

extra_args=()
if [ -n "${LOG_LEVEL}" ]; then
  extra_args+=("-Dorg.slf4j.simpleLogger.log.ru.herobrine1st=${LOG_LEVEL}")
fi

exec java "${extra_args[@]}" -jar /app/server.jar "${@}"
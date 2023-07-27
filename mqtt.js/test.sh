#!/usr/bin/env bash

PORT=${PORT:-8081}

printf "Starting web server on port \033[3m%s\033[23m...\n" "$PORT"

# for options, see https://www.npmjs.com/package/http-server
npx http-server . -c-1 --port "$PORT" -o

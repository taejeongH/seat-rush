#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: sh infra/scripts/verify.sh https://seat-rush.example.com"
  exit 1
fi

BASE_URL=${1%/}
MAX_ATTEMPTS=${VERIFY_MAX_ATTEMPTS:-30}
RETRY_DELAY_SECONDS=${VERIFY_RETRY_DELAY_SECONDS:-2}

wait_for_response() {
  name=$1
  url=$2
  attempt=1

  while [ "$attempt" -le "$MAX_ATTEMPTS" ]; do
    if curl --fail --silent --show-error "$url" >/dev/null 2>&1; then
      echo "$name responded successfully: $url"
      return 0
    fi

    if [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
      echo "Waiting for $name ($attempt/$MAX_ATTEMPTS)..."
      sleep "$RETRY_DELAY_SECONDS"
    fi

    attempt=$((attempt + 1))
  done

  echo "$name did not become ready: $url" >&2
  curl --fail --silent --show-error "$url" >/dev/null
}

wait_for_response "Frontend" "$BASE_URL/"
wait_for_response "Concert API" "$BASE_URL/api/concerts"

echo "Frontend and concert API responded successfully: $BASE_URL"

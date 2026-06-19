#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "Usage: sh infra/scripts/verify.sh https://seat-rush.example.com"
  exit 1
fi

BASE_URL=${1%/}

curl --fail --silent --show-error "$BASE_URL/" >/dev/null
curl --fail --silent --show-error "$BASE_URL/api/concerts" >/dev/null

echo "Frontend and concert API responded successfully: $BASE_URL"

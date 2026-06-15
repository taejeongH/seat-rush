#!/usr/bin/env sh
set -eu

if [ "$#" -ne 1 ]; then
  echo "사용법: ./infra/scripts/verify.sh https://seat-rush.example.com"
  exit 1
fi

BASE_URL=${1%/}

curl --fail --silent --show-error "$BASE_URL/" >/dev/null
curl --fail --silent --show-error "$BASE_URL/api/concerts" >/dev/null

echo "프론트엔드 및 공연 조회 API 응답을 확인했습니다: $BASE_URL"

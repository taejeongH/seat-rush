#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)

sh "$ROOT_DIR/infra/scripts/deploy-infra.sh"
sh "$ROOT_DIR/infra/scripts/deploy-server-local-build.sh"

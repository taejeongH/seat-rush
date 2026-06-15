#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)

"$ROOT_DIR/infra/scripts/deploy-infra.sh"
"$ROOT_DIR/infra/scripts/deploy-server.sh"

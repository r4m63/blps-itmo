#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
BASE_URL=${BASE_URL:-http://localhost:8080/api}
export BASE_URL

echo "Running all API scenarios against $BASE_URL"
for script in "$SCRIPT_DIR"/scenario_*.sh; do
  echo "\n>>> $script"
  bash "$script"
done

echo "\nAll scenarios finished."

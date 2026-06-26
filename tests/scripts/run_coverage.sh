#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

python -m pytest \
  -c "$ROOT/backend/tests/pytest.ini" \
  --cov="$ROOT/backend/adapters/bitcoin_core_flask" \
  --cov="$ROOT/backend/adapters/lightning_flask" \
  --cov-report=term-missing \
  --cov-report=html:"$ROOT/backend/tests/coverage/python-html" \
  --cov-report=xml:"$ROOT/backend/tests/coverage/python-coverage.xml" \
  "$ROOT/backend/tests/python"

(
  cd "$ROOT/backend/kerosene"
  ./gradlew test jacocoTestReport
)

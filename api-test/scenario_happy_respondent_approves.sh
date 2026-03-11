#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"
require_tools

echo "=== Scenario: happy path (grounds exist, respondent replies, support approves) ==="

# 1) create claim
resp=$(create_claim "host-1" "guest-1" 150 "USD" "Повреждение мебели" '[{"type":"EVIDENCE","url":"https://files/evidence1.jpg","uploaderId":"host-1"}]')
claim_id=$(echo "$resp" | jq -r '.id')
assert_field_equals "$resp" '.status' 'DATA_REVIEW'

# 2) intake check enoughData=true -> RISK_REVIEW
resp=$(json_post "$BASE_URL/claims/$claim_id/intake-check" '{"enoughData":true}')
assert_field_equals "$resp" '.status' 'RISK_REVIEW'
assert_field_equals "$resp" '.enoughData' 'true'

# 3) rules check groundsForPenalty=true -> WAITING_RESPONDENT
resp=$(json_post "$BASE_URL/claims/$claim_id/rules-check" '{"groundsForPenalty":true}')
assert_field_equals "$resp" '.status' 'WAITING_RESPONDENT'
assert_field_equals "$resp" '.groundsForPenalty' 'true'
response_due=$(echo "$resp" | jq -r '.responseDueAt')
if [[ "$response_due" == "null" ]]; then
  echo "[FAIL] responseDueAt not set" >&2; exit 1
else
  echo "[OK] responseDueAt=$response_due"
fi

# 4) respondent response -> SUPPORT_REVIEW
resp=$(json_post "$BASE_URL/claims/$claim_id/respondent/response" '{"comment":"Не согласен, прикладываю фото"}')
assert_field_equals "$resp" '.status' 'SUPPORT_REVIEW'
assert_field_equals "$resp" '.respondentTimeout' 'false'

# 5) support decision approve -> CLOSED_PENALTY
resp=$(json_post "$BASE_URL/claims/$claim_id/support-decision" '{"approvePenalty":true,"comment":"Подтверждаю удержание"}')
assert_field_equals "$resp" '.status' 'CLOSED_PENALTY'
assert_field_equals "$resp" '.penaltyApplied' 'true'
assert_field_equals "$resp" '.approvePenalty' 'true'

# Final state
final=$(json_get "$BASE_URL/claims/$claim_id")
print_status "$final"

echo "=== OK: happy path complete for claim $claim_id ==="

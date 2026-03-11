#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"
require_tools

echo "=== Scenario: respondent timeout -> support approves penalty ==="

resp=$(create_claim "host-3" "guest-3" 200 "USD" "Разбитое окно" '[{"type":"EVIDENCE","url":"https://files/window.jpg","uploaderId":"host-3"}]')
claim_id=$(echo "$resp" | jq -r '.id')
assert_field_equals "$resp" '.status' 'DATA_REVIEW'

resp=$(json_post "$BASE_URL/claims/$claim_id/intake-check" '{"enoughData":true}')
assert_field_equals "$resp" '.status' 'RISK_REVIEW'

resp=$(json_post "$BASE_URL/claims/$claim_id/rules-check" '{"groundsForPenalty":true}')
assert_field_equals "$resp" '.status' 'WAITING_RESPONDENT'

resp=$(json_post "$BASE_URL/claims/$claim_id/respondent/timeout" '{}')
assert_field_equals "$resp" '.status' 'SUPPORT_REVIEW'
assert_field_equals "$resp" '.respondentTimeout' 'true'

resp=$(json_post "$BASE_URL/claims/$claim_id/support-decision" '{"approvePenalty":true,"comment":"Нет ответа арендатора, удержание подтверждено"}')
assert_field_equals "$resp" '.status' 'CLOSED_PENALTY'
assert_field_equals "$resp" '.penaltyApplied' 'true'

final=$(json_get "$BASE_URL/claims/$claim_id")
print_status "$final"

echo "=== OK: timeout + penalty path complete for claim $claim_id ==="

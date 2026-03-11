#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"
require_tools

echo "=== Scenario: respondent disputes, support rejects penalty ==="

resp=$(create_claim "host-4" "guest-4" 120 "USD" "Задержка выезда" '[{"type":"EVIDENCE","url":"https://files/late-checkout.jpg","uploaderId":"host-4"}]')
claim_id=$(echo "$resp" | jq -r '.id')
assert_field_equals "$resp" '.status' 'DATA_REVIEW'

resp=$(json_post "$BASE_URL/claims/$claim_id/intake-check" '{"enoughData":true}')
assert_field_equals "$resp" '.status' 'RISK_REVIEW'

resp=$(json_post "$BASE_URL/claims/$claim_id/rules-check" '{"groundsForPenalty":true}')
assert_field_equals "$resp" '.status' 'WAITING_RESPONDENT'

resp=$(json_post "$BASE_URL/claims/$claim_id/respondent/response" '{"comment":"Предоставил доказательства обратного"}')
assert_field_equals "$resp" '.status' 'SUPPORT_REVIEW'

resp=$(json_post "$BASE_URL/claims/$claim_id/support-decision" '{"approvePenalty":false,"comment":"Доказательства арендатора убедительны"}')
assert_field_equals "$resp" '.status' 'CLOSED_REJECT'
assert_field_equals "$resp" '.penaltyApplied' 'false'

final=$(json_get "$BASE_URL/claims/$claim_id")
print_status "$final"

echo "=== OK: dispute then reject complete for claim $claim_id ==="

#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
source "$SCRIPT_DIR/common.sh"
require_tools

echo "=== Scenario: missing data -> provide docs -> no grounds -> reject ==="

resp=$(create_claim "host-2" "guest-2" 75 "EUR" "Шум ночью" '[{"type":"EVIDENCE","url":"https://files/audio1.mp3","uploaderId":"host-2"}]')
claim_id=$(echo "$resp" | jq -r '.id')
assert_field_equals "$resp" '.status' 'DATA_REVIEW'

# intake false -> NEED_ADDITIONAL_DATA
resp=$(json_post "$BASE_URL/claims/$claim_id/intake-check" '{"enoughData":false}')
assert_field_equals "$resp" '.status' 'NEED_ADDITIONAL_DATA'

# provide extra docs -> DATA_REVIEW
resp=$(json_post "$BASE_URL/claims/$claim_id/provide-docs" '{"attachments":[{"type":"CLARIFICATION","url":"https://files/additional.pdf","uploaderId":"host-2"}]}')
assert_field_equals "$resp" '.status' 'DATA_REVIEW'

# intake again true -> RISK_REVIEW
resp=$(json_post "$BASE_URL/claims/$claim_id/intake-check" '{"enoughData":true}')
assert_field_equals "$resp" '.status' 'RISK_REVIEW'

# rules: no grounds -> CLOSED_REJECT
resp=$(json_post "$BASE_URL/claims/$claim_id/rules-check" '{"groundsForPenalty":false}')
assert_field_equals "$resp" '.status' 'CLOSED_REJECT'
assert_field_equals "$resp" '.penaltyApplied' 'false'

final=$(json_get "$BASE_URL/claims/$claim_id")
print_status "$final"

echo "=== OK: rejection path complete for claim $claim_id ==="

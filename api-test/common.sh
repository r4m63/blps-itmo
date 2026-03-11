#!/usr/bin/env bash
set -euo pipefail

# Base URL can be overridden: BASE_URL=http://localhost:8080/api ./script.sh
BASE_URL=${BASE_URL:-http://localhost:8080/api}

require_tools() {
  for tool in curl jq; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      echo "[ERR] $tool is required" >&2
      exit 1
    fi
  done
}

json_post() {
  local url="$1" body="$2"
  local tmp status resp
  tmp=$(mktemp)
  status=$(curl -s -o "$tmp" -w "%{http_code}" -X POST "$url" \
    -H "Content-Type: application/json" \
    -d "$body")
  resp=$(cat "$tmp"); rm -f "$tmp"
  handle_response "$url" "$status" "$resp"
}

json_get() {
  local url="$1"
  local tmp status resp
  tmp=$(mktemp)
  status=$(curl -s -o "$tmp" -w "%{http_code}" -X GET "$url")
  resp=$(cat "$tmp"); rm -f "$tmp"
  handle_response "$url" "$status" "$resp"
}

handle_response() {
  local url="$1" status="$2" resp="$3"
  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    echo "[ERR] HTTP $status from $url" >&2
    echo "$resp" >&2
    exit 1
  fi
  if ! echo "$resp" | jq -e . >/dev/null 2>&1; then
    echo "[ERR] Non-JSON response from $url:" >&2
    echo "$resp" >&2
    exit 1
  fi
  echo "$resp"
}

assert_field_equals() {
  local json="$1" field="$2" expected="$3"
  local actual
  actual=$(echo "$json" | jq -r "$field")
  if [[ "$actual" != "$expected" ]]; then
    echo "[FAIL] $field expected=$expected actual=$actual" >&2
    exit 1
  else
    echo "[OK] $field=$actual"
  fi
}

create_claim() {
  local initiator="$1" respondent="$2" amount="$3" currency="$4" reason="$5" attachments_json="$6"
  local payload
  payload=$(cat <<JSON
{ "initiatorId": "$initiator", "respondentId": "$respondent", "amount": $amount, "currency": "$currency", "reason": "$reason", "attachments": $attachments_json }
JSON
)
  local resp
  resp=$(json_post "$BASE_URL/claims" "$payload")
  local id
  id=$(echo "$resp" | jq -r '.id')
  echo "[NEW] claim id=$id"
  echo "$resp"
}

print_status() {
  local json="$1"
  echo "$json" | jq '{id, status, enoughData, groundsForPenalty, respondentTimeout, approvePenalty, penaltyApplied, responseDueAt, createdAt}'
}

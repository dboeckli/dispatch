#!/bin/sh
set -eu

BOOTSTRAP="${1:?BOOTSTRAP missing (expected host:port)}"

MAX_ATTEMPTS=60
SLEEP_SECONDS=2
ATTEMPT_TIMEOUT_SECONDS=5

echo "[kcat: wait-for-kafka] waiting for Kafka at ${BOOTSTRAP} (attempts=${MAX_ATTEMPTS}, sleep=${SLEEP_SECONDS}s, attempt-timeout=${ATTEMPT_TIMEOUT_SECONDS}s)"

i=1
while [ "$i" -le "$MAX_ATTEMPTS" ]; do
  echo "[kcat: wait-for-kafka] attempt ${i}/${MAX_ATTEMPTS}: kcat -b ${BOOTSTRAP} -L"

  if command -v timeout >/dev/null 2>&1; then
    timeout "${ATTEMPT_TIMEOUT_SECONDS}s" kcat -b "${BOOTSTRAP}" -L -d broker,metadata
  else
    kcat -b "${BOOTSTRAP}" -L -d broker,metadata
  fi

  rc=$?
  if [ "$rc" -eq 0 ]; then
    echo "[kcat: wait-for-kafka] Kafka is reachable."
    exit 0
  fi

  echo "[kcat: wait-for-kafka] attempt ${i}/${MAX_ATTEMPTS} failed (rc=${rc}); retrying in ${SLEEP_SECONDS}s..."
  i=$((i + 1))
  sleep "$SLEEP_SECONDS"
done

echo "[kcat: wait-for-kafka] timeout waiting for Kafka at ${BOOTSTRAP}"
exit 1
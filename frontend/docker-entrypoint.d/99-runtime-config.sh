#!/bin/sh
set -eu

CONFIG_PATH="/usr/share/nginx/html/config.js"

API_BASE_URL="${API_BASE_URL:-${VITE_API_BASE_URL:-}}"

if [ -z "${API_BASE_URL}" ]; then
  echo "window.__RISK_CONSOLE_CONFIG__ = window.__RISK_CONSOLE_CONFIG__ || {};" > "${CONFIG_PATH}"
  exit 0
fi

escaped="$(printf '%s' "${API_BASE_URL}" | sed 's/\\/\\\\/g; s/\"/\\"/g')"

cat > "${CONFIG_PATH}" <<EOF
window.__RISK_CONSOLE_CONFIG__ = Object.assign(window.__RISK_CONSOLE_CONFIG__ || {}, {
  API_BASE_URL: "${escaped}"
});
EOF


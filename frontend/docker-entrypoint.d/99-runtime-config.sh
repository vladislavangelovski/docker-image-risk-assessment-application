#!/bin/sh
set -eu

CONFIG_PATH="/usr/share/nginx/html/config.js"

API_BASE_URL="${API_BASE_URL:-${VITE_API_BASE_URL:-}}"
AUTH_BASE_URL="${AUTH_BASE_URL:-${VITE_AUTH_BASE_URL:-}}"
AUTH_REALM="${AUTH_REALM:-${VITE_AUTH_REALM:-}}"
AUTH_CLIENT_ID="${AUTH_CLIENT_ID:-${VITE_AUTH_CLIENT_ID:-}}"

if [ -z "${API_BASE_URL}" ] && [ -z "${AUTH_BASE_URL}" ] && [ -z "${AUTH_REALM}" ] && [ -z "${AUTH_CLIENT_ID}" ]; then
  echo "window.__RISK_CONSOLE_CONFIG__ = window.__RISK_CONSOLE_CONFIG__ || {};" > "${CONFIG_PATH}"
  exit 0
fi

escape_js_string() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/\"/\\"/g'
}

{
  echo "window.__RISK_CONSOLE_CONFIG__ = Object.assign(window.__RISK_CONSOLE_CONFIG__ || {}, {"

  if [ -n "${API_BASE_URL}" ]; then
    echo "  API_BASE_URL: \"$(escape_js_string "${API_BASE_URL}")\","
  fi
  if [ -n "${AUTH_BASE_URL}" ]; then
    echo "  AUTH_BASE_URL: \"$(escape_js_string "${AUTH_BASE_URL}")\","
  fi
  if [ -n "${AUTH_REALM}" ]; then
    echo "  AUTH_REALM: \"$(escape_js_string "${AUTH_REALM}")\","
  fi
  if [ -n "${AUTH_CLIENT_ID}" ]; then
    echo "  AUTH_CLIENT_ID: \"$(escape_js_string "${AUTH_CLIENT_ID}")\","
  fi

  echo "});"
} > "${CONFIG_PATH}"

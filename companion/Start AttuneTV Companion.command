#!/bin/zsh
set -eu
cd "${0:A:h}"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"
if ! command -v node >/dev/null; then
  print "Install Node.js 22 or later, then open this launcher again."
  read -r "reply?Press Return to close. "
  exit 1
fi
if [[ ! -d node_modules/jose ]]; then npm ci --ignore-scripts; fi
print "Open http://127.0.0.1:8766 on this Mac to connect ChatGPT and pair your phone."
exec node server.mjs

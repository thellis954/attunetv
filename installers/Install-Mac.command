#!/usr/bin/env bash
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
bash ./install.sh
result=$?
read -r -p 'Press Enter to close this window: ' ignored
exit "$result"

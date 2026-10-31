#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Runs the Python tools of scripts/porting (tt_assets.py, tt_oracle.py and their tests) from the root of this repository.
#
#   tools/porting.sh test                      the unit tests of both tools
#   tools/porting.sh tt_oracle.py [ARGS...]    e.g. --check
#   tools/porting.sh tt_assets.py [ARGS...]    e.g. --check
#
#   TT_1710_SOURCE   the pinned checkout of the original 1.7.10 mod (NOTICE.md; default ../ThaumicTinkerer-1.7.10)
#   THAUMCRAFT_DIR   a checkout of Thaumcraft 4 Reload (default ../Thaumcraft-1.21.1, the one tools/publish-thaumcraft.sh makes);
#                    tt_oracle.py reads the aspect constants of thaumcraft.api and the legacy id table from it
# The tests against the real checkouts are skipped when one of them is missing; the other tests need neither.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ "$#" -ge 1 ] || { sed -n '3,$ { /^#/!q; s/^# \{0,1\}//; p }' "${BASH_SOURCE[0]}" >&2; exit 2; }
command -v python3 >/dev/null 2>&1 || { echo "porting: python3 not found" >&2; exit 2; }
cd "$ROOT"
case "$1" in
  test) shift; python3 -I -B -m unittest discover -s scripts/porting -p "test_tt_*.py" "$@" ;;
  tt_assets.py|tt_oracle.py) tool="$1"; shift; python3 -I -B "scripts/porting/$tool" "$@" ;;
  *) echo "porting: unknown command $1 (test, tt_assets.py or tt_oracle.py)" >&2; exit 2 ;;
esac

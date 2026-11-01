#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Runs the multiplayer check of Thaumic Tinkerer (docs/MULTIPLAYER_VERIFICATION.md): a real dedicated server with the add-on and its
# server test mod and five real clients (two at the same time, then two whose config differs, then one on a second server with Kami
# off), each in its own JVM, all started and stopped by ONE Gradle task, under Xvfb with Mesa software rendering.
#
#   tools/multiplayer-smoke.sh [--fabric|--neoforge] [-l GRADLE_LOG] [-t CLIENT_TIMEOUT_SECONDS] [-p PORT] [-- EXTRA_GRADLE_ARGS...]
#
# --fabric (the default) runs runTinkererMultiplayer of the root project; results in build/tinkerer-multiplayer/, run directories
# run-tinkerer-mp-{server,lead,peer,mismatch,mismatch-kami,kami-on}/. --neoforge runs :neoforge:runMultiplayer; results in neoforge/build/tinkerer-multiplayer/,
# run directories neoforge/run-mp-{server,lead,peer,mismatch,mismatch-kami,kami-on}/. The results are server-report.json,
# server-kami-off-report.json, <client>/report.json (with screenshots/), summary.txt, the console output, latest.log and command line of
# every JVM, and gradle.log (a copy).
# The exit code is 0 only when every harness completed and no check is FAIL, HARNESS_ERROR or NOT_RUN (the Gradle task checks it).
#
# GRADLE_CMD replaces ./gradlew, as for tools/client-smoke.sh: a command line that is given the Gradle arguments last; its output is
# tee'd to the log unless it contains the word {LOG}, which is then replaced by the log path (a wrapper that writes the log itself).
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOADER=fabric
OPTS=()
while [ $# -gt 0 ]; do
  case "$1" in
    --fabric) LOADER=fabric; shift;;
    --neoforge) LOADER=neoforge; shift;;
    --) break;;
    *) OPTS+=("$1"); shift;;
  esac
done
set -- ${OPTS[@]+"${OPTS[@]}"} "$@"
case "$LOADER" in
  fabric)
    OUT="$ROOT/build/tinkerer-multiplayer"
    TASK=runTinkererMultiplayer
    LOG="${TMPDIR:-/tmp}/tt-multiplayer-gradle.log";;
  neoforge)
    OUT="$ROOT/neoforge/build/tinkerer-multiplayer"
    TASK=:neoforge:runMultiplayer
    LOG="${TMPDIR:-/tmp}/tt-multiplayer-neoforge-gradle.log";;
esac
TIMEOUT=1200
PORT=""
while getopts "l:t:p:" opt; do
  case "$opt" in l) LOG="$OPTARG";; t) TIMEOUT="$OPTARG";; p) PORT="$OPTARG";; *) exit 2;; esac
done
shift $((OPTIND-1))
[ "${1:-}" = "--" ] && shift
EXTRA=(-PtinkererMultiplayerTimeout="$TIMEOUT")
[ -n "$PORT" ] && EXTRA+=(-PtinkererMultiplayerPort="$PORT")

GRADLE_CMD="${GRADLE_CMD:-./gradlew}"
TEE=1
case "$GRADLE_CMD" in *'{LOG}'*) GRADLE_CMD="${GRADLE_CMD//\{LOG\}/$LOG}"; TEE=0;; esac
read -r -a GRADLE <<< "$GRADLE_CMD"
[ "$TEE" = 1 ] && GRADLE+=(--console=plain)
for tool in xvfb-run "${GRADLE[0]}"; do
  case "$tool" in ./*) [ -x "$ROOT/$tool" ] && continue;; esac
  command -v "$tool" >/dev/null 2>&1 || [ -x "$tool" ] || { echo "multiplayer-smoke: $tool not found" >&2; exit 2; }
done

# Mesa software rendering: no GPU in this machine. Two clients render at the same time on 4 CPUs: two llvmpipe threads each.
export LIBGL_ALWAYS_SOFTWARE=1
export GALLIUM_DRIVER=llvmpipe
export LP_NUM_THREADS="${LP_NUM_THREADS:-2}"

echo "multiplayer-smoke: $LOADER, gradle log $LOG, results $OUT" >&2
cd "$ROOT" || exit 2
if [ "$TEE" = 1 ]; then
  xvfb-run -a -s "-screen 0 1280x720x24 -nolisten tcp" "${GRADLE[@]}" "$TASK" "${EXTRA[@]}" "$@" 2>&1 | tee "$LOG"
  status=${PIPESTATUS[0]}
else
  xvfb-run -a -s "-screen 0 1280x720x24 -nolisten tcp" "${GRADLE[@]}" "$TASK" "${EXTRA[@]}" "$@"
  status=$?
fi
mkdir -p "$OUT"
cp -f "$LOG" "$OUT/gradle.log" 2>/dev/null
if [ -f "$OUT/summary.txt" ]; then
  sed 's/^/multiplayer-smoke: /' "$OUT/summary.txt"
else
  echo "multiplayer-smoke: no summary.txt was written; see $OUT/gradle.log" >&2
fi
exit $status

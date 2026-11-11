#!/usr/bin/env bash
# Scans an APK with Exodus Privacy's tracker scanner (exodus-standalone) and fails if it finds a
# tracker, or if the APK's permissions differ from .github/privacy/permissions.txt (#217).
#
#   .github/scripts/exodus-scan.sh <apk> <out-dir>
#
# Writes <out-dir>/exodus.json (the scanner's report) and <out-dir>/exodus-summary.md (a few lines
# for release notes). The container sees a copy of the APK and nothing else of the checkout.
#
# The scanner matches the APK's code against Exodus's list of known tracker signatures, which it
# fetches when it runs. It finds libraries, not behaviour: "0 trackers" is what it can say, not
# that the app sends nothing.
#
# The image is pinned by digest, as the workflows pin actions by commit. To upgrade, pick a tag
# from https://hub.docker.com/r/exodusprivacy/exodus-standalone/tags and pin its index digest
# (`docker buildx imagetools inspect exodusprivacy/exodus-standalone:<tag>`).
# A false positive is ignored by the tracker's Exodus id: add `-i <id>` below, with a comment saying why.
set -euo pipefail

IMAGE="exodusprivacy/exodus-standalone:v1.5.0@sha256:a6531be9a6b666a8ffb6aec98b409cf6faf65536c5b9597e5b99195ca9422b88"
EXPECTED="$(dirname "$0")/../privacy/permissions.txt"

apk="$1"
out="$2"
mkdir -p "$out"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp "$apk" "$work/app.apk"

# The exit code is the tracker count; the report decides below, so a count isn't taken for a failure to run.
# No report usually means the signature list couldn't be fetched (the runner's network dropped), so try again.
for attempt in 1 2 3; do
  docker run --rm --platform linux/amd64 -v "$work:/app" "$IMAGE" -j -o /app/exodus.json /app/app.apk >/dev/null || true
  [ -s "$work/exodus.json" ] && break
  [ "$attempt" -lt 3 ] && { echo "::warning::The Exodus scanner wrote no report (attempt $attempt); trying again."; sleep $((attempt * 20)); }
done
if [ ! -s "$work/exodus.json" ]; then
  echo "::error::The Exodus scanner wrote no report."
  exit 1
fi
cp "$work/exodus.json" "$out/exodus.json"

failed=0

trackers="$(jq -r '.trackers | length' "$out/exodus.json")"
if [ "$trackers" != "0" ]; then
  echo "::error::Exodus found $trackers tracker(s) in the APK. The app carries no trackers (CLAUDE.md, Network)."
  jq -r '.trackers[] | "  - \(.name) (Exodus id \(.id))"' "$out/exodus.json"
  failed=1
fi

grep -v -e '^#' -e '^[[:space:]]*$' "$EXPECTED" | sort > "$work/expected"
jq -r '.application.permissions[]' "$out/exodus.json" | sort > "$work/actual"
added="$(comm -13 "$work/expected" "$work/actual")"
removed="$(comm -23 "$work/expected" "$work/actual")"
if [ -n "$added" ]; then
  echo "::error::The APK asks for permissions that aren't in .github/privacy/permissions.txt:"
  echo "$added" | sed 's/^/  + /'
  failed=1
fi
if [ -n "$removed" ]; then
  echo "::error::The APK no longer asks for permissions listed in .github/privacy/permissions.txt; take them out of the list:"
  echo "$removed" | sed 's/^/  - /'
  failed=1
fi

permissions="$(wc -l < "$work/actual" | tr -d ' ')"
{
  echo "**Exodus Privacy scan:** $trackers trackers, $permissions permissions ([exodus-standalone](https://github.com/Exodus-Privacy/exodus-standalone) v1.5.0, run on this build)."
  echo
  echo "<details><summary>Permissions</summary>"
  echo
  sed 's/^/- `/; s/$/`/' "$work/actual"
  echo
  echo "</details>"
} > "$out/exodus-summary.md"

cat "$out/exodus-summary.md"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then cat "$out/exodus-summary.md" >> "$GITHUB_STEP_SUMMARY"; fi
exit "$failed"

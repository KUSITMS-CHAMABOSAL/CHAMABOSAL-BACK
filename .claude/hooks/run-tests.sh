#!/usr/bin/env bash
# Stop 훅: Claude가 응답을 끝내려 할 때 테스트를 돌리고, 실패하면 결과를 돌려줘 계속 고치게 한다.
# - 커밋되지 않은 코드 변경이 없거나, 같은 변경으로 이미 통과했으면 건너뛴다.
# - 고친 뒤에도 같은 실패가 나오거나 MAX_BLOCKS번 막았으면 더 막지 않고 사용자에게 알린다.
set -u

MAX_BLOCKS=3

input=$(cat)
session_id=$(printf '%s' "$input" | tr -d '\n' \
  | sed -n 's/.*"session_id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
if printf '%s' "$input" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  active=true
else
  active=false
fi

cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0

state_dir="${TMPDIR:-/tmp}/chamabosal-harness/${session_id:-unknown}"
mkdir -p "$state_dir" || exit 0
log="$state_dir/test.log"

# 새 요청에서 처음 끝내려는 경우에는 이전 시도 기록을 지운다.
if [ "$active" = false ]; then
  rm -f "$state_dir/blocks" "$state_dir/failure"
fi

changes=$(
  {
    git diff HEAD -- src build.gradle settings.gradle
    git ls-files --others --exclude-standard -- src | while IFS= read -r file; do
      printf '%s\n' "$file"
      cat "$file"
    done
  } 2>/dev/null
)
[ -z "$changes" ] && exit 0

fingerprint=$(printf '%s' "$changes" | git hash-object --stdin)
[ "$(cat "$state_dir/passed" 2>/dev/null)" = "$fingerprint" ] && exit 0

if ./gradlew test --console=plain >"$log" 2>&1; then
  printf '%s\n' "$fingerprint" >"$state_dir/passed"
  rm -f "$state_dir/blocks" "$state_dir/failure"
  exit 0
fi

# 실패 위치와 메시지만 남긴다. 실행 시간처럼 매번 바뀌는 줄은 빼서 같은 실패를 같은 값으로 비교한다.
excerpt=$(grep -vE '^> Task|^$|INFO|Hibernate|OpenJDK|BUILD |actionable task|Consider enabling' "$log" \
  | grep -E -A12 'FAILED|error:' | head -100)
failure=$(printf '%s' "$excerpt" | git hash-object --stdin)
blocks=$(cat "$state_dir/blocks" 2>/dev/null || echo 0)

notice=""
if [ "$active" = true ] && [ "$failure" = "$(cat "$state_dir/failure" 2>/dev/null)" ]; then
  notice="같은 테스트 실패가 이어져 자동 수정을 멈췄습니다."
elif [ "$blocks" -ge "$MAX_BLOCKS" ]; then
  notice="자동 수정을 ${MAX_BLOCKS}번 시도해 멈췄습니다."
fi

if [ -n "$notice" ]; then
  rm -f "$state_dir/blocks" "$state_dir/failure"
  printf '{"systemMessage": "%s 테스트가 아직 실패합니다. 로그: %s"}\n' "$notice" "$log"
  exit 0
fi

printf '%s\n' "$((blocks + 1))" >"$state_dir/blocks"
printf '%s\n' "$failure" >"$state_dir/failure"

{
  echo "./gradlew test가 실패했습니다. 아래 원인을 고친 뒤 끝내세요."
  echo "테스트만 먼저 작성하는 단계처럼 실패가 의도된 경우에는 코드를 고치지 말고 그 사실을 보고하고 끝내세요."
  echo "검사를 통과시키려고 ArchitectureTest, ErrorCodeSnapshotTest, 스냅샷의 기존 줄을 고치지 마세요."
  echo "전체 로그: $log"
  echo
  printf '%s\n' "$excerpt"
} >&2
exit 2

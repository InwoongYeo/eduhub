#!/usr/bin/env bash
# Stop: Claude가 응답을 마칠 때 backend/src가 바뀌었으면 테스트를 돌린다.
# 실패하면 {"decision":"block","reason":...}을 stdout으로 내보내 Claude가 멈추지 않고 고치게 한다.
# 훅 자체 문제(git·gradle 실행 불가 등)로는 Claude를 막지 않는다 (항상 exit 0).

input=$(cat)

# 1. 무한 반복 방지: 이미 Stop 훅 때문에 이어서 작업한 응답이면 그대로 멈춘다
if command -v jq >/dev/null 2>&1; then
  active=$(printf '%s' "$input" | jq -r '.stop_hook_active // false' 2>/dev/null)
else
  printf '%s' "$input" | grep -qE '"stop_hook_active"[[:space:]]*:[[:space:]]*true' && active=true || active=false
fi
[ "$active" = "true" ] && exit 0

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$project_dir" 2>/dev/null || exit 0

# 2. 백엔드 소스가 바뀌지 않았으면 할 일이 없다 (커밋 안 된 변경·새 파일 기준)
changes=$(git status --porcelain -- backend/src 2>/dev/null)
[ -n "$changes" ] || exit 0

# 3. 테스트
output=$(cd backend && ./gradlew test -q 2>&1)
[ $? -eq 0 ] && exit 0

failures=""
if printf '%s' "$output" | grep -q 'There were failing tests'; then
  # -q는 테스트별 FAILED 줄을 찍지 않으므로 JUnit XML 결과에서 "클래스 > 메서드 FAILED"와 예외 줄을 만든다
  summary=$(printf '%s\n' "$output" | grep -E 'tests completed' | head -1)
  failures=$(grep -l '<failure' backend/build/test-results/test/*.xml 2>/dev/null \
    | xargs -r awk '
        /<testcase / {
          match($0, /name="[^"]*"/);      name = substr($0, RSTART + 6, RLENGTH - 7)
          match($0, /classname="[^"]*"/); cls  = substr($0, RSTART + 11, RLENGTH - 12)
        }
        /<failure / {
          match($0, /message="[^"]*"/); msg  = substr($0, RSTART + 9, RLENGTH - 10)
          match($0, /type="[^"]*"/);    type = substr($0, RSTART + 6, RLENGTH - 7)
          gsub(/&#1[03];/, " ", msg); gsub(/&lt;/, "<", msg); gsub(/&gt;/, ">", msg)
          gsub(/&quot;/, "\"", msg); gsub(/&apos;/, "\047", msg); gsub(/&amp;/, "\\&", msg)
          gsub(/[[:space:]]+/, " ", msg)
          print cls " > " name " FAILED"
          print "    " type ": " substr(msg, 1, 200)
        }' \
    | grep -E 'FAILED|Exception|Error' | head -20)
  [ -n "$summary" ] && failures="$summary
$failures"
else
  failures=$(printf '%s\n' "$output" | grep -E 'FAILED|Exception' | grep -v '^BUILD FAILED' | head -20)
fi
# 컴파일 오류처럼 위에서 고를 줄이 없으면 마지막 20줄을 보여 준다
[ -n "$failures" ] || failures=$(printf '%s\n' "$output" | tail -20)

reason="backend/src 변경 후 백엔드 테스트가 실패했습니다. 아래 실패를 고친 뒤 응답을 마치세요.
(전체 실행: cd backend && ./gradlew test, EnrollmentConcurrencyTest는 Docker 필요)

$failures"

if command -v jq >/dev/null 2>&1; then
  jq -cn --arg r "$reason" '{decision:"block",reason:$r}'
else
  escaped=$(printf '%s' "$reason" \
    | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/\\t/g' -e 's/\r$//' \
    | awk '{printf "%s%s", (NR>1 ? "\\n" : ""), $0}')
  printf '{"decision":"block","reason":"%s"}\n' "$escaped"
fi
exit 0

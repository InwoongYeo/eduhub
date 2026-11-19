#!/usr/bin/env bash
# PreToolUse(Edit|Write): 사람이 관리해야 하는 파일을 Claude가 수정하지 못하게 막는다.
# 막으면 exit 2와 함께 stderr로 이유·대신 할 일을 남긴다 (Claude가 읽고 다른 방법을 찾는다).
# 문자열 비교와 파일 존재 확인만 한다.

input=$(cat)
if command -v jq >/dev/null 2>&1; then
  file_path=$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
else
  # jq가 없으면 sed로 file_path 값만 꺼내고 JSON의 \\ 를 \ 로 되돌린다
  file_path=$(printf '%s' "$input" \
    | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p' \
    | sed 's/\\\\/\\/g')
fi
[ -n "$file_path" ] || exit 0

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"
project_dir="${project_dir//\\//}"

# 정규화: 역슬래시 → /, 상대 경로는 프로젝트 기준, 비교는 소문자 (Windows는 대소문자 무시)
path="${file_path//\\//}"
case "$path" in
  /*|[A-Za-z]:/*) ;;
  *) path="$project_dir/$path" ;;
esac
lower=$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')
name="${lower##*/}"

block() {
  printf '차단됨(protect-files): %s\n대신 할 일: %s\n대상: %s\n' "$1" "$2" "$file_path" >&2
  exit 2
}

# 1. 운영 설정
if [ "$name" = "application-prod.yml" ]; then
  block "운영 설정은 사람이 PR로만 수정합니다." \
    "개발 설정은 application-dev.yml을 수정하세요."
fi

# 2. 비밀 파일 (.env, .env.*) — .env.example은 허용
if [ "$name" = ".env" ] || { [[ "$name" == .env.* ]] && [ "$name" != ".env.example" ]; }; then
  block "비밀 파일은 수정하지 않습니다." \
    ".env.example에 변수 이름만 추가하세요 (값은 쓰지 않음)."
fi

# 3. 이미 존재하는 Flyway 마이그레이션
if [[ "$lower" == */db/migration/* ]] && [[ "$name" == v*.sql ]] && [ -f "$path" ]; then
  block "기존 마이그레이션은 수정하지 않습니다 (이미 적용된 DB와 체크섬이 어긋남)." \
    "새 버전 파일(V{n}__설명.sql)을 추가하세요."
fi

# 4. OpenAPI 생성 코드
if [[ "$lower" == */frontend/packages/api-client/src/generated/* ]]; then
  block "OpenAPI에서 생성한 코드입니다." \
    "openapi.json을 갱신한 뒤 frontend에서 npm run generate:api로 다시 생성하세요."
fi

exit 0

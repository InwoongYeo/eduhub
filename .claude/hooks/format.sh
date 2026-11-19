#!/usr/bin/env bash
# PostToolUse(Edit|Write): Claude가 수정한 파일을 자동 포맷한다.
# 포맷 도구가 없거나 실패해도 Claude 작업을 막지 않도록 항상 exit 0.

command -v jq >/dev/null 2>&1 || exit 0

file_path=$(jq -r '.tool_input.file_path // empty' 2>/dev/null)
[ -n "$file_path" ] || exit 0

project_dir="${CLAUDE_PROJECT_DIR:-$(pwd)}"

case "$file_path" in
  *.java)
    (cd "$project_dir/backend" && ./gradlew spotlessApply -q) >/dev/null 2>&1
    ;;
  *.ts|*.tsx)
    prettier="$project_dir/frontend/node_modules/.bin/prettier"
    if [ -x "$prettier" ]; then
      (cd "$project_dir/frontend" && "$prettier" --write "$file_path") >/dev/null 2>&1
    fi
    ;;
esac

exit 0

#!/usr/bin/env bash
# PreToolUse(Bash|PowerShell): 운영 환경 접속과 되돌릴 수 없는 명령을 실행 전에 막는다.
# 두 도구 모두 tool_input.command에 명령 문자열이 있으므로 같은 규칙으로 검사한다.
# 막으면 exit 2와 함께 stderr로 이유·대안을 남긴다 (Claude가 읽고 다른 방법을 찾는다).
# 문자열 검사만 한다. 외부 명령(git 등)을 부르지 않아 일반 명령을 느리게 하지 않는다.

input=$(cat)
if command -v jq >/dev/null 2>&1; then
  cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)
else
  # jq가 없으면 sed로 command 값만 꺼낸다. 실패하면 JSON 기호를 공백으로 바꾼 원본 전체를 검사한다 (막는 쪽으로 넓게)
  cmd=$(printf '%s' "$input" | sed -n 's/.*"command"[[:space:]]*:[[:space:]]*"\(\([^"\\]\|\\.\)*\)".*/\1/p')
  [ -n "$cmd" ] || cmd=$(printf '%s' "$input" | tr '"{},' '    ')
fi
[ -n "$cmd" ] || exit 0

block() {
  printf '차단됨(guard-bash): %s\n대안: %s\n' "$1" "$2" >&2
  exit 2
}

# 1. 운영 호스트·DB (팀 명명 규칙)
if printf '%s' "$cmd" | grep -qiE 'eduhub-prod|prod-db'; then
  block "운영 호스트·DB(eduhub-prod, prod-db)에 접근하는 명령입니다." \
    "운영 작업은 사람이 런북(NFR-06) 절차대로 직접 실행하세요. 확인이 필요하면 local·dev 프로필에서 재현하세요."
fi

# 2. flywayClean: 스키마 전체 삭제
if printf '%s' "$cmd" | grep -qi 'flywayClean'; then
  block "flywayClean은 DB 스키마와 데이터를 모두 지웁니다." \
    "local(H2)은 재시작하면 초기화됩니다. dev는 'docker compose down -v' 후 다시 올리는 것을 사람이 직접 판단해 실행하세요."
fi

# 3·4. git push: 명령을 ; & | 단위로 나눠 git push 구간만 본다
while IFS= read -r seg; do
  # git.exe(PowerShell), & git(호출 연산자)도 같은 git push로 본다
  printf '%s' "$seg" | grep -qiE '(^|[[:space:]&(])git(\.exe)?[[:space:]]+push([[:space:]]|$)' || continue

  # 강제 푸시: --force, --force-with-lease, -f, 묶은 플래그(-uf 등), +refspec
  if printf '%s' "$seg" | grep -qE '[[:space:]](--force[^[:space:]]*|-[a-zA-Z]*f[a-zA-Z]*|\+[^[:space:]]+)([[:space:]]|$)'; then
    block "강제 푸시(git push --force / -f)는 원격 이력을 덮어씁니다." \
      "새 커밋을 만들어 일반 푸시하거나, 꼭 필요하면 사람이 직접 실행하세요."
  fi

  # main 직접 푸시: main, HEAD:main, xxx:main, refs/heads/main
  if printf '%s' "$seg" | grep -qE '[[:space:]]([^[:space:]]*:)?(refs/heads/)?main([[:space:]]|$)'; then
    block "main 브랜치에 직접 푸시하는 명령입니다." \
      "작업 브랜치에 푸시한 뒤 PR로 main에 병합하세요 (CLAUDE.md 공통 규칙)."
  fi
done < <(printf '%s\n' "$cmd" | tr ';&|' '\n\n\n')

exit 0

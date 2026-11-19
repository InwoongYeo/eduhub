# Create or update the read-only MySQL account used by the eduhub-db MCP server (D-40).
# dev profile only: targets the docker-compose container eduhub-mysql. Never run against prod.
#
# Usage (Windows PowerShell, from anywhere):
#   1. Set your own password as a USER environment variable (letters and digits only):
#        [Environment]::SetEnvironmentVariable('MYSQL_READONLY_PASSWORD', '<password>', 'User')
#   2. docker compose up -d   (repo root)
#   3. .\scripts\setup-db-readonly.ps1   -> mysql prompts for the root password (MYSQL_ROOT_PASSWORD in .env)
#   4. Restart the Claude app so .mcp.json picks up the password.
# Safe to re-run: it also resets the password to the current environment variable value.
#
# Keep this file ASCII only: Windows PowerShell 5.1 reads BOM-less UTF-8 as the ANSI code page,
# and multibyte comments can swallow the next line.

# Terminals opened by an app inherit the value from when the app started, so read the user-scope store.
$pw = [Environment]::GetEnvironmentVariable('MYSQL_READONLY_PASSWORD', 'User')
if (-not $pw) {
    Write-Host 'MYSQL_READONLY_PASSWORD (User) is not set. See step 1 at the top of this script.' -ForegroundColor Red
    exit 1
}
if ($pw -match "['""``\\$]") {
    Write-Host 'Password contains a quote, backtick, backslash or dollar sign. Use letters and digits only.' -ForegroundColor Red
    exit 1
}

# - MAX_USER_CONNECTIONS: MySQL has no per-account statement timeout (MAX_STATEMENT_TIME is MariaDB only).
# - init_connect: does not apply to root (CONNECTION_ADMIN), so on the dev DB it only limits this account.
#   Do not use IF(..., 10000, @@GLOBAL.max_execution_time): the result is not an integer type,
#   init_connect fails with ERROR 1232 and every login of this account is dropped.
$sql = "CREATE USER IF NOT EXISTS 'eduhub_readonly'@'%' IDENTIFIED BY '$pw' WITH MAX_USER_CONNECTIONS 5; " +
       "ALTER USER 'eduhub_readonly'@'%' IDENTIFIED BY '$pw' WITH MAX_USER_CONNECTIONS 5; " +
       "GRANT SELECT, SHOW VIEW ON eduhub.* TO 'eduhub_readonly'@'%'; " +
       "SET PERSIST init_connect = 'SET SESSION max_execution_time = 10000'; " +
       "SHOW GRANTS FOR 'eduhub_readonly'@'%'; SELECT @@GLOBAL.init_connect AS init_connect;"

Write-Host 'Enter the MySQL root password:' -ForegroundColor Cyan
docker exec -it eduhub-mysql mysql -uroot -p -e $sql
if ($LASTEXITCODE -eq 0) {
    Write-Host 'OK. Expect only USAGE and SELECT, SHOW VIEW on eduhub.* above. Restart the Claude app.' -ForegroundColor Green
} else {
    Write-Host "FAILED (exit $LASTEXITCODE). ERROR 1045 for root means the root password is wrong." -ForegroundColor Red
    exit $LASTEXITCODE
}

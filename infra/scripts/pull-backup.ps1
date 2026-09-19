<#
.SYNOPSIS
DevPilot 백업 2차 사본 (결정 C): 서버 /opt/devpilot/backups 의 최신 .dump 를 scp 로 받아 로컬에 8주 보관한다.
기준: docs/10-deployment-and-operations.md §8.2 (2026-09-18). Windows PowerShell 5.1 호환.
파일 인코딩은 UTF-8 **BOM 포함**이다 — 5.1 은 BOM 이 없으면 ANSI 코드페이지로 읽어 한글 문자열이 깨진다.

.DESCRIPTION
1) ssh <server> 로 최신 devpilot-*.dump 이름을 얻고 2) 같은 이름이 로컬에 없으면 scp 로 받아(.part → 이름 변경)
3) KeepDays 가 지난 로컬 파일을 지운 뒤 4) 결과 한 줄을 출력한다.
전제: SSH 키 로그인, 서버 계정이 deploy 그룹(prepare-server.sh --operator <user>). 서버 주소·계정은 저장소에 쓰지 않는다
(DevPilot-ops/01-servers.md). 주 1회 작업 스케줄러 등록:
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File <repo>\infra\scripts\pull-backup.ps1

.PARAMETER Server
user@host (기본 $env:DEVPILOT_SERVER_SSH)
.PARAMETER Destination
로컬 보관 폴더 (기본 %USERPROFILE%\DevPilot-backups)
.PARAMETER KeepDays
로컬 보관 일수 (기본 56 = 8주)

.EXAMPLE
.\infra\scripts\pull-backup.ps1
.EXAMPLE
.\infra\scripts\pull-backup.ps1 -Server user@host -Destination D:\backups -KeepDays 56
#>
[CmdletBinding()]
param(
    [string]$Server = $env:DEVPILOT_SERVER_SSH,
    [string]$Destination = (Join-Path $env:USERPROFILE 'DevPilot-backups'),
    [int]$KeepDays = 56
)

$ErrorActionPreference = 'Stop'
$remoteDir = '/opt/devpilot/backups'
$minBytes = 1024

function Write-Log {
    param([string]$Message)
    Write-Output ("{0:yyyy-MM-ddTHH:mm:ssZ} [pull-backup] {1}" -f (Get-Date).ToUniversalTime(), $Message)
}

# $ErrorActionPreference='Stop' 에서 Write-Error 는 종료 오류를 던져 뒤따르는 exit 가 실행되지 않는다.
# 문서화한 종료 코드(1·2)가 실제로 나오도록 오류도 Write-Host 로 출력한다 (PowerShell 5.1 호환).
function Write-Err {
    param([string]$Message)
    Write-Host ("{0:yyyy-MM-ddTHH:mm:ssZ} [pull-backup] ERROR: {1}" -f (Get-Date).ToUniversalTime(), $Message) -ForegroundColor Red
}

if ([string]::IsNullOrWhiteSpace($Server)) {
    Write-Err '서버를 모른다: -Server user@host 또는 $env:DEVPILOT_SERVER_SSH 를 준다 (DevPilot-ops/01-servers.md)'
    exit 2
}
foreach ($tool in @('ssh', 'scp')) {
    if ($null -eq (Get-Command $tool -ErrorAction SilentlyContinue)) {
        Write-Err "$tool 을 찾을 수 없다 (Windows OpenSSH Client)"
        exit 2
    }
}

# 1) 최신 파일 이름 (배너 등이 섞여도 마지막 줄만 쓴다)
$remoteCommand = "ls -1t $remoteDir/devpilot-*.dump 2>/dev/null | head -n 1"
$listed = @(& ssh -o BatchMode=yes $Server $remoteCommand)
if ($LASTEXITCODE -ne 0) {
    Write-Err "ssh 실패 (exit $LASTEXITCODE): $Server"
    exit 1
}
$latest = ($listed | Select-Object -Last 1)
if ([string]::IsNullOrWhiteSpace($latest)) {
    Write-Err "서버에 백업 파일이 없다: ${Server}:${remoteDir} (devpilot-backup.timer 상태 확인)"
    exit 1
}
$latest = $latest.Trim()
$name = $latest.Substring($latest.LastIndexOf('/') + 1)
if ($name -notmatch '^devpilot-\d{8}T\d{6}Z\.dump$') {
    Write-Err "예상하지 못한 파일 이름: $name"
    exit 1
}

# 2) 받기
if (-not (Test-Path -LiteralPath $Destination)) {
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
}
$local = Join-Path $Destination $name
$size = 0
if (Test-Path -LiteralPath $local) {
    $size = (Get-Item -LiteralPath $local).Length
    Write-Log "skip: $name 은 이미 있다 ($size bytes)"
} else {
    $part = "${local}.part"
    & scp -q -o BatchMode=yes "${Server}:${latest}" $part
    if ($LASTEXITCODE -ne 0) {
        if (Test-Path -LiteralPath $part) { Remove-Item -LiteralPath $part -Force }
        Write-Err "scp 실패 (exit $LASTEXITCODE): $latest"
        exit 1
    }
    $size = (Get-Item -LiteralPath $part).Length
    if ($size -lt $minBytes) {
        Remove-Item -LiteralPath $part -Force
        Write-Err "받은 파일이 너무 작다 ($size bytes)"
        exit 1
    }
    Move-Item -LiteralPath $part -Destination $local -Force
    Write-Log "받음: $name ($size bytes) -> $Destination"
}

# 3) 보관 기간이 지난 로컬 파일 삭제 (새 사본이 있을 때만)
$cutoff = (Get-Date).AddDays(-$KeepDays)
$expired = @(Get-ChildItem -LiteralPath $Destination -Filter 'devpilot-*.dump' -File |
    Where-Object { $_.LastWriteTime -lt $cutoff -and $_.Name -ne $name })
foreach ($file in $expired) {
    Remove-Item -LiteralPath $file.FullName -Force
    Write-Log "삭제(보관 $KeepDays 일 경과): $($file.Name)"
}

# 4) 결과
$count = @(Get-ChildItem -LiteralPath $Destination -Filter 'devpilot-*.dump' -File).Count
Write-Log "OK: 최신 $name, 로컬 사본 $count 개 (보관 $KeepDays 일)"
exit 0

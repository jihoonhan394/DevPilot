<#
.SYNOPSIS
서버 Docker 를 로컬 Testcontainers 에서 쓰기 위한 SSH 소켓 포워딩 (docs/18 §3.1). 로컬 Docker Desktop 이 필요 없다.
Windows PowerShell 5.1 호환. 2026-09-18 검증 (서버 tailscale0 이 firewalld trusted zone).
파일 인코딩은 UTF-8 **BOM 포함**이다 — 5.1 은 BOM 이 없으면 ANSI 코드페이지로 읽어 한글 문자열에서 구문 오류가 난다.

.DESCRIPTION
  ssh -N -L 2375:/var/run/docker.sock <user>@<server>   를 백그라운드로 띄우고
  DOCKER_HOST=tcp://localhost:2375, TESTCONTAINERS_HOST_OVERRIDE=<서버 tailnet IP> 를 안내·설정한다.
  Testcontainers 가 만든 컨테이너의 publish 포트는 서버에 열리므로 HOST_OVERRIDE 로 서버 IP 를 알려 줘야 한다.
  2375 가 이미 열려 있으면(이전 터널) 새로 띄우지 않고 재사용한다.

  환경변수를 현재 세션에 남기려면 dot-source 로 실행한다:   . .\infra\scripts\dev-docker-tunnel.ps1
  (일반 실행은 안내만 출력한다. IntelliJ 는 Run Configuration 환경변수에 같은 값을 넣는다)

전제: SSH 키 로그인, 서버 계정이 docker 그룹. 서버 주소·계정은 저장소에 쓰지 않는다 (DevPilot-ops/01-servers.md):
  $env:DEVPILOT_SERVER_SSH = 'user@host'   $env:DEVPILOT_SERVER_IP = '<서버 tailnet IP>'
주의: 이 터널 위에서 infra/compose.dev.yml 을 띄우면 컨테이너가 서버에 생긴다 (compose.dev.yml 은 로컬 Docker 전용).

.PARAMETER Server
user@host (기본 $env:DEVPILOT_SERVER_SSH)
.PARAMETER ServerIp
서버 tailnet IP — TESTCONTAINERS_HOST_OVERRIDE 값 (기본 $env:DEVPILOT_SERVER_IP)
.PARAMETER Port
로컬 포트 (기본 2375)
.PARAMETER Stop
이 스크립트가 띄운 ssh 터널을 종료한다

.EXAMPLE
. .\infra\scripts\dev-docker-tunnel.ps1
.EXAMPLE
.\infra\scripts\dev-docker-tunnel.ps1 -Stop
#>
[CmdletBinding()]
param(
    [string]$Server = $env:DEVPILOT_SERVER_SSH,
    [string]$ServerIp = $env:DEVPILOT_SERVER_IP,
    [int]$Port = 2375,
    [switch]$Stop
)

# dot-source 실행에서 exit 는 셸을 닫으므로 return 만 쓴다
$ErrorActionPreference = 'Stop'
$forward = "${Port}:/var/run/docker.sock"

function Write-Log {
    param([string]$Message)
    Write-Output "[dev-docker-tunnel] $Message"
}

# $ErrorActionPreference='Stop' 에서 Write-Error 는 종료 오류를 던져 뒤따르는 return 이 실행되지 않는다.
# 오류도 Write-Host 로 출력하고 흐름은 return 이 정한다 (PowerShell 5.1 호환).
function Write-Err {
    param([string]$Message)
    Write-Host "[dev-docker-tunnel] ERROR: $Message" -ForegroundColor Red
}

function Get-TunnelProcess {
    # 이 스크립트가 띄운 ssh 만 (명령줄에 같은 -L 포워딩이 있는 것)
    try {
        Get-CimInstance Win32_Process -Filter "Name = 'ssh.exe'" |
            Where-Object { $_.CommandLine -like "*-L $forward*" }
    } catch {
        @()
    }
}

function Test-PortListening {
    param([int]$LocalPort)
    try {
        $listening = Get-NetTCPConnection -LocalPort $LocalPort -State Listen -ErrorAction Stop
        return ($null -ne $listening)
    } catch {
        return $false
    }
}

if ($Stop) {
    $processes = @(Get-TunnelProcess)
    if ($processes.Count -eq 0) {
        Write-Log "종료할 터널이 없다 (포트 $Port)"
        return
    }
    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId -Force -Confirm:$false
        Write-Log "종료: ssh PID $($process.ProcessId)"
    }
    return
}

if ([string]::IsNullOrWhiteSpace($Server)) {
    Write-Err '서버를 모른다: -Server user@host 또는 $env:DEVPILOT_SERVER_SSH 를 준다 (DevPilot-ops/01-servers.md)'
    return
}
if ($null -eq (Get-Command ssh -ErrorAction SilentlyContinue)) {
    Write-Err 'ssh 를 찾을 수 없다 (Windows OpenSSH Client)'
    return
}

if (Test-PortListening -LocalPort $Port) {
    $existing = @(Get-TunnelProcess)
    if ($existing.Count -gt 0) {
        Write-Log "포트 $Port 가 이미 열려 있다 (ssh PID $($existing[0].ProcessId)) → 재사용"
    } else {
        Write-Log "경고: 포트 $Port 를 다른 프로세스가 듣고 있다 (로컬 Docker?). 그대로 재사용한다. 다른 포트는 -Port"
    }
} else {
    Write-Log "터널 시작: ssh -N -L $forward $Server"
    $process = Start-Process -FilePath 'ssh' -ArgumentList @('-N', '-o', 'ExitOnForwardFailure=yes', '-o', 'ServerAliveInterval=30', '-L', $forward, $Server) -WindowStyle Hidden -PassThru
    $deadline = (Get-Date).AddSeconds(15)
    while (-not (Test-PortListening -LocalPort $Port)) {
        if ($process.HasExited) {
            Write-Err "ssh 가 종료됐다 (exit $($process.ExitCode)). SSH 키 로그인·서버 docker 그룹을 확인한다"
            return
        }
        if ((Get-Date) -gt $deadline) {
            Write-Err "15초 안에 포트 $Port 가 열리지 않았다"
            return
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Log "터널 준비 완료 (ssh PID $($process.Id))"
}

$env:DOCKER_HOST = "tcp://localhost:$Port"
if ([string]::IsNullOrWhiteSpace($ServerIp)) {
    Write-Log '경고: 서버 tailnet IP 를 모른다 (-ServerIp 또는 $env:DEVPILOT_SERVER_IP). TESTCONTAINERS_HOST_OVERRIDE 없이는 컨테이너에 접속하지 못한다'
} else {
    $env:TESTCONTAINERS_HOST_OVERRIDE = $ServerIp
}

Write-Output ''
Write-Output '이 세션(dot-source 실행 시) 또는 IDE Run Configuration 에 설정할 값:'
Write-Output "  `$env:DOCKER_HOST = 'tcp://localhost:$Port'"
if (-not [string]::IsNullOrWhiteSpace($ServerIp)) {
    Write-Output "  `$env:TESTCONTAINERS_HOST_OVERRIDE = '$ServerIp'"
}
Write-Output '확인: cd backend; .\gradlew integrationTest      종료: .\infra\scripts\dev-docker-tunnel.ps1 -Stop'

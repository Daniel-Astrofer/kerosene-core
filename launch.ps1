# ============================================================
# launch.ps1  —  Kerosene Hydra Launcher (Windows)
# ============================================================

param(
    [switch]$Build,
    [switch]$Down
)

Set-Location $PSScriptRoot

# Verificar se o Docker está rodando
& docker info >$null 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[Kerosene] ERRO: O Docker não está rodando. Por favor, inicie o Docker Desktop." -ForegroundColor Red
    exit 1
}

if ($Down) {
    Write-Host "[Kerosene] Stopping all containers..." -ForegroundColor Yellow
    docker compose down --remove-orphans --volumes
    exit 0
}

# ── 1. Bring up the stack ────────────────────────────────────────
if ($Build) {
    Write-Host "[Kerosene] Building images and starting stack..." -ForegroundColor Cyan
    docker compose up --build -d
}
else {
    Write-Host "[Kerosene] Starting stack..." -ForegroundColor Cyan
    docker compose up -d
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "[Kerosene] docker compose failed." -ForegroundColor Red
    exit 1
}

Write-Host "[Kerosene] Stack is up. Initializing terminals..." -ForegroundColor Green
Start-Sleep -Seconds 2

# ── 2. Dashboard (Simplified) ──────────────────────────────────────────
Write-Host "[Kerosene] Stack is operational. Streaming logs..." -ForegroundColor Cyan
docker compose logs -f

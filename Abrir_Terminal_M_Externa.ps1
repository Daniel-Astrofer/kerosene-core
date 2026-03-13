# Abrir_Terminal_M_Externa.ps1
# Dashboard de Controle Kerosene - MODO NAVEGAÇÃO POR ABAS
# Autor: Antigravity

try { chcp 65001 > $null } catch {}
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Host.UI.RawUI.WindowTitle = "CENTRO DE COMANDO KEROSENE"
Clear-Host

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host " [MAQUINA EXTERNA] - Montando Painel de Controle (Abas) " -ForegroundColor Yellow
Write-Host "================================================================" -ForegroundColor Cyan

# Comandos Refatorados
$dbCmd = 'powershell -NoExit -Command "while($true) { Clear; Write-Host ''--- LIVE DATABASE STATE ---'' -ForegroundColor Yellow; docker exec kerosene_db_se psql -U kerosene -d kerosene -c ''SELECT id, email, username FROM auth.users ORDER BY id DESC LIMIT 10;''; Write-Host ''`n--- RECENT TRANSACTIONS ---'' -ForegroundColor Cyan; docker exec kerosene_db_se psql -U kerosene -d kerosene -c ''SELECT id, amount, type, status FROM ledger.transactions ORDER BY created_at DESC LIMIT 5;''; Start-Sleep 5 }"'
$redisCmd = 'powershell -NoExit -Command "Write-Host ''--- REDIS LIVE OPERATIONS ---'' -ForegroundColor Magenta; docker exec kerosene_redis_se redis-cli monitor"'
$appLogsCmd = 'powershell -NoExit -Command "Write-Host ''--- GLOBAL HYDRA LOGS ---'' -ForegroundColor Green; docker compose logs -f kerosene-app-se kerosene-app-ee kerosene-app-is"'
$infraCmd = 'powershell -NoExit -Command "Write-Host ''--- INFRASTRUCTURE AND UPTIME GATUS ---'' -ForegroundColor Cyan; docker logs -f kerosene_gatus"'

# Tenta localizar o executável do Windows Terminal
$wtPath = (Get-Command wt.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
if (!$wtPath) { $wtPath = (Get-Command wt -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source) }

if ($wtPath) {
    Write-Host "Windows Terminal encontrado. Abrindo abas de monitoramento..." -ForegroundColor Green
    
    # Argumentos para o WT em modo abas (; separa os comandos)
    $arguments = @(
        "--title", "DB STATE", "powershell", "-NoExit", "-Command", $dbCmd, ";",
        "new-tab", "--title", "REDIS MONITOR", "powershell", "-NoExit", "-Command", $redisCmd, ";",
        "new-tab", "--title", "GLOBAL LOGS", "powershell", "-NoExit", "-Command", $appLogsCmd, ";",
        "new-tab", "--title", "INFRA WATCH", "powershell", "-NoExit", "-Command", $infraCmd
    )

    Start-Process -FilePath $wtPath -ArgumentList $arguments
}
else {
    Write-Host "Windows Terminal não encontrado. Abrindo janelas individuais..." -ForegroundColor Yellow
    
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $dbCmd
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $redisCmd
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $appLogsCmd
    Start-Process powershell -ArgumentList "-NoExit", "-Command", $infraCmd
}

Write-Host "`nDashboards disparados. Use Ctrl+Tab para navegar.`n" -ForegroundColor Blue

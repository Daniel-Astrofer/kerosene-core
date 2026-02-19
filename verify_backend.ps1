
$baseUrl = "http://localhost:8080"
$baseUrl = "http://localhost:8080"
$rand = Get-Random
$username = "testuser_$rand"
$passphrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

function Log-Msg {
    param($msg, $success=$true)
    if ($success) { Write-Host $msg -ForegroundColor Green }
    else { Write-Host $msg -ForegroundColor Red }
}

# 1. Login/Signup
Log-Msg "1. Tentando Login..."
$loginPayload = @{ username = $username; passphrase = $passphrase } | ConvertTo-Json

try {
    # Tentar Signup primeiro
    $signupResp = Invoke-RestMethod -Uri "$baseUrl/auth/signup" -Method Post -Body $loginPayload -ContentType "application/json" -ErrorAction SilentlyContinue
    # Se signup retornar 202 (string token/key), ok.
    # Mas Invoke-RestMethod lança erro em 4xx/5xx, e retorna o corpo em 2xx.
    # Se retornar user key/token, sucesso.
    # Vamos assumir que signup pede TOTP (retorna key).
    
    Log-Msg "   Signup/Login iniciado..."
    
    # 2. Verify TOTP (Signup ou Login)
    # Vamos tentar verify signup e depois login
    $verifyPayload = @{ username = $username; passphrase = $passphrase; code = "000000" } | ConvertTo-Json
    
    # Tentar verify signup
    try {
        $token = Invoke-RestMethod -Uri "$baseUrl/auth/signup/totp/verify" -Method Post -Body $verifyPayload -ContentType "application/json" -Headers @{ "X-Device-Hash" = "device123" } -ErrorAction Stop
        Log-Msg "   Signup completado! Token recebido."
    } catch {
        # Se falhar, tentar login flow
        Log-Msg "   Tentando fluxo de login..."
        $loginResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginPayload -ContentType "application/json" -ErrorAction SilentlyContinue
        
        $token = Invoke-RestMethod -Uri "$baseUrl/auth/login/totp/verify" -Method Post -Body $verifyPayload -ContentType "application/json" -Headers @{ "X-Device-Hash" = "device123" } -ErrorAction Stop
        Log-Msg "   Login completado! Token recebido."
    }
    
    $headers = @{ "Authorization" = "Bearer $token" }

    # 3. Verificar Saldo Inicial
    Log-Msg "`n2. Verificando saldo inicial..."
    # Usar /wallet/all para pegar wallet ID e saldo
    $wallets = Invoke-RestMethod -Uri "$baseUrl/wallet/all" -Method Get -Headers $headers
    Log-Msg "   Wallets encontradas: $($wallets.Count)"
    
    # 4. Registrar Depósito (Mock)
    Log-Msg "`n3. Registrando Depósito de 1.5 BTC..."
    $txid = "tx-mock-ps-" + (Get-Date).Ticks
    $depositPayload = @{ txid = $txid; fromAddress = "addr_origin"; amount = 1.5 } | ConvertTo-Json
    $depResp = Invoke-RestMethod -Uri "$baseUrl/transactions/confirm-deposit" -Method Post -Body $depositPayload -ContentType "application/json" -Headers $headers
    Log-Msg "   Depósito confirmado! Status: $($depResp.status)"

    # 5. Check Balance Update
    Log-Msg "`n4. Verificando Saldo após Depósito..."
    # Precisamos ver se o saldo mudou. Vamos consultar o ledger se possível ou confiar no log do backend.
    # O endpoint /transactions/deposit-balance retorna saldo de depósitos creditados.
    $balance = Invoke-RestMethod -Uri "$baseUrl/transactions/deposit-balance" -Method Get -Headers $headers
    Log-Msg "   Saldo de Depósitos: $balance BTC"

    # 6. Criar Payment Link
    Log-Msg "`n5. Criando Payment Link de 0.5 BTC..."
    $linkPayload = @{ amount = 0.5; description = "Payment test PS" } | ConvertTo-Json
    $linkResp = Invoke-RestMethod -Uri "$baseUrl/transactions/create-payment-link" -Method Post -Body $linkPayload -ContentType "application/json" -Headers $headers
    $linkId = $linkResp.id
    Log-Msg "   Link Criado: $linkId"

    # 7. Confirmar Pagamento Link
    Log-Msg "`n6. Confirmando Pagamento Link..."
    $payTxid = "tx-link-ps-" + (Get-Date).Ticks
    $payPayload = @{ txid = $payTxid; fromAddress = "addr_payer" } | ConvertTo-Json
    $payResp = Invoke-RestMethod -Uri "$baseUrl/transactions/payment-link/$linkId/confirm" -Method Post -Body $payPayload -ContentType "application/json" -Headers $headers
    Log-Msg "   Pagamento Confirmado! Status: $($payResp.status)"

    # 8. Listar Payment Links
    Log-Msg "`n7. Listando Meus Links..."
    $myLinks = Invoke-RestMethod -Uri "$baseUrl/transactions/payment-links" -Method Get -Headers $headers
    $found = $false
    foreach ($l in $myLinks) {
        if ($l.id -eq $linkId) {
            $found = $true
            break
        }
    }
    
    if ($found) { Log-Msg "   Link recém criado encontrado na lista! ✅" }
    else { Log-Msg "   Link NÃO encontrado na lista! ❌" $false }

} catch {
    Log-Msg "Erro durante teste: $_" $false
    # Imprimir detalhes do erro se for web ex
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        Log-Msg "Detalhes do erro: $body" $false
    }
}

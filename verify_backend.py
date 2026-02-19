
import requests
import json
import time

BASE_URL = "http://localhost:8080"
USERNAME = "testuser"
PASSPHRASE = "testpass"

# Colors for output
GREEN = "\033[92m"
RED = "\033[91m"
RESET = "\033[0m"

def log(msg, success=True):
    color = GREEN if success else RED
    print(f"{color}{msg}{RESET}")

def test_flow():
    session = requests.Session()
    
    # 1. Login/Signup
    log("1. Tentando Login...")
    login_payload = {"username": USERNAME, "passphrase": PASSPHRASE}
    
    # Primeiro tentamos signup caso não exista
    signup_resp = session.post(f"{BASE_URL}/auth/signup", json=login_payload)
    if signup_resp.status_code == 202:
        log("   Signup iniciado via TOTP flow...")
        # Simular verify TOTP
        verify_payload = {"username": USERNAME, "passphrase": PASSPHRASE, "code": "000000"}
        verify_resp = session.post(f"{BASE_URL}/auth/signup/totp/verify", json=verify_payload, headers={"X-Device-Hash": "device123"})
        if verify_resp.status_code == 202:
            token = verify_resp.text
            log(f"   Signup completado! Token: {token[:10]}...")
        else:
            log(f"   Falha no verify signup: {verify_resp.text}", False)
            return
    else:
        # Tentar login normal
        login_resp = session.post(f"{BASE_URL}/auth/login", json=login_payload)
        
        if login_resp.status_code == 202:
             # Pode ser login direto ou pedir TOTP? O controller retorna ID.
             # Se retornar ID, é porque pediu TOTP?
             # Controller: return ResponseEntity.status(HttpStatus.ACCEPTED).body(id); (String)
             # Vamos assumir que 202 pede TOTP.
             log("   Login iniciado, verificando TOTP...")
             verify_payload = {"username": USERNAME, "passphrase": PASSPHRASE, "code": "000000"} 
             verify_resp = session.post(f"{BASE_URL}/auth/login/totp/verify", json=verify_payload, headers={"X-Device-Hash": "device123"})
             if verify_resp.status_code == 202:
                 token = verify_resp.text
                 log(f"   Login completado! Token: {token[:10]}...")
             else:
                 log(f"   Falha no login verify: {verify_resp.text}", False)
                 return
        else:
            log(f"   Login falhou ou comportamento inesperado: {login_resp.status_code}", False)
            return

    headers = {"Authorization": f"Bearer {token}"}
    
    # 2. Verificar Saldo Inicial
    log("\n2. Verificando saldo inicial...")
    # Precisamos achar o endpoint de saldo. WalletController: /wallet/all ou LedgerService?
    # Controller não tem endpoint direto de saldo, mas WalletController tem /wallet/all
    # Vamos usar TransactionController /transactions/deposit-balance (só depósitos)
    # Ou melhor, precisamos ver o saldo total.
    # Parecia que não tinha um endpoint simples de "meu saldo total"?
    # Vamos ver LedgerController? Não vi LedgerController na lista.
    # Ah, WalletController tem /wallet/all que retorna WalletEntity, que tem o Ledger?
    # Vamos tentar pegar todos as wallets.
    
    wallets_resp = session.get(f"{BASE_URL}/wallet/all", headers=headers)
    if wallets_resp.status_code == 200:
        wallets = wallets_resp.json()
        log(f"   Wallets encontradas: {len(wallets)}")
        initial_balance = 0
        if len(wallets) > 0:
            # Assumindo que o saldo vem no json da wallet ou precisamos de outra call
            # O ledger é associado à wallet.
            pass
    else:
        log(f"   Falha ao listar wallets: {wallets_resp.text}", False)

    # 3. Confirmar Depósito (Mock)
    log("\n3. Realizando Depósito de 1.5 BTC...")
    deposit_payload = {
        "txid": f"tx-mock-{int(time.time())}",
        "fromAddress": "addr_origin",
        "amount": 1.5
    }
    deposit_resp = session.post(f"{BASE_URL}/transactions/confirm-deposit", json=deposit_payload, headers=headers)
    if deposit_resp.status_code == 201:
        log("   Depósito confirmado com sucesso!")
        dep_data = deposit_resp.json()
        log(f"   Status: {dep_data.get('status')}")
    else:
        log(f"   Falha no depósito: {deposit_resp.text}", False)

    # 4. Criar Payment Link
    log("\n4. Criando Payment Link de 0.5 BTC...")
    link_payload = {"amount": 0.5, "description": "Payment test"}
    link_resp = session.post(f"{BASE_URL}/transactions/create-payment-link", json=link_payload, headers=headers)
    link_id = None
    if link_resp.status_code == 201:
        link_data = link_resp.json()
        link_id = link_data.get("id")
        log(f"   Link criado: {link_id}")
    else:
        log(f"   Falha ao criar link: {link_resp.text}", False)
        return

    # 5. Confirmar Pagamento do Link
    log("\n5. Confirmando Pagamento do Link...")
    pay_payload = {
        "txid": f"tx-link-{int(time.time())}",
        "fromAddress": "addr_payer"
    }
    pay_resp = session.post(f"{BASE_URL}/transactions/payment-link/{link_id}/confirm", json=pay_payload, headers=headers)
    if pay_resp.status_code == 200:
        log("   Pagamento confirmado!")
        pay_data = pay_resp.json()
        log(f"   Status: {pay_data.get('status')}")
    else:
        log(f"   Falha ao confirmar pagamento: {pay_resp.text}", False)
    
    # 6. Listar Meus Payment Links
    log("\n6. Listando Meus Payment Links...")
    list_resp = session.get(f"{BASE_URL}/transactions/payment-links", headers=headers)
    if list_resp.status_code == 200:
        links = list_resp.json()
        log(f"   Links encontrados: {len(links)}")
        found = any(l['id'] == link_id for l in links)
        if found:
            log("   Link recém criado encontrado na lista! ✅")
        else:
            log("   Link NÃO encontrado na lista! ❌", False)
    else:
        log(f"   Falha ao listar links: {list_resp.text}", False)

if __name__ == "__main__":
    try:
        test_flow()
    except Exception as e:
        log(f"Erro durante teste: {e}", False)

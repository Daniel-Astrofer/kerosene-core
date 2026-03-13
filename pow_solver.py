import hashlib

def solve_pow(challenge, difficulty_prefix="0000"):
    nonce = 0
    while True:
        nonce_str = str(nonce)
        data = challenge + nonce_str
        hex_hash = hashlib.sha256(data.encode('utf-8')).hexdigest()
        if hex_hash.startswith(difficulty_prefix):
            return nonce_str, hex_hash
        nonce += 1

if __name__ == "__main__":
    challenge = "WqoUjquSwzwgNjE64Pk4285EjzG9L32uuOMRSZu2lr0-f0416d8b-71ea-418d-9736-9ee1eccebc40"
    nonce, hex_hash = solve_pow(challenge)
    print(f"Nonce: {nonce}")
    print(f"Hash: {hex_hash}")

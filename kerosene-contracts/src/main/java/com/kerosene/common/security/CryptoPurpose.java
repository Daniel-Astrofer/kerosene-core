package com.kerosene.common.security;

/**
 * Declares the cryptographic purpose for domain separation.
 * Combined with table+column+entityId+tenantId for AAD.
 */
public enum CryptoPurpose {
    COLUMN_ENCRYPTION,
    WALLET_SECRET,
    API_KEY,
    TOKEN
}

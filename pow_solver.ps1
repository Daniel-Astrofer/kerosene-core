function Solve-PoW {
    param (
        [string]$challenge,
        [string]$prefix = "0000"
    )
    $nonce = 0
    while ($true) {
        $nonceStr = [string]$nonce
        $input = $challenge + $nonceStr
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($input)
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        $hashBytes = $sha256.ComputeHash($bytes)
        $hashHex = [System.BitConverter]::ToString($hashBytes).Replace("-", "").ToLower()
        
        if ($hashHex.StartsWith($prefix)) {
            return @{
                Nonce = $nonceStr
                Hash  = $hashHex
            }
        }
        $nonce++
    }
}

$challenge = "WqoUjquSwzwgNjE64Pk4285EjzG9L32uuOMRSZu2lr0-f0416d8b-71ea-418d-9736-9ee1eccebc40"
$result = Solve-PoW -challenge $challenge
Write-Host "Nonce: $($result.Nonce)"
Write-Host "Hash: $($result.Hash)"

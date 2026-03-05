#!/bin/sh
# Kerosene Tor Entrypoint
# Installs Tor on the debian:bookworm-slim base image, sets correct
# permissions on the hidden service directory, and starts the daemon.

set -e

# Install Tor if not present
if ! command -v tor > /dev/null 2>&1; then
    apt-get update -qq
    DEBIAN_FRONTEND=noninteractive apt-get install -y -o Dpkg::Options::="--force-confold" --no-install-recommends tor
    rm -rf /var/lib/apt/lists/*
fi

echo "==> Auditing Tor binary integrity (Anti Tor-Inside Attacker)..."
TOR_BIN="/usr/bin/tor"
ACTUAL_HASH=$(sha256sum "$TOR_BIN" | awk '{print $1}')

if [ -n "$EXPECTED_TOR_HASH" ]; then
    if [ "$ACTUAL_HASH" != "$EXPECTED_TOR_HASH" ]; then
        echo "=========================================================="
        echo "CRITICAL SECURITY ALERT: Tor binary hash mismatch!"
        echo "Expected: $EXPECTED_TOR_HASH"
        echo "Actual  : $ACTUAL_HASH"
        echo "=========================================================="
        echo "The binary may be compromised. Halting container execution to protect keys."
        exit 1
    else
        echo "==> Tor binary integrity verified successfully."
    fi
else
    echo "WARNING: EXPECTED_TOR_HASH environment variable not set. Skipping strict hash enforcement."
    echo "Actual Tor SHA256: $ACTUAL_HASH"
    echo "Please set EXPECTED_TOR_HASH in .env for production environments."
fi

# Tor 0.4.7 requires a NAMED user in /etc/passwd for the "User" directive.
# Numeric UIDs are NOT supported. We create a "kerosene" system user with
# UID 65532 to match the Distroless non-root user in the app containers.
if ! id kerosene >/dev/null 2>&1; then
    groupadd -g 65532 kerosene 2>/dev/null || true
    useradd -r -u 65532 -g 65532 -M -s /usr/sbin/nologin kerosene 2>/dev/null || true
fi

# Tor is extremely selective about directory permissions for SOCKS Unix sockets.
# It MUST be 0700 and owned by the user running Tor.
mkdir -p /var/run/tor/socks
mkdir -p /var/lib/tor/kerosene_service
chown -R kerosene:kerosene /var/run/tor /var/lib/tor
chmod 700 /var/run/tor/socks
chmod 700 /var/lib/tor/kerosene_service

echo "==> Starting Tor hidden service for Kerosene..."
echo "==> Once connected, .onion address will be in:"
echo "    /var/lib/tor/kerosene_service/hostname"
echo ""

# OnionBalance features removed for Push-Beaconing Architecture


# Tor will drop privileges to 65532 (User option in torrc) after starting as root.
tor -f /etc/tor/torrc &
TOR_PID=$!

echo "==> Waiting for Tor to establish UDS socket..."
while [ ! -S /var/run/tor/socks/tor.sock ]; do
  sleep 1
done

# Secure the created socket just in case, though User 65532 in torrc handles it.
chown kerosene:kerosene /var/run/tor/socks/tor.sock
chmod 660 /var/run/tor/socks/tor.sock

wait $TOR_PID

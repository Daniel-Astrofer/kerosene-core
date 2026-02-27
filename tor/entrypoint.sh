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

# Tor requires strict 700 permissions on the HiddenServiceDir.
# The volume mount may not have the correct owner on first run.
mkdir -p /var/lib/tor/kerosene_service
chown -R debian-tor:debian-tor /var/lib/tor
chmod 700 /var/lib/tor/kerosene_service

echo "==> Starting Tor hidden service for Kerosene..."
echo "==> Once connected, .onion address will be in:"
echo "    /var/lib/tor/kerosene_service/hostname"
echo ""

# OnionBalance v3 requires an ob_config file with the Master address
if [ -n "$MASTER_ONION" ]; then
    echo "MasterOnionAddress $MASTER_ONION" > /var/lib/tor/kerosene_service/ob_config
    chown debian-tor:debian-tor /var/lib/tor/kerosene_service/ob_config
    chmod 600 /var/lib/tor/kerosene_service/ob_config
fi


# Tor strictly refuses to read config files or write to dirs if run as root
# when the permissions are set for debian-tor. We drop privileges here.
exec su debian-tor -s /bin/sh -c "exec tor -f /etc/tor/torrc"

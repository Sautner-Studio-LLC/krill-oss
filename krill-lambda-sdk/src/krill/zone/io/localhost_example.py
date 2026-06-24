#!/usr/bin/env python3
"""
Krill Python Lambda - localhost /health example.

Performs a GET against the local Krill server's /health endpoint and prints
the JSON response to stdout. When run as a Krill Lambda, the server captures
stdout and stores it in the target DataSource.
"""
import socket
import sys

import requests

import krill_auth

# Local Krill server. DEFAULT_PORT is 8442 (see server ServerIdentity.kt).
HEALTH_URL = "https://localhost:8442/health"
# Self-signed server cert installed by the krill package. If the cert's
# hostname doesn't cover "localhost", swap verify=KRILL_CERT for verify=False.
KRILL_CERT = "/etc/krill/certs/krill.crt"


def main():

    # KrillPinAuth reads /etc/krill/credentials/pin_derived_key and attaches the
    # cluster Bearer token. /trust is unauthenticated, but the same pattern works
    # for protected endpoints like /health and /nodes.
    response = requests.get(
        HEALTH_URL,
        auth=krill_auth.KrillPinAuth(),
        verify=KRILL_CERT,
        timeout=10,
    )
    response.raise_for_status()

    # stdout is the Lambda's output -> goes into the target DataSource.
    print(response.text)


if __name__ == "__main__":
    # Diagnostics go to stderr so they don't pollute the DataSource output.
    print(socket.gethostname(), file=sys.stderr)
    main()

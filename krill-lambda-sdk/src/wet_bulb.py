#!/usr/bin/env python3
"""
Computes a "Wet Bulb" temperature from temp Fahrenheit and relative humidity. Fetches the two values from a Krill server
"""
import math
import requests
import krill.zone.io.krill_auth as krill_auth

host = "localhost"
KRILL_CERT = "/etc/krill/certs/krill.crt"
PORT = 8442
TEMP_ID = "201733eb-e703-4f2d-b819-ecefc206bc46"
HUMIDITY_ID = "baf2ff69-1ce9-4eb1-b42e-3211bcaa2068"

# Uses the provided krill_auth module to authenticate with the Krill server
# and fetch the snapshot values for temperature and humidity using https.
# Then calculates the wet bulb temperature using Stull's approximation formula.
# running on the same host as the Krill server we can use the self signed certificate.
def read_snapshot(node_id):
    """Fetch a node and return its snapshot value as a float."""
    response = requests.get(
        "https://" + host + ":" + str(PORT) + "/node/" + node_id,
        auth=krill_auth.KrillPinAuth(),
        verify=krill_auth.KRILL_CERT,
        timeout=10,
    )
    response.raise_for_status()
    return float(response.json()["meta"]["snapshot"]["value"])


def wet_bulb(temp_f, humidity):
    """Stull (2011) wet-bulb approximation. Input/output in degrees Fahrenheit."""
    t = (temp_f - 32.0) * 5.0 / 9.0  # Stull's formula works in Celsius
    tw = (
        t * math.atan(0.151977 * math.sqrt(humidity + 8.313659))
        + math.atan(t + humidity)
        - math.atan(humidity - 1.676331)
        + 0.00391838 * humidity ** 1.5 * math.atan(0.023101 * humidity)
        - 4.686035
    )
    return tw * 9.0 / 5.0 + 32.0  # back to Fahrenheit


def main():
    temp_f = read_snapshot(TEMP_ID)
    humidity = read_snapshot(HUMIDITY_ID)
    # Krill will read the output of a script so we return a raw
    # float value to stdout so it can process it as a number.
    print("{:.2f}".format(wet_bulb(temp_f, humidity)))


if __name__ == "__main__":

    main()

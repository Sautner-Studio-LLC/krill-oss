import board
import adafruit_tcs34725
import time
import json

i2c = board.I2C()
sensor = adafruit_tcs34725.TCS34725(i2c)

CEILING = 55000
FLOOR   = 18000
PROBE_TIME = 100
FINAL_TIME = 400

# Assumes LED has been on long enough to thermally stabilize.
# If your LED cycles on/off with reads, uncomment:
# time.sleep(30)

# Probe phase: short integration, find the right gain
sensor.integration_time = PROBE_TIME
chosen_gain = 1

for gain in [1, 4, 16, 60]:
    sensor.gain = gain
    sensor.color_raw  # discard stale
    _, _, _, c = sensor.color_raw
    projected = c * (FINAL_TIME / PROBE_TIME)
    if projected > CEILING:
        break
    chosen_gain = gain
    if projected >= FLOOR:
        break

# Burst phase at chosen settings
sensor.gain = chosen_gain
sensor.integration_time = FINAL_TIME
sensor.color_raw  # discard first

N = 10
r_sum = g_sum = b_sum = c_sum = 0
for _ in range(N):
    r, g, b, c = sensor.color_raw
    r_sum += r; g_sum += g; b_sum += b; c_sum += c

print(json.dumps({
    "r": r_sum / N,
    "g": g_sum / N,
    "b": b_sum / N,
    "c": c_sum / N,
    "gain": chosen_gain,
    "integration_time": FINAL_TIME
}))

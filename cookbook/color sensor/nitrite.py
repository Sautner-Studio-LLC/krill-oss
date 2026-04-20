# /opt/krill/lambdas/nitrite.py
#
# API Freshwater Nitrite test — sky blue at 0 ppm, shifting through lavender
# to deep magenta/red at toxic levels. Wire this Lambda with:
#   source = the calibrate DataPoint (DataType.JSON, blank reference)
#   target = a nitrite-ppm DataPoint (DataType.DOUBLE)
#
# Starting curve values are estimates — recalibrate from /tmp/krill_nitrite.log
# against known-ppm samples for your rig.

from colorkit import run_test

# ((r_ratio, g_ratio, b_ratio), ppm)
CURVE = [
    ((0.40, 0.80, 1.00), 0.00),  # sky blue
    ((0.90, 0.70, 0.80), 0.25),  # light lavender
    ((1.00, 0.50, 0.70), 0.50),  # pink
    ((1.00, 0.30, 0.50), 1.00),  # purple-red
    ((1.00, 0.20, 0.40), 2.00),  # red-purple
    ((0.90, 0.10, 0.30), 5.00),  # deep magenta
]

run_test("nitrite", CURVE)

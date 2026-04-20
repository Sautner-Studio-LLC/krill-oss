# /opt/krill/lambdas/ph_high.py
#
# API Freshwater pH High Range (7.4–8.8) — uses a different indicator
# (phenol-red family) than the regular kit. Yellow at the low end shifting
# through olive/green to blue at the top.
#
# Wire this Lambda with:
#   source = the calibrate DataPoint (DataType.JSON, blank reference)
#   target = a pH DataPoint (DataType.DOUBLE)
#
# Output is pH. Starting curve values are estimates — recalibrate from
# /tmp/krill_ph_high.log against known-pH reference solutions for your rig.

from colorkit import run_test

# ((r_ratio, g_ratio, b_ratio), pH)
CURVE = [
    ((1.00, 1.00, 0.20), 7.40),  # yellow
    ((1.00, 0.70, 0.25), 7.80),  # yellow-orange
    ((0.70, 0.90, 0.30), 8.00),  # olive green
    ((0.40, 0.90, 0.40), 8.20),  # green
    ((0.30, 0.70, 0.80), 8.40),  # blue-green
    ((0.20, 0.50, 1.00), 8.80),  # blue
]

run_test("ph_high", CURVE)

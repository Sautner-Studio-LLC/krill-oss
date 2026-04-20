# /opt/krill/lambdas/ph.py
#
# API Freshwater pH (Regular Range, 6.0–7.6) — yellow at the acid end, shifting
# through green to blue at neutral/alkaline. For readings above 7.6 use
# ph_high.py (the high-range test kit) — this one's bromothymol-blue indicator
# saturates there.
#
# Wire this Lambda with:
#   source = the calibrate DataPoint (DataType.JSON, blank reference)
#   target = a pH DataPoint (DataType.DOUBLE)
#
# Output is pH (not ppm). Starting curve values are estimates — recalibrate
# from /tmp/krill_ph.log against known-pH reference solutions for your rig.

from colorkit import run_test

# ((r_ratio, g_ratio, b_ratio), pH)
CURVE = [
    ((1.00, 1.00, 0.20), 6.00),  # yellow
    ((0.70, 1.00, 0.30), 6.40),  # yellow-green
    ((0.50, 1.00, 0.40), 6.60),  # green-yellow
    ((0.30, 1.00, 0.50), 6.80),  # green
    ((0.25, 0.80, 0.70), 7.00),  # green-blue
    ((0.20, 0.70, 0.80), 7.20),  # blue-green
    ((0.20, 0.60, 0.90), 7.40),  # mostly blue
    ((0.20, 0.50, 1.00), 7.60),  # blue
]

run_test("ph", CURVE)

# /opt/krill/lambdas/nitrate.py
#
# API Freshwater Nitrate test — yellow at 0 ppm, through orange to dark red at
# high ppm. Wire this Lambda with:
#   source = the calibrate DataPoint (DataType.JSON, blank reference)
#   target = a nitrate-ppm DataPoint (DataType.DOUBLE)
#
# Starting curve values are estimates — recalibrate from /tmp/krill_nitrate.log
# against known-ppm samples for your rig.

from colorkit import run_test

# ((r_ratio, g_ratio, b_ratio), ppm)
CURVE = [
    ((1.00, 1.00, 0.20), 0.00),   # yellow
    ((1.00, 0.80, 0.25), 5.00),   # light orange
    ((1.00, 0.60, 0.25), 10.00),  # orange
    ((1.00, 0.40, 0.25), 20.00),  # red-orange
    ((1.00, 0.30, 0.30), 40.00),  # red
    ((0.80, 0.20, 0.25), 80.00),  # dark red
    ((0.50, 0.10, 0.20), 160.00), # very dark red
]

run_test("nitrate", CURVE)

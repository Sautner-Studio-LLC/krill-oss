# /opt/krill/lambdas/ammonia.py
#
# API Freshwater Ammonia test — yellow at 0 ppm, shifting through yellow-green
# to green as ammonia rises. Wire this Lambda with:
#   source = the calibrate DataPoint (DataType.JSON, blank reference)
#   target = an ammonia-ppm DataPoint (DataType.DOUBLE)
#
# Starting curve values approximate the API color card translated into white-
# balanced channel ratios. Replace with measurements from your own rig for
# accuracy — sample at each known ppm level, copy the `ratios:` line from
# /tmp/krill_ammonia.log, and update CURVE.

from colorkit import run_test

# ((r_ratio, g_ratio, b_ratio), ppm), ordered ascending by ppm.
# Ammonia absorbs blue at 0 ppm (looks yellow), shifts to absorbing red as
# concentration rises (looks green), and darkens overall at high ppm.
CURVE = [
    ((1.00, 1.00, 0.20), 0.00),
    ((0.80, 1.00, 0.22), 0.25),
    ((0.50, 1.00, 0.30), 0.50),
    ((0.30, 1.00, 0.40), 1.00),
    ((0.20, 0.70, 0.35), 2.00),
    ((0.15, 0.50, 0.30), 4.00),
    ((0.10, 0.30, 0.20), 8.00),
]

run_test("ammonia", CURVE)

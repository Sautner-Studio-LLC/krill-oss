# /opt/krill/lambdas/colorkit.py
#
# Shared helpers for Krill freshwater-aquarium test lambdas. Each test script
# (ammonia.py, nitrate.py, …) supplies a color→ppm reference curve and calls
# `run_test(tag, curve)`; this module handles sensor sampling, white-balance,
# piecewise-linear curve lookup, and diagnostic logging to
# /tmp/krill_<tag>.log.
#
# Expected pipeline:
#   calibrate.py   → writes blank JSON to a DataPoint(dataType=JSON)
#   <metric>.py    → reads that DataPoint as its Lambda source, samples the
#                    sensor at the calibration's gain / integration_time,
#                    white-balances, matches against the supplied CURVE,
#                    prints a single ppm value to stdout.
#
# Curve format: a list of entries ordered by ppm, each entry is a tuple
#   ((r_ratio, g_ratio, b_ratio), ppm)
# where r_ratio = r_sample / r_blank (sample channel divided by calibration
# channel). Intermediate ppm values are linearly interpolated along the
# closest curve segment in ratio-space.

import json
import os
import sys
import time
import traceback


def _log(tag, msg):
    """Append a timestamped line to /tmp/krill_<tag>.log — one file per test."""
    try:
        with open(f"/tmp/krill_{tag}.log", "a") as f:
            ts = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
            f.write(f"[{ts} pid={os.getpid()}] {msg}\n")
    except Exception:
        pass  # never break the lambda because of diagnostics


def _load_blank():
    if len(sys.argv) < 2:
        raise RuntimeError("no source DataPoint provided — wire the calibrate "
                           "DataPoint as this Lambda's source")
    source_node = json.loads(sys.argv[1])
    value = source_node.get("meta", {}).get("snapshot", {}).get("value", "")
    if not value:
        raise RuntimeError("calibration source has no snapshot value — run "
                           "calibrate.py first")
    blank = json.loads(value)
    for k in ("r", "g", "b", "gain", "integration_time"):
        if k not in blank:
            raise RuntimeError(f"calibration blank missing field: {k}")
    return blank


def _sample_sensor(blank, n=10):
    """Configure the TCS34725 to match the calibration and average N reads."""
    import board
    import adafruit_tcs34725
    i2c = board.I2C()
    sensor = adafruit_tcs34725.TCS34725(i2c)
    sensor.gain = int(blank["gain"])
    sensor.integration_time = int(blank["integration_time"])
    sensor.color_raw  # discard: first post-config read reflects stale integration
    sensor.color_raw  # defensive second discard for longer integration windows
    rs = gs = bs = cs = 0
    for _ in range(n):
        r, g, b, c = sensor.color_raw
        rs += r; gs += g; bs += b; cs += c
    return rs / n, gs / n, bs / n, cs / n


def _white_balance(sample_rgb, blank):
    def div(v, ref):
        return v / ref if ref > 0 else 0.0
    return (
        div(sample_rgb[0], blank["r"]),
        div(sample_rgb[1], blank["g"]),
        div(sample_rgb[2], blank["b"]),
    )


def _interpolate_ppm(ratios, curve):
    """Project `ratios` onto the closest segment of `curve` in RGB-ratio
    space and linearly interpolate the ppm. If the projection clamps to an
    endpoint the nearest curve ppm is returned — no extrapolation beyond
    the reference range."""
    rs, gs, bs = ratios
    best_ppm = curve[0][1]
    best_dist_sq = float("inf")
    for i in range(len(curve) - 1):
        (r1, g1, b1), ppm1 = curve[i]
        (r2, g2, b2), ppm2 = curve[i + 1]
        vx, vy, vz = r2 - r1, g2 - g1, b2 - b1
        wx, wy, wz = rs - r1, gs - g1, bs - b1
        vv = vx * vx + vy * vy + vz * vz
        if vv <= 0:
            continue
        t = max(0.0, min(1.0, (wx * vx + wy * vy + wz * vz) / vv))
        px, py, pz = r1 + t * vx, g1 + t * vy, b1 + t * vz
        dx, dy, dz = rs - px, gs - py, bs - pz
        dist_sq = dx * dx + dy * dy + dz * dz
        if dist_sq < best_dist_sq:
            best_dist_sq = dist_sq
            best_ppm = ppm1 + t * (ppm2 - ppm1)
    return best_ppm


def run_test(tag, curve):
    """Entry point — call this from each per-metric script."""
    _log(tag, "=" * 72)
    try:
        blank = _load_blank()
        _log(tag, f"blank: {blank}")

        r, g, b, c = _sample_sensor(blank)
        _log(tag, f"avg raw: r={r:.1f} g={g:.1f} b={b:.1f} c={c:.1f}")

        ratios = _white_balance((r, g, b), blank)
        _log(tag, f"ratios:  r={ratios[0]:.3f} g={ratios[1]:.3f} b={ratios[2]:.3f}")

        ppm = _interpolate_ppm(ratios, curve)
        _log(tag, f"ppm: {ppm:.3f}")
        # stdout is what Krill stores in the target DataPoint (DataType.DOUBLE).
        print(f"{ppm:.3f}")
    except SystemExit:
        raise
    except Exception as e:
        _log(tag, f"EXCEPTION: {e!r}")
        _log(tag, traceback.format_exc())
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(1)

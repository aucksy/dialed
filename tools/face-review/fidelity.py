"""
FIDELITY SCORE — how far the built face is from the design, measured not eyeballed.

Implements the pixelmatch comparison (YIQ perceptual colour distance + anti-alias
rejection, mapbox/pixelmatch) over two same-size renders, then scores named REGIONS
of the dial separately, because a whole-dial percentage hides a broken register.

  python fidelity.py orig.png current.png out_diff.png regions.json [noise_floor_pct]

Regions JSON: [{"name": "Top register", "cx": 260, "cy": 150, "r": 64}, ...]
              (or {"name":..,"x":..,"y":..,"w":..,"h":..})

Prints a ranked table, worst region first, and exits non-zero if the dial is over
threshold — so "is it done?" has an answer that is not my opinion.
"""
import json
import math
import sys

from PIL import Image

# pixelmatch's default: per-pixel YIQ tolerance, 0..1. Smaller = stricter.
THRESHOLD = 0.1
# how much of a region may differ before it is called a mismatch (visual-regression
# practice: start strict, loosen only with a reason)
REGION_FAIL_PCT = 0.35

MAX_YIQ = 35215.0  # pixelmatch's maximum possible YIQ delta (white vs black)


def rgb2y(r, g, b): return r * 0.29889531 + g * 0.58662247 + b * 0.11448223
def rgb2i(r, g, b): return r * 0.59597799 - g * 0.27417610 - b * 0.32180189
def rgb2q(r, g, b): return r * 0.11147026 - g * 0.52261711 + b * 0.41114685


def delta(a, b):
    """Perceptual colour distance, pixelmatch's colorDelta (squared, signed by Y)."""
    y = rgb2y(*a) - rgb2y(*b)
    i = rgb2i(*a) - rgb2i(*b)
    q = rgb2q(*a) - rgb2q(*b)
    d = 0.5053 * y * y + 0.299 * i * i + 0.1957 * q * q
    return -d if y > 0 else d


def neighbourhood(px, x, y, w, h):
    """min/max brightness delta around a pixel + the coords of the extremes —
    the ingredients of pixelmatch's anti-alias test."""
    mn = mx = 0.0
    mnx = mny = mxx = mxy = 0
    zeroes = 0
    c = px[x, y]
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if dx == 0 and dy == 0:
                continue
            nx, ny = x + dx, y + dy
            if nx < 0 or ny < 0 or nx >= w or ny >= h:
                continue
            d = delta(c, px[nx, ny])
            if d == 0:
                zeroes += 1
                if zeroes > 2:
                    return None
            elif d < mn:
                mn, mnx, mny = d, nx, ny
            elif d > mx:
                mx, mxx, mxy = d, nx, ny
    return mn, mx, (mnx, mny), (mxx, mxy)


def is_antialiased(px, other, x, y, w, h):
    """A pixel is anti-aliasing (not a real change) if it is the darkest or
    brightest of its neighbourhood in ONE image and has a neighbour that is
    identical in the other — the classic AA signature. Skipping these is what
    keeps a diff from screaming about every curved edge."""
    n = neighbourhood(px, x, y, w, h)
    if not n:
        return False
    mn, mx, mnc, mxc = n
    if mn == 0 or mx == 0:
        return False
    n2 = neighbourhood(other, mnc[0], mnc[1], w, h)
    n3 = neighbourhood(other, mxc[0], mxc[1], w, h)
    return bool(n2) or bool(n3)


def region_median_y(px, x0, y0, x1, y1, w, h, inside):
    vals = []
    for Y in range(max(0, y0), min(h, y1), 2):
        for X in range(max(0, x0), min(w, x1), 2):
            if inside(X, Y):
                vals.append(rgb2y(*px[X, Y]))
    vals.sort()
    return vals[len(vals) // 2] if vals else 0.0


def ink_of(px, x0, y0, x1, y1, w, h, inside):
    """Pixels that stand out from the region's own background — i.e. drawn content.
    Measured per image against its own median, so it works on light and dark dials."""
    med = region_median_y(px, x0, y0, x1, y1, w, h, inside)
    n = 0
    for Y in range(max(0, y0), min(h, y1)):
        for X in range(max(0, x0), min(w, x1)):
            if inside(X, Y) and abs(rgb2y(*px[X, Y]) - med) > 18:
                n += 1
    return n


def main():
    a_path, b_path, out_path, regions_path = sys.argv[1:5]
    # A region cannot be "wrong" for differing by less than two rasterisers differ on
    # identical input. Flag against the measured floor, not an arbitrary constant —
    # otherwise every run reports failures that no change could ever fix.
    # The floor is not one number: a region full of small text has far more
    # rasteriser disagreement than a plain metal rim, so each region is judged
    # against ITS OWN floor. Pass the floor run's JSON to get that.
    floor, floor_by = 0.0, {}
    if len(sys.argv) > 5 and sys.argv[5] not in ('', '0'):
        a5 = sys.argv[5]
        try:
            floor = float(a5)
        except ValueError:
            fj = json.load(open(a5, encoding='utf-8'))
            floor = fj.get('overall', 0.0)
            floor_by = {r['name']: r['pct'] for r in fj.get('regions', [])}
    fail_at = max(REGION_FAIL_PCT, floor * 1.5)
    limit_for = lambda name: max(REGION_FAIL_PCT, floor_by.get(name, floor) * 1.5)
    A = Image.open(a_path).convert('RGB')
    B = Image.open(b_path).convert('RGB')
    if A.size != B.size:
        B = B.resize(A.size, Image.LANCZOS)
    w, h = A.size
    pa, pb = A.load(), B.load()

    out = Image.new('RGB', (w, h))
    po = out.load()
    mask = [[0] * h for _ in range(w)]
    total_diff = 0
    max_delta = THRESHOLD * MAX_YIQ

    for y in range(h):
        for x in range(w):
            ca, cb = pa[x, y], pb[x, y]
            d = delta(ca, cb)
            if abs(d) > max_delta and not is_antialiased(pa, pb, x, y, w, h):
                mask[x][y] = 1
                total_diff += 1
                po[x, y] = (255, 0, 190)                       # magenta = real difference
            else:
                g = int(rgb2y(*ca) * 0.22 + 18)                # dimmed base for context
                po[x, y] = (g, g, g)

    regions = json.load(open(regions_path, encoding='utf-8'))
    rows = []
    for reg in regions:
        if 'r' in reg:
            cx, cy, r = reg['cx'], reg['cy'], reg['r']
            x0, y0, x1, y1 = int(cx - r), int(cy - r), int(math.ceil(cx + r)), int(math.ceil(cy + r))
            inside = lambda X, Y: (X - cx) ** 2 + (Y - cy) ** 2 <= r * r
        else:
            x0, y0 = int(reg['x']), int(reg['y'])
            x1, y1 = int(reg['x'] + reg['w']), int(reg['y'] + reg['h'])
            inside = lambda X, Y: True
        n = bad = 0
        for Y in range(max(0, y0), min(h, y1)):
            for X in range(max(0, x0), min(w, x1)):
                if not inside(X, Y):
                    continue
                n += 1
                bad += mask[X][Y]
        # ⭐ A percentage can hide a MISSING ELEMENT: a small omission inside a large
        # region dilutes to near nothing, and a text-heavy region's noise floor then
        # swallows it (this is the classic "pixel diffs let real changes slip through").
        # So also compare how much INK each side has in the region. If the design draws
        # markedly more than the build, something the design has is simply not there.
        ink_a = ink_of(pa, x0, y0, x1, y1, w, h, inside)
        ink_b = ink_of(pb, x0, y0, x1, y1, w, h, inside)
        ink_d = (ink_b - ink_a) / ink_a * 100.0 if ink_a else 0.0
        rows.append((reg['name'], bad, n, 100.0 * bad / n if n else 0.0, ink_d))

    rows.sort(key=lambda r: -r[3])
    overall = 100.0 * total_diff / (w * h)

    print(f'\n  FIDELITY vs DESIGN — {w}x{h}, pixelmatch threshold {THRESHOLD}, AA ignored')
    print(f'  {"REGION":<26}{"DIFF":>9}{"INK d":>9}')
    print('  ' + '-' * 62)
    for name, bad, n, pct, ink in rows:
        lim = limit_for(name)
        flag = ''
        if pct > lim:
            flag = f'  OVER (floor {floor_by.get(name, floor):.2f}%)'
        if ink < -12:
            flag += '  <<< CONTENT MISSING vs design'
        elif ink > 12:
            flag += '  <<< EXTRA content vs design'
        print(f'  {name:<26}{pct:>8.2f}%{ink:>+9.1f}%{flag}')
    print('  ' + '-' * 62)
    print(f'  {"WHOLE DIAL":<26}{overall:>8.2f}%   ({total_diff:,} of {w*h:,} px)')
    print(f'  diff image: {out_path}\n')

    out.save(out_path)
    # machine-readable twin, so the review page can show the same numbers
    with open(out_path.rsplit('.', 1)[0] + '.json', 'w', encoding='utf-8') as fh:
        json.dump({
            'overall': overall,
            'width': w, 'height': h,
            'regions': [{'name': n, 'pct': p, 'px': b, 'floor': floor_by.get(n),
                         'ink': i, 'over': p > limit_for(n) or abs(i) > 12}
                        for n, b, _, p, i in rows],
            'failAt': fail_at,
        }, fh, indent=2)
    sys.exit(1 if overall > fail_at else 0)


if __name__ == '__main__':
    main()

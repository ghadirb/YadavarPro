import cairosvg

PRIMARY = "#0F766E"
PRIMARY_DARK = "#134E4A"
CREAM = "#F4F1EA"
GOLD = "#E7B25C"

# Bell glyph path, hand-drawn in a 0-100 local coordinate space, centered around x=50.
BELL_PATH = (
    "M50,14 C46.7,14 44,16.7 44,20 L44,22.3 "
    "C33.6,25.1 26,34.6 26,46 L26,62 "
    "C26,66.5 23.8,70.7 20.1,73.3 L18,74.8 L18,80 L82,80 L82,74.8 L79.9,73.3 "
    "C76.2,70.7 74,66.5 74,62 L74,46 "
    "C74,34.6 66.4,25.1 56,22.3 L56,20 C56,16.7 53.3,14 50,14 Z"
)
CLAPPER = '<circle cx="50" cy="90" r="7" fill="{fg}"/>'
BASE_BAR = '<rect x="18" y="80" width="64" height="7" rx="3.5" fill="{fg}"/>'

def bell_group(fg):
    return (
        f'<path d="{BELL_PATH}" fill="{fg}"/>'
        f'{BASE_BAR.format(fg=fg)}'
        f'{CLAPPER.format(fg=fg)}'
    )

def badge_group(cx, cy, r, ring, check):
    # Checkmark centered in a small circle badge, sitting on the bell's lower-right.
    return f'''
    <circle cx="{cx}" cy="{cy}" r="{r}" fill="{ring}"/>
    <path d="M{cx-r*0.5},{cy} L{cx-r*0.12},{cy+r*0.4} L{cx+r*0.55},{cy-r*0.38}"
          stroke="{check}" stroke-width="{r*0.24}" fill="none"
          stroke-linecap="round" stroke-linejoin="round"/>
    '''

def store_svg():
    # Full-bleed square store icon (Bazaar/Myket want a plain square/rounded-square PNG,
    # not an adaptive-icon safe zone), gradient background + centered bell + checkmark badge.
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="100" y2="100" gradientUnits="userSpaceOnUse">
          <stop offset="0" stop-color="{PRIMARY}"/>
          <stop offset="1" stop-color="{PRIMARY_DARK}"/>
        </linearGradient>
      </defs>
      <rect x="0" y="0" width="100" height="100" rx="22" fill="url(#bg)"/>
      <g transform="translate(0,-2) scale(0.82)" transform-origin="50 50">
        {bell_group(CREAM)}
      </g>
      {badge_group(74, 78, 13, GOLD, PRIMARY_DARK)}
    </svg>'''

def foreground_svg_108():
    # Adaptive icon foreground: content must stay inside the ~66dp safe zone centered in the
    # 108x108 canvas (roughly a 33% margin on each side) since launchers crop this layer to
    # circle/squircle/rounded-square masks that vary by device.
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
      <g transform="translate(4,2) scale(0.62)">
        {bell_group(CREAM)}
        {badge_group(74, 78, 13, GOLD, PRIMARY_DARK)}
      </g>
    </svg>'''

def legacy_svg():
    # Pre-Android-8 launcher icon: not adaptive, so the shape itself must already look like a
    # finished rounded-square icon (no OS-level masking to rely on).
    return store_svg()

with open("/home/claude/icon/store.svg", "w") as f:
    f.write(store_svg())
with open("/home/claude/icon/foreground.svg", "w") as f:
    f.write(foreground_svg_108())
with open("/home/claude/icon/legacy.svg", "w") as f:
    f.write(legacy_svg())

# Store / promo assets
cairosvg.svg2png(url="/home/claude/icon/store.svg", write_to="/home/claude/icon/store_512.png", output_width=512, output_height=512)
cairosvg.svg2png(url="/home/claude/icon/store.svg", write_to="/home/claude/icon/store_1024.png", output_width=1024, output_height=1024)

# Legacy mipmap densities
for name, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    cairosvg.svg2png(url="/home/claude/icon/legacy.svg", write_to=f"/home/claude/icon/ic_launcher_{name}.png", output_width=px, output_height=px)
    cairosvg.svg2png(url="/home/claude/icon/legacy.svg", write_to=f"/home/claude/icon/ic_launcher_round_{name}.png", output_width=px, output_height=px)

print("done")

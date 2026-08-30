# App icon source

`build_icon.py` generates every launcher icon asset from scratch (no binary source-of-truth
to keep in sync). Re-run it with `pip install cairosvg && python3 build_icon.py` after editing
the colors/paths, then copy the outputs into `app/src/main/res/mipmap-*` and
`app/src/main/res/drawable/ic_launcher_foreground.xml` as needed.

- Brand colors: `#0F766E` (primary), `#134E4A` (primary dark), `#F4F1EA` (cream/bell), `#E7B25C` (gold badge)
- `store.svg` / the generated 512/1024 PNGs are also what to upload directly as the Bazaar/Myket store listing icon.

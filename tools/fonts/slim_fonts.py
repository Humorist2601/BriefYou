"""Makes the app's fonts smaller without changing how its text looks.

- Google Sans Flex: its grade axis (GRAD) is fixed at 0, the value the app always used. The other
  axes (opsz, wdth, wght, ROND, slnt) stay variable.
- Literata and Literata Italic: kept to Latin, basic Greek and the usual punctuation and symbols
  (Cyrillic and extended Greek are dropped; those letters use the phone's serif font).

Run from the repository root with the original fonts (fonttools needed:
`pip install fonttools`):

    python3 tools/fonts/slim_fonts.py ORIGINALS_DIR

It writes the slimmed fonts to app/src/main/res/font/.
"""

import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

OUT = Path("app/src/main/res/font")
LATIN = (
    "U+0000-024F,U+0259,U+02B0-02FF,U+0300-036F,U+0370-03FF,U+1E00-1EFF,U+2000-206F,"
    "U+2070-209F,U+20A0-20CF,U+2100-214F,U+2150-218F,U+2190-21FF,U+2200-22FF,U+2460-24FF,"
    "U+25A0-25FF,U+2600-26FF,U+FB00-FB06,U+FEFF,U+FFFD"
)


def main(originals: Path) -> None:
    font = TTFont(originals / "google_sans_flex.ttf")
    instancer.instantiateVariableFont(font, {"GRAD": 0}, inplace=True)
    font.save(OUT / "google_sans_flex.ttf")

    for name in ("literata", "literata_italic"):
        subset.main(
            [
                str(originals / f"{name}.ttf"),
                f"--unicodes={LATIN}",
                "--layout-features=*",
                "--name-IDs=*",
                "--glyph-names",
                "--notdef-outline",
                f"--output-file={OUT / f'{name}.ttf'}",
            ]
        )


if __name__ == "__main__":
    main(Path(sys.argv[1]))

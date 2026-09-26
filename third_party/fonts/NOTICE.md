# Bundled fonts

These seven upstream, unmodified TrueType variable font binaries provide offline
launcher typography. They are packaged once each under `app/src/main/assets/fonts/`.
No font download or Google Fonts network call is required at application runtime.

Retrieved on **2026-09-26**. Exact source URLs, original filenames, byte sizes and
SHA-256 checksums are recorded in [sources.json](sources.json). Each font is
distributed under **SIL Open Font License 1.1**; the unmodified notices are in
[licenses](licenses/). The same notices and font provenance are included in the APK
as `assets/fonts/NOTICE.txt`.

## Inventory

| Family | Asset filename | Bytes | Purpose |
| --- | --- | ---: | --- |
| Josefin Sans | `josefin_sans.ttf` | 118,588 | Primary Latin / Latin Extended / Vietnamese typography |
| Noto Sans | `noto_sans.ttf` | 2,049,096 | Latin Extended, Greek, Cyrillic and other upstream-supported characters |
| Noto Sans Arabic | `noto_sans_arabic.ttf` | 844,676 | Arabic, Persian and Urdu script coverage |
| Noto Sans Hebrew | `noto_sans_hebrew.ttf` | 112,640 | Hebrew, including combining marks |
| Noto Sans Devanagari | `noto_sans_devanagari.ttf` | 641,944 | Devanagari, including Hindi / Sanskrit shaping |
| Noto Sans Thai | `noto_sans_thai.ttf` | 218,652 | Thai script coverage |
| Noto Sans CJK SC (full pan-CJK) | `noto_sans_cjk.ttf` | 36,144,788 | Simplified and traditional Chinese, Japanese, Korean; regional glyph variants retained |

Total uncompressed binary payload: **40,130,384 bytes (38.271 MiB)**. The four
additional common-script families contribute **1,817,912 bytes (1.734 MiB)**.
The final APK size depends on its packaging/compression settings.

## Why one full CJK font

The bundled file is upstream `Sans/Variable/TTF/NotoSansCJKsc-VF.ttf`,
not the smaller region-specific `Subset/NotoSansSC-VF.ttf`. Its SC suffix specifies
the default glyph appearance, not a Chinese-only character subset.

The official [Noto CJK deployment guide](https://github.com/notofonts/noto-cjk/blob/main/Sans/README.md)
documents that language-specific variable fonts retain full CJK coverage and
support regional glyph selection using language tags plus OpenType `locl`.
This single binary retains Chinese, Japanese and Korean characters, including
simplified/traditional forms, hiragana, katakana and Hangul; its GSUB language
systems contain `JAN`, `KOR`, `ZHS`, `ZHT` and `ZHH`. Text locale must be
forwarded to Android shaping so it can select the appropriate regional forms.

The binary character map contains 44,810 Unicode codepoints. This is not a claim
to cover every Unicode character or every rare CJK ideograph. Other scripts and
characters should continue through Android's system fallback chain.

The full TTF was chosen instead of the 30,737,452-byte CFF2 OTF to use the more
conservative TrueType variable-font path on supported Android versions. Shipping
separate SC, TC, JP and KR binaries would unnecessarily duplicate the large shared
glyph inventory.

## Variation axes and integration notes

- Josefin Sans: `wght=100..700` (default **100**), no slant axis. Explicitly
  set the desired weight; requests above 700 must use its maximum supported
  weight rather than pretend that heavier masters exist.
- Noto Sans and the four common-script fonts: `wght=100..900`,
  `wdth=62.5..100` (default width 100).
- Noto Sans CJK: `wght=100..900`, no width axis.
- **Noto Sans CJK and Noto Sans Hebrew default to weight 100.** Always set
  `wght` explicitly for each requested text weight; the other Noto assets default
  to 400.
- Preserve the full glyph/GSUB/GPOS tables. Avoid text-derived subsetting: installed
  application labels, shortcut names and calendar titles can contain characters
  not present in the project's string resources.
- Basic Noto Sans is not sufficient for CJK or all world scripts; the explicit
  CJK/common-script assets and a final system fallback are intentional.
- Merely listing unrelated Compose fonts by weight does not establish per-glyph
  fallback. The application must build a proper Android fallback chain.

## Offline verification

Run:

```powershell
python third_party/fonts/verify_fonts.py
```

The verifier uses only Python's standard library. It checks every asset's exact
length and SHA-256 against the manifest, parses the variable axes, tests
representative Latin/Greek/Cyrillic/CJK/Arabic/Hebrew/Devanagari/Thai character
coverage, and checks that CJK regional language tags and the `locl` substitution
feature remain present. It only reads local files; it never downloads, executes
upstream code, or modifies the fonts. Runtime shaping/rendering still requires
Android UI verification.

# Bundled fonts

The reading and display type is Hyle's own three internal families, taken from
the Hyle Design System's `fonts/` source-of-truth (owner-supplied, 2026-07):

| App role | Family | File(s) | Built on |
| --- | --- | --- | --- |
| Sans reading voice (default) | **Hyle Grotesk Classic** | `hyle_grotesk_classic_{regular,medium,bold}.ttf` | Space Grotesk + Archivo letterforms |
| Sans reading voice (alt) | **Hyle Grotesk Plus** | `hyle_grotesk_plus_{regular,medium,bold}.ttf` | Grotesk Classic + Deco-sweep N/R |
| Serif reading + all display/headline | **Hyle Print** | `hyle_print_{regular,medium,heavy}.ttf` | Literata + Hyle's identity pass (square dots, five-armed asterisk) |
| The "Nooz" wordmark only | **PT Serif Bold** | `pt_serif_bold.ttf` | Google Fonts (ParaType), unmodified |

These are the "two sans serif, one serif — all from Hyle, not Hyle Deco" the
owner specified. Hyle Classic and **Hyle Deco Pro** (the two Bitstream-derived
families) are deliberately not bundled — Deco is excluded by name per the owner.

The wordmark is the one exception to "all display type is Hyle Print": the
owner's splash mock sets "Nooz" in **PT Serif Bold at -2% letter-spacing**
(owner-confirmed, 2026-07), not Hyle Print — a first pass at this had guessed
Playfair Display Black from the mock's vector-outlined wordmark (no embedded
font name to read directly), which was visually close but not the owner's
actual spec.

Updated 2026-07-12 to `hyle-design-system@4c63219`: the prior Hyle Print cut had
two foreign glyphs (N, R) mistakenly spliced in from elsewhere, breaking
running text (a mirrored-looking R, an odd N) — both are now restored to the
Literata chassis. Hyle Classic/Hyle Deco Pro also had internal naming
collisions fixed upstream, but neither is bundled here regardless.

Each family is an SIL Open Font License 1.1 work (the Hyle families are OFL
derivative works; PT Serif is the unmodified upstream Google Fonts release);
the per-family provenance notes are `Hyle*-LICENSE-NOTE.txt` /
`PTSerif-LICENSE-NOTE.txt` and the license body is `OFL-1.1.txt`.
Every file was verified with `fonttools` (real family name + weight class)
before bundling, not trusted by filename.

The `.ttf` binaries live next to the code that references them, under
`core/design/src/main/res/font/`, so they compile into both flavors.

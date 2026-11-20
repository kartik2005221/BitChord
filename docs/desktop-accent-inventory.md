# Desktop accent inventory

The desktop theme owns application chrome colors; shared screens inherit the desktop Material
scheme or a host override. Android keeps its existing defaults.

The user explicitly chose **UI controls only** after the generated-artwork audit:
station illustrations, genre/mood art, and multicolor visualizers retain their
decorative palettes. Actual album and artist artwork is never recolored.

| Location | Previous pink / purpose | Desktop replacement |
| --- | --- | --- |
| `desktopApp/.../DesktopTheme.kt` | `#FA586A`, selected navigation icon and indicator | `navAccent` (orange in dark, darker orange in light) |
| `desktopApp/.../DesktopApp.kt` | Theme accent on selected player controls, favorites, downloads, inputs and shared Material controls | `accent` / Material `primary`; navigation uses `navAccent` |
| `sharedUi/.../components/Common.kt`, `screens/SearchScreen.kt` | `#FB4A62`, playing search result and library row | `LocalPlayingAccent`, supplied from desktop `navAccent`; Android default remains `#FB4A62` |
| `desktopApp/.../DesktopArtworkPalette.kt` | `#FF375F` and blue, fallback when artist artwork is unavailable | Orange `#FF6500` and blue `#1E3E62`; real artwork still determines its own palette |

Intentional non-theme colors remain: destructive/error red; Spotify green and other integration
branding; image-derived artist colors; the personal-station illustration and its pink text in
`TopPicksShelf.kt`, mood/collage and replay card artwork, explore category gradients, and
`MeshGradient.kt`'s decorative visualizer palettes. These are content artwork rather than selected
controls or global accents. Desktop SVG app icons and transport glyphs use neutral fills rather
than the classic pink; Android launcher assets retain their original pink gradient.

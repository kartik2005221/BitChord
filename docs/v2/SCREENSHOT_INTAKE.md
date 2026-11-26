# User-supplied screenshots at Gate A0

The archive contains 16 JPEGs at 1600 × 841 pixels, with 14 distinct images by SHA-256. The reported workstation is Fedora 44 with GNOME on Wayland, at 1920 × 1080 and 144 Hz. The JPEG size describes the files delivered here. The application revision, original window bounds and display scale are unknown. Use these observations as starting points for later measurement. The archive contains identifying music and account details and the observations below are sufficient for the port review.

| File, by capture time | Screen | What can be seen |
| --- | --- | --- |
| 01 · 15.20.21 | Expanded Now Playing | Both ends of the lettering on the square still are cut off. The title starts mid-word inside a faded text viewport. The timestamps, output label and bottom actions occupy little space relative to the artwork. |
| 02 · 15.20.32 | Account & integrations | Back to Settings above the Spotify account section, small toggles far to the right of their labels, preview and Done below. |
| 03 · 15.20.46 | Search landing | Moods and genres use tinted image tiles. The category name and lettering baked into the image compete on several tiles. The search field is centered and the mini player overlaps the lowest visible row. |
| 04 · 15.20.51 | Commute category | Multiple horizontal shelves of square covers with small captions. The last visible card is partly clipped at the right. This is a category detail, distinct from the Search landing. |
| 05 · 15.20.55 | Library | Four listening-stat cards above wide links, one visible on-device playlist and a shelf below. The Library Spotify glyph appears white in this capture. |
| 06 · 15.20.59 | Replay, top | All time selected. The four cards repeat the Library summary, followed by ranked songs and artists with counts at the far edge. This view does not exercise duplicate album identities. |
| 07 · 15.21.04 | Replay, scrolled | End of a ranking, Top genres and Listening shape. Genre totals and names sit at opposite edges. The fixed player remains in place. |
| 08 · 15.21.08 | History | Wide track rows place three actions at the far right. No date heading is visible in this viewport. Hover behavior needs a live pointer test. |
| 09 · 15.21.11 | Downloads, Playlists | Playlist grid, A–Z control, layout switch and an orange Queue · 4 button. A static image cannot establish how sorting or switching behaves. |
| 10 · 15.21.15 | Account & integrations | Byte-for-byte duplicate of 02. |
| 11 · 15.21.20 | Spotify library | Large playlist tiles. Import playlist, Account & integrations and Refresh are small text actions at the far right of the header. |
| 12 · 15.21.25 | Spotify playlist detail | Daylist cover and transport controls above a wide table. Artist, album and row actions are spread across the window. |
| 13 · 15.21.30 | Account & integrations | Byte-for-byte duplicate of 02. |
| 14 · 15.21.36 | Settings landing | Search, account, Listen Together, sources, audio quality and Downloads share one central column. Several section labels are much smaller than the body. |
| 15 · 15.21.46 | Expanded Now Playing, second recording | A different still also loses lettering on both edges at the sleeve boundary. The scrolling title is partially visible at the leading edge. |
| 16 · 15.22.02 | New | New albums & singles, a dense multi-column Trending shelf and part of New music videos. The mini player overlaps the lower shelf in this frame. |

The expanded player has separate cover and text columns in both images. They establish a clipped still and a partially visible title frame. They cannot show whether the title actually crosses the artwork boundary over time. The landscape renderer uses a square sleeve with `ContentScale.Crop`, which can remove both sides of a wide still. The live-video layer has a separate fit policy. For item 8, compare a known wide source still and a square album cover through the same route. Record the first frame, the settled frame and a short marquee cycle to distinguish thumbnail cropping from title animation.

Several frames show the page-level Back to Settings control or older Library icon treatment. Use the Git revision of PR #13 as the implementation baseline. An undated binary cannot establish a regression in it. These stills contain no performance trace, focus sequence, display-scale metadata, remote-speaker session or offline transition. New real-app captures and responsive measurements belong to the later, separately approved overhaul.

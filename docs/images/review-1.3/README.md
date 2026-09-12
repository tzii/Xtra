# ThystTV 1.3 UI preview sources

These PNGs are native Robolectric renders of production Android layouts with
sample data. They are layout previews, not device screenshots or installation
evidence. Version details, viewing totals and release notes in the images are
examples. Android tests use API 28 and the app's packaged resources.

| Preview | Rendering test | Notes |
| --- | --- | --- |
| `quality-font-1.0.png` | `PlayerPopupContentTest` | Dark Quality overlay, normal text |
| `quality-font-2.0.png` | `PlayerPopupContentTest` | Quality overlay at 200% text |
| `speed-large-text.png` | `PlayerPopupContentTest` | Speed overlay at enlarged text size |
| `stats-font-1.0.png` | `StatsLayoutTest` | Dark Stats layout, sample weekly totals |
| `updater-dark-1.0.png` | `UpdateUiTest` | Dark update prompt with sample release details |
| `updater-light-1.0.png` | `UpdateUiTest` | Earlier light preview; current-version label comes from a temporary QA build |

The README's quality, speed, Stats and dark updater previews were generated from
the reviewed app source at `cf1c7b182332469d84c7582016b8f880f4f4ba20`, using the normal
debug variant. They are copied unchanged from `app/build/ui-previews/`; no device
screen, application data or private account information was used.

The README launcher image is the existing supplied artwork at
`docs/images/icons/launcher/store-512.png`. The retained floating-chat device demo
is identified separately as predating 1.3.

To reproduce the current previews, use the documented JDK/Android SDK setup and run
the three named test classes through `:app:testDebugUnitTest`. Actual playback,
gesture timing, device window placement and OS installation still require the
checks in [MANUAL_QA.md](../../MANUAL_QA.md).

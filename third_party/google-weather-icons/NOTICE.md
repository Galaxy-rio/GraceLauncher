# Google Weather (Outlined), set-5

Source: https://github.com/mrdarrengriffin/google-weather-icons

Pinned revision: `7b6464bbf98273d69ae2c2bf8022b9b4ac1981c2`

Upstream original source: `www.gstatic.com/weather/conditions/v2/svg/`.

The upstream README states:

> All icons are the property of Google. This repository is for reference and educational purposes.

The repository does **not** supply a permissive redistribution or commercial-use
license for this artwork. Inclusion here is not a grant of rights. Confirm the
appropriate authorization before distributing the app with these assets. The
project's own code license does not relicense Google's artwork.

## Vendored subset and conversion

`icons.json` is the unmodified upstream mapping. `set-5/light/` and
`set-5/dark/` retain the original SVGs used by the app. The Android drawables at
`app/src/main/res/drawable/weather_google_*.xml` are mechanical conversions:
48 × 48 viewport, unchanged path data, fill/stroke colors, stroke widths,
fill rules, and clipping paths. There are no bitmaps, recoloring, runtime
downloads, or third-party image-rendering dependencies.

Regenerate the vectors offline from the repository root:

```powershell
pwsh -File third_party/google-weather-icons/convert-to-android.ps1
pwsh -File third_party/google-weather-icons/verify-conversion.ps1
```

Day/night denotes the weather time; light/dark denotes the display background.
Those choices are independent. The home row chooses background contrast from
its wallpaper-aware text color, while the agenda follows its Material surface.

| App condition | Set-5 source asset |
| --- | --- |
| Clear, day / night | `sunny.svg` / `clear_night.svg` |
| Partly cloudy, day / night | `partly_cloudy.svg` / `partly_cloudy_night.svg` |
| Cloudy | `cloudy.svg` |
| Rain | `drizzle.svg` (generic model does not expose intensity) |
| Snow | `flurries.svg` (generic model does not expose intensity) |
| Thunderstorm | `thunderstorms.svg` |
| Wind | `windy.svg` |
| Hail / sleet | `sleet_hail.svg` (combined upstream asset) |
| Fog / unknown | No matching set-5 asset; neutral em dash, with the actual accessible description |

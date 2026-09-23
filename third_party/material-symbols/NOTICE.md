# Google Material Symbols

The `ms_*.xml` Android drawables in `app/src/main/res/drawable/` are derived from
**Material Symbols Outlined** by Google.

- Upstream: https://github.com/google/material-design-icons
- Browse: https://fonts.google.com/icons
- License: Apache License 2.0 (unmodified upstream license in [LICENSE](LICENSE))
- Retrieved: 2026-09-23
- Style: Outlined, optical size 24, weight 400, grade 0, fill 0.

## Conversion

The SVG path data is copied verbatim from the official 24px SVG files. Android
VectorDrawable has no negative-origin viewBox, so each resource preserves the
original 960 by 960 viewport and adds `translateY="960"` to represent the original
SVG `viewBox="0 -960 960 960"`. There is no path redrawing, non-uniform scaling,
or stroke-width adjustment. Intrinsic display size is 24dp; Compose applies the
current content tint.

## Sources

- `ms_star.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/star/materialsymbolsoutlined/star_24px.svg
- `ms_info.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/info/materialsymbolsoutlined/info_24px.svg
- `ms_hourglass_empty.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/hourglass_empty/materialsymbolsoutlined/hourglass_empty_24px.svg
- `ms_category.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/category/materialsymbolsoutlined/category_24px.svg
- `ms_delete.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/delete/materialsymbolsoutlined/delete_24px.svg
- `ms_expand_more.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/expand_more/materialsymbolsoutlined/expand_more_24px.svg
- `ms_settings.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/settings/materialsymbolsoutlined/settings_24px.svg
- `ms_open_in_new.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/open_in_new/materialsymbolsoutlined/open_in_new_24px.svg
- `ms_add.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/add/materialsymbolsoutlined/add_24px.svg
- `ms_edit.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/edit/materialsymbolsoutlined/edit_24px.svg
- `ms_apps.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/apps/materialsymbolsoutlined/apps_24px.svg
- `ms_check.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/check/materialsymbolsoutlined/check_24px.svg
- `ms_palette.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/palette/materialsymbolsoutlined/palette_24px.svg
- `ms_home.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/home/materialsymbolsoutlined/home_24px.svg
- `ms_search.xml`: https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/search/materialsymbolsoutlined/search_24px.svg

# Brand icons

The shared mark is a deep-green cookbook with a debossed lime M, cream pages,
and a lime bookmark. In-app and site logos use the transparent book. Composed
icons use the cool-grey canvas (`#EEF0F2`); tiny favicons use a simplified lime M
on green so the letter stays legible. Native icon appearance variants do not
change the apps' light-only UI.

## Sources and regeneration

- `branding/book-source.png`: original 1024px transparent artwork. Preserve it.
- `branding/favicon.svg`: small-size vector monogram.
- `branding/MainCourse.icon`: editable Icon Composer document, used by iOS.
- `bin/brand-icons`: ImageMagick 7 generator for checked-in derivatives.

From the main app repo:

```sh
bin/brand-icons --landing ../maincourse-landing
```

Omit `--landing` to update only this repository. The generator normalizes the
source's alpha (removing sub-1% specks and making the nearly opaque cover fully
opaque), trims transparent padding, and fits the artwork to 840px on a 1024px
canvas. It then shifts the artwork down 38px to centre the **book body**, ignoring
the hanging bookmark when balancing the top/bottom margins. This optical offset
is calibrated to the aligned source artwork; revisit it if the book or bookmark
proportions change. The bookmark remains fully visible. The same foreground is
used by Icon Composer, Android and the flat icon exports, so their alignment
stays consistent. It retains antialiased edges and does not remove book colours.
The script updates the Composer foreground but preserves `icon.json` edits.

After changing the logo, run `node scripts/make-og-image.ts` in the landing repo
to refresh its social cards, then `npm test` there.
Refresh the checked-in subscription artwork with
`bash docs/app-store/subscription-promo/build.sh` in the main app repo.

## Apple

Open `branding/MainCourse.icon` in Icon Composer. It has a separate background
and transparent foreground, with glass, translucency and extra foreground
shadows disabled to preserve the existing bookbinding texture and lighting.
The default background is canvas; the dark rendition uses ink. Icon Composer
derives tinted renditions. Preview all appearances after editing the document.

`hauptgang-ios/project.yml` includes the document as a resource and selects
`MainCourse` as `ASSETCATALOG_COMPILER_APPICON_NAME`. Xcode compiles it for the
supported iOS versions, including the pre-iOS-26 flattened representation.
Use `bin/ios-build` to verify changes; never edit the generated Xcode project.

The generator also refreshes `LoginLogo` and the 1x/2x/3x `LaunchLogo` assets.
The older `AppIcon.appiconset` contains matching flat exports for tooling, but
the selected application icon is the Composer document.

To render a preview on macOS:

```sh
"/Applications/Icon Composer.app/Contents/Executables/ictool" \
  branding/MainCourse.icon --export-image \
  --output-file tmp/icon-preview.png --platform iOS \
  --rendition Default --width 1024 --height 1024 --scale 1
```

Composer previews include the system enclosure. Use the unmasked, opaque PNGs
in `branding/exports/` for services that require a flat square image. These
exports share the source book and standard palette; custom Composer changes
to placement, effects or backgrounds must also be reflected in the generator
if they should carry over to the non-Apple platforms.

## Android

The adaptive launcher uses `launcher_book.png`, a square transparent foreground,
over `mc_canvas`. Its inset keeps the book inside launcher mask safe bounds.
The portrait `brand_mark.png` is separate so login/onboarding logos can retain
their natural proportions. `ic_launcher_monochrome.xml` is a book-and-M stencil
for themed icons. Verify with `bin/android-build` and `bin/android-test`.

`branding/exports/play-store-512.png` is the opaque, square Play Store image.
Store uploads and app releases are separate from generating these assets.

## Web

Rails uses the transparent `app/assets/images/logo.png`, 16/32/48px ICO, 96px
PNG favicon, and opaque 180px Apple touch icon. Public `/favicon.ico`, `/icon.png`
and `/icon.svg` are updated too, including direct requests outside the layout.

The landing site receives the same logo and favicon artwork, 16/32/96px PNGs,
180px touch icon and 192/512px manifest icons. Header/footer images derive width
from height to preserve the book's proportions. Keep home-screen icons square
and opaque; do not bake rounded device masks into them.

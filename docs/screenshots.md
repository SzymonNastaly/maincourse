# Screenshot and marketing artwork pipeline

`bin/screenshots` captures the real iOS app using AXe/ASC JSON plans and renders
marketing artwork with Koubou. Source material lives in `screenshots/`; generated
artifacts live in the git-ignored `screenshots/output/` directory.

## Setup

Use the project's Ruby/Xcode environment and install the capture/render tools:

```sh
brew tap cameroncooke/axe
brew trust --formula cameroncooke/axe/axe # Homebrew versions requiring tap trust
brew install axe
uv tool install koubou==0.20.0
kou setup-frames
kou setup-html
bin/screenshots doctor
```

ASC 5.4.0 and AXe 1.8.0 are the initial capture toolchain. ASC's local screenshot
commands are experimental; check `asc screenshots run --help` after upgrading.
Koubou is pinned to 0.20.0 for transparent PNG frames and managed Chromium HTML
rendering. We call Koubou directly: ASC's `screenshots frame` wrapper pins an older
version that flattens transparent frames onto white. The iOS
runtime and exact device/frame combinations are pinned in `screenshots/catalog.json`.
Install that simulator runtime in Xcode before capturing. Python 3.10+ is required.

## Commands

```sh
bin/screenshots list
bin/screenshots capture                              # Build, seed, iPhone + iPad, all screens
bin/screenshots capture --screen recipe-detail       # Retake just one feature on both devices
bin/screenshots capture --devices iphone --screen recipe-library,recipe-import
bin/screenshots capture --app /absolute/path/Hauptgang.app # Reuse a Debug simulator build

bin/screenshots render --preset app-store
bin/screenshots render --preset framed               # Transparent individual device PNGs
bin/screenshots render --preset website --screen recipe-library
bin/screenshots render --preset social --screen shopping-review
bin/screenshots render --preset story --screen recipe-detail
bin/screenshots gallery
```

The default locale is `en-US`. `--devices`, `--screen`, and `--locale` work for
capture and render. Website compositions require both iPhone and iPad captures.
App Store output is validated against ASC's accepted dimensions. Rendering uses
existing raw PNGs and does not start the app or Rails backend.

Capture creates/reuses simulators named **MainCourse Screenshots iPhone** and
**MainCourse Screenshots iPad**. It sets light appearance and a clean 9:41 status
bar. The iPad also displays its current system date. It builds once through
`bin/ios-build` and resolves that build's product via
XcodeBuildMCP from the same iOS working directory.

Each capture invocation resets the showcase data and owns a temporary Rails
server on `127.0.0.1:3100`. The port must be free; the server stops on completion
or failure. Database and uploads are under `storage/screenshots/`, independently
of ordinary development data. `bin/screenshots seed` can prepare that data alone.

## Content and photos

`screenshots/recipes.json` contains 14 complete recipes with structured
ingredients, tags, timing, servings, instructions, and per-dish photo prompts.
`screenshots/photo-style.txt` defines shared realism and crop constraints. Each
dish prompt chooses its own lighting, camera angle, surface and tableware: the
library mixes daylight plates, colorful overheads, dark restaurant scenes,
rustic baking and close-up textures. Recipes are interleaved in display order so
the iPad grid shows this variety throughout, rather than in a separate block.
All 14 dishes have individual art direction, including the original six: green
enamel-pan orzo, retro diner pancakes, garden-lunch salad, Nordic salmon,
flash-lit cafe toast, and a dark patisserie tart.

```sh
# OPENAI_API_KEY must be available in the environment.
bin/screenshots photos
bin/screenshots photos --recipe peach-burrata-salad
```

The photo command calls the OpenAI Images API with **gpt-image-2.5-sunburst**, high
quality, 1536×1024 PNG. It generates only missing files. To replace a photo, move
its existing PNG and JSON sidecar aside, edit the prompt, and run the command for
that recipe. The API call costs money; normal capture/render commands reuse the
checked-in files and make no image-generation requests.

`screenshots/photos/<slug>.json` records the exact prompt, model, generation
parameters, timestamp, usage, and image hash. Keep it with its PNG. Rerun capture
after changing food photos; the seed updates attachments when their checksum changes.

The fixture account is `screenshots@example.test` / `maincourse-screenshots`.
Its display name is Alex Morgan and its personal cookbook is My Recipes. Recipe
IDs stay stable across resets, and a unique fixed `updated_at` establishes the
library order. Shopping-list state resets each run. The seed refuses ordinary
development/production databases. Photos are uploaded before attachment commit,
and card/hero variants are processed before launching the app.

## Capture readiness and adding screens

`screenshots/catalog.json` maps stable feature names to ASC plans under
`screenshots/plans/`. Plans use native UI interactions and accessibility IDs;
`{{tomato-orzo}}` resolves to the seeded recipe ID. Capture steps are added by the
runner after the navigation plan completes and readiness is confirmed.

The runner supports one addition to ASC's schema: `element_type` on a `tap` step.
SwiftUI sometimes exposes both a Group and Button with the same identifier.
For those steps it invokes `axe tap --element-type Button` (or `RadioButton` for
tabs), and runs the surrounding steps as ordinary ASC plan segments. Resolved
segments and logs are saved in the run directory. Use the repository command to
run an enriched source plan; passing it directly to ASC ignores that extra field.
If iPad UIKit exposes the same typed tab twice, the runner re-reads the UI tree
and taps the resolved centre only when both matches occupy exactly the same
rectangle. Distinct matching targets still fail. No screen coordinates are stored
in the source plans.

The `-screenshots YES` launch argument works only in Debug simulator builds. It
uses the local screenshot backend, signs into the fixture account, resets the
cookbook selection, uses an in-memory SwiftData store, and suppresses notification
permission prompts. Authentication, recipe loading, rendering, and shopping-list
behavior use the normal app services and views.

`ScreenshotSupport` writes `Documents/screenshot-status.json` in the simulator's
app container. Startup must be ready, all started image loads must settle, and
there must be no image failures before saving a PNG. The runner also allows the
last navigation animation to finish. Missing status or failed images fail the
capture instead of saving a placeholder screen.

To add a feature:

1. Make its data reproducible in the fixture cookbook.
2. Inspect the UI with `axe describe-ui --udid <screenshot-simulator-uuid>`.
3. Add a plan using `wait_for` and accessibility IDs. Add app identifiers where
   necessary. Avoid coordinate taps and arbitrary sleeps for readiness.
4. Add a stable name/order in the catalog and copy in `screenshots/copy/en-US.json`.
5. Capture that name on both devices and inspect the raw and rendered images.

Every scenario relaunches the app. Existing plans only navigate/read; if adding a
scenario that changes server data, reset that scenario's fixture state as part of
the capture workflow so later scenarios remain independent.

## Rendering and artifact layout

```text
screenshots/output/
  raw/ios/en-US/iphone/recipe-detail.png
  raw/ios/en-US/ipad/recipe-detail.png
  rendered/framed/ios/en-US/iphone/recipe-detail.png
  rendered/app-store/ios/en-US/iphone/02-recipe-detail.png
  rendered/social/ios/en-US/iphone/03-shopping-review.png
  rendered/website/ios/en-US/both/01-recipe-library.png
  metadata/<preset>/ios/en-US/<device>/ # Render provenance and measured HTML layouts
  runs/<run-id>/                   # Resolved ASC plans, logs, capture manifest
  runs/render-<run-id>/            # Resolved Koubou configs and logs
  index.html                      # Local contact sheet, click through to full images
```

Raw sidecars identify the device/runtime/locale, source commit and dirty state,
app binary hash, plan, recipe/photo hashes, capture timestamp, and PNG hash.
Rendered JSON under `metadata/` records input hashes, renderer, template, and copy.
Export directories contain only PNGs so ASC can validate/upload them directly. Failed
capture runs have a failed manifest; previously successful images remain usable
with their original metadata. A successful retake replaces only the requested
images. Save the output directory as a CI/release artifact when keeping history.

Koubou first generates transparent frames at their native dimensions. The
HTML/CSS templates under `screenshots/templates/` then place those device images
with the saved marketing copy. Colours come from the web theme tokens and fonts
are the repository's bundled IBM Plex Sans files. Text changes need only `render`.
Individual transparent frames are useful directly on the website; the `website`
preset generates an overlapping iPhone/iPad composite on the brand canvas.

All feature panels use one large, concrete `headline` close to the device, with
no subtitle. Social/story templates retain a MainCourse header with the shared
transparent cookbook logo on its left. The App Store template has only the
feature headline and device.

The initial presets are App Store device-size canvases, 1080×1350 social feed,
1080×1920 stories, and a 2400×1800 website composite. App Store iPhone output uses
the `IPHONE_69` display type; iPad uses `IPAD_PRO_3GEN_129`.

## Updating App Store listings

Use the `asc-release-flow` skill to stage a new editable version with an existing
eligible TestFlight build, then upload the rendered panels before submitting.
Canonical listing metadata lives under `metadata/`. With ASC 5.4.0, pass the
metadata **root** (`--metadata-dir ./metadata`) to `asc release stage`; its help
example showing a version subdirectory does not resolve the canonical files.

For each target version-localization ID, preview and replace its screenshot set:

```sh
asc screenshots upload --version-localization LOCALIZATION_ID \
  --path screenshots/output/rendered/app-store/ios/en-US/iphone \
  --device-type IPHONE_69 --replace --dry-run
# After inspecting the plan, replace --dry-run with --confirm.
```

Repeat for `ipad` with `IPAD_PRO_3GEN_129`. ASC maps the `IPHONE_69` alias to the
API's `APP_IPHONE_67` set. A pre-existing `APP_IPHONE_65` set is a different set:
replacing the large-iPhone set does not remove its old images. Inspect and remove
obsolete smaller-device screenshots after the new set has finished processing
so fallback images do not advertise the retired UI.
Then remove the empty set with `asc localizations screenshot-sets delete --id
SCREENSHOT_SET_ID --confirm`; an empty set is itself a submission-readiness error.

Using the English panels in another listing locale is an explicit publication
choice, not an app translation. Keep the target localization IDs and upload
receipts in the release report under `.asc/`, and verify remote filenames,
checksums, ordering and COMPLETE delivery state before submitting for review.

## Extending to locales, web and Android

The stable interface is a named raw PNG and JSON sidecar under
`raw/<platform>/<locale>/<device>/`. Plans are platform-specific, while recipe
content, screen names, copy, fonts, and marketing templates can be shared.

The CLI currently enables iOS only. Add Android/web capture adapters behind that
same interface, appropriate viewport/device profiles and frames, and platform
options when implementing them. The local Rails fixture dataset already works
for all clients. Keep Android/emulator and web/browser launch setup in their adapters.

Add a locale only once the interface and demo content support it; provide its
copy file and localized fixture text, then add it to the catalog. Capture passes
AppleLanguages and AppleLocale as app launch arguments. Marketing text and app
content are separate translations; the renderer intentionally does not fall back
to English assets or copy for an unsupported locale.

## Checks

```sh
python3 -m unittest discover -s test/screenshots -p '*_test.py'
bin/rails test test/services/screenshots_seed_test.rb
bin/ios-build
```

The tooling tests run in `bin/ci`. Actual simulator capture is an explicit local
task: it needs Xcode, the pinned runtime, AXe, ASC, and the built app. To debug a
failed capture, inspect its run manifest/backend log and resolved plans, then
use AXe with that run's simulator UUID. Never target the ambiguous `booted` alias
when multiple simulators are running.

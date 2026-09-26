---
name: maincourse-screenshots
description: Capture reproducible MainCourse feature screenshots on iPhone and iPad and render device frames, App Store panels, website composites, and social cards. Use when refreshing app marketing images, capturing a specific screen, updating screenshot copy/templates, or generating showcase recipe photos.
---

# MainCourse screenshots

Use the repository command `bin/screenshots`. Read `docs/screenshots.md` for
setup, readiness, output conventions, and adding scenarios. Source plans,
recipes, photos, copy and templates are in `screenshots/`.

## Retake and render

```sh
bin/screenshots doctor
bin/screenshots list
bin/screenshots capture --screen recipe-detail --devices iphone,ipad
bin/screenshots render --preset app-store --screen recipe-detail
bin/screenshots render --preset social --screen recipe-detail
bin/screenshots render --preset website --screen recipe-library
```

Omit `--screen` to capture/render all named features. Capture builds the app,
seeds a dedicated cookbook, starts/stops the local backend, runs ASC/AXe plans,
waits for image readiness, and saves raw PNGs. Use `--app /path/Hauptgang.app`
only when intentionally reusing a known Debug simulator build.

Presets: `framed` (transparent devices), `app-store`, `website` (both devices),
`social` (1080×1350), `story` (1080×1920). Rendering reuses existing captures.
The initial locale is `en-US`; do not create translated headlines and imply the
app itself is localized. Check `screenshots/catalog.json` for supported locales.

## Inspect results

- Open `screenshots/output/index.html` and inspect both device sizes.
- Present representative output images to the user through the available file
  preview tool. Check photo crops, text wrapping, complete loading, native chrome,
  and whether the intended feature is visible.
- Raw PNGs and metadata: `screenshots/output/raw/ios/en-US/<device>/`.
- Final artwork: `screenshots/output/rendered/<preset>/ios/en-US/<device>/`.
- Failure diagnostics: `screenshots/output/runs/<run-id>/`.
- A failed retake preserves older successful images; verify the sidecar's run
  and timestamp before describing an image as refreshed.

## Change content or add a feature

- Photos: `screenshots/photos/`; recipe data and per-dish prompts:
  `screenshots/recipes.json`; shared photographic direction: `screenshots/photo-style.txt`.
  The 14-recipe library interleaves photographic styles. Keep shared realism/crop
  constraints, but vary lighting, surfaces, tableware and angles per dish.
- To generate missing artwork with the user-requested model:
  `bin/screenshots photos --recipe <slug>` using `OPENAI_API_KEY` from the environment.
  This uses `gpt-image-2.5-sunburst`; keep the PNG and its provenance JSON together.
  Never print credentials. Photo generation is separate from routine recapture.
- Copy edits: `screenshots/copy/en-US.json`, then rerender. Use one concrete
  `headline` close to the device, with no subtitle. Social/story panels also
  include the MainCourse name with the shared cookbook logo to its left.
- Layout edits: `screenshots/templates/`; load the `maincourse-design` skill.
- New screens: inspect with `axe describe-ui --udid <exact-uuid>`, add an ASC
  navigation plan in `screenshots/plans/`, then catalog entry and marketing copy.
  Prefer accessibility IDs and readiness conditions. Recipe IDs are resolved
  from `{{recipe-slug}}` placeholders. Prove the plan on both phone and tablet.

## Verification

For App Store publication, use `asc-release-flow` with the requested existing
TestFlight build. See the publication section of `docs/screenshots.md` for metadata
root paths, per-locale replacement, and obsolete device-size screenshot sets.
Finish the requested template revisions and rerender before uploading.

For tooling changes run the Python tests in `test/screenshots/`; for fixture
changes run `bin/rails test test/services/screenshots_seed_test.rb`. Compile iOS
changes with `bin/ios-build`, and exercise affected scenarios using the command
interface. App Store rendering validates output dimensions locally via ASC.

Koubou is pinned to 0.20.0 and called directly for transparent frames. ASC local capture is experimental, so verify its CLI
help when changing plan behavior. Always use explicit simulator IDs; the harness
creates dedicated screenshot simulators and does not rely on `booted`.

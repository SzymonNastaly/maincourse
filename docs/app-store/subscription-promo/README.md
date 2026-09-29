# Subscription promotional images

The 1024×1024 promotional image App Store Connect attaches to each subscription.
It is what the App Store uses to promote a subscription, and it is what renders
on offer-code redemption pages and in win-back offers. It is **not** the App
Review screenshot, which is review-only and set separately on the subscription.

Tracked in #85.

## Files

| File | Subscription | Product ID |
|---|---|---|
| `hauptgang-pro-monthly.png` | Monthly (`6758990894`) | `app.hauptgang.pro.monthly` |
| `hauptgang-pro-yearly.png` | Yearly (`6758991030`) | `app.hauptgang.pro.yearly` |

Apple wants a *unique* image per subscription, so the two are inverted rather
than shared: Monthly on the accent green, Yearly on the grey canvas.

## Regenerating

```bash
docs/app-store/subscription-promo/build.sh
```

Renders `build.sh`'s inline HTML through headless Chrome at 1024×1024, then
flattens with ImageMagick. Edit `build.sh`, never the PNGs. Requires Google
Chrome and `magick` on PATH; both fonts and the logo are read out of
`app/assets`, so the mark and type stay in sync with the app automatically.

Two details in there are deliberate and worth not "cleaning up":

- **Alpha is stripped** (`-alpha remove -alpha off`, `PNG24:`). App Store
  Connect rejects images with an alpha channel.
- **The green M cookbook, on the app icon's grey canvas.** The `#EEF0F2`
  ground matches the composed app icon and keeps the green cover distinct on
  the monthly card's accent-green background. See `docs/brand-icons.md` for
  the shared artwork workflow.

## Uploading

Upload is version-scoped; the product-scoped `asc subscriptions images`
commands are deprecated as of App Store Connect API 4.4.1.

```bash
asc subscriptions versions images upload \
  --version-id "SUBSCRIPTION_VERSION_ID" \
  --file docs/app-store/subscription-promo/hauptgang-pro-monthly.png
```

**A subscription version only accepts an image while it is modifiable.**
Uploading to an `APPROVED` version fails with:

```
failed to reserve: Version is not in modifiable state.
```

Changing the image then means `asc subscriptions versions create` for a new
version per subscription, which App Review checks together with the next app
version. The current images are on version 2 of each subscription, created in
September 2026 for the MainCourse rename:

| Subscription | Version 2 ID |
|---|---|
| Monthly | `0653c7db-3c34-4cea-aa48-8275ce680991` |
| Yearly | `b05e3fe9-c2e8-44ef-87f2-fc5c664f4b66` |

Verify with:

```bash
asc validate subscriptions --app 6758990872
```

It should report no `subscriptions.images.recommended` warnings.

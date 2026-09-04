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
- **The leather cookbook, on the app icon's cream.** The palette dropped the
  old brown theme but the brand mark kept it — see the brand-mark
  non-negotiable in the `maincourse-design` skill. The cream ground
  (`#F1DFA9` → `#E6D097`) is sampled from the iOS app icon so the mark reads
  as the mark instead of a brown smudge on green.

## Uploading

Upload is version-scoped; the product-scoped `asc subscriptions images`
commands are deprecated as of App Store Connect API 4.4.1.

```bash
asc subscriptions versions images upload \
  --version-id "SUBSCRIPTION_VERSION_ID" \
  --file docs/app-store/subscription-promo/hauptgang-pro-monthly.png
```

**A subscription version only accepts an image while it is modifiable.** Both
subscriptions are currently on `APPROVED` version 1, and uploading to those
fails with:

```
failed to reserve: Version is not in modifiable state.
```

Attaching these therefore means `asc subscriptions versions create` for a new
version per subscription and putting that version through review — not a
metadata edit in place. Do not start that while an app submission is in flight.

Verify with:

```bash
asc validate subscriptions --app 6758990872
```

The two `subscriptions.images.recommended` warnings should be gone.

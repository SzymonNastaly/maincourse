# MainCourse for iPhone and iPad: interface direction

Written on 2026-11-24 against commit `9e25874`. It is a direction for
implementers and a record of what was built from it. It follows the method of
Schublade's `notes/iphone_interface_direction.md`: critique the build from
simulator screenshots, decide a few things firmly, specify each screen, and
prove each unit with screenshots.

## The goal

The app should follow the Human Interface Guidelines, read at every text size,
and feel like the apps of small studios that cooks already use, such as Crouton
and Paprika. The app has one loop, in this order of importance.

1. A person saves a recipe from wherever they found it.
2. Later they find it again.
3. They shop for it and cook from it.

Step 3 happens with flour on the hands and a phone or iPad propped against the
wall. That is where most of this direction goes.

## What this direction rests on

- Simulator screenshots of the Debug build at `9e25874` on an iPhone 17 Pro Max
  and an iPad Pro 13-inch (M4), iOS 26.5, seeded by `bin/screenshots seed`: the
  library, a recipe, the shopping review, the shopping list, import, search,
  Settings, the edit sheet, the cookbook menu, and the recipe at the largest
  accessibility text size. They are not kept in the repository.
- The Swift files of `Hauptgang/Views`, `MainCourseTheme.swift`, and the
  `maincourse-design` skill.
- Contrast ratios computed with the WCAG formula from the hex values in
  `MainCourseTheme.swift`.
- Comparisons with Crouton, Paprika and Apple's apps are from memory.

## The five decisions

1. **The recipe page is a cooking surface.** A tap on an ingredient checks it
   off. A tap on a step marks it done. On iPad the ingredients and the steps sit
   side by side, so neither scrolls the other off the screen.
2. **Secondary text reads at 4.5:1.** `mcMuted` is for graphics only: icons,
   bullets, rules. Text that used it moves to `mcBody`.
3. **Search is a list, not a gallery.** Results are compact rows with a small
   photo. The empty search shows the recipes added most recently.
4. **iPad content keeps a readable width.** Lists and forms stop at about
   700 points and centre. Only the library grid and the recipe's photo span
   the window.
5. **Nothing has a fixed size that text outgrows.** Icons scale with their text
   style, tap targets are at least 44 points, and the facts card stacks at the
   accessibility sizes.

Dark mode stays off. The design system has no dark palette, and designing one is
the owner's call, not this direction's.

---

## 1. Critique of the build at `9e25874`

### The recipe page

- On iPad the page is one column 1,300 points wide. A step runs to 180
  characters per line. The ingredients end a third of the way down the screen
  and the steps start under them, so a cook reading step 4 cannot see the
  ingredient list.
- Nothing on the page responds to a tap except the servings stepper. A cook
  loses their place in the ingredients and the steps, which is what Crouton's
  checkable lists solve.
- The recipe's tags ("Dinner", "Vegetarian") and its source link are in the
  model and appear nowhere on the page.
- The labels "Prep", "Cook" and "Servings" and the hint under the stepper are
  `mcMuted` on `mcSunken`: 2.38:1.
- The − and + buttons are 28 points square, below the 44-point minimum.
- At the largest accessibility size the facts card breaks. "Cook" wraps to
  "Coo / k", the icons stay at 18 points beside 40-point numbers, and the
  stepper's symbols stay at 14 points. The 6-point bullets stay at the top of
  three-line ingredients.

### Search

- The prompt is one grey line in an empty screen. It offers nothing to tap.
- Results are the library's 224-point photo cards. Three and a half fit on an
  iPhone, which suits browsing and not a search.
- Nothing says how many recipes matched.
- The field capitalises its first letter, so "choc" is typed as "Choc".
- `RecipeRowView` draws a compact recipe row and nothing uses it.

### The shopping list

- On iPad each row is 1,300 points wide for a word like "Lemons".
- The amounts ("150 g"), the "To Buy" and "Already Got" headers and a checked
  item's name are `mcMuted`: 2.55:1 on white, 2.24:1 on the canvas.

### Settings and other lists

- On iPad Settings is a full-width form, so a toggle sits 1,200 points from its
  label.
- Of the 34 uses of `mcMuted` in the iOS sources, 18 draw text, including
  onboarding, login and the meal plan.

### Haptics

- `HapticManager` exists and has one caller, the shopping list's checkbox.
  Adding a recipe's ingredients to the list, which is the step from cooking to
  shopping, has no feedback beyond the sheet closing.

### What works and stays

- The library: a photo grid capped at 1,100 points on iPad, the cookbook menu in
  the title, Import in one menu.
- The tab bar with Search as its own tab, the iOS 26 pattern.
- Native Liquid Glass bars, sheets and menus, tinted green.
- The shopping review sheet before anything is added.
- The servings stepper and the scaled quantities in Plex Mono.

---

## 2. The recipe page

### Layout

iPhone and iPad in compact width keep today's order: photo, title, tags, facts,
ingredients, steps, notes, source.

iPad in regular width:

```text
┌──────────────────────────────────────────────────────────────┐
│ the photo, full width, 340 pt                                 │
├──────────────────────────────────────────────────────────────┤
│ Creamy Tomato & Basil Orzo                                    │
│ Lunch · Dinner · Vegetarian                                   │
│ ┌──────────────────────┐  Steps                               │
│ │ 10m    20m   − 4 +   │  ① Warm the olive oil in a deep …    │
│ └──────────────────────┘  ② Add the cherry tomatoes and …     │
│ Ingredients               ③ Stir in the orzo and vegetable …  │
│ • 2 tbsp olive oil        ④ Stir through the cream and …      │
│ • 3 cloves garlic         Notes                               │
│ …                         A one-pan lunch that is …            │
└──────────────────────────────────────────────────────────────┘
```

- The left column holds the facts card and the ingredients, 360 points wide.
  The right column holds the steps and the notes, and takes the rest up to a
  readable width.
- Both columns scroll together in one scroll view. A recipe has a dozen
  ingredients, so the left column fits beside the steps without a separate
  scroll.
- The layout follows `horizontalSizeClass`, so Split View and Stage Manager get
  one column when narrow.

### Cooking state

- A tap on an ingredient checks it: its text turns `mcBody` and is struck
  through, and its bullet becomes `checkmark.circle.fill` in the accent.
- A tap on a step marks it done: its number badge becomes a checkmark and its
  text turns `mcBody`. Steps are not struck through, because a cook rereads them.
- Each tap fires `HapticManager.shared.selection()`.
- The state lives in the view and resets when the person leaves the recipe. It
  is a cooking aid, not data, and it never reaches the server.
- VoiceOver reads each row as a button with the value "Checked" or "Done".

### Tags and source

- Under the title, the tags as plain text joined by " · " in `subheadline`,
  `mcBody`. They are not buttons, because the library has no tag filter.
- At the foot of the page, when the recipe has a source URL, a link "View
  original at" followed by the host, in the accent, which opens Safari.

### The facts card

- One compact row: a caption label above each value, no icons. Labels in
  `mcBody`.
- The − and + buttons have a 44-point tap area that extends past their
  32-point frame, so they do not make the card taller.
- At an accessibility size the card becomes a vertical list: one row each for
  Prep, Cook and Servings, label leading and value trailing.

---

## 3. Search

- **Empty search.** A section "Recently Added": the eight newest recipes of the
  active cookbook as compact rows. With no recipes, today's prompt stays.
- **Results.** A header with the count, "2 Recipes", using the existing plural
  catalog entry. Compact rows under it.
- **The compact row** reuses `RecipeRowView`, redrawn: a 56-point square photo
  with the card radius, the title in `headline` up to two lines, and the total
  time in Plex Mono, `mcBody`. One row is about 72 points, so ten fit on an
  iPhone where three and a half fit today.
- The context menu with Move and Delete stays.
- The field does not capitalise or autocorrect.
- On iPad the list keeps the readable width.

---

## 4. The shopping list, Settings and the readable width

- The shopping list, the shopping review sheet, Settings and the cookbook
  settings stop at 700 points and centre on iPad. A list keeps its background
  to the window's edges, so only the content narrows.
- Every `mcMuted` text in these screens moves to `mcBody`. A checked item keeps
  the strikethrough and turns `mcBody`, so it reads as done by the line, not by
  fading below legibility.

## 5. Haptics

One helper exists, `HapticManager`. Each moment below calls it.

| Moment | Feedback |
| --- | --- |
| An ingredient or a step is checked | `selection()` |
| The shopping review sheet adds its items | `notification(.success)` |
| A shopping item is checked | `lightTap()`, as today |

No sound.

## 6. Cut

| Idea | Why not |
| --- | --- |
| Dark mode | The design system is light only. A dark palette is a design task for the owner |
| An iPad sidebar of cookbooks | The cookbook menu in the title works. A sidebar is worth it only if people keep many cookbooks |
| A separate cook mode screen | The checkable page is the cook mode, without a mode switch |
| Persisting checked ingredients | Leaving the recipe ends the cooking session. Persisting would need a reset button |
| Tag filters in the library | The tags show on the page first. A filter needs the owner's call on where it lives |

Timers on steps with durations were cut here because they needed parsing of
free text. They are no longer cut: the server now parses the durations, and
the app shows them as chips in the step text. See `ios-cooking-timers.md`.

---

## 7. Build plan

Three units on disjoint files, then one pass over the string catalogs. Each
unit ends with `bin/ios-build` and screenshots on the iPhone and the iPad from
the seeded backend.

- **D1, the recipe page.** `RecipeDetailContentView.swift`,
  `PortionScalerView.swift`. The two columns, the cooking state, tags and
  source, the facts card. Proof: the iPad page with two checked ingredients and
  one done step, the iPhone page, and the iPhone page at the largest
  accessibility size.
- **D2, search.** `RecipeSearchView.swift`, `RecipeRowView.swift`. Proof: the
  empty search with Recently Added, a search with its count, both devices.
- **D3, the readable width, the contrast sweep and haptics.** The shopping list
  views, `ShoppingListReviewSheet.swift`, `SettingsView.swift`,
  `CookbookSettingsView.swift`, and every other file that draws text in
  `mcMuted`. Proof: the iPad shopping list and Settings, and a search of the
  sources that finds no `mcMuted` text.
- **Strings.** `bin/ios-localize`, then German and Polish for every new entry,
  following `docs/ios-localization.md`.

## 8. What was built

All three units and the strings landed on `main` on 2026-11-24, proven on the
iPhone 17 Pro Max and iPad Pro 13-inch simulators against the seeded backend.

- **D1.** `RecipePageLayout` picks one or two columns from the size class and
  the text size. `RecipeFactsCard` and `RecipeCookingRows` are new files. Two
  changes from the plan: at accessibility sizes the iPad also uses one column,
  because a 360-point column holds a few words per line there, and the stacked
  facts card drops its icons. The onboarding demo forces the compact layout,
  because it embeds the page in a 680-point frame that an iPad still reports as
  regular width. The source link is proven only by a preview, because the seed
  recipes have no source URL.
- **D2.** The count header uses the existing plural entry, so it reads
  "2 recipes", not "2 Recipes".
- **D3.** `.readableContentWidth()` in `MainCourseStyles.swift` insets the
  scroll content with `contentMargins`, so backgrounds stay full-bleed. After the
  sweep, `mcMuted` has 14 uses in the iOS sources and none draws text. The
  `maincourse-design` skill records the rule.
- **Density pass.** The first build gave every ingredient and step a 44-point
  minimum height and the facts card a separate icon line, so only part of the
  ingredient list fitted on an iPhone and the steps started below the tab bar.
  The owner found it too airy, so ingredient rows now take 3 points of padding,
  section gaps shrink from 24 to 20 points, and the facts card is one row of
  label over value. An iPhone now shows the facts, all eight ingredients of
  the seeded orzo, and the first two steps.
- **Not changed.** The large title of Settings stays at the leading edge on
  iPad, because it belongs to the navigation bar.

## 9. What only a real device shows

- The haptics. The simulator plays none.
- Whether a tap on an ingredient with a wet finger checks it reliably at the
  denser row height.

# Shopping List

Shopping list items belong to a cookbook and are identified by
`(cookbook_id, client_id)`. `name` and `details` are exactly what the user or
recipe review submitted; they are never rewritten by enrichment.

## Aisle categories

Each item carries derived metadata used to group the list into aisles:

- `category` — one of `Llm::IngredientInstructions::CATEGORIES`, in display order
  (produce, bakery, meat_seafood, dairy_eggs, pantry, oils_spices_condiments,
  frozen, beverages, household, other). Clients show unknown or missing values as
  Other.
- `canonical_name` — lowercase English identity, as for recipe ingredients.
- `enrichment_version` — set only once the category is confirmed.

`ShoppingListItem` categorizes itself before save whenever it is new or renamed
(details never change the aisle), trying the cheapest source first:

1. **Source recipe.** If `source_recipe` has an enriched ingredient whose `name`
   matches the item name (case-insensitive), its category, canonical name, and
   version are copied. Every client's recipe review submits the parsed ingredient
   name, so recipe additions are complete without an LLM call.
2. **Client hint.** The API's `category` and `canonical_name` params arrive as
   `category_hint`/`canonical_name_hint` (virtual attributes). A valid hint is kept
   provisionally; invalid hints are ignored, never a validation error. Because
   hints are only read for new or renamed items, a replay can't overwrite a
   server result.
3. **Lookup.** `ShoppingList::CategoryLookup` picks the most common category among
   current-version recipe ingredients and confirmed shopping list items with the
   same lowercased name, so an item the parser has categorized once (e.g. dish
   soap, which no recipe mentions) is placed immediately next time. Also
   provisional.

Any commit of an unchecked item without a confirmed version enqueues
`EnrichShoppingListItemsJob` (a failed enqueue is logged, never raised). Wrap
multi-item writes in `ShoppingListItem.batching_enrichment { ... }` to send them as
one job and one LLM call; `ShoppingList::UpsertItems` and the web recipe add do.
The job runs `IngredientParser` on the squished `"#{details} #{name}"` outside any
transaction, then locks each row and applies the result only if the row still
exists, has the same name, and still needs enrichment. It writes with
`update_columns`, so `updated_at` (which stale-list nudges read) and the Turbo
refresh are left alone. A parsed `other` doesn't replace a specific provisional
category. Parser fallbacks retry at 5, 10 and 15 minutes; items that still fail
are asked for again on their next edit (e.g. unchecking) or by the rake task
below. Checking or unchecking an item never recategorizes it.

The API exposes `category`, `canonical_name`, and `category_pending`
(`needs_enrichment?`). All are optional for clients. `category_pending` can stay
true if the parser keeps failing; clients should treat it as a hint to refresh, not
as a reason to poll indefinitely.

To queue unconfirmed unchecked items (e.g. after a deploy or a category
vocabulary change):

```bash
bin/rails shopping_list:enqueue_enrichment
```

## Clients

iOS (SwiftData schema V9) groups To Buy into aisles; web and Android show a flat
list for now. A manual add looks the name up in cached recipes' structured
ingredients and sends a hit as the hint. If a create comes back with
`category_pending`, the app refetches once about eight seconds later. Items
without a category display under Other, and a list that is all Other shows no
aisle headers (e.g. right after upgrading, before the enqueue task has run).

See `docs/ingredients.md` for staple defaults and the old-list replacement flow.

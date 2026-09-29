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

`ShoppingListItem` categorizes itself before save whenever it is new or its
`name`/`details` change, trying the cheapest source first:

1. **Source recipe.** If `source_recipe` has an enriched ingredient whose `name`
   matches the item name (case-insensitive), its category, canonical name, and
   version are copied. Every client's recipe review submits the parsed ingredient
   name, so recipe additions are complete without an LLM call.
2. **Client hint.** A valid `category` (and `canonical_name`) sent by the client is
   kept provisionally. Invalid hints are dropped, never a validation error.
   `ShoppingList::UpsertItems` only applies hints to new or changed content, so a
   replay can't overwrite a server result.
3. **Lookup.** `ShoppingList::CategoryLookup` picks the most common category among
   current-version recipe ingredients with the same lowercased name (indexed on
   `lower(name)`). Also provisional.

Rows without a confirmed version enqueue `EnrichShoppingListItemsJob` after commit.
It runs `IngredientParser` on `"#{details} #{name}"` outside any transaction, then
locks each row and applies the result only if the row still exists, its
name/details are unchanged, and it still needs enrichment. Parser fallbacks keep the
provisional category and retry, up to three attempts in total. Checking or
unchecking an item never recategorizes it.

The API exposes `category`, `canonical_name`, and `category_pending`
(`needs_enrichment?`). All are optional for clients. `category_pending` can stay
true if the parser keeps failing; clients should treat it as a hint to refresh, not
as a reason to poll indefinitely.

To queue unconfirmed unchecked items (e.g. after a deploy or a category
vocabulary change):

```bash
bin/rails shopping_list:enqueue_enrichment
```

See `docs/ingredients.md` for staple defaults and the old-list replacement flow.

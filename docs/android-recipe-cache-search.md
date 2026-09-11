# Android Recipe Cache and Search

Android recipe search is local and cookbook-scoped. Room recipe rows are the
source of truth; the full-text index is a derived projection that can always be
rebuilt from those rows.

Search follows the selected cookbook. Searching every accessible cookbook
would require loading and hydrating every cookbook and remains a separate
product decision.

## Data flow

```text
GET /api/v1/recipes
        |
        v
authoritative Room summary replacement -----> searchable names
        |
        v
GET /api/v1/recipes/batch?cursor=...&limit=100
        |
        v
partial Room detail upserts + cursor --------> ingredients and instructions
        |
        v
Room search documents ----Room triggers-----> FTS4 index
        |
        v
selected-cookbook Search screen
```

Opening a recipe still displays cached detail immediately and revalidates it
with `GET /api/v1/recipes/:id`.

## Canonical recipe cache

`RecipeRepository.refreshList` writes every summary returned by
`GET /api/v1/recipes` to Room. `CatalogDao.replaceRecipes` treats the response
as authoritative for one `(userId, cookbookId)` scope: it removes missing
recipes while preserving `detailJson` for retained rows. A successful empty
response is authoritative.

After a successful active-cookbook list refresh, `RecipeRepository.syncDetails`
resumes `GET /api/v1/recipes/batch` from a Room cursor and stores full details
in pages of 100. Hydration is best-effort and does not keep the recipe-list
loading state active.

Selecting a cookbook first changes the observed Room scope, then refreshes and
hydrates that cookbook. Existing cached content remains visible if the network
request fails. `RecipeDetailViewModel` uses stale-while-revalidate behavior, so
opening a cached detail never suppresses its network freshness check.

All recipe rows use `(userId, cookbookId, recipeId)` identity. Sign-out,
confirmed account deletion, and authenticated `401` cleanup clear Room, keeping
recipe data, hydration cursors, and search data in the same cleanup boundary.

## Detail hydration

`MainCourseService.recipeDetails` implements the existing
`GET /api/v1/recipes/batch` contract. Android treats `next_cursor` as opaque.
Progress lives in `recipe_detail_syncs`, keyed by `(userId, cookbookId)` and
foreign-keyed to `cookbooks`.

Each non-empty page and its new cursor commit in the same Room transaction. A
failed decode or write leaves the cursor at the last committed page. An empty
response ends the run while retaining that cursor as the incremental-sync
watermark. The paging loop rejects blank, missing, and repeated cursors after a
non-empty page.

A batch is a partial sync and never removes peer recipes. Each incoming detail
must belong to an existing completed recipe in the captured scope. Responses
older than either the cached summary or cached detail are ignored. An accepted
detail updates `summaryJson` and `detailJson` together because detail responses
can contain fresher list fields such as name, timing, favorite state, image, and
`updated_at`.

`RecipesViewModel` owns one finite hydration job in `viewModelScope`; the
repository owns no coroutine scope. Launch, foreground reconciliation,
cookbook refresh, pull-to-refresh, and import polling provide natural retry
points. WorkManager is unnecessary because the persisted cursor makes the next
in-process run resumable.

## Room search projection

Room database schema 4 adds two derived tables:

- `recipe_search_documents` contains scope, recipe identity, name,
  ingredients, instructions, and `updatedAt`. Its composite foreign key points
  at `recipes`, so recipe, cookbook, and user cleanup also removes search data.
- `recipe_search_documents_fts` is an external-content FTS4 table over the
  three searchable text fields. It uses Unicode61 with diacritic removal and
  prefix indexes of length 2, 3, and 4.

Room creates the external-content synchronization triggers. Application code
writes the ordinary document table; it never writes the virtual FTS table.

`RecipeRepository` maintains canonical rows and their search documents in the
same database transaction:

- A list replacement rebuilds documents for that cookbook from the retained
  recipe rows. Valid summary-only and pending rows are searchable by name;
  failed imports are excluded.
- A detail write enriches the corresponding document with ingredients and
  instructions.
- Structured ingredient names are preferred, falling back to their raw text;
  recipes without structured ingredients use the detail's raw ingredient list.
- Move writes the target document before removing the source row. Delete and
  cookbook-membership loss cascade through foreign keys.

Migration 3-to-4 deliberately creates an empty projection because SQL
migrations should not decode application JSON. `SearchViewModel` asks the
repository to rebuild the selected cookbook from Room when search starts or
the selected cookbook changes. `RecipeRepository.searchSummaries` also makes
one rebuild-and-retry attempt if an FTS query fails. Index drift or a schema
change therefore does not require a network fetch.

## Query and ranking behavior

`RecipeSearchQuery` lowercases input, removes diacritics, splits on
non-alphanumeric characters, and turns each token into a safe prefix term.
Tokens use AND semantics across the whole document, so every query token must
match the name, ingredients, instructions, or a combination of those fields.

FTS4 identifies candidates. Results whose full query matches the name rank
first, followed by full ingredient matches, then instruction and mixed-field
matches. `updatedAt` and recipe list position provide deterministic ordering
within a bucket. The Search screen returns at most 50 recipe IDs and renders
the corresponding Room summaries, keeping UI models independent from index
storage.

Search is reactive: active results update as list refreshes and detail
hydration change the document table. The query is retained while navigating to
a recipe and back, and the selected cookbook name is shown below the search
field to make scope explicit.

## iOS relationship

iOS uses SwiftData as canonical storage and a separate GRDB FTS5 database.
Android preserves the important behavior—authoritative list reconciliation,
incremental detail hydration, name-first indexing, detail enrichment, local
scope, and a rebuildable index—while using Room FTS4 in the existing database.
This avoids a second Android storage API and cleanup lifecycle.

Relevant iOS references are:

- `hauptgang-ios/Hauptgang/Services/RecipeRepository.swift`
- `hauptgang-ios/Hauptgang/Services/RecipeSearchIndex.swift`
- `hauptgang-ios/Hauptgang/Services/RecipeSearchStore.swift`
- `hauptgang-ios/Hauptgang/ViewModels/RecipeViewModel.swift`

## Verification and extension points

Run `bin/android-build`, `bin/android-test`, and
`bin/android-test --device` after changing cache or search behavior. Device
coverage is required because it validates the platform SQLite FTS module,
Room's generated triggers, migrations, and Compose navigation.

The most relevant coverage lives in:

- `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/SimpleRepositoriesTest.kt`
- `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/cache/MainCourseDatabaseMigrationTest.kt`
- `maincourse-android/app/src/test/java/com/getmaincourse/app/features/search/SearchViewModelTest.kt`
- `maincourse-android/app/src/test/java/com/getmaincourse/app/data/cache/RecipeSearchQueryTest.kt`

Synonym expansion, fuzzy or typo matching, cross-cookbook search, system search
surfaces, and persistent background hydration are intentionally absent. Add
them only in response to observed search-quality or product requirements. Do
not upgrade Room or bundle another SQLite runtime solely to reproduce iOS's
FTS5/BM25 implementation.

## Historical constraint

An earlier Android implementation combined detail hydration, a hand-written
search engine, session coordination, mutation barriers, and extensive request
generation state. It was deliberately removed as part of the simple MVP
rewrite. The current implementation keeps coordination in the existing
repository/ViewModel boundary and does not restore those abstractions.

# Ingredient Enrichment Foundation

## Goal

Give every recipe ingredient a consistent, language-independent identity, unit, and shopping category without changing its original display text. This metadata is the foundation for staple defaults, aisle grouping, and quantity aggregation on web, iOS, and Android.

## Data contract

`Ingredient` keeps `raw`, `name`, `amount`, `amount_max`, `unit`, and `note`. It gains:

- `canonical_name`: a concise lowercase English food identity used only for matching, such as `olive oil`.
- `canonical_unit`: a language-independent value from the application unit vocabulary; unknown or absent units remain `nil`.
- `category`: one of `produce`, `bakery`, `meat_seafood`, `dairy_eggs`, `pantry`, `oils_spices_condiments`, `frozen`, `beverages`, `household`, or `other`.
- `enrichment_version`: the instruction version that successfully produced the normalized fields.

Original-language `name` and `raw` remain the user-facing source of truth. Canonical metadata must not silently replace recipe text.

## Unified processing

One `Llm::IngredientInstructions` module owns the field instructions, category vocabulary, unit vocabulary, and current enrichment version. Both full recipe extraction and the standalone `IngredientParser` use it together with `Llm::IngredientSchema`.

An ingredient is enriched only when its `enrichment_version` equals the current version. Quantity and unit presence no longer determine completion. Successful full-recipe extraction may satisfy the contract directly; otherwise `ParseRecipeIngredientsJob` enriches the row. A fallback caused by an LLM error remains usable but unversioned and therefore retryable.

## Import, edit, and backfill flow

All import and recipe-edit entry points enqueue the existing parse job when any row needs enrichment. The job preserves original `raw` and position while updating parsed and canonical fields. A rake task queues recipes containing old or incomplete rows so production backfill is explicit and resumable.

## API and clients

The recipe API adds the four enrichment fields to each `structured_ingredients` object. They are optional additions, so existing clients remain compatible. iOS and Android decode and retain the new fields for later shopping-list work; this phase does not change UI behavior.

## Failure behavior

Invalid model categories become `other`; unsupported canonical units become `nil`. Missing canonical names prevent completion. LLM/network failures preserve the raw ingredient and leave it eligible for retry.

## Verification

Tests cover shared prompts, normalization and validation, completion semantics, persistence from full recipe extraction, parser-job retries, API output, client decoding, and backfill selection.

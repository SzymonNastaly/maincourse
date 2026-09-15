# Ingredient Enrichment Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and expose consistently enriched multilingual recipe ingredients across Rails, iOS, and Android.

**Architecture:** A shared Rails instruction module defines the LLM contract and vocabularies. Recipe extraction and standalone parsing both produce versioned enrichment; incomplete rows flow through one retryable background job. Optional API/client fields preserve backward compatibility.

**Tech Stack:** Rails 8.1, RubyLLM structured schemas, Active Job, Minitest, Swift Codable/XCTest, Kotlin Serialization/JUnit.

**Spec:** `docs/superpowers/specs/2026-09-15-ingredient-enrichment-foundation-design.md`

## Global Constraints

- Preserve original-language ingredient `raw` and `name` values.
- Do not alter shopping-list behavior in this phase.
- LLM failures must remain usable and retryable.
- API additions must be backward-compatible.

---

### Task 1: Shared enrichment contract and persistence

**Files:**
- Create: `app/services/llm/ingredient_instructions.rb`
- Create: `db/migrate/20260915120000_add_enrichment_to_ingredients.rb`
- Modify: `app/services/llm/ingredient_schema.rb`
- Modify: `app/models/ingredient.rb`
- Test: `test/models/ingredient_test.rb`

**Interfaces:**
- Produces: `Llm::IngredientInstructions::VERSION`, `.prompt`, `.normalize_category`, and `.normalize_unit`.
- Produces: `Ingredient#needs_enrichment?`.

- [x] Write model and instruction tests that require explicit version-based completion and vocabulary validation.
- [x] Run the focused tests and verify they fail because the fields and contract do not exist.
- [x] Add the migration, shared instruction module, schema fields, and completion predicate.
- [x] Migrate the test database and run the focused tests until green.

### Task 2: Unified extraction and parser pipeline

**Files:**
- Modify: `app/services/ingredient_parser.rb`
- Modify: `app/services/llm/recipe_extraction.rb`
- Modify: `app/services/recipe_llm_service.rb`
- Modify: `app/services/recipe_image_llm_service.rb`
- Modify: `app/models/recipe.rb`
- Test: `test/services/ingredient_parser_test.rb`
- Test: `test/services/recipe_llm_service_test.rb`
- Test: `test/services/recipe_image_llm_service_test.rb`
- Test: `test/models/recipe_ingredients_test.rb`

**Interfaces:**
- Consumes: `Llm::IngredientInstructions` from Task 1.
- Produces: ingredient hashes containing `canonical_name`, `canonical_unit`, `category`, and `enrichment_version` only when enrichment succeeds.

- [x] Add failing tests for canonical metadata, shared prompt text, invalid-value normalization, and persistence.
- [x] Run the focused tests and confirm failures identify missing enrichment behavior.
- [x] Route all ingredient LLM prompts through the shared instructions and persist validated metadata.
- [x] Run the focused tests until green.

### Task 3: Completion tracking and production backfill

**Files:**
- Modify: `app/jobs/parse_recipe_ingredients_job.rb`
- Modify: recipe import/edit jobs and controllers that enqueue parsing
- Create: `lib/tasks/ingredients.rake`
- Test: `test/jobs/parse_recipe_ingredients_job_test.rb`
- Create: `test/tasks/ingredients_rake_test.rb`

**Interfaces:**
- Consumes: `Ingredient#needs_enrichment?`.
- Produces: `ingredients:enqueue_enrichment`, which enqueues one parse job per recipe containing incomplete rows.

- [x] Add failing tests for quantity-less enrichment, retryable fallbacks, already-versioned rows, and backfill selection.
- [x] Run the focused tests and verify the expected failures.
- [x] Replace amount/unit completion checks and add the explicit backfill task.
- [x] Run the focused tests until green.

### Task 4: API and native client compatibility

**Files:**
- Modify: `app/controllers/api/v1/recipes_controller.rb`
- Modify: `hauptgang-ios/Hauptgang/Models/StructuredIngredient.swift`
- Modify: `hauptgang-ios/HauptgangTests/Models/StructuredIngredientTests.swift`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/model/ApiModels.kt`
- Modify: Android API model tests
- Test: `test/controllers/api/v1/recipes_controller_test.rb`

**Interfaces:**
- Produces: optional `canonical_name`, `canonical_unit`, `category`, and `enrichment_version` fields in `structured_ingredients`.

- [x] Add failing Rails, Swift, and Kotlin decoding assertions for the enrichment fields.
- [x] Run each focused test and confirm it fails for the missing fields.
- [x] Add backward-compatible optional fields to the API and native models.
- [x] Run Rails, iOS, and Android focused suites until green.

### Task 5: Documentation and verification

**Files:**
- Modify: `docs/ingredients.md`

**Interfaces:**
- Documents the enrichment contract, retry semantics, and production backfill command.

- [x] Update durable ingredient-pipeline documentation.
- [x] Run focused Rails tests, `bin/ios-test`, and `bin/android-test`.
- [x] Run `bin/ci`, discounting only the five accepted baseline authentication failures.
- [x] Review the diff for scope and backward compatibility.

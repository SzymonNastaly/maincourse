# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Hauptgang is a Rails 8.1 application using Ruby 3.4.7. It follows Rails conventions and uses modern Rails 8+ features including:

- **Hotwire** (Turbo and Stimulus) for SPA-like interactions
- **SQLite multi-database setup** - Rails 8+ approach using separate SQLite databases for:
  - `primary`: Main application data
  - `cache`: Solid Cache storage (migrations in `db/cache_migrate`)
  - `queue`: Solid Queue storage (migrations in `db/queue_migrate`)
  - `cable`: Solid Cable storage (migrations in `db/cable_migrate`)
- **Solid suite** (Solid Cache, Solid Queue, Solid Cable) for caching, background jobs, and WebSockets
- **Tailwind CSS v4** via `tailwindcss-rails` gem
- **Importmap** for JavaScript (no Node.js/npm required)

## Scale

This app is used by a handful of people. Prefer pragmatic, working, sensible solutions
for that scale — avoid architecture, abstraction, or infrastructure justified only by
large user counts.

## Essential Commands

```bash
# Setup & Development
bin/setup                    # Install dependencies, prepare database, start server
bin/dev                      # Start development server

# Quality Checks
bin/ci                       # Run full CI suite (style, security, tests)
bin/rubocop -a               # Auto-fix Ruby style issues
bin/ios-test                 # Run iOS tests (auto-finds simulator, macOS only)
bin/logs                     # Attach lazyjournal to production (host from config/deploy.yml)

# Standard Rails commands for database, testing, etc. work as expected
```

**Running `bin/ci`:** it prints a per-step summary and the failing step's output
scrolls far past a terminal's worth, so capture it and read the file:

```bash
bin/ci > tmp/ci.log 2>&1; echo "exit=$?"   # then grep/tail tmp/ci.log
```

Never pipe `bin/ci` straight into `tail`/`head` — the pipeline reports *that*
command's exit status, so a failed suite looks like it passed. If you do pipe,
`set -o pipefail` first. `bin/ci` also ends with a red "Continuous Integration
failed" line, so grep for it rather than trusting the last few lines.

`bin/ci` sets `CI=true`, which turns the recipe-corpus snapshot tests into a
single skip, so it reports ~200 fewer tests than a bare `bin/rails test`. That
gap is expected, not a regression.

**Recipe Import Corpus:** A regression test suite for recipe extractors using cached HTML snapshots. See `docs/recipe-import-corpus.md` for usage and `recipe_corpus:*` rake tasks.

## Web UI

The web app has its own design system — grey canvas, deep green accent, IBM Plex —
with tokens in `app/assets/tailwind/application.css`. See `docs/web-ui.md` for how
the app is assembled and the `maincourse-design` skill for the tokens themselves
before touching views or styling. The old brown/Lato/Merriweather theme still
describes **iOS only**; it survives as that skill's `ios-legacy-tokens.md`.

## Documentation

`docs/` contains guides and reference documentation about how things work in this codebase. Check there first when working on a feature area.

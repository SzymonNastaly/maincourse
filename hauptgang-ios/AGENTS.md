# CLAUDE.md - iOS App

This file provides guidance to Claude Code when working with the Hauptgang iOS app.

## Project Overview

Native SwiftUI app for iOS 17+ that communicates with the Rails backend API. Uses MVVM architecture with async/await networking.

## Essential Commands

```bash
# From project root (hauptgang/):
bin/ios-build        # Canonical iOS compile-check: runs xcodegen, builds via xcodebuildmcp, saves tmp/ios-build-*.log, prints warning/error summary
bin/ios-build --simulator-id UUID  # Same, but force a concrete simulator UDID
bin/ios-test         # Run all iOS tests (auto-finds simulator)
bin/ios-release      # Build, export, upload to TestFlight (internal testers)
bin/ios-release --external  # Same + distribute to external testers & submit for beta review

# From hauptgang-ios directory:
xcodegen generate    # Regenerate Xcode project after adding/removing files
```

- If using XcodeBuildMCP, use the installed XcodeBuildMCP skill before calling XcodeBuildMCP tools.

## XcodeBuildMCP Workflow

When an agent needs a compile check or compiler warnings for the iOS app, use the repo wrapper first:

```bash
bin/ios-build
```

`bin/ios-build` is the canonical agent entrypoint. It always:

- runs `xcodegen generate`
- builds scheme `Hauptgang` via `xcodebuildmcp simulator build`
- resolves a concrete iPhone simulator UDID automatically unless `--simulator-id` is provided
- writes the full build log to `tmp/ios-build-*.log`
- prints a warnings/errors summary at the end

Use raw `xcodebuildmcp` commands only when you need something `bin/ios-build` does not do, such as launching the app, UI automation, log capture, or lower-level project discovery. The equivalent repo-specific flow is:

```bash
# 1. Discover the generated project
xcodebuildmcp simulator discover-projects --workspace-root /Users/szymonnastaly/projects/maincourse/hauptgang-ios

# 2. Confirm the app scheme
xcodebuildmcp simulator list-schemes --project-path /Users/szymonnastaly/projects/maincourse/hauptgang-ios/Hauptgang.xcodeproj

# 3. List concrete simulator IDs
xcodebuildmcp simulator list

# 4. Compile-check on a specific simulator
xcodebuildmcp simulator build \
  --project-path /Users/szymonnastaly/projects/maincourse/hauptgang-ios/Hauptgang.xcodeproj \
  --scheme Hauptgang \
  --simulator-id SIMULATOR_UUID
```

Notes:

- Use the real app scheme: `Hauptgang`. `list-schemes` also shows package/example schemes that are usually not what you want.
- Prefer `bin/ios-build` for compile checks and warning sweeps; it already handles XcodeGen, simulator selection, log capture, and summary output.
- If you need a deterministic destination, pass `bin/ios-build --simulator-id UUID` or use raw `xcodebuildmcp simulator build --simulator-id UUID`.
- `xcodebuildmcp simulator list` does **not** support ad-hoc `simctl`-style flags; check `--help` instead of assuming them.
- If `bin/ios-test` fails before compilation, `bin/ios-build` is the quickest repo-level way to verify Swift/UI changes.

## XcodeGen Rules

**IMPORTANT**: This project uses XcodeGen to generate the Xcode project from `project.yml`.

- **NEVER** manually edit `Hauptgang.xcodeproj/project.pbxproj`
- After adding/removing `.swift` files, run `xcodegen generate`
- The `project.yml` auto-discovers all `.swift` files in `Hauptgang/`
- Only edit `project.yml` to change build settings, add dependencies, or modify schemes

## Architecture

```
Hauptgang/
├── App/           # @main entry point (HauptgangApp.swift)
├── Models/        # Data models (Codable structs matching Rails JSON)
├── ViewModels/    # @Observable view models, business logic
├── Views/         # SwiftUI views (declarative UI)
├── Services/      # APIClient, KeychainService, AuthService
├── Utilities/     # Constants, extensions, theme helpers
└── Resources/     # Assets.xcassets, Info.plist
```

### Key Patterns

- **APIClient** (`Services/APIClient.swift`): Singleton actor for all HTTP requests. Uses `async/await`, handles snake_case ↔ camelCase conversion automatically.
- **AuthManager** (`ViewModels/AuthManager.swift`): Global auth state using `@Observable`. Inject via `.environment()`.
- **KeychainService**: Secure token storage. Never store auth tokens in UserDefaults.

## Rails API Integration

The iOS app consumes the Rails JSON API at `/api/v1/`. When working on iOS features:

1. Check the Rails API endpoint exists: look in `app/controllers/api/v1/`
2. Match iOS model properties to Rails JSON keys (snake_case in Rails → camelCase in Swift)
3. API base URL is configured in `Utilities/Constants.swift` (different for DEBUG vs Release)

### Example: Adding a New API Feature

1. Verify Rails endpoint and JSON structure
2. Create/update Model in `Models/` with `Codable` conformance
3. Add service method in appropriate `Services/` file
4. Update ViewModel to call service
5. Update View to display data

## Code Style

- Use Swift's modern concurrency (`async/await`, actors) - no completion handlers
- Prefer `@Observable` (iOS 17+) over `@ObservableObject`
- Use `#Preview` macro for SwiftUI previews
- Keep views simple; move logic to ViewModels
- Use `Constants` enum for magic strings/URLs
- **Always use `Color.hauptgangPrimary` (explicit `Color.` prefix), never `.hauptgangPrimary`** — static member lookup without the type fails in `ShapeStyle` contexts (e.g. `foregroundStyle`, `background`, `tint`)

## Testing

- Unit tests go in `HauptgangTests/`
- Test ViewModels by mocking Services

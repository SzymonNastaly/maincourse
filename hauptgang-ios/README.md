# Hauptgang iOS

Native SwiftUI iOS app for the Hauptgang recipe tracker.

## Requirements

- Xcode 16.0+
- iOS 18.0+
- XcodeGen

## Setup

### 1. Install XcodeGen (if not already installed)

```bash
brew install xcodegen
```

### 2. Generate Xcode Project

```bash
cd hauptgang-ios
xcodegen generate
```

### 3. Open in Xcode

```bash
open Hauptgang.xcodeproj
```

Or run from command line:

```bash
xcodebuild -project Hauptgang.xcodeproj -scheme Hauptgang -destination 'platform=iOS Simulator,name=iPhone 16' build
```

## Project Structure

```
hauptgang-ios/
├── project.yml              # XcodeGen configuration
├── Hauptgang/
│   ├── App/                 # App entry point
│   ├── Models/              # SwiftData models
│   ├── ViewModels/          # MVVM view models
│   ├── Views/               # SwiftUI views
│   ├── Services/            # API client, networking
│   ├── Utilities/           # Extensions, helpers
│   └── Resources/           # Assets, Info.plist
└── HauptgangTests/          # Unit tests
```

## Architecture

- **SwiftUI** - Declarative UI framework
- **SwiftData** - Apple's modern persistence framework
- **MVVM** - Model-View-ViewModel pattern
- **URLSession** - Native async/await networking
- **AuthenticationServices / GoogleSignIn** - Native provider authentication;
  see `../docs/oauth-sign-in.md` for provider setup

## Development

The `.xcodeproj` is generated and can be gitignored. After cloning, run `xcodegen generate` to create it.

### Regenerate Project

After modifying `project.yml`, regenerate:

```bash
xcodegen generate
```

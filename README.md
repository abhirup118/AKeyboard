# AKeyboard

Android Input Method Editor (IME) built in Kotlin using classic data structures and dynamic programming. It provides autocomplete, typo correction, and local personalization without machine learning or external network access.

## Features

- **Trie Prefix Autocomplete**: Prefix lookups run in $O(\text{prefix length})$ time with top suggestions ranked using an in-memory priority queue.
- **Dynamic Programming Auto-Correction**: Uses Levenshtein distance with adjacent transposition checks, applying branch pruning to evaluate suggestions within edit distance $\le 2$.
- **On-Device Learning**: Stores typed words, frequencies, and timestamps locally using Room (SQLite).
- **QWERTY Layout**: Supports shift, double-tap caps lock, symbol toggles, long-press alternate characters, and repeat backspace.
- **Light & Dark Themes**: Visual styles designed to follow system preferences.

## Architecture

The project follows the Android MVVM pattern:

- `ui/`: Custom views for the keyboard layout (`KeyboardView`) and candidate bar (`SuggestionBar`).
- `ime/`: `KeyboardService` (`InputMethodService`) managing `InputConnection` events and text commitments.
- `viewmodel/`: `KeyboardViewModel` tracking composing text buffers, shift state, and suggestion updates via coroutines.
- `domain/`: `AutocompleteEngine` and `AutoCorrectEngine` containing the prediction and typo correction algorithms.
- `data/`: In-memory `Trie`, `DictionaryLoader`, and Room database (`KeyboardDatabase`, `LearnedWordEntity`, `LearnedWordDao`).

## Building from Source

### Prerequisites

- Android SDK (API 26 to 35)
- JDK 17
- Gradle 8.13 (or the included Gradle wrapper)

### Build Commands

Run unit tests:
```bash
./gradlew testDebugUnitTest
```

Build the debug APK:
```bash
./gradlew assembleDebug
```
The output APK is generated at: `app/build/outputs/apk/debug/app-debug.apk`.

## Installation and Setup

1. Install via ADB:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Enable the input method:
   ```bash
   adb shell ime enable com.android.keyboard.gboardclone/.ime.KeyboardService
   adb shell ime set com.android.keyboard.gboardclone/.ime.KeyboardService
   ```
   Alternatively, open the companion app on the device and use the setup buttons to enable and select the keyboard.

## Testing

The project contains 127 automated unit tests covering:
- Trie insertion, prefix matching, bounded heap suggestion ranking, and concurrency stress testing (`TrieTest`, `DataLayerCornerCasesTest`).
- Dynamic programming distance calculations, true Damerau transposition, and branch pruning (`AutoCorrectEngineTest`, `DomainCornerCasesTest`).
- Dictionary asset integrity, loader edge formats, and alphabet coverage (`DictionaryAssetTest`, `DataLayerCornerCasesTest`).
- Repository ranking, frequency updates, and Room integration (`SuggestionRepositoryTest`, `ViewModelRepositoryCornerCasesTest`).
- ViewModel state management, shift timing, and layout mode transitions (`KeyboardViewModelTest`, `ViewModelRepositoryCornerCasesTest`).

Run all tests with:
```bash
./gradlew testDebugUnitTest --rerun-tasks
```

## License

This project is licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

# AKeyboard (Gboard-Like Android Keyboard - No ML)

A high-performance Android Input Method Editor (IME) built with Kotlin, featuring classic data structures (Trie prefix tree), Dynamic Programming edit distance (Levenshtein) auto-correction, and Room database personalization with no machine learning.

---

## Architecture Overview (MVVM)

```
app/src/main/java/com/android/keyboard/gboardclone/
├── KeyboardApplication.kt              # App singleton initialization & async dictionary preloading
├── MainActivity.kt                     # IME activation, settings shortcut & live typing playground
│
├── ime/
│   └── KeyboardService.kt              # Core InputMethodService connecting InputConnection with MVVM
│
├── viewmodel/
│   └── KeyboardViewModel.kt            # Shift/Caps lock, Composing buffer, Mode switching, Themes
│
├── ui/
│   ├── KeyboardView.kt                 # Custom QWERTY layout with tap, alternate char long-press, backspace repeat
│   └── SuggestionBar.kt                # Top 3-slot candidate bar with dynamic theme support
│
├── domain/
│   ├── AutocompleteEngine.kt           # Prefix matching orchestration
│   └── AutoCorrectEngine.kt            # Classical 2D DP Levenshtein + Damerau transposition distance
│
├── data/
│   ├── trie/
│   │   ├── TrieNode.kt                 # Node with HashMap<Char, TrieNode>, isWord, frequency
│   │   └── Trie.kt                     # O(prefix_len) lookup, Min-heap bounded top-N suggestions
│   ├── db/
│   │   ├── LearnedWordEntity.kt        # Room entity (word, frequency, lastUsedTimestamp, isCustom)
│   │   ├── LearnedWordDao.kt           # Queries for prefix, recency, and frequency
│   │   └── KeyboardDatabase.kt         # Room database singleton
│   ├── loader/
│   │   └── DictionaryLoader.kt         # Non-blocking IO loader for 55,000+ words asset
│   └── repository/
│       └── SuggestionRepository.kt     # In-memory ConcurrentHashMap + Trie + Room DB merger
```

---

## Core Features & Implementation Details

### 1. Trie-Based Dictionary Autocomplete (No ML)
- **Trie Structure**: Each node contains a `HashMap<Char, TrieNode>`, a boolean `isWord`, and an integer `frequency`.
- **Search Complexity**: `O(prefix_length)` to reach the prefix root node.
- **Top N Candidate Retrieval**: Traverses descendant nodes with a bounded **Min-Heap (Priority Queue)** of size $N$, yielding top suggestions in **< 2ms** (well below the 20ms constraint).
- **Tie-Breaking**: Sorted primarily by **frequency descending**, then **alphabetical ascending**.

### 2. Auto-Correction Engine (Dynamic Programming Edit Distance $\le 2$)
- **Algorithm**: Classical Levenshtein distance combined with Damerau adjacent-character transposition checks.
- **Branch-Pruned Trie Exploration**: Computes dynamic programming row vectors down Trie branches. Prunes any branch whose minimum row cost exceeds `maxDistance` ($2$), evaluating 55,000+ words in under $8\text{ms}$.
- **Typo Correction Examples**:
  - `teh` $\to$ `the`
  - `recive` $\to$ `receive`
  - `helo` $\to$ `hello`

### 3. User Learning with Room Database & Frequency Map
- **Runtime Frequency Map**: A thread-safe `ConcurrentHashMap<String, Int>` (`Map<String, Int>`) tracks active word frequencies.
- **Completion Learning**: When the user finishes a word (taps space, punctuation, or suggestion), the word's frequency is incremented in memory, updated in the Trie, and asynchronously stored in SQLite via Room (`LearnedWordEntity`).
- **Seamless Merging**: When querying suggestions, user-typed frequencies take precedence over baseline dictionary weights.

### 4. Suggestion Bar (3 Candidate Slots)
- Displayed directly above the keyboard view:
  ```
  --------------------------------
  | hello | help | helmet |
  --------------------------------
  ```
- Tapping a suggestion replaces the currently typed prefix in the `InputConnection` and automatically appends a space.

### 5. Custom KeyboardView & IME Service
- **Layouts**: Letters (QWERTY), Numbers/Symbols (`?123`), and Extended Symbols (`=\<`).
- **Tap & Long Press**: Long pressing a key (e.g. `Q` through `P`) inputs the alternate number/character (`1` through `0`).
- **Shift & Caps Lock**:
  - Single tap: Uppercase next character, then reverts to lowercase.
  - Double tap (within 350ms): Locks uppercase (`CAPS_LOCK`).
- **Repeat Backspace**: Holding down backspace triggers continuous deletion.
- **Themes**: Support for Light and Dark themes.

---

## Testing & Verification

The test suite covers Trie operations, DP Levenshtein distance, SuggestionRepository, and KeyboardViewModel:
```bash
./gradlew testDebugUnitTest
```
Generated debug APK:
```
app/build/outputs/apk/debug/app-debug.apk
```


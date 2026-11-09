# AKeyboard Backlog

Priority: **P1** = correctness / core promise, **P2** = quality, **P3** = extensibility.
Size: S (<½ day), M (~1 day), L (multi-day). Each item should land as its own commit with tests.

## P1 – Core behaviour gaps

| ID | Item | Why | Acceptance criteria | Size |
|----|------|-----|---------------------|------|
| B-01 | **True autocorrect on space** | Today corrections are only suggested; `teh` + space stays `teh`. | Space replaces the word with the top correction when confident (distance ≤ 2, clearly better than the typed word). Backspace immediately after reverts to the original. Unit tests for apply and undo. | M |
| B-02 | **Preserve capitalization** | Suggestions and learned words are always lowercase; tapping a suggestion for `Hel` commits `hello`. | Suggestion inherits the typed case (`Hel` → `Hello`, `HEL` → `HELLO`). Learned words keep the user's original casing where it is not purely sentence-initial. | M |
| B-03 | **Proper nouns / names** | Dictionary has no capitalized entries; names are learned only as lowercase. | Separate proper-noun list or case-aware entries. The corrector does not "fix" known names. Learned names are offered with their capitalization. | M |
| B-04 | **Apostrophes and hyphens inside words** | `don't` becomes `don` + `t`; `well-known` becomes `well` + `known`. Dictionary excludes non-alpha tokens. | `'` and `-` between letters stay in the composing word. Contractions are added to the dictionary. Learning stores the whole token. | M |
| B-05 | **Known-word-but-wrong detection** | A valid word (`form` for `from`) is never corrected. | Optional: surface a low-frequency exact match alongside higher-frequency near neighbours in the suggestion bar. | S |
| B-06 | **Corrections for 1–2 letter input** | Corrector only runs at ≥ 3 letters. | Define behaviour for short words (e.g. `teh` is covered; `th`, `ot`) without noisy suggestions. | S |
| B-07 | **Long-word handling** | Cursor sync reads only the last 30 characters; 1-letter words are never learned. | Raise or remove the 30-char limit (read until a non-word char). Decide whether `a`/`I` should count toward learning. | S |

## P2 – Quality and robustness

| ID | Item | Why | Size |
|----|------|-----|------|
| B-08 | **Dictionary quality pass** | Source is subtitle text: slang, names and misspellings are in the top 30k. | M |
| B-09 | **Build-time dictionary generator script** | Dictionary was produced ad hoc. Commit a script plus provenance and licence notes (hermitdave/FrequencyWords, CC-BY-SA 4.0) so it is reproducible and auditable. | S |
| B-10 | **Measure on device** | Trie memory (HashMap per node), cold-start load time and suggestion latency are only unit-tested, not profiled on a real device. Add a benchmark and record numbers. | M |
| B-11 | **Instrumented tests** | Room DAO queries (`getLearnedPrefixSuggestions`, recents) and IME `InputConnection` behaviour have no `androidTest` coverage. | M |
| B-12 | **Use Room's recency and prefix queries** | `getRecentlyUsedWords` / `getLearnedPrefixSuggestions` exist but are unused, so "recently used words" is stored but not yet influencing ranking. | M |
| B-13 | **Dictionary asset test as Gradle input** | Changing `dictionary.txt` does not re-run unit tests (asset is not a declared task input). Declare it as an input. | S |
| B-14 | **Kapt to KSP** | Kapt falls back to language version 1.9 with Kotlin 2.0. Migrate Room to KSP. | S |
| B-15 | **Learning management UI** | No way to view, delete or export learned words, or reset learning (privacy). | M |
| B-16 | **Theme follows system and user choice** | Theme only follows system night mode at input start. Add a manual toggle. | S |
| B-17 | **Accessibility and haptics settings** | Key content descriptions, TalkBack, optional haptics and key-popup preview. | M |
| B-18 | **Emoji key is a placeholder** | Currently inserts a single smiley. | L |
| B-19 | **Long-press popup UI** | Long-press commits the alternate immediately, with no popup showing choices (e.g. `e` → `é è ê`). | M |
| B-20 | **Password / URL field behaviour** | Suppress suggestions and learning in password, email and URL fields; respect `no suggestions` flags. | S |

## P3 – Extensibility (from the original brief)

| ID | Item | Notes | Size |
|----|------|-------|------|
| B-21 | **Compact dictionary structure** (DAWG / flat-array Trie) | Needed before growing the vocabulary well beyond ~70k words. | L |
| B-22 | **Multilingual dictionaries** | Per-locale assets and Tries, subtype switching via `method.xml`. | L |
| B-23 | **Swipe (glide) typing** | Path to key-sequence matching against the Trie. | L |
| B-24 | **Next-word prediction** (bigram table, no ML) | Suggest after a space using a frequency table. | L |
| B-25 | **Pluggable suggestion pipeline** | Interface so engines (autocomplete, autocorrect, bigram) can be added or reordered. | M |

## Known issues / tech debt

- Git history was rewritten so each commit builds. A pre-rewrite copy is kept in the `backup-history` branch; delete it once reviewed.
- `Trie` uses `@Synchronized` on every call; contention is untested.
- Fallback `fallbackToDestructiveMigration()` on Room would wipe learned words on a schema change. Replace with real migrations before release.
- `MainActivity` polls Room every 1.5 s for stats; replace with a Flow.
- Release build has minification disabled.

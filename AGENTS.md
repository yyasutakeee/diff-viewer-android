# Repository Instructions

Before planning, implementing, reviewing, or documenting changes in this repository, read completely:

- `/storage/emulated/0/CodexRules/AGENTS.md`
- `/storage/emulated/0/CodexRules/android/AGENTS.md`
- `docs/product-overview.md`

Treat `docs/product-overview.md` as the authoritative description of the product goal and agreed initial scope.
Do not silently broaden the product beyond that scope. Record newly agreed product decisions in the document.

This project is an Android Git diff viewer for reviewing changes made from Termux, including Codex edits. The
Android app displays changes; Git remains the source of truth in Termux.

## Repository architecture

- `:app` is the composition root. It creates concrete repositories and the single `class AppStore`, owns screen
  navigation, and contains the concrete domain-to-feature view-model adapters.
- `:feature:repository` owns the repository summary and changed-file list screen, its `interface RepositoryViewModel`,
  `sealed interface RepositoryEvent`, and display-only UI state.
- `:feature:filediff` owns the full file-diff screen, its `interface FileDiffViewModel`, `sealed interface FileDiffEvent`,
  and display-only UI state.
- `:feature:alldiffs` owns the full, continuously scrollable all-files diff screen, its
  `interface AllDiffsViewModel`, `sealed interface AllDiffsEvent`, and display-only UI state.
- `:core:domain` owns `data class AppState`, `class AppStore`, Git-diff domain values, and I/O repository interfaces.
- `:core:data` implements Termux HTTP/JSON access and private-preference storage behind domain-owned interfaces.
- `:core:designsystem` owns the Compose theme and reusable diff colors.
- `:core:diffui` owns presentation-only diff display values and reusable line, hunk, file, wrapping, and font-size
  controls shared by the single-file and all-files feature screens. It must not contain domain records or I/O.

Do not move HTTP, JSON, SharedPreferences, or domain records into a feature module. Do not let a feature depend on
`:app`, `:core:domain`, `:core:data`, or another feature. A diff-rendering feature may depend on `:core:diffui`;
other feature dependencies remain limited to `:core:designsystem`. All feature events pass through the feature's
single `send(event)` surface, and all shared-state changes pass through verb-named `AppStore` actions.

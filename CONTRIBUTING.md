# Contributing to Daisychain

Thanks for helping! Daisychain is a young project: bug reports, translations, new blocks and plugins are all welcome.

## Report a bug or suggest something

Open an issue and pick the template. For a bug, say which phone and Android version you use, which blocks the shortcut
contains and what you expected. A screenshot of the shortcut helps a lot.

## Add or fix a block

Blocks are small Kotlin classes found automatically. Read [docs/ACTIONS.md](docs/ACTIONS.md): one class, one
`@AutoService(ActionDefinition::class)` annotation, and it shows up in the picker.

* Keep blocks **generic** (Scratch-like): prefer a flexible block to a very specific one.
* Show the input and the output of the block clearly; add a one-sentence `help` to non-obvious parameters.
* Use Apple's identifiers and parameter keys when the block exists in Apple Shortcuts, so import/export keeps working.
* Every text a user can see needs all five languages (see below).

## Translations

Interface texts live in `app/src/main/res/values-*/strings.xml` (English, Français, Italiano, Deutsch, Español).
Block names, parameters and messages written with `t("English", "Français")` get their Italian, German and Spanish
versions from `engine/spec/Translations.kt`, found by the English text. A unit test (`TranslationsTest`) fails when a
text has no translation, so you cannot forget one.

## Write a plugin

No code needed: see [docs/PLUGINS.md](docs/PLUGINS.md) and the examples in `docs/examples/`.

## Before sending a pull request

```bash
./gradlew assembleDebug testDebugUnitTest
```

Both must pass. Try the change on a real phone when it touches the editor or the device (the unit tests cannot).
By contributing you agree that your work is released under the project's license (GPL-3.0-or-later).

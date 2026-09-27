# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android app. Production Java code is in
`app/src/main/java/com/cookie/securenotes`, organized by responsibility:
`ui/` for activities, fragments, adapters, and view models; `data/local/` for
Room entities/DAOs, encrypted preferences, and storage; `security/` for
cryptography; `session/` for locking; and `manager/repository/` for data access.
XML layouts, drawables, strings, and configuration resources live in
`app/src/main/res`. Keep user-facing strings in `res/values/strings.xml` (and
the Italian variant in `values-it/`). Architecture notes are under `docs/` and
are best viewed with Obsidian.

## Build, Test, and Development Commands

Run commands from the repository root using the Gradle wrapper:

- `./gradlew assembleDebug` builds an installable debug APK.
- `./gradlew test` runs JVM unit tests in `app/src/test`.
- `./gradlew connectedAndroidTest` runs device/emulator tests in
  `app/src/androidTest`; start an emulator or connect a device first.
- `./gradlew clean` removes generated build outputs before a clean rebuild.

## Coding Style & Naming Conventions

Write Java 11-compatible code, using four-space indentation and Android Studio
formatting. Use `PascalCase` for classes (`NoteViewModel`), `camelCase` for
methods and fields (`loadRecentNotes`), and descriptive resource names with
lowercase underscores (`activity_note_editor.xml`, `buttonNotes`). Keep package
placement aligned with responsibility. New UI behavior should keep Activities
thin and place state/data operations in ViewModels and repositories.

## Testing Guidelines

Use JUnit 4 for local tests and AndroidX JUnit/Espresso for instrumentation
tests. Name test classes `*Test` and methods for the behavior under test, such
as `loadRecentNotes_returnsNewestFirst`. Add a focused unit test with repository,
session, or crypto behavior changes; use instrumentation tests for Android
framework, database, or UI interactions. No coverage threshold is configured.

## Commit & Pull Request Guidelines

Recent history uses concise Conventional Commit-style subjects such as
`feat: added the archive system`, `fix: ...`, `docs: ...`, and `chore: ...`.
Follow `type: imperative summary`; keep each commit focused. PRs should explain
the user-visible or security impact, link the relevant issue when available,
list validation commands, and include screenshots for layout or viewer changes.
Never commit keys, plaintext user notes, or generated build artifacts.

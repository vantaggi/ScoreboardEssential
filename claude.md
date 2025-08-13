# Scoreboard Flutter Project Guidelines

This document provides a set of guidelines for AI assistants interacting with the Scoreboard Flutter repository. Adhering to these rules is mandatory to ensure code quality, consistency, and alignment with the project's vision.

## 1. Project Mission & Vision

**Mission: "Professionalize the Passion"**

The app's goal is to elevate the amateur sports experience. It's not just a scoreboard; it's a tool to organize, track, and create a memorable history of informal matches. All features and suggestions should align with this core mission.

**Target UI/UX: "Digital Stadium"**

The visual identity is inspired by modern, luminous stadium scoreboards. It must be energetic, clear, and highly functional.

- **Style System:** **Material 3 Expressive** is the required design system.
- **Default Theme:** **Dark Mode is the primary and default theme.** All UI components must be designed and implemented for a dark, high-contrast aesthetic first.
- **Color Palette:**
    - **Background (`Surface`):** Dark Gray (`#1A1B1E`)
    - **Primary Action (`Action Blue`):** Vibrant Blue (`#4FC3F7`)
    - **Team 1 (`Vibrant Orange`):** Energetic Orange (`#FFA726`)
    - **Team 2 (`Electric Lime`):** Electric Lime (`#AEEA00`)
- **Typography:** Use **Roboto Condensed (Bold)** for all scores and timers to maximize impact and readability.

## 2. Tech Stack & Architecture

- **Framework:** Flutter
- **Language:** Dart
- **State Management:** **BLoC (Business Logic Component)** is the standard for state management. Ensure a clear separation between UI (Widgets) and business logic (Blocs/Cubits).
- **Database:** **Drift (Moor)** for local persistence, built on top of `sqflite`.
- **Code Quality:** Use the official Flutter Linter rules (`flutter_lints`).

## 3. Core Instructions for the AI Assistant

### When Writing Code:

1.  **Language:** All code, comments, logs, and documentation **must be written in English.**
2.  **Architecture:** Strictly adhere to the **BLoC pattern**. Events are sent from the UI to the Bloc, which processes them and emits new states. Widgets rebuild based on these states.
3.  **UI Implementation:**
    - All new widgets must implement the **Material 3 Expressive** style.
    - Colors must be sourced from the defined "Digital Stadium" palette. Do not use hardcoded or generic colors.
    - Widgets should be as stateless as possible.
4.  **Error Handling:** Implement clear and robust error handling within Blocs, emitting specific error states that the UI can display gracefully.
5.  **Immutability:** States emitted by Blocs must be immutable.

### When Analyzing or Reviewing Code:

- Your primary focus should be on verifying adherence to the guidelines above.
- Check for correct implementation of the BLoC pattern.
- Verify that the UI/UX matches the "Digital Stadium" theme and correctly uses the color palette.
- Identify any business logic present in the UI layer and suggest refactoring it into a Bloc.

### When Suggesting New Features:

- Propose features that align with the mission of "Professionalizing the Passion."
- Good examples: advanced player statistics, match history timelines, shareable match summary cards.
- Bad examples: generic social media integrations, mini-games, features unrelated to sports tracking.

## 4. Project Commands

- **Install dependencies:**
  ```bash
  flutter pub get

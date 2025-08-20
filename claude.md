# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## High-Level Architecture

This is a native Android application written in Kotlin. The project follows a multi-module architecture:

*   **`mobile`**: The main Android application module for handheld devices. It uses:
    *   **UI**: Android Views with Data Binding.
    *   **Lifecycle**: `ViewModel` and `LiveData` for managing UI-related data in a lifecycle-conscious way.
    *   **Database**: Room for local data persistence.
    *   **Wear OS Communication**: Google Play Services for Wearable to sync data with the `wear` module.
*   **`wear`**: The Wear OS application module for wearable devices.
*   **`shared`**: A common library module containing shared logic for data synchronization between the `mobile` and `wear` modules, utilizing the Google Play Services Wearable Data Layer API.

The `README.md` file is written in Italian and provides more details on the specific features implemented.

## Common Development Commands

This is a standard Gradle-based Android project. Here are the common commands to be run from the root directory:

*   **Build the entire project**:
    ```bash
    ./gradlew build
    ```

*   **Run checks on the mobile app**:
    ```bash
    ./gradlew :mobile:check
    ```

*   **Assemble the mobile debug APK**:
    ```bash
    ./gradlew :mobile:assembleDebug
    ```

*   **Install the mobile debug app on a connected device/emulator**:
    ```bash
    ./gradlew :mobile:installDebug
    ```

*   **Run unit tests for the mobile module**:
    ```bash
    ./gradlew :mobile:testDebugUnitTest
    ```

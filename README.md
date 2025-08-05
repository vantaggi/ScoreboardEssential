# ScoreboardEssential

ScoreboardEssential is a simple and intuitive Android and Wear OS application for keeping track of scores in a football game. The app allows you to easily increment and decrement scores for two teams, and it features a synchronized timer on both the mobile and wearable devices.

## Features

*   **Real-time Score-keeping:** Easily add or subtract points for each team.
*   **Synchronized Timer:** A countdown timer that is synchronized between your mobile and Wear OS devices.
*   **Two-way Data Sync:** Changes made on either the mobile or the wearable device are instantly reflected on the other.
*   **Simple and Clean UI:** A straightforward and easy-to-use interface.

## How to Use

1.  Install the application on your Android smartphone and your Wear OS watch.
2.  Open the app on either device to start keeping score.
3.  Use the `+` and `-` buttons to adjust the scores for each team.
4.  Use the timer controls to start, stop, and reset the game timer.
5.  The scores and timer will automatically stay in sync across both devices.

## Project Structure

The project is divided into three modules:

*   `mobile`: The Android application for handheld devices.
*   `wear`: The Wear OS application for wearable devices.
*   `shared`: A common module that contains the data synchronization logic used by both the `mobile` and `wear` modules.

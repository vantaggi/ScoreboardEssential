# ScoreboardEssential

ScoreboardEssential is a simple and intuitive application for Android and Wear OS designed to keep track of scores in a soccer match. The app allows you to easily increase and decrease the scores for two teams and features a synchronized timer across both mobile and smartwatch devices. This project aims to "Professionalize the Passion" by providing a robust tool for amateur sports.

## Project Architecture

The project is built with a multi-module architecture, ensuring a clean separation of concerns and promoting code reusability.

*   `mobile`: The main Android application for handheld devices.
*   `wear`: The Wear OS application for wearable devices.
*   `shared`: A common library module containing the data synchronization logic used by both the `mobile` and `wear` modules, leveraging the Google Play Services Wearable Data Layer API.

## Tech Stack

*   **UI**: Android Views with Data Binding
*   **Lifecycle**: `ViewModel` and `LiveData`
*   **Database**: Room
*   **Communication**: Google Play Services for Wearables

## Implemented Features

The following features have been implemented and are fully functional:

*   **Score Management:** Add or subtract points for each team.
*   **Custom Team Names:** Users can set custom names for the two teams.
*   **Match Timer:** A main timer for the match that can be started, stopped, and reset.
*   **Goalkeeper Timer:** A secondary timer, useful for goalkeeper rotation, which shows an alert when the time expires.
*   **Player Management:**
    *   Create new players with a name and role.
    *   Assign players to teams.
    *   Remove players from teams.
*   **Match History:** Match results are saved and can be viewed on a dedicated screen.
*   **Mobile -> Wear Sync:** Changes made on the mobile device (scores, names, timers) are reflected on the smartwatch.
*   **Basic Wear OS Controls:** The smartwatch app allows for score modifications and interaction with the goalkeeper timer.
*   **Wear -> Mobile Sync:** Full two-way synchronization is enabled. Changes made on the wearable, such as score updates or timer interactions, are now reflected on the mobile app.
*   **Team Color Selection:** Customize the color for each team. Long-press on a team's card to open the color picker and choose a new color.

## Work in Progress / Missing Features

*   **Player Roles:** It is possible to assign "roles" to players, but this information is not currently used or displayed anywhere in the application.
*   **Backup Configuration:** The rules for automatic data backup on Android have not yet been defined.
*   **Package Name:** The application's package name (`com.example.scoreboardessential`) is the default and should be changed for a production release.

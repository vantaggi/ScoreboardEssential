# Privacy Policy - ScoreboardEssential

**Last updated:** October 15, 2026

This Privacy Policy explains what information ScoreboardEssential ("we", "us", or "our") handles,
where it goes and how you can delete it, when you use the phone app and its companion Wear OS app
(collectively, the "App").

**In short.** The App works without an account and without an internet connection, and in that use
nothing leaves your phone and watch. Sending matches to **Padel Elite** (a padel group dashboard)
is optional: it exists only in builds configured for it, works only after you sign in with your
Padel Elite account, and sends a match only when you ask. The App shows no ads and contains no
analytics, crash-reporting or tracking libraries.

## 1. Using the App without Padel Elite

This is how the App works for everyone who does not sign in to Padel Elite, and it is unchanged.

### Data stored on your device
The App stores, in a local database (Room) and in private app preferences on your phone: matches
and their scores, the point-by-point log, team names and colors, players and their roles, rosters,
statistics, match history and your settings. This data is not sent to any server.

### Phone and watch
The phone and the watch exchange the current match (scores, timers, team names and colors, the
roster's player names, the scorer you pick on the watch) through the Google Play Services Wearable
Data Layer. The App does not send this data to any server of its own. The watch app has no internet
permission.

### Exporting and sharing
When you choose to export or share a match report (a JSON file or a PDF), the App hands the file to
the Android share sheet and it goes only to the app or person you pick. The App prepares these files
in its own private storage and does not request broad storage permissions.

### Permissions
- **Notifications** (`POST_NOTIFICATIONS`): the ongoing notifications of the match timer and, with
  Padel Elite, of the live broadcast.
- **Foreground services**: the match timer (`specialUse`) and the Padel Elite live broadcast
  (`dataSync`, see section 2.3).
- **Vibration** and **wake lock**: alerts and the timer.
- **Internet** (phone only): used only by the optional Padel Elite features below. Until you sign
  in to Padel Elite the App makes no network requests of its own.
- The App does **not** access your camera, microphone, location or contacts.

## 2. Padel Elite (optional)

### 2.1 When it applies
Padel Elite controls appear only in builds that include the address of the Padel Elite server, and
only for padel matches. Everything in section 1 keeps working without signing in. The Padel Elite
account is created in the Padel Elite dashboard, not in the App.

### 2.2 Signing in
- **What is sent:** your email address and password, to Padel Elite's sign-in service (Supabase
  Auth), over an encrypted (HTTPS) connection.
- **What stays on the phone:** the session returned by the server (access and refresh tokens, your
  account ID and your email address) is stored encrypted (AES-256-GCM, with a key held in the
  Android Keystore that cannot be exported) and is excluded from Android cloud backups. Your
  password is not stored. The App also stores the group you choose and your role in it (owner,
  admin or member).

### 2.3 What is sent, and when
Match data leaves the phone **only after a command from you**:

| Your action | What is sent | Where |
|---|---|---|
| **Send to Padel Elite** on a finished padel match in the history, **Also send to Padel Elite** in the end-of-match dialog (unchecked by default), or **Send again** | The match file (below), complete | The inbox of the group you chose (for "Send again", the group of the earlier send). Without a network the send waits in a queue on the phone and leaves by itself when the network is back. |
| **Broadcast live** switch (visible only to owners and admins of the chosen group) | The match file so far, after every change (a point or an undo, also from the watch, or a change of players), at most once per second; at the end of the match the final file, which closes the broadcast. Discarding the match or stopping the broadcast closes it without a file. | The group's live section. While it is on, an ongoing notification shows the group and the score and lets you stop it. |

**The match file** contains: a random match ID; the date and time the match started, with your
time-zone offset; the app version; the match rules (scoring mode, sets, tie-break, serve order);
the four players' **names as you typed them in the App** and their side; for players you linked to
the group (section 2.4), their Padel Elite player ID; the score, set scores and winner; and the
**point by point**: which side won each point, who was serving and the time since the start. It
does not contain team names or colors, other matches, or matches of other sports.

**Without match data**, while you are signed in the App also contacts Padel Elite by itself to:
renew the session token; read your groups and your role once per app launch; check the status of
matches you sent (waiting, imported, discarded) when you open the history; retry a queued send or
the closing of a live broadcast.

### 2.4 Players of the group
With a group chosen, the App downloads the list of the group's players (Padel Elite ID and name) so
that you can link them to the players in the App. The links (and the names downloaded with them)
are stored on the phone. A link is made only by you (a proposal for identical names is applied only
when you confirm it), and it is used only in what you send to that group.

### 2.5 Where the data goes
To the Padel Elite database, hosted by Supabase (supabase.com), over HTTPS.

### 2.6 Who can see it
According to the rules of the Padel Elite dashboard:
- A match in the **inbox** is visible to you (the sender) and to the owners and admins of the group.
  An admin imports it, choosing the group's players, or discards it.
- An **imported** match becomes a regular match of the group, with its point-by-point chronicle, and
  is visible like the group's other matches: to the group's members and, as the dashboard is
  configured today, to anyone who has the group's public link.
- A **live broadcast** is visible in the group's live section of the dashboard.

### 2.7 Deleting your data
On the phone:
- **Sign out** (in Settings or on the Padel Elite screen) removes the session and the chosen group
  from the phone. It does not delete what was already sent. The status of sent matches and the
  player links stay on the phone, without being used, until you sign in again, uninstall the App or
  clear its data in Android settings.
- **Unlink** removes the link between a group player and a player in the App; deleting a player in
  the App also removes their links.
- **Uninstalling** the App, or clearing its data in Android settings, deletes everything the App
  stored, including the encryption key of the session.

On Padel Elite:
- A group admin can discard a match in the inbox, and delete an imported match from the group's
  history in the dashboard (its chronicle is deleted with it).
- A live broadcast that is not closed is closed by the dashboard within 6 hours.
- To delete your Padel Elite account, or the data you sent, write to the contact address below.

## 3. What the App does not do
- No advertising, no analytics, no crash reporting, no tracking: the App contains no third-party
  libraries for these purposes.
- No sale or sharing of your data. Match data reaches the members of a Padel Elite group only
  because you send it to that group.
- Diagnostic messages are written only to the device's system log, which the App does not send
  anywhere.

## 4. Backups
Android's automatic cloud backup is disabled for the phone app (`allowBackup="false"`). The Padel
Elite session is also explicitly excluded from cloud backups, and it cannot be decrypted on another
device: its key never leaves the phone's Keystore.

## 5. Security
Connections to Padel Elite use HTTPS, and the session is encrypted on the phone as described above.
No method of electronic storage or transmission is 100% secure.

## 6. Changes to This Privacy Policy
We may update this Privacy Policy from time to time. We will post the new version on this page and
update the date above.

## 7. Contact Us
For questions about this Privacy Policy, or to ask for the deletion of your Padel Elite data, write
to **mattiavantaggi@gmail.com**.

> **Note for publishing:** Google Play requires the privacy policy to be reachable at a public URL.
> Host this document (e.g. via GitHub Pages) and enter that URL in the Play Console listing.
> Keep the answers of the Play Console "Data safety" form consistent with this page
> (`RELEASE_CHECKLIST.md`, "Play Console: Sicurezza dei dati").

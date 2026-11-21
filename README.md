# Back to TV

A tiny home screen for an Android TV box (built for an NVIDIA SHIELD) that sits behind an LG TV running
[CleanYourTV](https://github.com/cleanyourtv). When you back out of an app on the box, Android returns to its home screen,
which is this app, and it sends the TV back to **its** Home instead of leaving you on the box's launcher.

```
Netflix on the box ──Back──► Back to TV (box's Home) ──HTTP──► CleanYourTV on the TV ──► TV Home
```

## How it decides

- **App closed** (Home appears while the box has been awake for 15 s or more): asks the TV what it's showing, and if it's
  still the box's input (`com.webos.app.hdmi1` by default) it sends the TV Home. If you've already switched the TV to
  something else, it leaves it alone.
- **Box just woke** (within 15 s): does nothing to the TV. That's the TV turning on, you choosing the box's input, or
  CleanYourTV starting an app on the box.
- **Using the box directly:** the screen shows "OK: apps on this box". OK opens your usual launcher (FLauncher, Google
  TV's, ...); apps started from there come back to it. Pressing Home on the box's own remote still sends the TV Home.

It talks to the TV through CleanYourTV's MCP server (Features > AI agents (MCP) on the TV), with its own pairing token,
kept in the app's private storage. It uses the TV's `get_current_app` and `navigate` tools only. The token is refreshed by
use; if the TV drops it (HTTP 401), pair again.

## Build and install

Needs JDK 17+ and the Android SDK (compileSdk 36).

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Set up

1. On the TV: CleanYourTV manager > Features > AI agents (MCP) > Connect an agent. Note the code.
2. Pair (or open "Back to TV" on the box and fill in the form):
   ```sh
   adb shell am start -n io.github.robhannan.backtotv/.SetupActivity --es host <tv-ip> --es code <code>
   ```
3. Make it the box's Home:
   ```sh
   adb shell cmd package set-home-activity io.github.robhannan.backtotv/.HomeActivity
   ```

To undo, set your old launcher back (for example `me.efesser.flauncher/.MainActivity`) or uninstall the app.

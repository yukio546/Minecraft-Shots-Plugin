# Testing

## Plugin

```sh
./gradlew clean test build
```

The unit suite covers counting, overflow and invalid input, saved player data,
feedback preferences, font components, PvP world settings, effect thresholds,
and rule persistence. Results are written to `build/reports/tests/test/`.

GitHub Actions builds the plugin and resource pack on Linux and Windows. The
workflow must run in the published repository before its status can be treated
as a passing hosted build.

## Optional Minecraft font check

Build the pack first, then run with Java 25:

```sh
python3 scripts/build_resource_pack.py
cd client-verification
./gradlew runClientGameTest
```

This launches an isolated Minecraft 26.2 client. It does not connect to a server
or use an existing launcher profile. A graphical desktop is required; the
standard CI workflow does not run this check. Screenshots are written to
`verification/` at GUI scales 1, 2, and 3.

The test checks all 94 bitmap glyphs, integer spacing, Unicode fallback,
unchanged vanilla text widths, mixed username/counter lines, and clipping.
Inspect the screenshots as well as the assertion output.

To add a selected capture to your local HTML preview:

```sh
python3 scripts/build_resource_pack.py --proof verification/minecraft-five-1.1-scale-2.png
```

## Server smoke test

Use a disposable test server and test players for gameplay checks. Verify a
normal Survival hit lowers health and adds one; pausing must stop the count
while damage continues. Check completion, reset confirmation, and saved counts
after restarting.

With two test players, check PvP on and off for melee and projectile hits in
all loaded worlds. Environmental damage must still work with PvP off.

Add an effect rule, enable effects, and cross its threshold. Check completed
and remaining counters separately, including a jump past a threshold, falling
below and crossing again, and removal or disabling before the next tick.
Existing effects should expire normally when rules are disabled. Check that
joining or restarting does not replay a threshold that was already reached.

Unit and configuration checks do not replace those joined-player tests.

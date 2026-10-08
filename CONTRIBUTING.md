# Contributing

Use JDK 25 and the checked-in Gradle wrapper:

```sh
./gradlew clean test build
python3 scripts/build_resource_pack.py
```

On Windows, use `gradlew.bat` and `py -3`.

Keep changes focused. If you change counting, persistence, permissions, or effect
triggers, add a test for the behavior that could break. Font changes also need
the optional client check described in [docs/TESTING.md](docs/TESTING.md).

A pull request should explain what changes for players and which checks you ran.
For a bug report, include a short reproduction, plugin and Paper versions, and
relevant error lines. Remove addresses, player records, tokens, and unrelated
log output before attaching anything.

Development outputs belong in `build/` or `dist/`. Do not commit server
configuration, player data, deployment backups, or local game profiles.

## Meteor Raid Addon

[![Build](https://github.com/co1acraft/ad/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/co1acraft/ad/actions/workflows/build.yml)

An addon for the Meteor Client that:
1. Enables KillAura (configured to attack monsters only) on load.
2. Enables AutoEat and AutoTotem on load.
3. After a raid victory (detected via Hero of the Village effect), waits ~1s and attempts to drink an Ominous Bottle automatically.

### Building

Ensure you have the Gradle wrapper or a local Gradle installation (Gradle 8+ recommended). If you need a wrapper run:

```bash
gradle wrapper
```

Then build:

```bash
./gradlew build
```

The resulting jar will be in `build/libs/`.

### CI & Releases

- CI builds run on every push/PR to `main` via GitHub Actions.
- Download artifacts from each run under the "Artifacts" section.
- Tag a release (e.g., `v0.1.1`) to auto-publish a GitHub Release with the built jar attached and a generated changelog.
- The project version is derived from the tag and Minecraft version: `mod_version = <tag-without-v>-<minecraft_version>`.
- Example:

```bash
git tag v0.1.1
git push origin v0.1.1
```

### Version Configuration

Adjust `gradle.properties` for (current target 1.21.8):

```
minecraft_version=1.21.8
fabric_loader_version=0.16.7
meteor_version=0.5.5
```

Pick a `meteor_version` that exists in either the releases or snapshots repositories:
- Releases: https://maven.meteordev.org/#/releases/meteordevelopment/meteor-client/
- Snapshots: https://maven.meteordev.org/#/snapshots/meteordevelopment/meteor-client/

If 1.21.8 mappings or dependencies fail to resolve, verify that Fabric Loader and Meteor versions listed actually publish artifacts for that exact patch; you may need to downgrade to the latest supported 1.21.x patch (e.g. 1.21.1) if 1.21.8 is not yet available.

If a newer version is available, update the property accordingly.

### Installing

Place the built addon jar inside your `.minecraft/mods` folder alongside the Meteor Client jar.

### Notes

- If KillAura setting field names differ between Meteor versions you may need to adapt the code in `RaidHelperAddon.java` (players/animals/monsters toggles).
- Ominous Bottle interaction relies on standard right-click; ensure item exists in inventory.
- Raid detection uses presence of the Hero of the Village status effect; adjust logic if you need more precise server-side raid state checks.

### Disclaimer

This addon automates combat and item usage. Use responsibly and in accordance with server rules.
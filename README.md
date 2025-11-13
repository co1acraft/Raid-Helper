## Meteor Raid Addon

[![Build](https://github.com/co1acraft/ad/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/co1acraft/ad/actions/workflows/build.yml)

A Meteor Client addon that automates post-raid actions and enables helpful combat/survival modules.

**Features:**
- **Auto-enable modules:** Optionally enables KillAura (monsters only) and AutoEat when the addon activates.
- **Flexible raid detection:** Triggers on Hero of the Village effect (rising edge), raid bossbar disappearance, or victory keywords in bossbar text.
- **Automatic Ominous Bottle drinking:** After raid victory, automatically drinks an Ominous Bottle with configurable delay, persistent retry logic, and forced right-click hold to ensure full consumption.
- **Smart Raid Omen handling:** Stops attempting to drink if Raid Omen is already active (configurable).
- **Hotbar restore:** Automatically switches back to your previous hotbar slot after a successful drink.
- **Debug logging:** All chat announcements are gated behind a debug-logs setting for silent operation.

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

### Configuration

The addon adds a **Raid Helper** module under the **Misc** category in Meteor Client. All features are configurable via module settings:

**Module Settings:**
- `auto-enable-killaura` (default: true) — Enable KillAura on module activation
- `auto-enable-autoeat` (default: true) — Enable AutoEat on module activation
- `drink-ominous-bottle` (default: true) — Automatically drink Ominous Bottle after raid victory
- `trigger-on-hero-effect` (default: true) — Trigger on Hero of the Village rising edge
- `trigger-on-raid-bossbar-end` (default: true) — Trigger when raid bossbar disappears or shows victory
- `post-raid-delay-ticks` (default: 20) — Delay in ticks before drinking
- `require-hero-with-victory` (default: true) — Require Hero effect when using bossbar triggers (strict mode)
- `victory-keywords` (default: "victory, won") — Comma-separated keywords for bossbar victory detection
- `retry-drink` (default: true) — Retry starting the drink until animation begins
- `max-drink-attempt-ticks` (default: 40) — Maximum ticks to retry initiating bottle use
- `retry-attempt-interval-ticks` (default: 5) — Ticks to wait between each retry attempt
- `force-hold-use` (default: true) — Force holding right-click while drinking to ensure completion
- `stop-when-has-raid-omen` (default: true) — Stop attempting to drink if Raid Omen is already active
- `debug-logs` (default: false) — Enable chat announcements for trigger events and drinking progress

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

- **Module location:** Find "Raid Helper" under the Misc category in Meteor Client.
- **Raid detection:** Multiple trigger methods (Hero effect, bossbar disappearance, victory keywords) ensure reliable detection across server configurations.
- **Drinking reliability:** Persistent retry logic with configurable intervals and forced key hold ensures bottles are fully consumed even under lag or timing issues.
- **Hotbar restore:** After a successful drink when Raid Omen is active, automatically switches back to your previous hotbar slot.
- **Silent operation:** All chat messages are gated behind `debug-logs` setting — enable only when troubleshooting.

### Disclaimer

This addon automates combat and item usage. Use responsibly and in accordance with server rules.
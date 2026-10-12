# Sparkling Mutuals

A Fabric mod for Hypixel SkyBlock Safari, sparkling discoveries, and Miria contests.

## Features

- **Safari modes:** Full Clear, Unique and Sparkling, with progress, missing-species and capture panels.
- **Party commands:** shared/missing sparkling discoveries, Safari tickets, command help and personal-best lookups.
- **Personal bests:** separate Full Clear and Unique biome records, solo/duo Doomspiral and Wumpa records, and observed party boss times.
- **ESP and helpers:** critters, floor supplies, nests, mounds, walls, party teammates, hidden Litterbugs and Shyworm paths/countdowns.
- **Alerts:** sparkling discoveries, inventory readiness, run completion, party warp reminders and Miria contest warnings.
- **Quality of life:** Hideyho quest clicks, mound-only Auto Clicker, red candle hitboxes, painting/darkness removal, capture chat filtering and capsule hiding.
- **Customization:** movable/resizable HUDs and alerts, configurable colors, backgrounds, sounds and per-biome ESP visibility.

## Commands

- `/sm`: settings and command help; `/sm gui`: HUD editor; `/sm debug`: local tracking diagnostics.
- `/fc`, `/unique`, `/sparkle`: select a Safari mode.
- `/captures`: current or last run's capture counts.
- `/apikey <key>`: set your Hypixel API key.
- `/alert`, `/alertdelay <seconds>`: configure warp reminders.
- Party chat: `!mutual`, `!missing <IGN>`, `!tickets <IGN>`, `!commands` and `!pb`. See the settings help for mode-specific PB targets and solo/duo options.

## Installation

Download the mod JAR from [Releases](https://github.com/johnceoskyblock/sparkling-mutuals/releases) and place it in your `mods` folder. Requires Minecraft 26.1.2, Java 25, Fabric Loader 0.19.5 or newer, Fabric API, and Fabric Language Kotlin.

Development builds are available from successful [Actions](https://github.com/johnceoskyblock/sparkling-mutuals/actions) runs: download the **Artifacts** ZIP and extract the JAR without `-sources` in its name.

Player lookups require a [Hypixel API key](https://developer.hypixel.net), entered through settings or `/apikey <key>`.

Release notes are available in each [release description](https://github.com/johnceoskyblock/sparkling-mutuals/releases).

## Credits

Includes work adapted from [CritterMod v0.9.0](https://github.com/MrCloudy2/critterMod/tree/v0.9.0), [ShinyHunter](https://github.com/javabetter/ShinyHunter), [Nebulune](https://github.com/Gaeritag/Nebulune), and [SkyHanni 9.1.0](https://github.com/hannibal002/SkyHanni/tree/9.1.0). Settings use [MoulConfig](https://github.com/NotEnoughUpdates/MoulConfig). See [LICENSE](LICENSE) and [third-party notices](licenses) for licensing and attribution.

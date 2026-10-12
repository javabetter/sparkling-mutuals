# Sparkling Mutuals

A Fabric mod for Hypixel SkyBlock Safari, sparkling discoveries, and Miria contests.

## Safari tracking

- **Full Clear mode (`/fc`):** track all required captures and highlight remaining critters.
- **Unique mode (`/unique`):** track one capture per species and highlight missing uniques.
- **Sparkling mode (`/sparkle`):** target sparkling discoveries missing for you or your party.
- **Profitable shard ESP:** keep profitable critters highlighted in Sparkling mode.
- **Progress HUD:** show elapsed time and overall/per-biome completion.
- **Missing panel:** list remaining species, nests, mounds, and Snooper walls.
- **Biome captures HUD:** show species counts and Rockmite mound statistics.
- **Capture review (`/captures`):** view the current or last run's captures.
- **Biome personal bests:** save separate Full Clear and Unique clear times.
- **Party Unique PBs:** save party biome clear times alongside personal Unique records.
- **Boss personal bests:** save solo/duo Doomspiral and Wumpa times, plus observed party records.
- **Boss capture feedback:** show elapsed capture time and the difference from the previous PB.
- **Biome announcements:** send `fd`, `cd`, `id`, or `hd` when eligible biomes are complete.

## Highlights and helpers

- **Critter ESP:** draw colored, slightly enlarged boxes around critters across all four biomes.
- **Floor drop ESP:** highlight needed Safari supplies until their requirements are met.
- **Bee nest waypoints:** locate nests that still need punching.
- **Rockmite highlights:** distinguish mounds from spawned silverfish.
- **Snooper wall waypoints:** locate walls to break for Snoozles.
- **Hidden Litterbugs:** show projected positions and countdowns while they are underground.
- **Shyworm helpers:** show projected positions, movement paths, and rest countdowns.
- **Bird food tracking:** track Forest food collection and feeding progress.
- **Wumpa prerequisites:** retain needed prerequisite critter highlights in Sparkling mode.
- **Nearby sparklings HUD:** list detected sparklings and their distances.
- **Sparkling detection:** highlight nearby sparkling critters in your current biome.
- **Teammate outlines:** highlight loaded party members throughout SkyBlock.
- **Teammate names:** show party member names through walls.
- **Hideyho quest clicks:** accept Hideyho's offer by clicking with chat open.
- **Mound Auto Clicker:** hold the attack button on Rockmite mounds for 12 CPS.
- **Candle hitboxes:** make Haunted candle clicks easier while holding Soothing Incense.
- **Painting removal:** hide paintings in the Haunted biome.
- **Darkness removal:** remove the darkness effect in Safari.
- **Chat filtering:** hide repetitive Safari messages while keeping tracking active.
- **Ground capsule hiding:** hide ordinary dropped capsules in Safari.
- **Flying capsule hiding:** hide capsules near the camera at a configurable distance.

## Alerts and contests

- **Sparkling alerts:** show and sound an alert for each detected sparkling critter.
- **Party sparkling announcements:** share the critter, biome, and coordinates in party chat.
- **All Gems alert:** notify when all three Cavern gem colors are in your inventory.
- **All Bird Food alert:** notify after collecting three of each Forest bird food.
- **All Incense alert:** notify when four Soothing Incense are in your inventory.
- **Run completion alert:** notify when all biomes are complete in the active mode.
- **Warp reminders (`/alert`, `/alertdelay <seconds>`):** remind you to warp party members before entry closes.
- **Miria contest HUD:** track contest time, score, and tier.
- **Miria warnings:** warn below Uncommon at the selected 5-, 3-, and 1-minute marks.
- **Honeyhive beacons:** mark available Torrhus Canyon hives with personal refill countdowns.

## History and achievements

- **Run history (`/sm history`):** browse saved captures, loot shares, splits, bests and run statistics.
- **Sparkling history:** record personal and party finds with ticket counts and dry streaks.
- **Log import (`/sm history import`):** rebuild past runs from Minecraft logs.
- **Achievements (`/sm achievements`):** track Safari milestones, collection progress and playtime.
- **Profit history:** estimate shard and feather values from Bazaar prices, with a Profit tab and coin milestones.

## Party commands and settings

- **`!mutual` / `!mutuals`:** list shared sparkling discoveries that save Safari time.
- **`!missing <IGN>`:** list a player's missing sparkling discoveries that save time.
- **`!tickets <IGN>`:** show a player's Safari ticket counts.
- **`!m [biome]`:** report what remains in the current Safari run.
- **`!dt [reason]` / `r`:** request downtime after the run and release your hold when ready.
- **Safari Manager kick hold:** pause manager interaction and ticket use after a kick request; sneak to override.
- **`/sparkling [IGN|party|IGNs]`:** show Safari player cards or compare sparkling collections.
- **`!pb <biome>`:** share Forest, Cavern, Icy, or Haunted PBs for the active mode.
- **`!pb doom` / `!pb wumpa`:** share solo boss PBs; add `duo` or an IGN for other records.
- **`!commands`:** show the party commands available in the active mode.
- **Settings (`/sm`):** toggle features and open command help.
- **HUD editor (`/sm gui`):** drag HUDs and alerts to move them; scroll to resize.
- **Appearance:** customize critter/waypoint colors, panel borders, backgrounds, and transparency.
- **ESP visibility:** independently restrict each biome and floor drops to your current biome.
- **Alert customization:** choose sounds, volumes, and run completion text.
- **Player lookups:** use your Hypixel key (`/apikey <key>`) or the optional Shiny Hunter shared API.
- **Diagnostics (`/sm debug`):** save local entity tracking details to `latest.log`.

## Installation

- Requires Minecraft **26.1.2**, Java **25**, Fabric Loader **0.19.5+**, Fabric API, and Fabric Language Kotlin.
- Download the JAR from [Releases](https://github.com/johnceoskyblock/sparkling-mutuals/releases) and place it in your `mods` folder.
- Development builds: download the Artifacts ZIP from a successful [Actions run](https://github.com/johnceoskyblock/sparkling-mutuals/actions) and extract the JAR without `-sources` in its name.
- Profile lookups use a [Hypixel API key](https://developer.hypixel.net) or the optional Shiny Hunter shared API.
- Release notes are in the [release descriptions](https://github.com/johnceoskyblock/sparkling-mutuals/releases).

## Credits

- JavaBetter contributed player cards, cloud lookups, party helpers, honeyhives, history, achievements and profit tracking; GamingLegend123 contributed the Shiny Hunter icon.
- Adapted work: [CritterMod v0.9.0](https://github.com/MrCloudy2/critterMod/tree/v0.9.0), [ShinyHunter](https://github.com/javabetter/ShinyHunter), [Nebulune](https://github.com/Gaeritag/Nebulune), and [SkyHanni 9.1.0](https://github.com/hannibal002/SkyHanni/tree/9.1.0).
- Settings: [MoulConfig](https://github.com/NotEnoughUpdates/MoulConfig).
- Licensing and attribution: [LICENSE](LICENSE) and [third-party notices](licenses).

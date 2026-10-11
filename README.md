# Sparkling Mutuals

Unique progress and missing rows require one catch per species. When the party needs sparklings, Honeybug also needs three distinct UUIDs, three punched nests and no remaining nests; Rockmite and Snoozle need cleared mounds/walls; all birds wait for all required food feedings. Unvisited Forest/Cavern special species wait for party fd/cd confirmation. Local Unique clears announce done once; Icy/Haunted stay capture-based. Personal PBs still use personal catches.

- **Teammate ESP:** independent Safari-helper toggles outline loaded party members and show distance-scaled names through walls anywhere in SkyBlock. Both default on; party matching uses UUIDs.

Failed API party commands and discovery refreshes retry after three minutes. Updating the API key through `/apikey` or the settings GUI retries failed work on the next client tick. Party retries are canceled when the connection, roster or Safari mode changes, commands are disabled, or another party client has already answered.

Sparkling Forest birds complete after nine feeder announcements plus any extra bird food held during the run’s first three seconds. A two-Macaw announcement counts as one feeding. Full Clear retains Forest/Haunted floor drops and unpunched nests despite party discoveries or clear claims; Cavern drops remain until all three gems are held together, and intact walls/mounds remain highlighted.

Cavern completion uses species confirmation zones in Full Clear (capture minimums plus no remaining entities) and Sparkling (distinct UUID minimums). Cavernfish/Flitter/Shyworm/Gemzie use 30/40-block Full Clear/Sparkling radii; Driftling/Chuckwalla use 40/60. Centers: Cavernfish (-85,81), Flitter (-84,62), Shyworm (-119,43), Gemzie (-141,51), Driftling (-120,55), Chuckwalla (-100,45). Scrappy needs three UUIDs, plus three captures in Full Clear. Existing mound and wall requirements remain. Sparkling keeps Driftling ESP, hides Rockmite silverfish and Shyworm helpers, and expires other Cavern ESP after ten seconds per UUID.

A Fabric mod for Hypixel SkyBlock Safari, sparkling discoveries, and Miria contests.

Icy zones use 30/40-block Full Clear/Sparkling radii: Tepid, Nozzlenose and Mantis Shrimp (-73,-46); Strongarm (-107,-56); Polaris and Wumpa (-110,-78); Shuddersquid (-127,-48); Billygoat (-121,-55). Troodon needs three distinct UUIDs without a zone, plus three captures in Full Clear. Sparkling uses distinct spawn minimums and ten-second ESP windows; when Wumpa is still needed, prerequisite ESP stays until one capture, including profitable Mantis Shrimp.

## Party commands

- **Mutual sparklings:** `!mutual` finds sparkling discoveries shared by your party.
- **Missing sparklings:** `!missing <IGN>` lists a player's missing discoveries: all species in Full Clear mode or timesaves in Unique and Sparkling modes.
- **Safari tickets:** `!tickets <IGN>` shows a player's ticket counts.
- **Personal bests:** `!pb doom`, `!pb wumpa`, and `!pb forest|cavern|icy|haunted` share each mod user’s saved times. Full Clear returns biome PBs only; Unique returns biome and Doomspiral/Wumpa PBs; Sparkling returns Doomspiral/Wumpa PBs only. Biome records identify their type.
- **Command help:** `!commands` lists available party commands. Settings help also lists local commands. Replies are staggered by UUID, matching results are suppressed, and lookup errors appear only locally.

## Safari tracking

- **Progress HUD:** shows the run timer and collected species, or checked party-needed discoveries in Sparkling mode.
- **Missing panel:** lists uncaught species in Unique, species below their capture minimum or still rendered in Full Clear, and unchecked party-needed discoveries in Sparkling. Remaining nests, mounds and walls follow the selected mode.
- **Capture counts:** tracks catches per critter and Rockmite mound results. `/captures` opens the current or last run's counts. First visiting a biome with earlier party catches and none of your own uses minimum counts and remaining rendered critters to verify party progress.
- **Run mode:** `/fc` selects full clear, Full Clear PBs and all-species lookups. `/unique` selects unique runs, Unique Run PBs and timesaves. Full-clear capture numbers stay white until you visit a biome, then turn green when completion requirements are met. The Modes tab also offers these controls and Sparkling mode.
- **Party boss history:** `!pb doom <IGN>` / `!pb wumpa <IGN>` query saved observed times for past party members. All Doomspiral/Wumpa captures show elapsed time and PB gap locally. Party captures use your run start; your own catches compare solo when eligible, duo otherwise. Observed records do not establish another player’s solo eligibility.
- **PB tracking:** saves your fastest Doomspiral, Wumpa, full-clear, and unique-biome times from the start of a run and announces new records locally. Unique clears require one personal catch of every species; only your own catches qualify for either biome PB type.

## Safari helpers

- **Miria contest HUD:** tracks Miria's contest timer, tier, and score, with configurable end warnings.
- **Warp reminders:** sends title to warp party before afk timeout. Useful for party leeching so that other players can have a chance to get sparkling critters without using a ticket. Use `/alert` and `/alertdelay <seconds>` to control reminders.
- **Sparkling mode:** `/sparkle` highlights your missing sparkling discoveries when solo, or the party's combined missing discoveries. Updates when players join or leave, with optional profitable-shard ESP. Requires an API key.
- **Sparkling checks:** ordinary species are checked near the biome center; spawned species require their nest, mound, wall, food, coin or distinct-entity evidence. In party chat, `fd`, `cd`, `id` or `hd` marks that biome checked for mod users in Sparkling mode; these messages do not change captures or PBs.
- **Critter ESP:** modes select highlights in all four biomes. Unique runs highlight every species until its first catch, then hide collected species; Sparkling gives each needed critter UUID ten seconds of ESP. Optional profitable-shard ESP stays visible. Honeybug, Rockmite and Snoozle checks remain visible while anyone needs their sparkling, or party discovery data is unavailable. Unique and Full Clear keep loaded moving-critter highlights when distant name tags disappear or movement pauses. Captured, removed and unloaded entities still stop highlighting; Sparkling and PB lifecycle checks remain intact.
- **Icy floor drops:** automatic Full Clear highlights stop after one Icebreaker is found or held. Unique and Sparkling keep them hidden.
- **Shyworm helpers:** loaded underground models retain ESP, with a red 1×7 outline on the next clockwise side throughout movement. Rest messages queue for the nearest stationary hidden worm and show an eight-second green/yellow/red countdown, then white “moving soon...” above its saved surface projection. Return-to-start resets and lingering labels preserve the timer; the first observed travel side stays warned throughout rest. Moving ESP follows the head. Outlines use emergence ground levels Y40, Y43 or Y60 independently of head height. Brief corner pauses do not start timers; equal-distance ties wait. Helpers follow the selected mode and Sparkling UUID windows, and direction requires observed movement.
- **Wumpa prerequisites:** Sparkling mode lists and highlights uncaught Icy species while the party needs Wumpa checked, removing each prerequisite after its first catch.
- **Hidden Litterbugs:** projects ESP to their emergence height and shows an estimated countdown while hidden, returning to the real position when they emerge.
- **Floor drop ESP:** hides completed or unneeded drops using discoveries, pickups, inventory and captures. Full Clear Cavern stays visible until all three gems have been held together. Full Clear Cavern stays visible until all three gems have been held together. Unique Icy keeps drops hidden; Haunted keeps coin drops while anyone needs Gimmiegold.
- **Sparkling detection:** highlights nearby sparkling critters and shows their names and distances.
- **Sparkling alerts:** displays a centered title, critter, location and screen flash, with a customizable sound and volume. Can announce discoveries in party chat.
- **Inventory alerts:** shows small movable alerts for all three gem types in Cavern, all nine Forest bird food pickups, or four Soothing Incense in Haunted.
- **Forest birds:** tracks food pickups and feeding, adjusts capture requirements for Macaw pairs and spare Macaws, and includes starting bird food.
- **Bee nests and Snooper walls:** highlights structures that still need checking. Honeybug completion requires punched nests in your own biome; nests stop highlighting when you enter a party-cleared Forest with earlier Honeybug catches.
- **Hideyho quest clicks:** lets you accept the current Hideyho quest prompt by clicking with chat open.
- **Auto Clicker:** repeats attacks at 12 CPS while holding Mouse 0 on Rockmite mounds.
- **Full candle hitbox:** enlarges red candles in Haunted while holding Soothing Incense; white decorative candles keep their normal shape.
- **Painting hider:** hides paintings in the Haunted biome.
- **Darkness removal:** clears the darkness effect in Safari.
- **Capture chat filter:** hides capture-related spam while tracking continues.
- **Capsule hiding:** hides ground capsules and nearby flying capsules.

## Settings and customization

- **Settings:** `/sm` opens feature settings, command help, and API key setup.
- **Troubleshooting:** `/sm debug` saves nearby critter tracking details to your game log.
- **HUD editor:** `/sm gui` lets you move and resize panels, inventory alerts and the warp reminder.
- **ESP visibility:** the ESP tab has independent current-biome filters for floor drops and each biome's critters. Disable a filter to show eligible targets across loaded biomes.
- **Appearance:** customize ESP and waypoint colors, HUD backgrounds, transparency, and borders.

## Installation

Open a successful build in [Actions](https://github.com/johnceoskyblock/sparkling-mutuals/actions), download its **Artifacts** ZIP, and extract the mod JAR into your `mods` folder. Use the JAR without `-sources` in its name. Requires Minecraft 26.1.2, Java 25, Fabric Loader, Fabric API, and Fabric Language Kotlin.

Player lookups require a [Hypixel API key](https://developer.hypixel.net), entered through settings or `/apikey <key>`.

## Credits

Includes work adapted from [CritterMod v0.9.0](https://github.com/MrCloudy2/critterMod/tree/v0.9.0), [ShinyHunter](https://github.com/javabetter/ShinyHunter), [Nebulune](https://github.com/Gaeritag/Nebulune), and [SkyHanni 9.1.0](https://github.com/hannibal002/SkyHanni/tree/9.1.0). Settings use [MoulConfig](https://github.com/NotEnoughUpdates/MoulConfig). See [LICENSE](LICENSE) and [third-party notices](licenses) for licensing and attribution.

Full Clear progress follows the captures panel's completion checks. Party `fd`, `cd`, `id` and `hd` messages complete progress for unvisited biomes only; visiting a biome restores local completion checks.

Issue #19 follow-ups: Sparkling Icy progress uses captures of needed species when Wumpa is needed; a Wumpa capture completes the row. Exact global chamber-opening, darkness-fading and cave-collapse messages check Gemzie, Doomspiral and Wumpa respectively. The saved chat toggle is now labeled “Hide useless chats in safari” and also hides the four mound progress lines. Other mode and personal-best rules are unchanged.

Mode switches keep the current run’s captures, completion evidence and Sparkling checks. Party fd/cd/id/hd messages are retained for Sparkling and unvisited Full Clear progress regardless of the selected mode; they do not create catches or personal bests. A new run clears that evidence.

Birdfeeder announcements now confirm one food used each, including the two-Macaw announcement. Bird completion waits for all collected food to be fed; Full Clear requires every announced Bluebird and Parakeet plus one Macaw, even if extra Macaws unload. Captures, progress and missing rows use these checks. Unique and Sparkling keep their existing capture/discovery rules; personal PBs still require personal catches.

- **Appearance and chat:** local feedback consistently uses a light-gold `[SM]` prefix. Settings, HUD headings and default panel styling share restrained gold accents. Saved custom colors and rarity/status/timer colors remain available.

Haunted natural critters confirm Full Clear absence within 30 blocks of (-3, -63), with capture minimums; Sparkling requires distinct UUID minimums within 40 blocks. Gazer needs four UUIDs; Hideyho/Doomspiral need one without a zone, with captures required in Full Clear and for Sparkling Doomspiral. Gimmiegold uses the picked-up Shining Coin count (at least three), retaining center/drop/spending/inventory requirements. Haunted Sparkling ESP lasts ten seconds per UUID, including profitable Hideonwall; Doomspiral remains until capture.

Forest zones center on (3, 47): Foxtrot/Fluffling use 30/40-block Full Clear/Sparkling radii; Woodchucker/Treefrog/Hideonfloor use 50/60. Full Clear retains captures and confirms absence; Sparkling requires distinct UUID minimums. Honeybug needs punched nests and all observed bugs captured in Full Clear, or three distinct UUIDs in Sparkling. Existing bird food/spawn accounting and the one-Macaw capture exception remain. Forest Sparkling ESP expires after ten seconds per UUID, including profitable species.

Communal completion: Full Clear and Sparkling announce fd/cd/id/hd once per biome. Party done messages update progress; loaded critters (Full Clear) or intact Cavern structures (Sparkling) correct the claim. Unique still uses captures. Prior LOOT SHARE before the first visit identifies inherited biomes: hide spent nest helpers, keep inherited mound totals neutral, and skip unavailable Gimmiegold requirements without altering captures or personal PBs. Run Complete alerts default on with bell sound, green title and light-green background at 80% transparency; title, sound/volume, colors, position and scale are customizable.

Boss PBs: !pb wumpa / !pb doom now show solo records. Wumpa requires a personal capture of every Icy prerequisite; Doomspiral requires a run inventory observation of four Soothing Incense together before capture. Add duo to either command for unrestricted personal boss captures. Old unrestricted records migrate to duo. Empty mutuals reply None. :skull:; missing every discovery in the selected list replies All. :skull:. Other-player PB timing is awaiting clarification on issue #22.

Profitable-shard ESP: Unique and Sparkling keep Hideonfloor, Hideonwall, Chuckwalla, Fluffling and Mantis Shrimp highlighted while their mode’s profitable option is enabled, even after captures, checks or ten seconds. Loaded current entities retain highlights during label/motion gaps; captured or removed entities stay excluded.

Unique mode collects each species once before party discoveries can hide its floor-drop supplies. Honeybug/Rockmite/Snoozle helpers remain after that first catch while the party still needs their sparkling and structures remain unchecked; intact Forest nests remain available for Unique even in inherited biomes. Existing pickup/inventory, manual ESP and Icy floor-drop rules still apply.

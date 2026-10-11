## v3 development — Unique completion

- Verified empty Cavern mounds/walls complete zero-spawn Rockmite/Snoozle progress and remove their missing rows; sightings or remaining structures revoke the exception.
- Party fd completes Macaw progress even without a capture. Capture totals and personal PB evidence remain independent.
- Unique hides all critter entities of a captured species, including profitable species; unfinished nests/mounds/walls retain their helper highlights. The old Unique profitable setting is no longer shown.

- Unique missing rows track first captures; progress retains required Forest/Cavern sparkle checks.
- Party fd/cd claims stay trusted after entering a non-starting biome, unless visible Cavern mounds/walls contradict the corresponding check. Other species still require a capture.
- Unique announces fd/cd only; Icy and Haunted remain capture-based without id/hd messages. PB requirements are unchanged.
- Regression coverage includes nests, walls/mounds, extra bird food, remote confirmations and local announcements. Live in-game validation remains needed.

## v3 development — teammate ESP

- Add independent Highlight teammates and Show teammate names toggles under Safari helpers, both enabled by default. Noamm-style outlines and distance-scaled through-wall names work for loaded party players throughout SkyBlock.
- Exclude the local player, unrelated players and unknown/stale party rosters. Settings persist across restarts. Live rendering and server party changes still need in-game validation.

## v3 development — failed API retries

- Retain failed API party commands and discovery refreshes for a retry three minutes after failure. API key changes through the command or GUI wake failed work on the next client tick; repeated failures restart the delay.
- Preserve command/party context and reply suppression. Cancel stale party retries on connection, roster or mode changes and when commands are disabled; discard results fetched with an outdated key.
- Regression coverage verifies delay boundaries, key replacement, independent operations and cancellation/reset. Live expired-key recovery and delayed party replies still need in-game testing.

## v3 development — bird targets and Full Clear spawning supplies

- Sparkling Forest birds require nine feeder announcements plus starting bird food observed during the first three seconds. Repeated scans and already recorded floor pickups do not inflate the extra food; a Macaw pair uses one feeding.
- Full Clear retains Forest/Haunted floor supplies and unpunched nests regardless of party discoveries or clear claims. Cavern requires all three gems held together; existing intact wall/mound helpers remain active.
- Regression coverage verifies starting-food timing, announcement counts and Full Clear helper visibility. Live perk timing and in-game rendering still need testing.

## v3 development — party boss records and capture feedback

- Save party-player history and observed Doomspiral/Wumpa PBs from the mod user’s run start. Query with `!pb doom <IGN>` / `!pb wumpa <IGN>`; name lookups ignore case and persist across restarts.
- Every personal or loot-share boss capture shows elapsed time and the gap against the previous PB. Personal catches compare solo when eligible, duo otherwise; party catches compare observed records without claiming solo eligibility.
- Regression coverage covers command targeting, record persistence, slower/faster captures, category selection and invalid/stale capture rejection. Live chat and party roster integration still need in-game testing.

## v3 development — Unique helper priority

- Unique mode gives uncaught Gemzie, Forest birds and Gimmiegold priority over the party’s sparkling discoveries when choosing floor-drop supplies. Pickup/inventory completion and manual ESP choices remain authoritative.
- After the first Honeybug/Rockmite/Snoozle capture, helpers follow remaining party sparkling needs and unchecked structures. Unique can show intact unpunched Forest nests despite inherited-clear claims.
- Existing Icy floor-drop and Full Clear/Sparkling mode behavior remain. Regression coverage verifies first-capture priority and the handoff to party checks; live supply/structure rendering still needs in-game testing.

## v3 development — profitable ESP follow-up

- Restore persistent profitable-shard highlights in Unique and Sparkling when enabled, including captured-species completion and Wumpa prerequisites. The option bypasses Sparkling UUID timers and label/motion gaps for current loaded entities.
- Disabling the option restores ordinary mode rules. Captured/removed entity exclusion and per-species/biome ESP controls still apply.
- Regression coverage includes all five profitable species, toggle behavior, Wumpa prerequisites and entity lifecycle guards. Live rendering still needs in-game validation.

# Boss solo/duo PB rules and party replies

- Solo Wumpa PBs require all eight personal Icy prerequisite captures; solo Doomspiral PBs require four Soothing Incense observed simultaneously in inventory earlier in the run. LOOT SHARE cannot qualify a personal PB.
- !pb wumpa duo / !pb doom duo retain unrestricted own-boss capture records. Existing unrestricted records migrate to duo; solo and duo save independently.
- Empty mutuals reply None. :skull:; wholly missing selected discoveries reply All. :skull:.
- Other-player records now use the mod user’s run start as clarified on issue #22. Inventory sampling and party command rendering need live in-game validation.

# Communal biome and run completion

- Automatic fd/cd/id/hd party announcements for locally completed Full Clear/Sparkling biomes; incoming claims update progress and local remaining entities/structures correct them. Unique remains capture-based.
- Prior LOOT SHARE before first entry identifies inherited clears. Nest highlights are suppressed and inherited mound statistics stay neutral in every mode; remaining mounds remain listed. Haunted skips unavailable Gimmiegold evidence, without fabricating catches or personal PBs.
- Configurable Run Complete alert defaults on: green title, light-green background, 80% transparency, minecraft:block.bell.use. Customize title, sound/volume, colors, position and scale.
- Automated regression coverage and build validation; party chat pacing, loaded entity/structure detection and alert appearance still need a live in-game check.

# Forest mode completion rules

- Forest zones center on (3, 47). Foxtrot/Fluffling use 30/40-block Full Clear/Sparkling radii; Woodchucker/Treefrog/Hideonfloor use 50/60.
- Full Clear retains capture minimums and requires no remaining entities inside the zone. Sparkling requires distinct UUID spawn minimums inside the zone.
- Honeybug Full Clear requires punched nests, at least three observed UUIDs and captures covering all observed UUIDs. Sparkling retains punched nests plus three distinct UUIDs.
- Existing Birdfeeder food/spawn accounting and Bluebird/Parakeet captures remain; one Macaw capture is sufficient even with extras remaining.
- Forest Sparkling ESP expires after ten seconds per UUID, including profitable Fluffling/Hideonfloor. Live in-game loading and zone validation remain needed.

# Haunted mode completion rules

- Areita, Bloodbat, Solsnatcher, Litterbug, Duplico and Hideonwall use (-3, -63): Full Clear absence within 30 blocks with capture minimums; Sparkling distinct UUID minimums within 40 blocks.
- Gazer needs four distinct UUIDs; Hideyho and Doomspiral need one, without zones. Full Clear additionally requires their captures. Sparkling Doomspiral also requires capture.
- Gimmiegold UUID/capture minimums scale with picked-up Shining Coins (at least three). Existing center, cleared floor drops, spent coins and empty coin inventory requirements remain.
- Haunted Sparkling ESP expires after ten seconds per UUID, including profitable Hideonwall. Doomspiral stays until capture.
- Forest specifications remain pending. Live in-game zone/entity-loading verification remains needed.

# Icy mode completion zones

- Icy Full Clear confirms absence only within the supplied 30-block species zones, retaining capture minimums. Sparkling needs 40-block zones plus distinct UUID spawn minimums.
- Troodon uses three distinct UUIDs without a location zone, plus three captures in Full Clear.
- Icy Sparkling ESP expires after ten seconds per UUID. If the party still needs Wumpa checked, prerequisite ESP instead stays until one capture, including Mantis Shrimp with profitability enabled.
- Cavern rules remain. Forest and Haunted zone specifications can follow separately; live in-game entity-loading validation remains needed.

# Cavern mode completion zones

- Cavernfish, Flitter, Shyworm, Driftling, Chuckwalla and Gemzie confirm Full Clear absence only inside their specified species zones, while keeping capture minimums.
- Sparkling checks require each species' zone and distinct UUID spawn minimum; Scrappy requires three distinct UUIDs without a zone.
- Full Clear Scrappy additionally needs three observed UUIDs and three captures. Rockmite mounds/captures and Snoozle walls/absence retain their requirements.
- Sparkling keeps Driftling ESP, hides Rockmite silverfish and Shyworm path/timer helpers, and limits other Cavern critter ESP to ten seconds per UUID (including profitable Chuckwalla).
- Other biome completion rules remain; their zone specifications can be added separately. Live in-game zone and entity-loading validation remains needed.

# Shyworm return-to-start follow-up

- Rest messages retain their eight-second countdown when the model resets to its start, including lingering labels and non-Y1 hiding positions.
- The first observed travel side stays warned during the countdown and “moving soon...” phase; movement resumes next-side warnings.
- Red 1×7 outlines use the nearest known emergence ground level (Y40, Y43 or Y60), with a tiny offset to avoid flickering.
- Initial travel direction requires observed movement; live in-game reset and alignment testing remains needed.

# Chat polish and Shyworm follow-ups

- All local mod feedback uses one `[SM]` prefix per line, including command help and contest warnings.
- Light-gold accents across settings, HUD titles and default panel styling; custom colors and semantic colors are preserved.
- Rest messages queue for the nearest stationary underground Shyworm, including models hidden before the message.
- Red 1×7 warning always covers the next clockwise side after direction is observed, with ground height independent of head bobbing.

# Shyworm corner warning and countdown

- Warns the next clockwise 1×7 path before corner emergence once movement establishes its direction.
- Rest messages start an eight-second green/yellow/red countdown, then white “moving soon...”.
- Moving ESP follows head height; only models at the hidden sentinel height use the saved surface projection. Mode and capture eligibility still apply.

## Full Clear progress follow-up

- Full Clear progress now uses the captures panel's minimum, nearby-entity and structure completion rules.
- Party `fd`, `cd`, `id` and `hd` messages complete unvisited biome progress. Visiting that biome restores local evidence checks.
- Manual progress never adds captures, Sparkling checks or personal bests. Unique and Sparkling progress retain their rules.

# Shyworm underground helpers

- Loaded Shyworms retain ESP while underground, projected to their last observed surface height. Captured, removed and unloaded models remain excluded.
- A red 1×7 ground outline anticipates the next clockwise side at corners. Rest messages start a per-worm eight-second colored countdown; brief corner pauses do not start it.
- Normal mode filters and Sparkling per-UUID expiry gate the box, outline and timer together. Ambiguous messages do not assign a timer to several worms; helper state clears on unloading or run reset.

# Icy floor drops and moving-critter ESP

- One Icebreaker is enough to stop automatic Full Clear Icy floor ESP; consumption and biome changes retain that progress for the run.
- Unique and Full Clear restore loaded moving-critter ESP when name tags disappear at distance or movement pauses, including Driftling. Capture retirement, entity validity and mode target filters still apply.
- Other Sparkling lifecycle checks, per-UUID windows, capture observations and PB requirements retain their existing rules. Issue #18 clarified clockwise movement and long-rest chat boundaries.

# Run mode fixes

- Full Clear missing rows remain until the capture minimum is met and no matching critters are nearby.
- Full Clear Cavern floor drops stay highlighted until all three gem types are held together, regardless of party sparkling discoveries.
- Unique mode highlights every uncaught species, even when everyone already has its sparkling discovery. Existing profitable-shard and shiny-check exceptions still apply after captures.

# Sparkling ESP and Wumpa prerequisites

- While the party needs Wumpa checked, Sparkling mode keeps uncaught Icy prerequisites in the missing panel and ESP until each is captured.
- Needed critter UUIDs get independent ten-second ESP windows, remembered across unloading for the run. Newly found UUIDs get their own window; profitable-shard ESP stays visible when enabled.
- ESP expiry does not remove entities from capture, checklist or PB evidence. Unchecked Rockmite mounds remain highlighted until checked.
- Verified the existing hidden Litterbug countdown also covers issue #16.

# Party biome completion fix

- Fixed party `fd`, `cd`, `id` and `hd` messages being filtered out before reaching the Sparkling checklist. They now mark the matching biome checked during an active Sparkling run.
- Player chat remains excluded from capture, entry and PB tracking. Unique and Full Clear behavior is unchanged.

# Litterbug emergence timer

- Hidden Litterbug ESP boxes now show an estimated 8.0-second countdown that changes from green to yellow to red, followed by white "Moving soon..".
- Each entity keeps its own timer. Emergence clears it; the next wall entry starts a new countdown. Timers reset with the Safari run and follow normal ESP visibility.

# Sparkling checklist update

- Sparkling missing panels and progress now track party-needed checks instead of unique captures.
- Biome-center checks cover ordinary critters. Special critters require nests, mounds, walls, bird feeding, coin spending or distinct UUID sightings, remembered for the run.
- Party messages `fd`, `cd`, `id` and `hd` mark the matching biome checked in Sparkling mode; no automatic progress messages are sent.
- Remaining nests, mounds and walls disappear after their Sparkling check is complete. Unique and Full Clear helper rules remain intact.
- Verified the previously implemented party-cleared biome display: earlier party catches and no personal catches use minimum counts and rendered critters, without relaxing personal PB requirements.

# Sparkling modes and Safari helpers update

- Modes now manage remaining structures and their highlights; removed redundant Remaining, Bee Nest Highlight and Snooper Wall Highlight controls.
- Full Clear missing lists follow currently rendered species, restoring a row if critters reappear. Capture completion and quota labels are unchanged.
- Unique Honeybug, Rockmite and Snoozle shiny checks remain visible after a unique catch while anyone in the party still needs their sparkling; unavailable party discovery data keeps checks enabled. Sparkling mode hides both Rockmite forms and Snooper walls when everyone has their discoveries.
- Gem, bird food, incense and warp alerts are smaller independent HUDs. Move and resize them in `/sm gui`, with separate appearance controls.
- Icy floor drops stay hidden in Sparkling mode.

- Hidden Litterbug ESP now appears at the known mansion floor or ledge height, preferring that entity's last observed emergence height. Unknown or ambiguous spawns stay at their real position until observed. The box follows the real entity again when it emerges; capture and PB tracking are unchanged.

- Party-cleared biome capture displays now use minimum catches and absence of rendered critters after your first visit, including Honeybug, Rockmite, Snoozle and birds. Previously collected Forest nests stop highlighting. Personal PB requirements stay unchanged.
- Restored the centered sparkling title, critter name, location and tinted screen flash. Added customizable sparkling sound, volume and sound preview in Safari Helpers → Alert.

- `!commands` now returns only party commands; local commands remain in settings help.

- Settings now open with `/sm`; use `/sm gui` for the HUD editor and `/sm debug` for diagnostics.

- Consolidated ESP settings into one tab with independent “Only in current biome” toggles for floor drops, Forest, Cavern, Icy and Haunted. Modes manage targets; all color customizations remain available.
- Sparkling mode uses your own missing discoveries when solo and refreshes the combined list when party membership changes.
- Party lookups start immediately. Ready replies follow descending UUID order at 500 ms intervals and check for matching chat results again before sending.
- Added `/sparkle` and a Modes settings tab for Unique, Full Clear and Sparkling runs.
- Sparkling mode checks every party member’s missing sparkling discoveries, including your own, and highlights the combined list for the whole run.
- Added a separate “Critter ESP for profitable shards” setting for Sparkling mode.
- Party lookups run in the background with shared caching. API errors stay local; incomplete lookups never mark discoveries as complete for the party.
- Forest floor drops hide when everyone has all three sparkling birds.
- Cavern floor drops hide when everyone has sparkling Gemzie, when Gemzie has been caught, or when all gems have been found.
- Haunted floor drops stay visible while anyone still needs sparkling Gimmiegold. Otherwise they hide after four Soothing Incense or a Doomspiral capture, including incense found at the start of the run.
- Icy floor drops remain hidden in Unique and Sparkling modes and hide after one Icebreaker in Full Clear.
- Full Clear returns biome PBs and all-species sparkling lookups. Unique returns unique-biome and Doomspiral/Wumpa PBs with timesave lookups. Sparkling returns Doomspiral/Wumpa PBs only, also with timesave lookups.
- Auto Clicker now requires physically holding Mouse 0 on a Rockmite mound, stopping immediately on release or target changes.
- Added a Full candle hitbox setting in Safari Helpers, making Haunted candles easier to click while holding Soothing Incense.
- Party help lists actual commands available in your mode. Local command help, settings descriptions and chat feedback are shorter and clearer.
- Warp reminder settings now describe their purpose: warping party leechers in before entry closes.
- Updated README. Builds are available from GitHub Actions.

Issue #19 follow-ups: Sparkling Icy progress uses captures of needed species when Wumpa is needed; a Wumpa capture completes the row. Exact global chamber-opening, darkness-fading and cave-collapse messages check Gemzie, Doomspiral and Wumpa respectively. The saved chat toggle is now labeled “Hide useless chats in safari” and also hides the four mound progress lines. Other mode and personal-best rules are unchanged.

Mode switches keep the current run’s captures, completion evidence and Sparkling checks. Party fd/cd/id/hd messages are retained for Sparkling and unvisited Full Clear progress regardless of the selected mode; they do not create catches or personal bests. A new run clears that evidence.

Birdfeeder announcements now confirm one food used each, including the two-Macaw announcement. Bird completion waits for all collected food to be fed; Full Clear requires every announced Bluebird and Parakeet plus one Macaw, even if extra Macaws unload. Captures, progress and missing rows use these checks. Unique and Sparkling keep their existing capture/discovery rules; personal PBs still require personal catches.

Full candle hitbox now applies to red candles in Haunted while holding Soothing Incense, without pedestal or candle-count restrictions. White and other decorative candles retain their normal selection shapes; collision and server block state are unchanged.

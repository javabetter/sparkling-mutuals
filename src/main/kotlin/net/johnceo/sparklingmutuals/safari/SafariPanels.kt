package net.johnceo.sparklingmutuals.safari

import net.johnceo.sparklingmutuals.hud.HudPanel
import net.johnceo.sparklingmutuals.hud.HudRow
import net.johnceo.sparklingmutuals.config.ConfigManager

/** One collected total, with the v0.9.0 biome and critter rarity colors. */
object SafariPanels {
    fun progress(run: SafariRun?, unique: Boolean, now: Long, party: PartySparklingState = SafariSparklingMode.state): HudPanel {
        val seconds = (run?.elapsed(now) ?: 0) / 1000
        val time = "%d:%02d".format(seconds / 60, seconds % 60)
        val title = when { run == null -> "Critter Safari (ready)"; run.endedAt != null -> "Last Safari  $time"; else -> "Critter Safari  $time" }
        fun bar(label: String, critters: List<SafariCritter>, color: Int): HudRow {
            if (SafariFullClear.mode == SafariMode.SPARKLING && !party.ready) return HudRow(label, "?", color, color)
            val targets = if (SafariFullClear.mode == SafariMode.SPARKLING) critters.filter { party.needs(it.name) } else critters
            val count = run?.completed(SafariFullClear.mode, targets, party) ?: 0
            return HudRow(label, "$count/${targets.size}", color, color,
                if (targets.isEmpty()) 1f else count.toFloat() / targets.size)
        }
        return HudPanel(title, rows = listOf(bar(if (SafariFullClear.mode == SafariMode.SPARKLING) { if (party.ready) "Checked" else "Loading discoveries" } else "Collected", SafariRoster.all, HudRow.WHITE), HudRow()) +
            SafariBiome.entries.map { bar(it.label, it.critters, it.color) })
    }
    fun missing(run: SafariRun?, biome: SafariBiome, unique: Boolean, nests: Int, mounds: Int = 0,
        walls: WallSummary = WallSummary(emptyList()), party: PartySparklingState = SafariSparklingMode.state): HudPanel {
        val sparkling = SafariFullClear.mode == SafariMode.SPARKLING
        val missing = biome.critters.filter { if (sparkling) {
                if (run?.sparklingChecks?.wumpaPrerequisite(it.name, party) == true) run.count(it.name) == 0
                else party.needs(it.name) && run?.sparklingComplete(it.name, party) != true
            }
            else if (SafariFullClear.mode == SafariMode.UNIQUE) run?.uniqueComplete(it.name, party) != true
            else run?.missing(it, ConfigManager.fullClearMode) != false }
        val rows = missing.map { critter ->
            HudRow(critter.name, if (!sparkling && ConfigManager.fullClearMode && critter.quota > 1) "${run?.count(critter.name) ?: 0}/${critter.quota}" else null,
                critter.color, HudRow.GRAY)
        }.ifEmpty { listOf(HudRow(if (sparkling) "All checked!" else if (ConfigManager.fullClearMode) "All caught!" else "All caught!", color = biome.color)) }
        val footer = buildList {
            fun needed(species: String) = SafariHelperRules.needed(species, SafariFullClear.mode, run, party)
            if (biome == SafariBiome.FOREST && needed("Honeybug")) {
                add(HudRow()); add(HudRow("Bee nests to punch", "$nests", HudRow.GOLD, HudRow.GRAY))
            }
            if (biome == SafariBiome.CAVERN) {
                if (needed("Snoozle") && walls.states.isNotEmpty()) {
                    val row = when {
                        !walls.allBroken -> HudRow("Snooper walls to break", walls.remaining, HudRow.GOLD, HudRow.GRAY)
                        run != null && !run.encountered("Snoozle") -> HudRow("No snoozles this run", color = HudRow.GRAY)
                        else -> null
                    }
                    if (row != null) { add(HudRow()); add(row) }
                }
                if (mounds > 0) {
                    add(HudRow()); add(HudRow("Mounds to break", "$mounds", HudRow.GOLD, HudRow.GRAY))
                } else if (needed("Rockmite") && run?.allMoundsBroken == true && !run.encountered("Rockmite")) {
                    add(HudRow()); add(HudRow("No rockmites this run", color = HudRow.GRAY))
                }
            }
        }
        return HudPanel("${biome.label} Biome — ${missing.size} left", biome.color, rows + footer)
    }
    private fun captureColor(complete: Boolean, verified: Boolean) = if (!ConfigManager.fullClearMode || !verified) HudRow.WHITE
        else if (complete) 0xFF55FF55.toInt() else 0xFFFF5555.toInt()
    fun captures(run: SafariRun?, biome: SafariBiome) = HudPanel("${biome.label} captures", biome.color, buildList {
        biome.critters.forEach { val count = run?.count(it.name) ?: 0
            add(HudRow(it.name, "$count", it.color, captureColor(run?.captureComplete(it.name) == true, run?.hasCaptureEvidence(biome) == true))) }
        add(HudRow()); add(HudRow("Total captures", "${biome.critters.sumOf { run?.count(it.name) ?: 0 }}", HudRow.GRAY))
        if (biome == SafariBiome.CAVERN && ConfigManager.showMoundStats) {
            add(HudRow())
            add(HudRow("Rockmite Mounds", "${run?.brokenMounds ?: 0}", HudRow.GOLD,
                captureColor(run?.moundsComplete() == true, run?.hasCaptureEvidence(biome) == true && run.inheritedMounds.not())))
            add(HudRow("Mounds with Rockmite", "${run?.rockmiteMounds ?: 0}", HudRow.GOLD))
        }
    })
    fun previewRun() = SafariRun(0).apply {
        record(SafariCatch(SafariRoster.named("Treefrog")!!)); lastBiome = SafariBiome.FOREST
    }
}

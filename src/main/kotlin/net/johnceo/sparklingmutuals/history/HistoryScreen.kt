package net.johnceo.sparklingmutuals.history

import net.johnceo.sparklingmutuals.config.ConfigManager
import net.johnceo.sparklingmutuals.safari.BiomePbType
import net.johnceo.sparklingmutuals.safari.SafariBiome
import net.johnceo.sparklingmutuals.safari.SafariRoster
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `/sm history`: saved runs, bests, sparklings, achievements and stats, in tabs. Drawn by hand (tabs and buttons
 * included) so the panel shares one look; clickable areas are collected while drawing and checked on the next click.
 * Parts that would overflow a small window shrink to fit.
 */
class HistoryScreen(private val parent: Screen?, private var tab: Tab = Tab.RUNS) : Screen(Component.literal("Sparkling Mutuals history")) {
    enum class Tab(val label: String, val colour: Int) {
        RUNS("Runs", 0xFF55CC66.toInt()), BESTS("Bests", 0xFF55DDDD.toInt()), SPARKLINGS("Sparklings", 0xFFFF77DD.toInt()),
        ACHIEVEMENTS("Achievements", 0xFFFFAA22.toInt()), STATS("Stats", 0xFF7799FF.toInt()), PROFIT("Profit", 0xFFFFD84A.toInt())
    }
    private enum class RunSort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), FASTEST("Fastest"), LONGEST("Longest"), CAUGHT("Most caught"), COINS("Most coins"), PARTY("Party dex"), YOU("Your dex") }
    private enum class Filter(val label: String) { ALL("All"), UNLOCKED("Unlocked"), LOCKED("Locked") }
    private enum class AchievementSort(val label: String) { DEFAULT("Unlocked first"), RARITY_UP("Rarity: low"), RARITY_DOWN("Rarity: high"), CLOSEST("Closest"), RECENT("Recent"), NAME("Name") }
    private class Hit(val x: Int, val y: Int, val w: Int, val h: Int, val action: () -> Unit) {
        fun contains(mx: Double, my: Double) = mx >= x && mx < x + w && my >= y && my < y + h
    }

    private val hits = mutableListOf<Hit>()
    private val scroll = mutableMapOf<Tab, Int>()
    private var sparklingRunsOnly = false
    private var runSort = RunSort.NEWEST
    private var selectedRun = -1
    private var mineOnly = false
    private var filter = Filter.ALL
    private var achievementSort = AchievementSort.DEFAULT
    private var listTop = 0; private var listBottom = 0; private var listRows = 1; private var listSize = 0
    private var px = 0; private var py = 0; private var pw = 0; private var ph = 0

    override fun onClose() { minecraft.setScreen(parent) }
    override fun isPauseScreen() = false

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(g, mouseX, mouseY, delta)
        hits.clear()
        pw = minOf(640, width - 20); ph = minOf(340, height - 50)
        px = (width - pw) / 2; py = (height - ph) / 2 + 12
        tabs(g, mouseX, mouseY)
        g.fill(px, py, px + pw, py + ph, BACKGROUND)
        g.outline(px, py, pw, ph, BORDER)
        when (tab) {
            Tab.RUNS -> runs(g, mouseX, mouseY)
            Tab.BESTS -> bests(g)
            Tab.SPARKLINGS -> sparklings(g, mouseX, mouseY)
            Tab.ACHIEVEMENTS -> achievements(g, mouseX, mouseY)
            Tab.STATS -> stats(g)
            Tab.PROFIT -> profit(g, mouseX, mouseY)
        }
        button(g, mouseX, mouseY, px + pw - 66, py + ph - 20, 58, "Close", ::onClose)
    }

    private fun tabs(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        val tabW = minOf(100, (pw - 8) / Tab.entries.size - 6)
        var x = px + (pw - (Tab.entries.size * (tabW + 6) - 6)) / 2
        val y = py - 22
        for (t in Tab.entries) {
            val on = t == tab
            val hover = mx in x until x + tabW && my in y until y + 18
            g.fill(x, y, x + tabW, y + 18, if (on) t.colour and 0xFFFFFF or 0xD0000000.toInt() else if (hover) 0xC0302820.toInt() else 0xC0181410.toInt())
            g.outline(x, y, tabW, 18, t.colour)
            // A label wider than its tab is shrunk to fit, so every tab stays readable in a narrow window.
            val scale = minOf(1f, (tabW - 6f) / font.width(t.label))
            val pose = g.pose()
            pose.pushMatrix(); pose.translate(x + tabW / 2f, y + 9f - 4f * scale); pose.scale(scale, scale)
            g.text(font, t.label, -font.width(t.label) / 2, 0, if (on) 0xFF101010.toInt() else TEXT, !on)
            pose.popMatrix()
            hits += Hit(x, y, tabW, 18) { tab = t }
            x += tabW + 6
        }
    }

    // ------------------------------------------------------------------ runs

    private fun filteredRuns(): List<RunRecord> {
        val all = SafariRoster.all.size
        val runs = RunHistory.runs.filter { !sparklingRunsOnly || it.hasSparkling() }
        return when (runSort) {
            RunSort.NEWEST -> runs.sortedByDescending { it.start }
            RunSort.OLDEST -> runs.sortedBy { it.start }
            RunSort.FASTEST -> runs.sortedBy { if (it.party >= all) it.length else Long.MAX_VALUE / 2 + it.length }
            RunSort.LONGEST -> runs.sortedByDescending { it.length }
            RunSort.CAUGHT -> runs.sortedByDescending { it.caught }
            RunSort.COINS -> runs.sortedByDescending { it.coins }
            RunSort.PARTY -> runs.sortedByDescending { it.party }
            RunSort.YOU -> runs.sortedByDescending { it.you }
        }
    }

    private fun runs(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        val all = RunHistory.runs
        val runs = filteredRuns()
        val total = SafariRoster.all.size
        g.text(font, "Saved Runs", px + 8, py + 6, GOLD)
        "${all.size} Kept".let { g.text(font, it, px + pw - 8 - font.width(it), py + 6, MUTED) }
        centred(g, "§f${all.size} Runs  §8·  §f${hours(all.sumOf { it.length })} Played  §8·  §f" +
            "%,d".format(all.sumOf { it.caught }) + " Caught  §8·  §e" + BazaarPrices.format(all.sumOf { maxOf(0L, it.coins) }) + " Coins", px + pw / 2, py + 19, TEXT)
        val headers = listOf("Run", "When", "Length", "Party", "You", "Caught", "Coins")
        val at = floatArrayOf(.08f, .24f, .40f, .52f, .63f, .74f, .88f)
        val headerY = py + 33
        headers.forEachIndexed { i, h -> centred(g, h, px + Math.round(pw * at[i]), headerY, 0xFFDDDDDD.toInt()) }
        listTop = headerY + 12; listBottom = py + ph - 48; listRows = maxOf(1, (listBottom - listTop) / ROW); listSize = runs.size
        val offset = clampScroll(Tab.RUNS)
        if (runs.isEmpty()) centred(g, if (all.isEmpty()) "No runs saved yet: finish a Safari run, or /sm history import." else "No runs with a sparkling yet.",
            px + pw / 2, listTop + 20, MUTED)
        for (i in 0 until listRows) {
            val index = offset + i
            val run = runs.getOrNull(index) ?: break
            val y = listTop + i * ROW
            val hover = mx in px + 4 until px + pw - 4 && my in y - 1 until y + ROW - 1
            if (index == selectedRun || hover) g.fill(px + 4, y - 1, px + pw - 4, y + ROW - 1, if (index == selectedRun) 0x40FFFFFF else 0x18FFFFFF)
            val cells = listOf("#${run.number}", WHEN.format(Instant.ofEpochMilli(run.start).atZone(ZoneId.systemDefault())), clock(run.length),
                "${run.party}/$total", "${run.you}/$total", run.caught.toString(), BazaarPrices.format(run.coins))
            if (run.hasSparkling()) {
                // A sparkling run shimmers in a rainbow, one colour per character across the whole row.
                rainbow(g, "✦", px + 8, y); rainbow(g, "✦", px + pw - 14, y)
                cells.forEachIndexed { c, text -> rainbow(g, text, px + Math.round(pw * at[c]) - font.width(text) / 2, y) }
            } else {
                val colours = intArrayOf(TEXT, TEXT, MUTED, if (run.party >= total) 0xFF55FF55.toInt() else 0xFFDDDDDD.toInt(), 0xFF77BBFF.toInt(), 0xFF88DD88.toInt(), GOLD)
                cells.forEachIndexed { c, text -> centred(g, text, px + Math.round(pw * at[c]), y, colours[c]) }
            }
            hits += Hit(px + 4, y - 1, pw - 8, ROW) { selectedRun = if (selectedRun == index) -1 else index }
        }
        scrollbar(g, offset, px + pw - 8)
        val detailY = py + ph - 44
        val detail = runs.getOrNull(selectedRun)
        if (detail != null) {
            if (detail.hasSparkling()) centred(g, "§d✦ ${detail.sparklings.size} Sparkling${if (detail.sparklings.size == 1) "" else "s"}: " +
                detail.sparklings.joinToString("§7, ") { colour(it.substringAfter(':')) + it.substringAfter(':') } + " §d✦", px + pw / 2, detailY, PINK)
            else centred(g, "§7No sparklings this run", px + pw / 2, detailY, MUTED)
            val splits = SafariBiome.entries.mapNotNull { b -> detail.splits[b.label]?.let { "§f${b.label} §b${pbTime(it)}" } }
            val extra = "§7Macaws §f${detail.macaws}  §7Rainbow Feathers §f${detail.feathers}  §7Loot shares §f${detail.lootShares}  §7Tickets §f${detail.tickets}" +
                if (detail.imported) "  §8(from old logs)" else "  §7Party §f${detail.partySize}"
            centred(g, if (splits.isEmpty()) extra else splits.joinToString(" §8· "), px + pw / 2, detailY + 11, TEXT)
            if (splits.isNotEmpty() && mx in px until px + pw && my in detailY until detailY + 22)
                g.setComponentTooltipForNextFrame(font, listOf(Component.literal(extra)), mx, my)
        } else if (runs.isNotEmpty()) centred(g, "§8Click a run for its sparklings and splits", px + pw / 2, detailY + 5, DIM)
        val fy = py + ph - 20
        toggle(g, mx, my, px + pw / 2 - 150, fy, "Sparkling Runs", sparklingRunsOnly) { sparklingRunsOnly = !sparklingRunsOnly; selectedRun = -1; scroll[Tab.RUNS] = 0 }
        button(g, mx, my, px + pw / 2 - 30, fy, 110, "Sort: ${runSort.label}") { runSort = RunSort.entries[(runSort.ordinal + 1) % RunSort.entries.size]; selectedRun = -1 }
    }

    // ------------------------------------------------------------------ bests

    private fun bests(g: GuiGraphicsExtractor) {
        val bests = ConfigManager.personalBests
        g.text(font, "Personal Bests", px + 8, py + 6, GOLD)
        g.text(font, "§7Times from the start of the run.", px + 8, py + 18, MUTED)
        val left = px + 16
        val labelW = maxOf(font.width("Doomspiral"), font.width("Haunted"))
        val timeW = maxOf(font.width("9:42.000"), font.width("Full Clear"))
        val colA = left + labelW + 8 + timeW / 2
        val colB = colA + timeW + 6
        var y = py + 36
        g.text(font, "Biome", left, y, 0xFFDDDDDD.toInt()); centred(g, "Full Clear", colA, y, 0xFFDDDDDD.toInt()); centred(g, "Unique", colB, y, 0xFFDDDDDD.toInt())
        y += 13
        for (biome in SafariBiome.entries) {
            g.text(font, biome.label, left, y, biome.color)
            cell(g, bests.time(biome.label, BiomePbType.FULL_CLEAR), colA, y, 0xFF55FF55.toInt())
            cell(g, bests.time(biome.label, BiomePbType.UNIQUE), colB, y, 0xFF55DDDD.toInt())
            y += ROW + 1
        }
        y += 6
        g.text(font, "Boss", left, y, 0xFFDDDDDD.toInt()); centred(g, "Solo", colA, y, 0xFFDDDDDD.toInt()); centred(g, "Duo", colB, y, 0xFFDDDDDD.toInt())
        y += 13
        for (boss in listOf("Doomspiral", "Wumpa")) {
            g.text(font, boss, left, y, SafariRoster.color(boss))
            cell(g, bests.time(boss), colA, y, 0xFF55FF55.toInt())
            cell(g, bests.time(boss, duo = true), colB, y, 0xFF55DDDD.toInt())
            y += ROW + 1
        }
        val rx = colB + timeW / 2 + 14
        var ry = py + 36
        g.text(font, "Run Records §8(best in one run)", rx, ry, 0xFFDDDDDD.toInt())
        ry += 13
        val runs = RunHistory.runs
        val all = SafariRoster.all.size
        fun record(label: String, value: String?, run: RunRecord?) {
            g.text(font, "§7$label", rx, ry, MUTED)
            val shown = if (value == null) "§8-" else "§f$value" + (run?.let { " §8(#${it.number})" } ?: "")
            g.text(font, shown, px + pw - 12 - font.width(shown), ry, TEXT)
            ry += ROW + 1
        }
        runs.filter { it.party >= all }.minByOrNull { it.length }.let { record("Fastest full run", it?.let { r -> clock(r.length) }, it) }
        runs.maxByOrNull { it.caught }.let { record("Most caught", it?.caught?.toString(), it) }
        runs.filter { it.coins >= 0 }.maxByOrNull { it.coins }.let { record("Most coins", it?.let { r -> BazaarPrices.format(r.coins) }, it) }
        runs.maxByOrNull { it.you }.let { record("Best own dex", it?.let { r -> "${r.you}/$all" }, it) }
        runs.filter { it.hasSparkling() }.maxByOrNull { it.sparklings.size }.let { record("Sparklings", it?.sparklings?.size?.toString(), it) }
        runs.filter { it.macaws > 0 }.maxByOrNull { it.macaws }.let { record("Macaws", it?.macaws?.toString(), it) }
        ry += 6
        g.text(font, "Luck", rx, ry, 0xFFDDDDDD.toInt()); ry += 13
        record("Dry streak", "${Achievements.longestDrySparkling} runs", null)
        record("Macaw drought", "${Achievements.longestMacawDrought} runs", null)
    }
    private fun cell(g: GuiGraphicsExtractor, time: Long?, x: Int, y: Int, colour: Int) =
        centred(g, time?.let(::pbTime) ?: "-", x, y, if (time == null) DIM else colour)

    // ------------------------------------------------------------------ sparklings

    private fun sparklings(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        val self = minecraft.player?.name?.string.orEmpty()
        val entries = SparklingLog.entries.filter { !mineOnly || it.player.equals(self, true) }
        val listW = Math.round(pw * .60f)
        g.text(font, "Sparkling History", px + 8, py + 6, PINK)
        "${entries.size} catch${if (entries.size == 1) "" else "es"}".let { g.text(font, it, px + listW - font.width(it), py + 6, MUTED) }
        val y0 = py + 22
        g.text(font, "When", px + 10, y0, 0xFFDDDDDD.toInt()); g.text(font, "Player", px + 60, y0, 0xFFDDDDDD.toInt())
        // The critter column starts after the longest name on screen, so long names never run into it.
        val critterX = px + 60 + 8 + maxOf(font.width("Player"), entries.drop(scroll[Tab.SPARKLINGS] ?: 0).take(40).maxOfOrNull { font.width(it.player) } ?: 0)
        g.text(font, "Critter", critterX, y0, 0xFFDDDDDD.toInt())
        "Tickets".let { g.text(font, it, px + listW - font.width(it), y0, 0xFFDDDDDD.toInt()) }
        listTop = y0 + 12; listBottom = py + ph - 26; listRows = maxOf(1, (listBottom - listTop) / ROW); listSize = entries.size
        val offset = clampScroll(Tab.SPARKLINGS)
        if (entries.isEmpty()) g.text(font, "§7No sparklings recorded yet. Old logs: /sm history import", px + 10, listTop + 6, MUTED)
        for (i in 0 until listRows) {
            val entry = entries.getOrNull(offset + i) ?: break
            val y = listTop + i * ROW
            val when_ = Instant.ofEpochMilli(entry.at).atZone(ZoneId.systemDefault())
            if (mx in px + 4 until px + listW + 4 && my in y - 1 until y + ROW - 1) {
                g.fill(px + 4, y - 1, px + listW + 4, y + ROW - 1, 0x18FFFFFF)
                g.setComponentTooltipForNextFrame(font, listOfNotNull(Component.literal("§f" + FULL.format(when_)),
                    entry.tickets?.let { Component.literal("§7Took §e$it§7 tickets since the last §6§lSPARKLING") }), mx, my)
            }
            g.text(font, "§7" + DAY_SHORT.format(when_), px + 10, y, MUTED)
            g.text(font, "§b" + entry.player, px + 60, y, TEXT)
            g.text(font, colour(entry.critter) + entry.critter + if (entry.unique) " §e§lNEW!" else " §b#${SparklingLog.numberOf(entry)}",
                critterX, y, TEXT)
            (entry.tickets?.let { "§e$it" } ?: "§8-").let { g.text(font, it, px + listW - font.width(it), y, TEXT) }
        }
        scrollbar(g, offset, px + listW + 6)
        // Your dex, by biome, in two columns; shrunk to fit a short window.
        val dx = px + listW + 16
        val dex = Achievements.ownDex()
        g.text(font, "Your Sparkling Dex", dx, py + 6, GOLD)
        "${dex.size}/${SafariRoster.all.size}".let { g.text(font, it, px + pw - 10 - font.width(it), py + 6, TEXT) }
        val biomes = SafariBiome.entries
        val half = (biomes.size + 1) / 2
        val needed = IntArray(2).also { n -> biomes.forEachIndexed { b, biome -> n[if (b < half) 0 else 1] += ROW + 1 + 10 * biome.critters.size + 4 } }
        val s = fit(needed.max(), py + ph - 26 - (py + 22))
        val pose = g.pose()
        pose.pushMatrix(); pose.translate(dx.toFloat(), (py + 22).toFloat()); pose.scale(s, s)
        val colW = Math.round((px + pw - 10 - dx) / 2f / s)
        val colY = intArrayOf(0, 0)
        biomes.forEachIndexed { b, biome ->
            val col = if (b < half) 0 else 1
            val x = col * colW
            val have = biome.critters.count { it.name in dex }
            g.text(font, "§l§n${biome.label}§r §7$have/${biome.critters.size}", x, colY[col], biome.color)
            colY[col] += ROW + 1
            for (critter in biome.critters) {
                g.text(font, if (critter.name in dex) colour(critter.name) + critter.name else "§8" + critter.name, x + 4, colY[col], TEXT)
                colY[col] += 10
            }
            colY[col] += 4
        }
        pose.popMatrix()
        toggle(g, mx, my, px + 8, py + ph - 20, "Mine only", mineOnly) { mineOnly = !mineOnly; scroll[Tab.SPARKLINGS] = 0 }
    }

    // ------------------------------------------------------------------ achievements

    private fun order(): Comparator<Achievement> {
        val rarity = compareBy<Achievement> { it.tier.ordinal }
        val name = compareBy<Achievement, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        val base = when (achievementSort) {
            AchievementSort.DEFAULT -> compareBy<Achievement> { !Achievements.isUnlocked(it) }.then(rarity)
            AchievementSort.RARITY_UP -> rarity
            AchievementSort.RARITY_DOWN -> rarity.reversed()
            AchievementSort.CLOSEST -> compareBy { when {
                Achievements.isUnlocked(it) -> 3.0
                !it.hasProgress -> if (it.secret) 2.5 else 2.0
                else -> 1 - minOf(1.0, Achievements.progress(it).toDouble() / it.goal)
            } }
            AchievementSort.RECENT -> compareBy { -Achievements.unlockedAt(it) }
            AchievementSort.NAME -> name
        }
        return base.then(rarity).then(name)
    }

    private fun achievements(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        val all = Achievements.all()
        val unlocked = all.count(Achievements::isUnlocked)
        val shown = all.filter { filter == Filter.ALL || (filter == Filter.UNLOCKED) == Achievements.isUnlocked(it) }.sortedWith(order())
        g.text(font, "Achievements", px + 8, py + 6, GOLD)
        "$unlocked/${all.size} unlocked (${if (all.isEmpty()) 0 else unlocked * 100 / all.size}%)".let { g.text(font, it, px + pw - 8 - font.width(it), py + 6, TEXT) }
        bar(g, px + 8, py + 18, pw - 16, 4, if (all.isEmpty()) 0.0 else unlocked.toDouble() / all.size, GOLD)
        val rowH = 23
        listTop = py + 28; listBottom = py + ph - 26; listRows = maxOf(1, (listBottom - listTop) / rowH); listSize = shown.size
        val offset = clampScroll(Tab.ACHIEVEMENTS)
        for (i in 0 until listRows) {
            val a = shown.getOrNull(offset + i) ?: break
            val y = listTop + i * rowH
            val has = Achievements.isUnlocked(a)
            val hidden = a.secret && !has
            g.fill(px + 6, y, px + pw - 12, y + rowH - 2, if (has) a.tier.argb and 0xFFFFFF or 0x22000000 else 0x14FFFFFF)
            g.fill(px + 6, y, px + 9, y + rowH - 2, if (has) a.tier.argb else 0xFF444444.toInt())
            // The right column (date or progress) is reserved; text on the left is cut to fit beside it.
            val right = px + pw - 18
            val progress = if (!has && a.hasProgress) minOf(Achievements.progress(a), a.goal) else 0
            val side = when {
                has -> "§a✔ §7" + DAY.format(Instant.ofEpochMilli(Achievements.unlockedAt(a)).atZone(ZoneId.systemDefault()))
                a.hasProgress -> "§7${compact(progress)}/${compact(a.goal)}"
                else -> null
            }
            val sideW = if (side == null) 0 else maxOf(font.width(side), if (a.hasProgress && !has) 70 else 0)
            val textW = right - sideW - 10 - (px + 14)
            val name = cut(if (hidden) "§8§l" else if (has) a.tier.code + "§l" else "§7§l", if (hidden) "???" else a.name, textW)
            g.text(font, name, px + 14, y + 2, TEXT)
            val tierLabel = (if (has || !hidden) a.tier.code else "§8") + a.tier.name
            if (shownWidth(name) + 8 + font.width(tierLabel) <= textW) g.text(font, tierLabel, px + 14 + shownWidth(name) + 8, y + 2, TEXT)
            val description = if (hidden) "Secret achievement: keep playing to find out." else a.description
            g.text(font, cut(if (hidden) "§8" else "§7", description, textW), px + 14, y + 12, MUTED)
            if (has) g.text(font, side!!, right - font.width(side), y + 7, TEXT)
            else if (side != null) { g.text(font, side, right - font.width(side), y + 2, MUTED); bar(g, right - sideW, y + 13, sideW, 4, progress.toDouble() / a.goal, a.tier.argb) }
            if (mx in px + 6 until px + pw - 12 && my in y until y + rowH - 2) g.setComponentTooltipForNextFrame(font,
                listOf(Component.literal(if (hidden) "§8§l???" else a.tier.code + "§l" + a.name), Component.literal(tierLabel), Component.literal("§7$description")), mx, my)
        }
        scrollbar(g, offset, px + pw - 8)
        val fy = py + ph - 20
        button(g, mx, my, px + 8, fy, 100, "Show: ${filter.label}") { filter = Filter.entries[(filter.ordinal + 1) % Filter.entries.size]; scroll[Tab.ACHIEVEMENTS] = 0 }
        button(g, mx, my, px + 114, fy, 130, "Sort: ${achievementSort.label}") { achievementSort = AchievementSort.entries[(achievementSort.ordinal + 1) % AchievementSort.entries.size]; scroll[Tab.ACHIEVEMENTS] = 0 }
    }

    // ------------------------------------------------------------------ stats

    private fun stats(g: GuiGraphicsExtractor) {
        val runs = RunHistory.runs
        val played = runs.sumOf { it.length }
        val all = SafariRoster.all.size
        val self = minecraft.player?.name?.string.orEmpty()
        val byCritter = linkedMapOf<String, Int>().also { m -> runs.forEach { r -> r.catches.forEach { (c, n) -> m.merge(c, n, Int::plus) } } }
        g.text(font, "Stats", px + 8, py + 6, GOLD)
        val rows = listOf(
            "Runs" to "%,d".format(runs.size),
            "Time on runs" to hours(played),
            "Safari playtime" to if (PlaytimeReader.minutes > 0) hours(PlaytimeReader.safariMillis()) else "§7open /playtime → Safari",
            "Average run" to if (runs.isEmpty()) "-" else clock(played / runs.size),
            "Full clears (37/37)" to "${runs.count { it.party >= all }}",
            "Critters caught" to "%,d".format(runs.sumOf { it.caught }),
            "Loot shares" to "%,d".format(runs.sumOf { it.lootShares }),
            "Shards" to "%,d".format(runs.sumOf { r -> r.shards.values.sum() }),
            "Rainbow Feathers" to "${runs.sumOf { it.feathers }}",
            "Shard + feather value" to "§e" + BazaarPrices.format(runs.sumOf { maxOf(0L, it.coins) }) +
                if (played > 0) " §7· " + BazaarPrices.format(runs.sumOf { maxOf(0L, it.coins) } * 3_600_000L / played) + "/h" else "",
            "Tickets (in runs)" to "%,d".format(runs.sumOf { it.tickets }),
            "Sparklings (you / seen)" to "${SparklingLog.entries.count { it.player.equals(self, true) }} / ${SparklingLog.entries.size}",
            "Sparkling dex" to "${Achievements.ownDex().size}/$all",
            "Tickets since sparkling" to "${SparklingLog.ticketsSinceSparkling}",
            "Macaws" to "${runs.sumOf { it.macaws }}" + if (runs.isEmpty()) "" else " §7· ${runs.count { it.macaws > 0 } * 100 / runs.size}% of runs",
            "Runs since a Macaw" to "${Achievements.runsSinceMacaw}",
            "Contests completed" to "${Achievements.contestsCompleted}")
        val s = fit(rows.size * ROW, py + ph - 26 - (py + 22))
        val valueX = Math.round((pw * .50f - 12) / s)
        val pose = g.pose()
        pose.pushMatrix(); pose.translate((px + 12).toFloat(), (py + 22).toFloat()); pose.scale(s, s)
        rows.forEachIndexed { i, (label, value) ->
            g.text(font, "§7$label", 0, i * ROW, MUTED); g.text(font, "§f$value", valueX - font.width(value), i * ROW, TEXT)
        }
        pose.popMatrix()
        val bx = px + Math.round(pw * .56f)
        g.text(font, "Most caught", bx, py + 22, 0xFFDDDDDD.toInt())
        val top = byCritter.entries.sortedByDescending { it.value }
        val max = top.firstOrNull()?.value ?: 1
        val barX = bx + 76
        val barW = px + pw - 50 - barX
        var by = py + 35
        for (entry in top) {
            if (by >= py + ph - 32) break
            g.text(font, colour(entry.key) + entry.key, bx, by, TEXT)
            val w = maxOf(1, Math.round(barW * entry.value.toFloat() / max))
            g.fill(barX, by + 1, barX + w, by + 8, SafariRoster.color(entry.key) and 0xFFFFFF or 0xC0000000.toInt())
            g.text(font, "§7" + "%,d".format(entry.value), barX + w + 4, by, MUTED)
            by += 11
        }
        if (top.isEmpty()) g.text(font, "§7Nothing yet.", bx, by, MUTED)
    }

    // ------------------------------------------------------------------ profit

    private fun profit(g: GuiGraphicsExtractor, mx: Int, my: Int) {
        val runs = RunHistory.runs
        val days = ProfitDays.of(runs)
        val coins = runs.sumOf { maxOf(0L, it.coins) }
        val played = runs.sumOf { it.length }
        g.text(font, "Profit", px + 8, py + 6, GOLD)
        (if (ConfigManager.runCoinsInstaSell) "Insta-sell prices" else "Sell-offer prices").let { g.text(font, "§7$it", px + pw - 8 - font.width(it), py + 6, MUTED) }
        centred(g, "§e${BazaarPrices.format(coins)} §7total  §8·  §e${if (played > 0) BazaarPrices.format(coins * 3_600_000L / played) else "-"} §7an hour  §8·  §e" +
            "${if (runs.isEmpty()) "-" else BazaarPrices.format(coins / runs.size)} §7a run  §8·  §d${runs.sumOf { it.feathers }} §7Rainbow Feathers", px + pw / 2, py + 19, TEXT)
        val headers = listOf("Day", "Runs", "Time", "Coins", "Per hour", "Best run")
        val at = floatArrayOf(.10f, .26f, .40f, .55f, .71f, .87f)
        val headerY = py + 33
        headers.forEachIndexed { i, h -> centred(g, h, px + Math.round(pw * at[i]), headerY, 0xFFDDDDDD.toInt()) }
        listTop = headerY + 12; listBottom = py + ph - 26; listRows = maxOf(1, (listBottom - listTop) / ROW); listSize = days.size
        val offset = clampScroll(Tab.PROFIT)
        if (!BazaarPrices.ready() && runs.any { it.coins < 0 }) centred(g, "§7Loading bazaar prices...", px + pw / 2, listTop + 20, MUTED)
        else if (days.isEmpty()) centred(g, "§7No runs yet.", px + pw / 2, listTop + 20, MUTED)
        val best = days.maxOfOrNull { it.coins } ?: 0
        for (i in 0 until listRows) {
            val day = days.getOrNull(offset + i) ?: break
            val y = listTop + i * ROW
            // The best day stands out, so a good session is easy to find.
            if (day.coins == best && best > 0) g.fill(px + 4, y - 1, px + pw - 4, y + ROW - 1, 0x22FFD84A)
            val cells = listOf(DAY.format(day.date), "${day.runs}", hours(day.millis), BazaarPrices.format(day.coins),
                BazaarPrices.format(day.perHour), day.best?.let { "#${it.number} ${BazaarPrices.format(it.coins)}" } ?: "-")
            val colours = intArrayOf(TEXT, MUTED, MUTED, GOLD, 0xFFFFE08A.toInt(), 0xFF88DD88.toInt())
            cells.forEachIndexed { c, text -> centred(g, text, px + Math.round(pw * at[c]), y, colours[c]) }
        }
        scrollbar(g, offset, px + pw - 8)
        button(g, mx, my, px + 8, py + ph - 20, 140, if (ConfigManager.runCoinsInstaSell) "Price: insta-sell" else "Price: sell offer") {
            ConfigManager.runCoinsInstaSell = !ConfigManager.runCoinsInstaSell; ConfigManager.save()
            RunHistory.runs.forEach { it.coins = BazaarPrices.value(it.shards, it.feathers) }; RunHistory.save()
        }
    }

    // ------------------------------------------------------------------ widgets

    private fun button(g: GuiGraphicsExtractor, mx: Int, my: Int, x: Int, y: Int, w: Int, label: String, action: () -> Unit) {
        val hover = mx in x until x + w && my in y until y + 15
        g.fill(x, y, x + w, y + 15, if (hover) 0xC0403320.toInt() else 0xC0201A12.toInt())
        g.outline(x, y, w, 15, if (hover) 0xFFFFD27A.toInt() else BORDER)
        g.text(font, label, x + (w - font.width(label)) / 2, y + 4, TEXT)
        hits += Hit(x, y, w, 15, action)
    }
    private fun toggle(g: GuiGraphicsExtractor, mx: Int, my: Int, x: Int, y: Int, label: String, on: Boolean, action: () -> Unit) {
        val w = 14 + font.width(label)
        g.outline(x, y + 3, 9, 9, if (mx in x until x + w && my in y until y + 15) 0xFFFFD27A.toInt() else 0xFFBBBBBB.toInt())
        if (on) g.fill(x + 2, y + 5, x + 7, y + 10, 0xFF55FF55.toInt())
        g.text(font, label, x + 13, y + 4, TEXT)
        hits += Hit(x, y, w, 15, action)
    }
    private fun bar(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, fraction: Double, colour: Int) {
        g.fill(x, y, x + w, y + h, 0x60000000)
        g.fill(x, y, x + Math.round(w * fraction.coerceIn(0.0, 1.0)).toInt(), y + h, colour)
    }
    private fun scrollbar(g: GuiGraphicsExtractor, offset: Int, x: Int) {
        if (listSize <= listRows) return
        val track = listBottom - listTop
        val thumb = maxOf(10, track * listRows / listSize)
        val top = listTop + (track - thumb) * offset / maxOf(1, listSize - listRows)
        g.fill(x, listTop, x + 2, listBottom, 0x40FFFFFF); g.fill(x, top, x + 2, top + thumb, 0xC0E8A33A.toInt())
    }
    private fun clampScroll(t: Tab): Int { val value = (scroll[t] ?: 0).coerceIn(0, maxOf(0, listSize - listRows)); scroll[t] = value; return value }
    private fun centred(g: GuiGraphicsExtractor, text: String, cx: Int, y: Int, colour: Int) = g.text(font, text, cx - font.width(text) / 2, y, colour)
    /** Pastel rainbow: each character's hue from where it sits on screen, drifting with time. */
    private fun rainbow(g: GuiGraphicsExtractor, text: String, x: Int, y: Int) {
        val drift = (System.currentTimeMillis() % 6000L) / 6000f
        var cx = x
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val ch = String(Character.toChars(cp))
            g.text(font, ch, cx, y, 0xFF000000.toInt() or java.awt.Color.HSBtoRGB((cx / 140f + drift) % 1f, .45f, 1f))
            cx += font.width(ch); i += Character.charCount(cp)
        }
    }
    /** Bold gets a pixel per letter on top of what the font reports: some fonts draw it wider than they measure. */
    private fun shownWidth(text: String) = font.width(text) + if ("§l" in text) net.johnceo.sparklingmutuals.safari.SafariRules.strip(text).length else 0
    private fun cut(prefix: String, text: String, width: Int): String {
        if (shownWidth(prefix + text) <= width) return prefix + text
        var shown = text
        while (shown.isNotEmpty() && shownWidth("$prefix$shown…") > width) shown = shown.dropLast(1)
        return prefix + shown.trimEnd() + "…"
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) hits.toList().firstOrNull { it.contains(event.x(), event.y()) }?.let { it.action(); return true }
        return super.mouseClicked(event, doubleClick)
    }
    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY == 0.0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
        scroll[tab] = (scroll[tab] ?: 0) + if (scrollY > 0) -3 else 3
        return true
    }

    companion object {
        private const val BORDER = 0xFFE8A33A.toInt()
        private const val BACKGROUND = 0xE0141210.toInt()
        private const val ROW = 11
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFAAAAAA.toInt()
        private const val DIM = 0xFF666666.toInt()
        private const val GOLD = 0xFFFFC94A.toInt()
        private const val PINK = 0xFFFF77FF.toInt()
        private val WHEN = DateTimeFormatter.ofPattern("dd MMM  HH:mm", Locale.ENGLISH)
        private val DAY = DateTimeFormatter.ofPattern("MMM dd yyyy", Locale.ENGLISH)
        private val DAY_SHORT = DateTimeFormatter.ofPattern("MMM dd", Locale.ENGLISH)
        private val FULL = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy 'at' hh:mm:ss a", Locale.ENGLISH)
        fun colour(critter: String) = when (SafariRoster.color(critter)) {
            0xFF55FF55.toInt() -> "§a"; 0xFF5555FF.toInt() -> "§9"; 0xFFAA00AA.toInt() -> "§5"; 0xFFFFAA00.toInt() -> "§6"; else -> "§f"
        }
        fun clock(millis: Long): String { val s = maxOf(0L, millis) / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
        fun pbTime(millis: Long) = "%d:%02d.%03d".format(Locale.ROOT, millis / 60000, millis / 1000 % 60, millis % 1000)
        fun hours(millis: Long) = (millis / 60_000L).let { "${it / 60}h ${it % 60}m" }
        fun fit(needed: Int, available: Int) = if (needed <= available) 1f else maxOf(.6f, available.toFloat() / needed)
        fun compact(n: Long): String {
            fun trim(v: Double) = "%.1f".format(Locale.ROOT, v).removeSuffix(".0")
            return when { n >= 1_000_000_000L -> trim(n / 1e9) + "B"; n >= 1_000_000L -> trim(n / 1e6) + "M"; n >= 10_000L -> trim(n / 1e3) + "k"; else -> "%,d".format(n) }
        }
    }
}

package net.johnceo.sparklingmutuals.safari

import java.util.Base64

/** Identification, palette and quadrant limits adapted from Nebulune a573283 (BSD-3-Clause). */
data class EspEntity(val type: String, val texture: String? = null, val shulker: String? = null,
    val fish: String? = null, val variant: String? = null, val invisible: Boolean = false, val passengers: Boolean = false)
data class EspMob(val name: String, val biome: SafariBiome, val color: Int, val identifiers: List<EspEntity>)
data class EspDrop(val id: Int, val x: Int, val y: Int, val z: Int)
data class EspCaptureCandidate(val id: Int, val species: String, val display: Boolean, val mound: Boolean,
    val rangeSquared: Double, val aimSquared: Double)
data class EspModelLabel(val id: java.util.UUID, val species: String, val distanceSquared: Double, val visible: Boolean = true)
class EspModelLabels {
    private data class Binding(val species: String, val label: java.util.UUID, var visible: Boolean, var seenAt: Long)
    private val bindings = mutableMapOf<java.util.UUID, Binding>()
    /** Once paired, retain label identity instead of adopting another nearby critter's name tag. */
    fun observe(id: java.util.UUID, species: String, labels: List<EspModelLabel>, now: Long) {
        val nearby = labels.filter { it.species == species && it.distanceSquared in 0.0..9.0 }
        if (bindings[id]?.species != species) bindings.remove(id)
        val binding = bindings[id] ?: nearby.minByOrNull { it.distanceSquared }?.let {
            Binding(species, it.id, it.visible, now).also { bound -> bindings[id] = bound }
        } ?: return
        nearby.firstOrNull { it.id == binding.label && (!binding.visible || it.visible) }?.let {
            binding.visible = binding.visible || it.visible
            binding.seenAt = now
        }
    }
    // Unknown models remain conservative; a known label gets four scan intervals of packet grace.
    fun current(id: java.util.UUID, now: Long) = bindings[id]?.let { now - it.seenAt in 0..1000 } ?: true
    fun label(id: java.util.UUID) = bindings[id]?.label
    fun release(id: java.util.UUID) { bindings.remove(id) }
    fun reset() { bindings.clear() }
}
class EspCaptureMemory {
    private data class Attempt(val id: java.util.UUID, val species: String, val at: Long)
    private val pending = mutableListOf<Attempt>()
    private val captured = mutableSetOf<java.util.UUID>()
    private val recent = mutableMapOf<String, Attempt>()
    private data class Throw(val at: Long, val aim: Map<String, Attempt>)
    private val input = mutableListOf<Throw>()
    fun sighted(id: java.util.UUID, species: String, now: Long) {
        if (!hidden(id)) recent[species] = Attempt(id, species, now)
    }
    /** Freeze aim at item use, before a capsule impact or camera turn changes the scan. */
    fun throwing(now: Long) {
        input.removeAll { now - it.at !in 0..3000 }
        val aim = recent.filterValues { now - it.at in 0..250 && !hidden(it.id) }
        if (aim.isNotEmpty()) input.add(Throw(now, aim))
    }
    fun threw(species: String, now: Long) {
        input.removeAll { now - it.at !in 0..3000 }
        val index = input.indexOfFirst { it.aim[species]?.let { aim -> !hidden(aim.id) } == true }
        val attempt = if (index >= 0) input.removeAt(index).aim.getValue(species)
            else recent[species]?.takeIf { now - it.at in 0..1000 } ?: return
        aimed(attempt.id, species, now)
    }
    private fun expire(now: Long) { pending.removeAll { now - it.at !in 0..60000 } }
    fun aimed(id: java.util.UUID, species: String, now: Long) {
        expire(now)
        if (hidden(id)) return
        val index = pending.indexOfFirst { it.id == id }
        if (index < 0) pending.add(Attempt(id, species, now)) else pending[index] = Attempt(id, species, now)
    }
    fun escaped(species: String, now: Long): java.util.UUID? {
        expire(now)
        val attempt = pending.firstOrNull { it.species == species } ?: return null
        pending.remove(attempt)
        return attempt.id
    }
    fun caught(species: String, now: Long): java.util.UUID? {
        expire(now)
        val attempt = pending.firstOrNull { it.species == species } ?: return null
        pending.remove(attempt); captured.add(attempt.id)
        recent.entries.removeAll { it.value.id == attempt.id }
        return attempt.id
    }
    fun hidden(id: java.util.UUID) = id in captured
    fun reset() { pending.clear(); captured.clear(); recent.clear(); input.clear() }
}
object SafariEspRules {
    private val throwMessage = Regex("^You threw a (?:Masterful )?Critter Capsule at the (.+)!$")
    private val escapeMessage = Regex("^The (.+?) (?:escaped your (?:Masterful )?Critter Capsule|dodged your critter capsule)[!.]?$")
    fun labelSpecies(raw: String) = SafariRules.strip(raw).let { SafariRules.sparklingSpecies(it) ?: SafariRoster.named(it)?.name }
    fun thrownSpecies(text: String) = throwMessage.matchEntire(text)?.groupValues?.get(1)?.let(::labelSpecies)
    fun escapedSpecies(text: String) = escapeMessage.matchEntire(text)?.groupValues?.get(1)?.let(::labelSpecies)
    private val profitableSpecies = setOf("Hideonfloor", "Hideonwall", "Chuckwalla", "Fluffling", "Mantis Shrimp")
    fun profitableEsp(species: String, enabled: Boolean) = enabled && species in profitableSpecies
    fun neededForRun(species: String, mound: Boolean, run: SafariRun?, fullClear: Boolean, profitable: Boolean = true,
        party: PartySparklingState? = null) =
        if (fullClear) true
        else if (species == "Rockmite" && mound && party != null)
            SafariHelperRules.needed(species, if (fullClear) SafariMode.FULL_CLEAR else SafariMode.UNIQUE, run, party)
        else species == "Rockmite" && mound || (run?.count(species) ?: 0) == 0 && run?.uniqueUnavailable(species) != true
    fun neededForSparkling(species: String, mound: Boolean, party: PartySparklingState, profitable: Boolean,
        run: SafariRun? = null, uuid: java.util.UUID? = null, now: Long = System.currentTimeMillis()): Boolean {
        if (profitableEsp(species, profitable)) return true
        if (species == "Rockmite" && !mound) return false
        if (species == "Doomspiral") return party.needs(species) && (run?.count(species) ?: 0) == 0
        if (run?.sparklingChecks?.wumpaPrerequisite(species, party) == true) return run.count(species) == 0
        if (SafariRoster.named(species) != null && party.needs(species) &&
            species != "Driftling" && !mound &&
            uuid != null && run?.sparklingChecks?.keepEsp(uuid, now) == false) return false
        if (!party.needs(species)) return false
        if (species == "Driftling") return true
        if (mound && species == "Rockmite") return run?.sparklingChecks?.checked(species) != true
        return uuid == null || run?.sparklingChecks?.keepEsp(uuid, now) != false
    }
    fun captureModel(type: String) = type.endsWith("display") || type == "armor_stand"
    fun requiresModelLabel(species: String, type: String) = captureModel(type) && !(species == "Rockmite" && type == "item_display")
    fun modelVisible(x: Float, y: Float, z: Float) = listOf(x, y, z).all { it.isFinite() } &&
        maxOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z)) > .001f
    fun capturedDisplay(species: String, candidates: List<EspCaptureCandidate>) = candidates.filter {
        it.species == species && it.display && !it.mound && it.rangeSquared <= 80.0 * 80
    }.minByOrNull { it.aimSquared + it.rangeSquared * .001 }?.id
    val mobs = listOf(
        EspMob("Cavernfish", SafariBiome.CAVERN, 0xFFB4641E.toInt(), listOf(EspEntity("tropical_fish", fish = "CLAYFISH/GRAY/BROWN"))),
        EspMob("Flitter", SafariBiome.CAVERN, 0xFF285A6E.toInt(), listOf(EspEntity("item_display", texture = "a89a76deedd42b410344100df2fa79b6eeac7e6f287745d656179368340ffade"))),
        EspMob("Shyworm", SafariBiome.CAVERN, 0xFF50A032.toInt(), listOf(EspEntity("armor_stand", texture = "b4287b8a0a642dac535a6ee29459efd17d5cee1eb359e436bc8e7abba3da14b7"))),
        EspMob("Driftling", SafariBiome.CAVERN, 0xFF966E3C.toInt(), listOf(EspEntity("armor_stand", texture = "f4c4f8e5fce1ec2d299cb8a395792ecddc497a1d8af86faaa5e20373016c7225"))),
        EspMob("Chuckwalla", SafariBiome.CAVERN, 0xFF3C3228.toInt(), listOf(EspEntity("item_display", texture = "fc63cd0d480971a7beae5fd503e5d51658cd906330843cbad92018f5b98b4fe5"))),
        EspMob("Rockmite", SafariBiome.CAVERN, 0xFFA0A0A0.toInt(), listOf(EspEntity("silverfish"), EspEntity("item_display", texture = "5dbaab74d1acd0abe9d04abe9928725de5d4495fcb63b647228caf6944c20800"))),
        EspMob("Scrappy", SafariBiome.CAVERN, 0xFFBE7882.toInt(), listOf(EspEntity("armadillo"))),
        EspMob("Snoozle", SafariBiome.CAVERN, 0xFFA02828.toInt(), listOf(EspEntity("sniffer"))),
        EspMob("Gemzie", SafariBiome.CAVERN, 0xFF96B4F0.toInt(), listOf(EspEntity("vex"))),
        EspMob("Foxtrot", SafariBiome.FOREST, 0xFFF06E14.toInt(), listOf(EspEntity("fox"))),
        EspMob("Bluebird", SafariBiome.FOREST, 0xFF1432C8.toInt(), listOf(EspEntity("parrot", variant = "BLUE"))),
        EspMob("Honeybug", SafariBiome.FOREST, 0xFFF0BE1E.toInt(), listOf(EspEntity("bee"))),
        EspMob("Treefrog", SafariBiome.FOREST, 0xFF6E825A.toInt(), listOf(EspEntity("frog"))),
        EspMob("Woodchucker", SafariBiome.FOREST, 0xFF504646.toInt(), listOf(EspEntity("creaking"))),
        EspMob("Fluffling", SafariBiome.FOREST, 0xFFE6E6E6.toInt(), listOf(EspEntity("panda"))),
        EspMob("Hideonfloor", SafariBiome.FOREST, 0xFF648228.toInt(), listOf(EspEntity("shulker", shulker = "GREEN"), EspEntity("block_display", shulker = "GREEN"), EspEntity("item_display", shulker = "GREEN"))),
        EspMob("Parakeet", SafariBiome.FOREST, 0xFF5AC832.toInt(), listOf(EspEntity("parrot", variant = "GREEN"))),
        EspMob("Macaw", SafariBiome.FOREST, 0xFFD21E1E.toInt(), listOf(EspEntity("parrot", variant = "RED_BLUE"))),
        EspMob("Areita", SafariBiome.HAUNTED, 0xFF145A5A.toInt(), listOf(EspEntity("cave_spider"))),
        EspMob("Bloodbat", SafariBiome.HAUNTED, 0xFF5A281E.toInt(), listOf(EspEntity("bat"))),
        EspMob("Duplico", SafariBiome.HAUNTED, 0xFF787878.toInt(), listOf(EspEntity("interaction"))),
        EspMob("Gazer", SafariBiome.HAUNTED, 0xFF1E3246.toInt(), listOf(EspEntity("armor_stand", texture = "407b3c3d2c3fe259d69207a14ca5cd99713c7096ba122bb40326f3489e5d0d6c"))),
        EspMob("Litterbug", SafariBiome.HAUNTED, 0xFF8232AA.toInt(), listOf(EspEntity("endermite"))),
        EspMob("Solsnatcher", SafariBiome.HAUNTED, 0xFF323C82.toInt(), listOf(EspEntity("phantom"))),
        EspMob("Gimmiegold", SafariBiome.HAUNTED, 0xFFFFD200.toInt(), listOf(EspEntity("item_display", texture = "8b329e108ac28b0bec8d47b7cdce253df1db80b46052b5915d963e1bcbab0db4"))),
        EspMob("Hideonwall", SafariBiome.HAUNTED, 0xFF8C468C.toInt(), listOf(EspEntity("shulker", shulker = "PURPLE"), EspEntity("block_display", shulker = "PURPLE"), EspEntity("item_display", shulker = "PURPLE"))),
        EspMob("Hideyho", SafariBiome.HAUNTED, 0xFFBEBEBE.toInt(), listOf(EspEntity("player", texture = "3504f1f2327a5110e643bb8667082512815fa434a29ed37f4ca83bb16d2db533"))),
        EspMob("Doomspiral", SafariBiome.HAUNTED, 0xFF148C96.toInt(), listOf(EspEntity("warden"))),
        EspMob("Strongarm", SafariBiome.ICY, 0xFFDC7814.toInt(), listOf(EspEntity("snow_golem"))),
        EspMob("Tepid", SafariBiome.ICY, 0xFFC8D2C8.toInt(), listOf(EspEntity("tropical_fish", fish = "SNOOPER/WHITE/WHITE"))),
        EspMob("Polaris", SafariBiome.ICY, 0xFFFFFFFF.toInt(), listOf(EspEntity("polar_bear"))),
        EspMob("Shuddersquid", SafariBiome.ICY, 0xFF32BEB4.toInt(), listOf(EspEntity("glow_squid"))),
        EspMob("Billygoat", SafariBiome.ICY, 0xFFE6DCC8.toInt(), listOf(EspEntity("goat"))),
        EspMob("Mantis Shrimp", SafariBiome.ICY, 0xFF285A64.toInt(), listOf(EspEntity("item_display", texture = "9924c105aa431dabd47952dc1dddd6f751f883423f4db1487d9bacc2cfe99c7a"))),
        EspMob("Nozzlenose", SafariBiome.ICY, 0xFF8C96AA.toInt(), listOf(EspEntity("dolphin"))),
        EspMob("Troodon", SafariBiome.ICY, 0xFF7850BE.toInt(), listOf(EspEntity("item_display", texture = "53de4135a3b19a2187029c86a0020e58c907c7bdd4e37b7643f120e16a0aa9ab"))),
        EspMob("Wumpa", SafariBiome.ICY, 0xFF645A5A.toInt(), listOf(EspEntity("ravager")))
    )
    private val byType = mobs.flatMap { mob -> mob.identifiers.map { it.type to (mob to it) } }.groupBy({ it.first }, { it.second })
    fun identify(entity: EspEntity): EspMob? {
        if (entity.type.endsWith("display") && entity.invisible) return null
        if (entity.type == "silverfish" && (entity.invisible || entity.passengers)) return null
        return byType[entity.type]?.firstOrNull { (_, rule) ->
            (rule.texture == null || rule.texture == entity.texture) && (rule.shulker == null || rule.shulker == entity.shulker) &&
            (rule.fish == null || rule.fish == entity.fish) && (rule.variant == null || rule.variant == entity.variant)
        }?.first
    }
    fun targetCurrent(species: String, descriptor: EspEntity, removed: Boolean, registered: Boolean, sameLevel: Boolean) =
        !removed && registered && sameLevel && identify(descriptor)?.name == species
    /** Name-tag distance and idle movement are not reliable reasons to hide legacy moving-critter ESP. */
    fun renderCurrent(species: String, sparkling: Boolean, entityCurrent: Boolean, motionCurrent: Boolean, labelCurrent: Boolean, profitable: Boolean = false) =
        entityCurrent && (profitableEsp(species, profitable) || species == "Shyworm" || !sparkling && species in EspMotion.movingSpecies || motionCurrent && labelCurrent)
    fun textureHash(encoded: String?): String? = try {
        val json = String(Base64.getDecoder().decode(encoded ?: return null), Charsets.UTF_8)
        Regex("textures\\.minecraft\\.net/texture/([a-fA-F0-9]{64})(?![a-fA-F0-9])").find(json)?.groupValues?.get(1)?.lowercase()
    } catch (_: IllegalArgumentException) { null }
    fun biomeAt(x: Double, z: Double): SafariBiome? = when {
        x !in -200.0..100.0 || z !in -150.0..150.0 -> null
        x >= -50 && z >= 0 -> SafariBiome.FOREST
        x >= -50 -> SafariBiome.HAUNTED
        z >= 0 -> SafariBiome.CAVERN
        else -> SafariBiome.ICY
    }
    fun visible(inSafari: Boolean, enabled: Boolean, onlyInBiome: Boolean, player: SafariBiome?, mob: SafariBiome?) =
        inSafari && enabled && mob != null && (!onlyInBiome || player != null && player == mob)
    fun floorDrops(displays: List<EspDrop>) = displays.groupBy { Triple(it.x, it.y, it.z) }.values
        .filter { it.size >= 3 }.map { group -> group.minBy { it.id } }
}

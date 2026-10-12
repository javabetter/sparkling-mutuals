package net.johnceo.sparklingmutuals.safari

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.johnceo.sparklingmutuals.LocalChat
import net.johnceo.sparklingmutuals.commands.PartyChat
import net.johnceo.sparklingmutuals.config.ConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity

/**
 * Holds the Safari Manager shut for a while after someone calls a kick in party chat, so nobody starts the run
 * before the kick happens, and while a `!dt` downtime hold stands (see SafariDowntime). Both ways in are guarded:
 * the leader's click on the manager, and a member's ticket.
 * Only on the Safari island itself, never at the Critter Safari zone on Torrhus Canyon.
 * Sneaking goes anyway. The right-click is cancelled in SafariManagerMixin, since Fabric fires no client event for it.
 */
object SafariManagerGuard {
    const val HOLD_MILLIS = 30_000L
    private const val MESSAGE_COOLDOWN = 3_000L
    private const val MANAGER = "Safari Manager"
    private const val TICKET = "Critter Safari Ticket"
    /** "kick" as its own word, so "kickstart" doesn't count. */
    private val kickWord = Regex("\\bkick\\b", RegexOption.IGNORE_CASE)
    var heldUntil = 0L
        private set
    private var messagedAt = 0L

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) Minecraft.getInstance().execute { chat(message.string, System.currentTimeMillis()) }
        }
        AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
            if (player === Minecraft.getInstance().player && shouldBlock(entity)) InteractionResult.FAIL else InteractionResult.PASS
        }
        UseItemCallback.EVENT.register { player, _, hand ->
            val client = Minecraft.getInstance()
            val name = SafariRules.strip(player.getItemInHand(hand).hoverName.string)
            if (player === client.player && name.contains(TICKET, true) && blocks(client)) InteractionResult.FAIL else InteractionResult.PASS
        }
    }

    fun callsKick(text: String) = PartyChat.parse(text)?.let { kickWord.containsMatchIn(it.body) } == true
    fun chat(text: String, now: Long) {
        if (!ConfigManager.holdManagerOnKick || !callsKick(text)) return
        heldUntil = now + HOLD_MILLIS
        Minecraft.getInstance()?.player?.sendSystemMessage(LocalChat.component(
            "[SM] Kick called: Safari Manager held for ${HOLD_MILLIS / 1000}s. Sneak to go anyway."))
    }
    fun holding(now: Long = System.currentTimeMillis()) = now < heldUntil
    fun reset() { heldUntil = 0L }

    /** Whether clicking this entity should be refused. Cheap for everything that isn't the manager. */
    fun shouldBlock(entity: Entity?): Boolean {
        val client = Minecraft.getInstance()
        return entity != null && (holding() || SafariDowntime.holding) && isManager(client, entity) && blocks(client)
    }
    private fun blocks(client: Minecraft): Boolean {
        val player = client.player ?: return false
        val kick = ConfigManager.holdManagerOnKick && holding()
        val downtime = SafariDowntime.holding // false whenever the downtime setting is off
        if (!(kick || downtime) || !SafariIsland.onIsland) return false
        if (player.isShiftKeyDown) {
            // Deliberate override; it also spends the downtime hold, which is a one-shot.
            if (downtime) SafariDowntime.clear()
            return false
        }
        val now = System.currentTimeMillis()
        if (now - messagedAt > MESSAGE_COOLDOWN) {
            messagedAt = now
            val reason = if (downtime) "${SafariDowntime.holders()} asked for downtime"
                else "a kick was called (${(heldUntil - now + 999) / 1000}s left)"
            player.sendSystemMessage(LocalChat.component("[SM] Blocked: $reason. Sneak to go anyway."))
        }
        return true
    }
    /** Hypixel NPCs carry their visible name on separate nameplate stands, so those nearby count too. */
    private fun isManager(client: Minecraft, entity: Entity): Boolean {
        fun named(e: Entity) = SafariRules.strip(e.customName?.string ?: e.name.string).contains(MANAGER, true)
        if (named(entity)) return true
        return client.level?.getEntities(entity, entity.boundingBox.inflate(3.0))?.any(::named) == true
    }
}

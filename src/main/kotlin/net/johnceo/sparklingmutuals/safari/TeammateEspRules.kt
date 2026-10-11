package net.johnceo.sparklingmutuals.safari

import java.util.UUID

object TeammateEspRules {
    fun eligible(skyblock: Boolean, rosterKnown: Boolean, uuid: UUID, local: UUID?, members: List<String>) =
        skyblock && rosterKnown && local != null && uuid != local && members.any { it.equals(uuid.toString(), true) }
    fun nameScale(distance: Double) = (distance * .12).coerceAtLeast(1.0)
}

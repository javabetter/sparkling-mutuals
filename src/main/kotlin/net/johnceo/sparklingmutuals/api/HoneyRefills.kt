package net.johnceo.sparklingmutuals.api

import com.google.gson.JsonObject

/** A profile's honeyhive refill times; `refills` is null when the profile has no honey data at all. */
data class HoneyRefills(val profile: String?, val refills: Map<String, Long>?) {
    companion object {
        /**
         * Hypixel (and the shared API, which keeps the field as-is) leaves out `foraging.honey` for a profile that
         * has never looted a hive. That is "unknown", not "every hive full", so it reads as null; an existing honey
         * section without pending refills reads as an empty map.
         */
        fun read(member: JsonObject): Map<String, Long>? {
            val honey = member.getAsJsonObject("foraging")?.get("honey")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val times = honey.get("refill_times")?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyMap()
            return times.entrySet().filter { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isNumber }
                .associate { it.key to it.value.asLong }
        }
    }
}

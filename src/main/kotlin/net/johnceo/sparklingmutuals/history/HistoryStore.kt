package net.johnceo.sparklingmutuals.history

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/** JSON files in the config folder for the history features, each read once and rewritten whole. */
object HistoryStore {
    private val logger = LoggerFactory.getLogger("sparkling-mutuals/history")
    private val gson = GsonBuilder().setPrettyPrinting().create()
    /** Overridable for tests. */
    var directory: Path? = null
    private fun path(name: String) = (directory ?: FabricLoader.getInstance().configDir).resolve("sparkling-mutuals-$name.json")

    fun <T> load(name: String, type: Class<T>, empty: () -> T): T {
        val file = path(name)
        if (!Files.exists(file)) return empty()
        return runCatching { gson.fromJson(Files.readString(file), type) ?: empty() }
            .onFailure { logger.warn("Couldn't read {} — starting empty", file.fileName, it) }.getOrElse { empty() }
    }
    fun save(name: String, value: Any) {
        runCatching { val file = path(name); Files.createDirectories(file.parent); Files.writeString(file, gson.toJson(value)) }
            .onFailure { logger.warn("Couldn't save {}", name, it) }
    }
}

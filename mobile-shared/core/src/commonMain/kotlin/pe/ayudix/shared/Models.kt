package pe.ayudix.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class PairingPayload(val version: Int, val serverName: String, val urls: List<String>, val token: String) {
    override fun toString() = "PairingPayload(serverName=$serverName, token=<redacted>)"
}
@Serializable
data class ComputerConnection(
    val serverName: String, val baseUrl: String, val urls: List<String>,
    val deviceId: String, val token: String, val dataIdentity: String,
) {
    val key: String get() = "$dataIdentity|$deviceId"
    override fun toString() = "ComputerConnection(serverName=$serverName, token=<redacted>)"
}
@Serializable
data class ConnectionRegistry(val schema: Int = 1, val active: String = "", val computers: List<ComputerConnection> = emptyList()) {
    fun selected() = computers.firstOrNull { it.key == active }
    fun withConnection(value: ComputerConnection): ConnectionRegistry {
        require(computers.any { it.key == value.key } || computers.size < 20) { "Puedes guardar hasta 20 computadoras. Retira una antes de agregar otra." }
        return copy(active = value.key, computers = computers.filterNot { it.key == value.key } + value)
    }
    fun select(key: String): ConnectionRegistry {
        require(computers.any { it.key == key }) { "Computadora no vinculada." }
        return copy(active = key)
    }
    fun remove(key: String) = copy(active = if (active == key) "" else active, computers = computers.filterNot { it.key == key })
    companion object {
        fun decode(value: String): ConnectionRegistry = wireJson.decodeFromString<ConnectionRegistry>(value).also {
            require(it.schema == 1 && it.computers.size <= 20 && it.computers.map { c -> c.key }.distinct().size == it.computers.size) { "No se pudo leer la vinculación guardada. No se modificó." }
            it.computers.forEach { c -> NetworkPolicy.requirePrivateBase(c.baseUrl); require(c.deviceId.isNotBlank() && c.token.length in 40..128 && c.token.all { char -> char.code in 33..126 } && c.dataIdentity.isNotBlank()) }
            require(it.active.isEmpty() || it.computers.any { c -> c.key == it.active })
        }
    }
    fun encode(): String = wireJson.encodeToString(this)
    override fun toString() = "ConnectionRegistry(schema=$schema, computers=${computers.size}, credentials=<redacted>)"
}

/** Implemented with Keychain on iOS, never with model memory or plain preferences. */
interface SecureConnectionStore {
    fun load(): String?
    fun save(value: String): Boolean
}
interface QrResult {
    fun received(value: String)
    fun cancelled()
    fun failed(message: String)
}
interface QrScanner { fun start(callback: QrResult) }

@Serializable data class HealthResponse(val service: String, val version: String, val status: String, val dataIdentity: String? = null)
@Serializable internal data class Registration(val deviceId: String, val name: String)
@Serializable internal data class DeviceCredential(val deviceId: String, val token: String)
@Serializable data class ApiShare(val id: String, val name: String, val writable: Boolean = false, val manageable: Boolean = false, val fullAccess: Boolean = false)
@Serializable data class ShareSummary(val id: String, val name: String)
@Serializable data class FileEntry(val name: String, val relativePath: String, val kind: String, val size: Long, val modifiedAt: String, val mediaType: String) {
    val isFolder get() = kind == "folder"
}
@Serializable data class FileListResponse(val share: ShareSummary, val path: String, val parentPath: String? = null, val entries: List<FileEntry>)
data class ChatMessage(val id: String, val role: String, val text: String)
data class AssistantSnapshot(val sessionId: String, val messages: List<ChatMessage>, val busy: Boolean, val raw: JsonObject)
open class ClientFailure(message: String) : Exception(message)
class AccessRevoked : ClientFailure("La PC retiró el acceso a este dispositivo. Revisa sus permisos en Ayudix de Windows; no se volvió a vincular automáticamente.")
class UncertainSend : ClientFailure("No se recibió la confirmación de envío. Actualiza la conversación antes de reenviar; tu orden podría haber llegado a la PC.")

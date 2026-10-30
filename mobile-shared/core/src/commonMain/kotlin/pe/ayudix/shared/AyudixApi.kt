package pe.ayudix.shared

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

expect fun platformHttpClient(): HttpClient
expect fun newRequestId(): String

fun configurePrivateClient(client: HttpClient): HttpClient = client.config {
    followRedirects = false
    expectSuccess = false
    install(HttpTimeout) { connectTimeoutMillis = 6000; requestTimeoutMillis = 15000; socketTimeoutMillis = 15000 }
}

/** No automatic retries of registration or assistant sends. Credentials stay in headers. */
class AyudixApi(private val http: HttpClient) {
    private suspend fun json(base: String, path: String, token: String? = null, body: JsonObject? = null, query: Map<String,String> = emptyMap()): String {
        val response = http.request(NetworkPolicy.endpoint(base, path, query)) {
            method = if (body == null) HttpMethod.Get else HttpMethod.Post
            header(HttpHeaders.Accept, ContentType.Application.Json)
            if (token != null) header(HttpHeaders.Authorization, "Bearer $token")
            if (body != null) { contentType(ContentType.Application.Json); setBody(body.toString()) }
        }
        if (response.status.value in 300..399) { response.bodyAsChannel().cancel(); throw ClientFailure("La PC intentó redirigir la conexión. No se siguió el enlace ni se reenvió la credencial.") }
        if (response.status.value in listOf(401,403)) { response.bodyAsChannel().cancel(); throw AccessRevoked() }
        val channel = response.bodyAsChannel()
        // Accumulate bounded bytes before parsing. Headers alone are not a size proof.
        val bytes = mutableListOf<ByteArray>(); var total = 0
        try {
            val buffer = ByteArray(8192)
            while (true) { val count = channel.readAvailable(buffer,0,buffer.size); if (count == -1) break; if(count==0)continue; total += count; if(total>4_000_000)throw ClientFailure("El resultado es demasiado grande. Consulta una carpeta o tarea concreta."); bytes += buffer.copyOf(count) }
        } finally { channel.cancel() }
        val text = ByteArray(total).also { all -> var offset=0; bytes.forEach { part -> part.copyInto(all,offset); offset+=part.size } }.decodeToString()
        if (response.status.value !in 200..299) {
            val message = runCatching { wireJson.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull()
            throw ClientFailure(message?.take(1200) ?: "La PC no completó la solicitud (HTTP ${response.status.value}).")
        }
        return text
    }
    private inline fun <reified T> decode(value: String): T = runCatching { wireJson.decodeFromString<T>(value) }.getOrElse { throw ClientFailure("La respuesta de la PC no tiene el formato esperado. Revisa la versión de Ayudix en Windows.") }
    suspend fun pair(payload: PairingPayload, deviceId: String, deviceName: String): ComputerConnection {
        require(deviceId.matches(Regex("[a-fA-F0-9-]{36}")) && deviceName.isNotBlank() && deviceName.length<=120)
        for (base in payload.urls.sortedByDescending { NetworkPolicy.isTailscale(it) }) {
            val health = try { decode<HealthResponse>(json(base,"/health")) } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { continue }
            require(health.service == "ayudix-link" && health.status == "ok" && !health.dataIdentity.isNullOrBlank()) { "La dirección no corresponde a una instalación compatible de Ayudix." }
            // Once enrollment starts its outcome may be uncertain: never replay on another URL.
            val credential = decode<DeviceCredential>(json(base,"/api/v1/devices/register",payload.token,buildJsonObject{put("deviceId",deviceId);put("name",deviceName)}))
            require(credential.deviceId == deviceId && credential.token.length in 40..128 && credential.token.all { it.code in 33..126 }) { "La PC devolvió una credencial inválida." }
            return ComputerConnection(payload.serverName,base,payload.urls,deviceId,credential.token,health.dataIdentity)
        }
        throw ClientFailure("No se pudo alcanzar tu PC. Comprueba que Ayudix esté encendido y que ambos dispositivos tengan acceso a la misma red privada.")
    }
    private suspend fun verifyIdentity(connection: ComputerConnection, base: String) {
        val health = decode<HealthResponse>(json(base,"/health"))
        require(health.service == "ayudix-link" && health.status == "ok" && health.dataIdentity == connection.dataIdentity) { "La dirección responde con otra instalación. No se envió la credencial." }
    }
    suspend fun shares(connection: ComputerConnection): List<ApiShare> {
        verifyIdentity(connection,connection.baseUrl)
        return decode<List<ApiShare>>(json(connection.baseUrl,"/api/v1/shares",connection.token)).sortedBy{it.name.lowercase()}
    }
    suspend fun files(connection: ComputerConnection, shareId: String, path: String): FileListResponse {
        NetworkPolicy.relativePath(path)
        require(shareId.isNotBlank() && shareId.length<=200)
        verifyIdentity(connection,connection.baseUrl)
        return decode<FileListResponse>(json(connection.baseUrl,"/api/v1/files",connection.token,query=mapOf("shareId" to shareId,"path" to path))).also { response ->
            require(response.share.id == shareId && response.path == path) { "La PC respondió con otra carpeta. No se cambió la ubicación." }
        }
    }
    private suspend fun assistant(connection: ComputerConnection, body: JsonObject): JsonObject {
        val base = NetworkPolicy.assistantBase(connection)
        verifyIdentity(connection,base)
        return decode(json(base,"/api/v1/assistant",connection.token,body))
    }
    suspend fun snapshot(connection: ComputerConnection): AssistantSnapshot {
        val value = assistant(connection,buildJsonObject { put("action","snapshot") })
        val workspace = value["workspace"] as? JsonObject
        val session = if(workspace?.get("enabled")?.jsonPrimitive?.booleanOrNull == true) (workspace["session"] as? JsonObject)?.get("id")?.jsonPrimitive?.content.orEmpty() else ""
        val messages = (value["messages"] as? JsonArray).orEmpty().mapNotNull { raw ->
            val m=raw as? JsonObject ?: return@mapNotNull null
            ChatMessage(m["id"]?.jsonPrimitive?.content.orEmpty(),m["role"]?.jsonPrimitive?.content.orEmpty(),m["text"]?.jsonPrimitive?.content.orEmpty())
        }
        val busy=(value["jobs"] as? JsonArray).orEmpty().any { raw -> val job=raw as? JsonObject; job?.get("kind")?.jsonPrimitive?.content=="assistant" && job["state"]?.jsonPrimitive?.content in listOf("queued","running","verifying") && (job["session"]?.jsonPrimitive?.content.orEmpty().isBlank() || job["session"]?.jsonPrimitive?.content == session) }
        return AssistantSnapshot(session,messages,busy,value)
    }
    suspend fun send(connection: ComputerConnection, text: String, requestId: String, sessionId: String): JsonObject {
        require(text.isNotBlank() && text.length<=16000 && requestId.matches(Regex("[a-fA-F0-9-]{36}")))
        // Policy and identity checks happen before the uncertain POST boundary.
        val base=NetworkPolicy.assistantBase(connection);verifyIdentity(connection,base)
        val body=buildJsonObject{put("action","send");put("prompt",text);put("requestId",requestId);if(sessionId.isNotBlank())put("sessionId",sessionId)}
        return try { decode(json(base,"/api/v1/assistant",connection.token,body)) } catch(cancel:CancellationException){throw cancel} catch(e:AccessRevoked){throw e} catch(e:ClientFailure){throw e} catch(e:Exception){throw UncertainSend()}
    }
    fun close() = http.close()
}

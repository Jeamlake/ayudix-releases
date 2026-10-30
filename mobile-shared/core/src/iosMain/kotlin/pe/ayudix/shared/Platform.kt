package pe.ayudix.shared
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSUUID
actual fun platformHttpClient(): HttpClient = configurePrivateClient(HttpClient(Darwin))
actual fun newRequestId(): String = NSUUID().UUIDString.lowercase()

package pe.ayudix.shared
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
actual fun platformHttpClient(): HttpClient = configurePrivateClient(HttpClient(CIO))
actual fun newRequestId(): String = java.util.UUID.randomUUID().toString()

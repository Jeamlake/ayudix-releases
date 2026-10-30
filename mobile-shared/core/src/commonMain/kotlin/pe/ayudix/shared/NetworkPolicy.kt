package pe.ayudix.shared

import io.ktor.http.*

object NetworkPolicy {
    private fun ipv4(host: String): List<Int>? {
        val pieces = host.split('.')
        if (pieces.size != 4 || pieces.any { it.isEmpty() || it.any { c -> c !in '0'..'9' } || (it.length > 1 && it.startsWith('0')) }) return null
        return pieces.map { it.toIntOrNull() ?: return null }.takeIf { it.all { p -> p in 0..255 } }
    }
    fun isTailscale(value: String): Boolean = runCatching {
        val url = Url(value); val ip = ipv4(url.host)
        url.protocol == URLProtocol.HTTP && ip != null && ip[0] == 100 && ip[1] in 64..127
    }.getOrDefault(false)
    fun requirePrivateBase(value: String): String {
        require(value.length in 1..500 && value.none { it.isWhitespace() || it == '\\' }) { "Dirección de PC inválida." }
        val u = runCatching { Url(value) }.getOrElse { throw ClientFailure("Dirección de PC inválida.") }
        require(u.user == null && u.password == null && u.parameters.isEmpty() && u.fragment.isEmpty() && u.encodedPath in listOf("", "/")) { "La dirección de PC no puede contener credenciales, rutas ni parámetros." }
        val ip = ipv4(u.host)
        val privateIp = ip != null && (ip[0] == 10 || ip[0] == 192 && ip[1] == 168 || ip[0] == 172 && ip[1] in 16..31 || ip[0] == 100 && ip[1] in 64..127)
        val secureTailnet = u.protocol == URLProtocol.HTTPS && u.host.endsWith(".ts.net") && u.host.length > 7
        require(privateIp && u.protocol in listOf(URLProtocol.HTTP, URLProtocol.HTTPS) || secureTailnet) { "Vincula una dirección privada de tu PC. No se enviaron credenciales a Internet." }
        require(u.port in 1024..65535 || u.protocol == URLProtocol.HTTPS && u.port == 443) { "Puerto de PC inválido." }
        return u.toString().trimEnd('/')
    }
    fun assistantBase(connection: ComputerConnection): String = (listOf(connection.baseUrl) + connection.urls).distinct().firstOrNull {
        runCatching { requirePrivateBase(it); isTailscale(it) || Url(it).protocol == URLProtocol.HTTPS }.getOrDefault(false)
    }?.let(::requirePrivateBase) ?: throw ClientFailure("Para conversar, conecta Tailscale en la PC y el iPhone y vuelve a escanear el QR si no contiene la ruta privada. El chat no usa HTTP Wi-Fi sin cifrar.")
    fun relativePath(path: String) {
        require(path.length <= 4096 && !path.startsWith('/') && !path.contains('\\') && !path.contains('\u0000') && path.split('/').none { it == ".." || it == "." }) { "La ruta debe estar dentro de la carpeta autorizada." }
    }
    fun endpoint(base: String, path: String, query: Map<String, String> = emptyMap()): String {
        require(path.startsWith("/api/v1/") || path == "/health")
        return URLBuilder(requirePrivateBase(base) + path).apply { query.forEach { (key, value) -> parameters.append(key, value) } }.buildString()
    }
    fun parsePairing(value: String): PairingPayload {
        require(value.length <= 16384) { "Código de vinculación demasiado grande." }
        val payload = runCatching { wireJson.decodeFromString<PairingPayload>(value.trim()) }.getOrElse { throw ClientFailure("Escanea el QR de Ayudix en Seguridad de tu PC.") }
        require(payload.version == 2 && payload.serverName.isNotBlank() && payload.serverName.length <= 200 && payload.token.length in 40..128 && payload.token.all { it.code in 33..126 }) { "El código de Ayudix no es válido. Usa el QR actual de Seguridad en Windows." }
        require(payload.urls.isNotEmpty() && payload.urls.size <= 16) { "El QR no contiene una ruta utilizable." }
        return payload.copy(urls = payload.urls.mapNotNull { runCatching { requirePrivateBase(it) }.getOrNull() }.distinct().also { require(it.isNotEmpty()) { "El QR no contiene una dirección privada compatible." } })
    }
}

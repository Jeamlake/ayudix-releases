package pe.ayudix.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ClientContractTest {
    private val device="00000000-0000-4000-8000-000000000001"
    private val request="00000000-0000-4000-8000-000000000002"
    private val bootstrap="b".repeat(64)
    private val credential="c".repeat(64)
    private val base="http://100.64.1.2:48271"
    private val connection=ComputerConnection("PC de prueba",base,listOf(base),device,credential,"synthetic-identity")
    private val health="""{"service":"ayudix-link","version":"0.5.0-alpha.26","status":"ok","dataIdentity":"synthetic-identity"}"""
    private fun payload(url:String=base)="""{"version":2,"serverName":"PC de prueba","urls":["$url"],"token":"$bootstrap","remotePairingToken":"ignored-and-not-retained"}"""
    private fun api(handler:suspend MockRequestHandleScope.(HttpRequestData)->io.ktor.client.request.HttpResponseData):AyudixApi = AyudixApi(configurePrivateClient(HttpClient(MockEngine(handler))))
    @Test fun pairingAcceptsCurrentDesktopQrAndRedactsSecrets(){
        val p=NetworkPolicy.parsePairing(payload());assertEquals(listOf(base),p.urls);assertEquals(bootstrap,p.token)
        assertFalse(p.toString().contains(bootstrap));assertFalse(connection.toString().contains(credential))
    }
    @Test fun publicAndMalformedBasesCannotReceiveCredentials(){
        listOf("http://example.com:48271","http://8.8.8.8:48271","http://100.63.1.2:48271","http://user:pass@100.64.1.2:48271","http://100.64.1.2:48271?token=secret","http://100.64.1.2:48271/#x","http://100.64.1.2:48271/private","http://010.0.0.1:48271","http://100.64.1.2:80","https://attacker.example:48271").forEach { assertFailsWith<Exception>(it){NetworkPolicy.requirePrivateBase(it)} }
        assertFailsWith<Exception>{NetworkPolicy.parsePairing(payload("http://example.com:48271"))}
    }
    @Test fun privateNetworksAndEncryptedTailnetAreAccepted(){
        listOf("http://192.168.18.100:48271","http://10.1.2.3:48271","http://172.31.1.2:48271",base,"https://pc.example.ts.net").forEach { assertEquals(it,NetworkPolicy.requirePrivateBase(it)) }
    }
    @Test fun chatNeverUsesUnencryptedLan(){
        val lan=connection.copy(baseUrl="http://192.168.18.100:48271",urls=emptyList())
        assertFailsWith<ClientFailure>{NetworkPolicy.assistantBase(lan)}
        assertEquals(base,NetworkPolicy.assistantBase(lan.copy(urls=listOf(base))))
    }
    @Test fun pathsCannotEscapeTheSelectedShare(){
        listOf("../private","a/../private","/etc/passwd","C:\\Users\\owner","a/./b","\u0000").forEach { assertFailsWith<Exception>{NetworkPolicy.relativePath(it)} }
        NetworkPolicy.relativePath("Semana 11/Informe José.pdf")
    }
    @Test fun equalNamesAndAddressesDoNotMixComputerIdentities(){
        val a=connection;val b=connection.copy(dataIdentity="another-identity",token="d".repeat(64))
        val registry=ConnectionRegistry().withConnection(a).withConnection(b)
        assertEquals(2,registry.computers.size);assertEquals(b,registry.selected())
        assertEquals(a,registry.select(a.key).selected());assertEquals(b,registry.remove(a.key).selected())
        val encoded=registry.encode();assertEquals(registry,ConnectionRegistry.decode(encoded))
        assertFalse(registry.toString().contains(credential))
    }
    @Test fun registryDoesNotSilentlyDropAnExistingComputer(){
        var registry=ConnectionRegistry()
        repeat(20){registry=registry.withConnection(connection.copy(dataIdentity="identity-$it"))}
        assertFailsWith<Exception>{registry.withConnection(connection.copy(dataIdentity="identity-21"))}
        assertEquals(20,registry.computers.size)
    }
    @Test fun corruptSavedStateIsRejectedInsteadOfOverwritten(){
        assertFailsWith<Exception>{ConnectionRegistry.decode("{} invalid")}
        assertFailsWith<Exception>{ConnectionRegistry.decode("""{"schema":2}""")}
        assertFailsWith<Exception>{ConnectionRegistry.decode(ConnectionRegistry(active="missing").encode())}
    }
    @Test fun enrollmentUsesBootstrapOnlyOnceAndReturnsAnIndependentCredential()=runTest{
        val calls=mutableListOf<HttpRequestData>()
        val api=api{r->calls+=r;when(r.url.encodedPath){"/health"->respond(health);"/api/v1/devices/register"->{assertEquals("Bearer $bootstrap",r.headers[HttpHeaders.Authorization]);respond("""{"deviceId":"$device","token":"$credential"}""")};else->error("Unexpected route")}}
        try {val saved=api.pair(NetworkPolicy.parsePairing(payload()),device,"iPhone de prueba");assertEquals(credential,saved.token);assertFalse(saved.encodeForTest().contains(bootstrap));assertEquals(2,calls.size);assertNull(calls[0].headers[HttpHeaders.Authorization])} finally{api.close()}
    }
    @Test fun aWrongDeviceCredentialCannotFinishEnrollment()=runTest{
        val api=api{r->if(r.url.encodedPath=="/health")respond(health)else respond("""{"deviceId":"other-device","token":"$credential"}""")}
        try{assertFailsWith<Exception>{api.pair(NetworkPolicy.parsePairing(payload()),device,"iPhone")}}finally{api.close()}
    }
    @Test fun aDifferentDataIdentityNeverReceivesTheSavedCredential()=runTest{
        var calls=0
        val api=api{r->calls++;assertNull(r.headers[HttpHeaders.Authorization]);respond(health.replace("synthetic-identity","other-identity"))}
        try{assertFailsWith<Exception>{api.shares(connection)};assertEquals(1,calls)}finally{api.close()}
    }
    @Test fun aRedirectIsNotFollowedAndNeverReceivesAuthorization()=runTest{
        val calls=mutableListOf<HttpRequestData>()
        val api=api{r->calls+=r;if(r.url.encodedPath=="/health")respond(health)else respond("",HttpStatusCode.Found,headersOf(HttpHeaders.Location,"https://example.com/private"))}
        try{assertFailsWith<ClientFailure>{api.shares(connection)};assertEquals(2,calls.size);assertTrue(calls.all{it.url.host=="100.64.1.2"})}finally{api.close()}
    }
    @Test fun revokedDevicesAreNotSilentlyReEnrolled()=runTest{
        var registrations=0
        val api=api{r->when(r.url.encodedPath){"/health"->respond(health);"/api/v1/devices/register"->{registrations++;respond("",HttpStatusCode.Forbidden)};else->respond("",HttpStatusCode.Unauthorized)}}
        try{assertFailsWith<AccessRevoked>{api.shares(connection)};assertEquals(0,registrations)}finally{api.close()}
    }
    @Test fun unicodeFolderNamesAreEncodedAsQueryValues()=runTest{
        val folder="Semana 11/Redes & comunicación"
        val api=api{r->if(r.url.encodedPath=="/health")respond(health)else {assertEquals(folder,r.url.parameters["path"]);assertEquals("share-id",r.url.parameters["shareId"]);respond(buildJsonObject{put("share",buildJsonObject{put("id","share-id");put("name","Redes")});put("path",folder);put("entries",JsonArray(emptyList()))}.toString())}}
        try{assertEquals(folder,api.files(connection,"share-id",folder).path)}finally{api.close()}
    }
    @Test fun anotherFolderResponseDoesNotChangeTheWorkingLocation()=runTest{
        val api=api{r->if(r.url.encodedPath=="/health")respond(health)else respond("""{"share":{"id":"another","name":"Other"},"path":"","entries":[]}""")}
        try{assertFailsWith<Exception>{api.files(connection,"share-id","")}}finally{api.close()}
    }
    @Test fun aSendWithUnknownOutcomeIsNeverAutomaticallyRepeated()=runTest{
        var sends=0
        val api=api{r->if(r.url.encodedPath=="/health")respond(health)else{sends++;throw Exception("Synthetic disconnected transport")}}
        try{assertFailsWith<UncertainSend>{api.send(connection,"Abre Calculadora",request,"session-1")};assertEquals(1,sends)}finally{api.close()}
    }
    @Test fun snapshotUsesTheDesktopSessionAndShowsOnlyItsMessages()=runTest{
        val api=api{r->if(r.url.encodedPath=="/health")respond(health)else respond("""{"workspace":{"enabled":true,"session":{"id":"session-1"}},"messages":[{"id":"m1","role":"assistant","text":"Hola"}],"jobs":[{"kind":"assistant","state":"running","session":"another"}]}""")}
        try{val snapshot=api.snapshot(connection);assertEquals("session-1",snapshot.sessionId);assertEquals("Hola",snapshot.messages.single().text);assertFalse(snapshot.busy)}finally{api.close()}
    }
    @Test fun successfulSendCarriesOnlyTheExplicitSessionAndRequestId()=runTest{
        val api=api{r->if(r.url.encodedPath=="/health")respond(health)else{val body=(r.body as io.ktor.http.content.TextContent).text;val p=wireJson.parseToJsonElement(body).jsonObject;assertEquals(request,p["requestId"]?.jsonPrimitive?.content);assertEquals("session-1",p["sessionId"]?.jsonPrimitive?.content);assertEquals("send",p["action"]?.jsonPrimitive?.content);respond("""{"accepted":true}""")}}
        try{assertEquals(true,api.send(connection,"Hola",request,"session-1")["accepted"]?.jsonPrimitive?.boolean)}finally{api.close()}
    }
    private fun ComputerConnection.encodeForTest()=ConnectionRegistry().withConnection(this).encode()
}

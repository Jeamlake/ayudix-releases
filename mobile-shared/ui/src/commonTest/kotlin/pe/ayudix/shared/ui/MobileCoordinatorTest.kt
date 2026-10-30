package pe.ayudix.shared.ui

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*
import pe.ayudix.shared.*

@OptIn(ExperimentalCoroutinesApi::class)
class MobileCoordinatorTest {
    private val device="00000000-0000-4000-8000-000000000001"
    private val base="http://100.64.1.2:48271"
    private val health="""{"service":"ayudix-link","version":"0.5.0-alpha.26","status":"ok","dataIdentity":"synthetic-identity"}"""
    private val code="""{"version":2,"serverName":"PC sintética","urls":["$base"],"token":"${"b".repeat(64)}"}"""
    private val scanner=object:QrScanner{override fun start(callback:QrResult)=callback.cancelled()}
    @BeforeTest fun setup(){Dispatchers.setMain(StandardTestDispatcher())}
    @AfterTest fun teardown(){Dispatchers.resetMain()}
    private class Store(var saved:String?=null,var canWrite:Boolean=true):SecureConnectionStore{
        var writes=0
        override fun load()=saved
        override fun save(value:String):Boolean{writes++;if(!canWrite)return false;saved=value;return true}
    }
    private fun newApi(handler:suspend MockRequestHandleScope.(HttpRequestData)->HttpResponseData):AyudixApi {
        val engine=MockEngine(MockEngineConfig().apply{dispatcher=Dispatchers.Main;addHandler(handler)})
        return AyudixApi(configurePrivateClient(HttpClient(engine)))
    }
    @Test fun corruptKeychainLocksWritesBeforeAnyNetworkRequest()=runTest{
        val store=Store("KEYCHAIN_UNAVAILABLE_DO_NOT_REPLACE");var calls=0
        val api=newApi{calls++;respond("{}")}
        val model=MobileCoordinator(store,scanner,device,"Prueba",api)
        try{assertFalse(model.state.value.storageHealthy);model.connect(code);advanceUntilIdle();assertEquals(0,calls);assertEquals(0,store.writes);assertEquals("KEYCHAIN_UNAVAILABLE_DO_NOT_REPLACE",store.saved)}finally{model.close()}
    }
    @Test fun aFailedKeychainWriteDoesNotReplaceThePreviousConnection()=runTest{
        val original=ConnectionRegistry().encode();val store=Store(original,false)
        val api=newApi{r->when(r.url.encodedPath){"/health"->respond(health);"/api/v1/devices/register"->respond("""{"deviceId":"$device","token":"${"c".repeat(64)}"}""");"/api/v1/shares"->respond("[]");else->error("Unexpected route")}}
        val model=MobileCoordinator(store,scanner,device,"Prueba",api)
        try{model.connect(code);advanceUntilIdle();assertNull(model.state.value.connection);assertEquals(original,store.saved);assertEquals(1,store.writes);assertNotNull(model.state.value.error)}finally{model.close()}
    }
    @Test fun enrollingPersistsOnlyDeviceCredentialAndShowsThatComputersShares()=runTest{
        val store=Store()
        val api=newApi{r->when(r.url.encodedPath){"/health"->respond(health);"/api/v1/devices/register"->respond("""{"deviceId":"$device","token":"${"c".repeat(64)}"}""");"/api/v1/shares"->respond("""[{"id":"share-1","name":"Carpeta de prueba"}]""");else->error("Unexpected route")}}
        val model=MobileCoordinator(store,scanner,device,"Prueba",api)
        try{model.connect(code);advanceUntilIdle();val state=model.state.value;assertEquals(device,state.connection?.deviceId);assertEquals("c".repeat(64),state.connection?.token);assertEquals(MobileTab.Files,state.tab);assertEquals("Carpeta de prueba",state.shares.single().name);assertFalse(store.saved.orEmpty().contains("b".repeat(64)))}finally{model.close()}
    }
    @Test fun forgettingOneComputerKeepsTheOtherCredentials(){
        val a=ComputerConnection("Mismo nombre",base,listOf(base),device,"a".repeat(64),"identity-a")
        val b=a.copy(token="b".repeat(64),dataIdentity="identity-b")
        val registry=ConnectionRegistry().withConnection(a).withConnection(b).remove(b.key)
        assertEquals(listOf(a),registry.computers);assertNull(registry.selected())
    }
}

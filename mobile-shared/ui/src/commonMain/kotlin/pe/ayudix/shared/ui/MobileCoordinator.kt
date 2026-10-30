package pe.ayudix.shared.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pe.ayudix.shared.*

enum class MobileTab { Assistant, Files, Computers }
data class MobileState(
    val registry: ConnectionRegistry = ConnectionRegistry(), val storageHealthy: Boolean = true,
    val tab: MobileTab = MobileTab.Computers, val busy: Boolean = false,
    val error: String? = null, val notice: String? = null,
    val shares: List<ApiShare> = emptyList(), val directory: FileListResponse? = null,
    val snapshot: AssistantSnapshot? = null,
) { val connection get() = registry.selected() }

class MobileCoordinator(
    private val store: SecureConnectionStore, private val scanner: QrScanner,
    private val deviceId: String, private val deviceName: String,
    private val api: AyudixApi = AyudixApi(platformHttpClient()),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutable = MutableStateFlow(MobileState())
    val state = mutable.asStateFlow()
    private var foreground = false
    private var poll: Job? = null
    private var operation: Job? = null
    init {
        try {
            val saved = store.load()?.let(ConnectionRegistry::decode) ?: ConnectionRegistry()
            mutable.value = MobileState(registry = saved,tab = if(saved.selected()==null)MobileTab.Computers else MobileTab.Assistant)
        } catch(_:Exception) {
            mutable.value = MobileState(storageHealthy=false,error="No se pudo abrir la vinculación protegida. Se conservó intacta; no se reemplazará por una nueva.")
        }
    }
    private fun persist(registry:ConnectionRegistry) {
        check(mutable.value.storageHealthy) { "Primero recupera el acceso al llavero del iPhone." }
        check(store.save(registry.encode())) { "No se pudo guardar la credencial en el llavero. La conexión anterior se conservó." }
        mutable.value=mutable.value.copy(registry=registry,shares=emptyList(),directory=null,snapshot=null,error=null,notice=null)
    }
    private fun work(block:suspend ()->Unit) {
        if(mutable.value.busy)return
        poll?.cancel();poll=null
        operation=scope.launch {
            mutable.value=mutable.value.copy(busy=true,error=null,notice=null)
            try{block()}catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(error=e.message?.take(1400)?:"No se completó la solicitud.")}
            finally{mutable.value=mutable.value.copy(busy=false);restartPolling()}
        }
    }
    fun scan(){if(mutable.value.busy||!mutable.value.storageHealthy)return;scanner.start(object:QrResult{
        override fun received(value:String){connect(value)}
        override fun cancelled(){}
        override fun failed(message:String){mutable.value=mutable.value.copy(error=message)}
    })}
    fun connect(value:String)=work {
        check(mutable.value.storageHealthy) { "Primero recupera el acceso al llavero del iPhone. No se envió una nueva vinculación." }
        val payload=NetworkPolicy.parsePairing(value)
        val connection=api.pair(payload,deviceId,deviceName)
        val shares=api.shares(connection)
        persist(mutable.value.registry.withConnection(connection))
        mutable.value=mutable.value.copy(shares=shares,tab=MobileTab.Files,notice="iPhone vinculado con ${connection.serverName}. Sus permisos se administran en esa PC.")
    }
    fun selectComputer(key:String)=work {
        persist(mutable.value.registry.select(key))
        refreshCurrent()
    }
    fun forgetComputer(key:String)=work{persist(mutable.value.registry.remove(key));mutable.value=mutable.value.copy(tab=MobileTab.Computers)}
    fun chooseTab(tab:MobileTab){if(mutable.value.busy)return;mutable.value=mutable.value.copy(tab=tab,error=null,notice=null);refresh()}
    fun refresh()=work{refreshCurrent()}
    private suspend fun refreshCurrent(){
        val connection=mutable.value.connection?:return
        when(mutable.value.tab){
            MobileTab.Files->{val folder=mutable.value.directory
                if(folder==null){val shares=api.shares(connection);if(mutable.value.connection?.key==connection.key)mutable.value=mutable.value.copy(shares=shares)}
                else{val listing=api.files(connection,folder.share.id,folder.path);if(mutable.value.connection?.key==connection.key)mutable.value=mutable.value.copy(directory=listing)}
            }
            MobileTab.Assistant->refreshChat(connection)
            MobileTab.Computers->Unit
        }
    }
    private suspend fun refreshChat(connection:ComputerConnection){
        val snapshot=api.snapshot(connection)
        if(mutable.value.connection?.key==connection.key)mutable.value=mutable.value.copy(snapshot=snapshot)
    }
    fun openShare(share:ApiShare)=work{
        val connection=mutable.value.connection?:return@work
        val listing=api.files(connection,share.id,"")
        if(mutable.value.connection?.key==connection.key)mutable.value=mutable.value.copy(directory=listing)
    }
    fun openFolder(entry:FileEntry)=work{
        val current=mutable.value;val connection=current.connection?:return@work;val folder=current.directory?:return@work
        if(!entry.isFolder)return@work
        val listing=api.files(connection,folder.share.id,entry.relativePath)
        if(mutable.value.connection?.key==connection.key)mutable.value=mutable.value.copy(directory=listing)
    }
    fun up()=work{
        val current=mutable.value;val connection=current.connection?:return@work;val folder=current.directory?:return@work
        val parent=folder.parentPath
        if(parent==null){mutable.value=current.copy(directory=null);return@work}
        val listing=api.files(connection,folder.share.id,parent)
        if(mutable.value.connection?.key==connection.key)mutable.value=mutable.value.copy(directory=listing)
    }
    fun send(text:String,onAccepted:()->Unit)=work{
        val connection=mutable.value.connection?:return@work
        if(mutable.value.snapshot==null)refreshChat(connection)
        api.send(connection,text.trim(),newRequestId(),mutable.value.snapshot?.sessionId.orEmpty())
        onAccepted()
        mutable.value=mutable.value.copy(notice="Solicitud enviada a tu PC.")
        refreshChat(connection)
    }
    fun setForeground(active:Boolean){foreground=active;restartPolling();if(active&&mutable.value.connection!=null&&mutable.value.snapshot==null&&mutable.value.tab==MobileTab.Assistant)refresh()}
    private fun restartPolling(){
        poll?.cancel();poll=null
        if(!foreground||mutable.value.busy||mutable.value.tab!=MobileTab.Assistant||mutable.value.connection==null)return
        poll=scope.launch{while(isActive){delay(4000);if(!foreground||mutable.value.busy)continue;val connection=mutable.value.connection?:break;try{refreshChat(connection)}catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(error=e.message?.take(1400)?:"No se pudo actualizar la conversación.");break}}}
    }
    fun close(){scope.cancel();api.close()}
}

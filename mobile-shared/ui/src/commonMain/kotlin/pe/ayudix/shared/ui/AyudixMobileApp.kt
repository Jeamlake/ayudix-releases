package pe.ayudix.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pe.ayudix.shared.*
import kotlinx.coroutines.launch

private val colors=darkColorScheme(primary=Color(0xff34d399),secondary=Color(0xffab8aff),background=Color(0xff0d0c15),surface=Color(0xff191723),surfaceVariant=Color(0xff252133),primaryContainer=Color(0xff073d33),onPrimaryContainer=Color(0xffbbf7dc))

@Composable
fun AyudixMobileApp(model:MobileCoordinator){
    val state by model.state.collectAsState()
    MaterialTheme(colorScheme=colors){
        Scaffold(containerColor=colors.background,bottomBar={NavigationBar{
            listOf(Triple(MobileTab.Assistant,"Ayudix",Icons.Default.AutoAwesome),Triple(MobileTab.Files,"Archivos",Icons.Default.FolderOpen),Triple(MobileTab.Computers,"Mis PC",Icons.Default.Devices)).forEach{(tab,label,icon)->
                NavigationBarItem(selected=state.tab==tab,onClick={model.chooseTab(tab)},enabled=!state.busy,icon={Icon(icon,null)},label={Text(label)})
            }
        }}){padding->Column(Modifier.fillMaxSize().padding(padding).imePadding()){
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(when(state.tab){MobileTab.Assistant->"Ayudix";MobileTab.Files->state.directory?.share?.name?:"Tus archivos";MobileTab.Computers->"Tus computadoras"},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
                    if(state.tab!=MobileTab.Computers)Text(state.connection?.serverName?:"Vincula tu PC para empezar",style=MaterialTheme.typography.labelMedium,color=colors.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                if(state.connection!=null&&state.tab!=MobileTab.Computers)IconButton(onClick=model::refresh,enabled=!state.busy){Icon(Icons.Default.Refresh,"Actualizar contenido")}
            }
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let{Notice(it,true)}
            state.notice?.let{Notice(it,false)}
            when(state.tab){
                MobileTab.Computers->Computers(state,model,Modifier.weight(1f))
                MobileTab.Files->Files(state,model,Modifier.weight(1f))
                MobileTab.Assistant->Chat(state,model,Modifier.weight(1f))
            }
        }}
    }
}
@Composable private fun Notice(text:String,error:Boolean){Surface(color=if(error)colors.errorContainer else colors.primaryContainer,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=4.dp)){Text(text,Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)}}
@Composable private fun Computers(state:MobileState,model:MobileCoordinator,modifier:Modifier){
    var code by remember{mutableStateOf("")};var manual by remember{mutableStateOf(false)};var forget by remember{mutableStateOf<ComputerConnection?>(null)}
    LazyColumn(modifier.fillMaxWidth(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{Text("Tu espacio, conectado.",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);Text("Vincula este iPhone con tu computadora. Solo verás las carpetas y capacidades que autorices allí.",color=colors.onSurfaceVariant)}
        items(state.registry.computers,key={it.key}){pc->Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=if(pc.key==state.registry.active)colors.primaryContainer else colors.surfaceVariant),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(pc.serverName,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(if(pc.key==state.registry.active)"Computadora activa" else "Guardada en este iPhone",style=MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    if(pc.key!=state.registry.active)TextButton(onClick={model.selectComputer(pc.key)},enabled=!state.busy){Text("Usar esta PC")}
                    TextButton(onClick={forget=pc},enabled=!state.busy){Text("Olvidar")}
                }
            }
        }}
        item{Button(onClick=model::scan,enabled=!state.busy&&state.storageHealthy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Icon(Icons.Default.QrCodeScanner,null);Spacer(Modifier.width(8.dp));Text("Escanear QR de mi PC")}}
        item{TextButton(onClick={manual=!manual}){Text(if(manual)"Ocultar código manual" else "Tengo un código de vinculación")}}
        if(manual)item{
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                OutlinedTextField(value=code,onValueChange={if(it.length<=16384)code=it},label={Text("Código de Ayudix")},visualTransformation=PasswordVisualTransformation(),maxLines=4,enabled=!state.busy&&state.storageHealthy,modifier=Modifier.fillMaxWidth())
                Button(onClick={val value=code;code="";model.connect(value)},enabled=code.isNotBlank()&&!state.busy&&state.storageHealthy,modifier=Modifier.fillMaxWidth()){Text("Vincular esta computadora")}
                Text("Pega el contenido del QR de Seguridad en Ayudix Windows. No lo envíes por un chat.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            }
        }
        item{Text("Vista previa de iOS · Preparación 0.1",style=MaterialTheme.typography.labelMedium,color=colors.secondary);Text("La PC debe estar encendida. Para acceder fuera de casa, conecta tus dispositivos a la misma red privada de Tailscale.",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
    }
    forget?.let{pc->AlertDialog(onDismissRequest={forget=null},title={Text("¿Olvidar ${pc.serverName}?")},text={Text("Retira esta conexión del iPhone. Tus archivos y el permiso registrado en Windows se conservan.")},confirmButton={TextButton(onClick={forget=null;model.forgetComputer(pc.key)}){Text("Olvidar")}},dismissButton={TextButton(onClick={forget=null}){Text("Cancelar")}})}
}
@Composable private fun Files(state:MobileState,model:MobileCoordinator,modifier:Modifier){
    if(state.connection==null){EmptyState("Elige tu computadora en Mis PC para ver sus carpetas autorizadas.",modifier);return}
    LazyColumn(modifier.fillMaxWidth(),contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        state.directory?.let{folder->
            item{TextButton(onClick=model::up,enabled=!state.busy){Icon(Icons.AutoMirrored.Filled.ArrowBack,null);Spacer(Modifier.width(8.dp));Text(folder.path.ifBlank{"Ubicaciones compartidas"},maxLines=2,overflow=TextOverflow.Ellipsis)}}
            items(folder.entries.sortedWith(compareByDescending<FileEntry>{it.isFolder}.thenBy{it.name.lowercase()}),key={it.relativePath}){entry->
                ListItem(headlineContent={Text(entry.name,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Text(if(entry.isFolder)"Carpeta" else "${entry.size} bytes · Vista de archivo")},leadingContent={Icon(if(entry.isFolder)Icons.Default.Folder else Icons.Default.Description,null)},modifier=if(entry.isFolder)Modifier.clickable(enabled=!state.busy){model.openFolder(entry)}else Modifier)
            }
            if(folder.entries.isEmpty())item{Text("Esta carpeta está vacía.",Modifier.padding(16.dp),color=colors.onSurfaceVariant)}
            if(folder.entries.any{!it.isFolder})item{Text("En esta primera vista previa puedes explorar las carpetas y consultar sus archivos. Descargar, compartir y reproducir se incorporan en la siguiente etapa.",Modifier.padding(8.dp),style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}
        }?:run{
            items(state.shares,key={it.id}){share->Card(modifier=Modifier.fillMaxWidth().clickable(enabled=!state.busy){model.openShare(share)},shape=RoundedCornerShape(16.dp)){
                Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.FolderOpen,null,tint=colors.primary);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(share.name,style=MaterialTheme.typography.titleMedium);Text("Carpeta autorizada en tu PC",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)}}
            }}
            if(state.shares.isEmpty()&&!state.busy)item{Text("No hay carpetas disponibles. Autorízalas en Ayudix Windows y actualiza esta pantalla.",Modifier.padding(12.dp),color=colors.onSurfaceVariant)}
        }
    }
}
@Composable private fun Chat(state:MobileState,model:MobileCoordinator,modifier:Modifier){
    var draft by remember(state.connection?.key){mutableStateOf("")}
    val list=rememberLazyListState();val messages=state.snapshot?.messages.orEmpty();val followLatest=!list.canScrollForward;val scope=rememberCoroutineScope()
    LaunchedEffect(messages.lastOrNull()?.id){if(followLatest&&messages.isNotEmpty())list.animateScrollToItem(messages.lastIndex)}
    if(state.connection==null){EmptyState("Vincula tu PC en Mis PC para conversar con Ayudix.",modifier);return}
    Column(modifier.fillMaxWidth()){
        Box(Modifier.weight(1f).fillMaxWidth()){
            LazyColumn(Modifier.fillMaxSize(),state=list,contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                if(messages.isEmpty())item{Text("Tu asistente utiliza el motor de tu computadora. Activa Tailscale en ambos dispositivos para proteger la conversación.",color=colors.onSurfaceVariant)}
                items(messages,key={it.id}){message->Surface(shape=RoundedCornerShape(18.dp),color=if(message.role=="user")colors.surfaceVariant else colors.surface,modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text(if(message.role=="user")"Tú" else "Ayudix",style=MaterialTheme.typography.labelMedium,color=colors.primary);Text(message.text)}
                }}
                if(state.snapshot?.busy==true)item{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Text("Ayudix está trabajando en tu PC…",style=MaterialTheme.typography.bodySmall)}}
            }
            if(list.canScrollForward)FloatingActionButton(onClick={scope.launch{list.animateScrollToItem((messages.size-1).coerceAtLeast(0))}},modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp),containerColor=colors.primaryContainer){Icon(Icons.Default.ArrowDownward,"Ir al último mensaje")}
        }
        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(value=draft,onValueChange={if(it.length<=16000)draft=it},placeholder={Text("Escribe a Ayudix…")},modifier=Modifier.weight(1f),maxLines=5,shape=RoundedCornerShape(20.dp))
            FilledIconButton(onClick={model.send(draft){draft=""}},enabled=!state.busy&&draft.isNotBlank(),modifier=Modifier.size(48.dp)){Icon(Icons.AutoMirrored.Filled.Send,"Enviar a mi PC")}
        }
    }
}
@Composable private fun EmptyState(text:String,modifier:Modifier){Box(modifier.fillMaxWidth().padding(28.dp),contentAlignment=Alignment.Center){Text(text,color=colors.onSurfaceVariant)}}

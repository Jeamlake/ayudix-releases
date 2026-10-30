package pe.ayudix.shared.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import pe.ayudix.shared.*
import java.io.File

/** Headless rendering of shared UI only. No device, enrollment, real data or native window. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args:Array<String>) {
    val output=File(args.firstOrNull()?:error("Preview output directory required")).apply{mkdirs()}
    val store=object:SecureConnectionStore{override fun load():String?=null;override fun save(value:String)=false}
    val scanner=object:QrScanner{override fun start(callback:QrResult)=callback.cancelled()}
    val model=MobileCoordinator(store,scanner,"00000000-0000-4000-8000-000000000001","Vista previa sintética")
    try{
        val scene=ImageComposeScene(width=1170,height=2532,density=Density(3f)){AyudixMobileApp(model)}
        try{
            val image=scene.render()
            output.resolve("ios-shared-first-launch.png").writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        }finally{scene.close()}
    }finally{model.close()}
    println("Shared UI preview: "+output.resolve("ios-shared-first-launch.png").absolutePath)
}

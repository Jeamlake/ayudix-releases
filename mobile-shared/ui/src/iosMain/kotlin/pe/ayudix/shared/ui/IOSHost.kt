package pe.ayudix.shared.ui

import androidx.compose.ui.window.ComposeUIViewController
import pe.ayudix.shared.QrScanner
import pe.ayudix.shared.SecureConnectionStore
import platform.UIKit.UIViewController

class IOSHost(store:SecureConnectionStore,scanner:QrScanner,deviceId:String,deviceName:String){
    private val model=MobileCoordinator(store,scanner,deviceId,deviceName)
    fun viewController():UIViewController=ComposeUIViewController{AyudixMobileApp(model)}
    fun setForeground(active:Boolean)=model.setForeground(active)
    fun close()=model.close()
}

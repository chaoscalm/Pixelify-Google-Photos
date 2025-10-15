package balti.xposed.pixelifygooglephotos

import android.os.Build
import android.util.Log
import balti.xposed.pixelifygooglephotos.Constants.PACKAGE_NAME_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class DeviceSpoofer: IXposedHookLoadPackage {

    private fun log(message: String){
        XposedBridge.log("PixelifyGooglePhotos: $message")
        Log.d("PixelifyGooglePhotos", message)
    }

    /**
     * To read preference of user.
     */
    private val pref by lazy {
        XSharedPreferences(ModuleConfig.APPLICATION_ID, Constants.SHARED_PREF_FILE_NAME)
    }

    private val verboseLog: Boolean by lazy {
        pref.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
    }

    private val androidVersionToSpoof: DeviceProps.AndroidVersion? by lazy {
        if (pref.getBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false))
            finalDeviceToSpoof?.androidVersion
        else {
            pref.getString(PREF_SPOOF_ANDROID_VERSION_MANUAL, null)?.let {
                DeviceProps.getAndroidVersionFromLabel(it)
            }
        }
    }

    private val finalDeviceToSpoof by lazy {
        val deviceName = pref.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
        log("Device spoof: $deviceName")
        DeviceProps.getDeviceProps(deviceName)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (pref.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) &&
            lpparam?.packageName != PACKAGE_NAME_GOOGLE_PHOTOS) return

        log("Loaded DeviceSpoofer for ${lpparam?.packageName}")
        log("Device spoof: ${finalDeviceToSpoof?.deviceName}")

        finalDeviceToSpoof?.props?.run {
            if (keys.isEmpty()) return
            val classLoader = lpparam?.classLoader ?: return

            val classBuild = XposedHelpers.findClass("android.os.Build", classLoader)
            keys.forEach {
                XposedHelpers.setStaticObjectField(classBuild, it, this[it])
                if (verboseLog) log("DEVICE PROPS: $it - ${this[it]}")
            }
        }

        androidVersionToSpoof?.getAsMap()?.run {
            val classLoader = lpparam?.classLoader ?: return
            val classBuild = XposedHelpers.findClass("android.os.Build.VERSION", classLoader)

            keys.forEach {
                XposedHelpers.setStaticObjectField(classBuild, it, this[it])
                if (verboseLog) log("VERSION SPOOF: $it - ${this[it]}")
            }
        }
    }
}

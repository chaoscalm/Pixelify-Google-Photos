package balti.xposed.pixelifygooglephotos

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import balti.xposed.pixelifygooglephotos.Constants.CONF_EXPORT_NAME
import balti.xposed.pixelifygooglephotos.Constants.FIELD_LATEST_VERSION_CODE
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_LAST_VERSION
import balti.xposed.pixelifygooglephotos.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_FEATURES_LIST
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL2
import balti.xposed.pixelifygooglephotos.Constants.SHARED_PREF_FILE_NAME
import balti.xposed.pixelifygooglephotos.Constants.TELEGRAM_GROUP
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL2
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL

// REMOVE this block!
// object ModuleConfig {
//     const val VERSION_NAME = "4.1"
//     const val VERSION_CODE = 5
//     const val APPLICATION_ID = "balti.xposed.pixelifygooglephotos"
// }

class ActivityMain: AppCompatActivity(R.layout.activity_main) {

    private val pref by lazy {
        try {
            getSharedPreferences(SHARED_PREF_FILE_NAME, MODE_WORLD_READABLE)
        } catch (_: Exception){
            null
        }
    }

    private fun showRebootSnack(){
        if (pref == null) return
        val rootView = findViewById<ScrollView>(R.id.root_view_for_snackbar)
        Snackbar.make(rootView, R.string.please_force_stop_google_photos, Snackbar.LENGTH_SHORT).show()
    }

    private fun peekFeatureFlagsChanged(textView: TextView){
        textView.run {
            alpha = 1.0f
            animate().alpha(0.0f).apply {
                duration = 1000
                startDelay = 3000
            }.start()
        }
    }

    private val utils by lazy { Utils() }

    private val childActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                showRebootSnack()
            }
        }

    private fun restartActivity(){
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (pref == null){
            AlertDialog.Builder(this)
                .setMessage(R.string.module_not_enabled)
                .setPositiveButton(R.string.close) {_, _ ->
                    finish()
                }
                .setCancelable(false)
                .show()
        }

        val resetSettings = findViewById<Button>(R.id.reset_settings)
        val customizeFeatureFlags = findViewById<LinearLayout>(R.id.customize_feature_flags)
        val featureFlagsChanged = findViewById<TextView>(R.id.feature_flags_changed)
        val overrideROMFeatureLevels = findViewById<SwitchCompat>(R.id.override_rom_feature_levels)
        val switchEnforceGooglePhotos = findViewById<SwitchCompat>(R.id.spoof_only_in_google_photos_switch)
        val deviceSpooferSpinner = findViewById<Spinner>(R.id.device_spoofer_spinner)
        val forceStopGooglePhotos = findViewById<Button>(R.id.force_stop_google_photos)
        val openGooglePhotos = findViewById<ImageButton>(R.id.open_google_photos)
        val advancedOptions = findViewById<TextView>(R.id.advanced_options)
        val telegramLink = findViewById<TextView>(R.id.telegram_group)
        val updateAvailableLink = findViewById<TextView>(R.id.update_available_link)
        val confExport = findViewById<ImageButton>(R.id.conf_export)
        val confImport = findViewById<ImageButton>(R.id.conf_import)

        resetSettings.setOnClickListener {
            pref?.edit()?.run {
                putString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
                putBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true)
                putBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true)
                putStringSet(
                    PREF_SPOOF_FEATURES_LIST,
                    DeviceProps.defaultFeatures.map { it.displayName }.toSet()
                )
                putBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
                putBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false)
                putString(PREF_SPOOF_ANDROID_VERSION_MANUAL, null)
                apply()
            }
            restartActivity()
        }

        overrideROMFeatureLevels.apply {
            isChecked = pref?.getBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true) ?: false
            setOnCheckedChangeListener { _, isChecked ->
                pref?.edit()?.run {
                    putBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, isChecked)
                    apply()
                    showRebootSnack()
                }
            }
        }

        switchEnforceGooglePhotos.apply {
            isChecked = pref?.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: false
            setOnCheckedChangeListener { _, isChecked ->
                pref?.edit()?.run {
                    putBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, isChecked)
                    apply()
                    showRebootSnack()
                }
            }
        }

        deviceSpooferSpinner.apply {
            val deviceNames = DeviceProps.allDevices.map { it.deviceName }
            val aa = ArrayAdapter(this@ActivityMain,android.R.layout.simple_spinner_item, deviceNames)

            aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            adapter = aa
            val defaultSelection = pref?.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
            setSelection(aa.getPosition(defaultSelection), false)

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val deviceName = aa.getItem(position)
                    pref?.edit()?.apply {
                        putString(PREF_DEVICE_TO_SPOOF, deviceName)
                        putStringSet(
                            PREF_SPOOF_FEATURES_LIST,
                            DeviceProps.getFeaturesUpToFromDeviceName(deviceName)
                        )
                        apply()
                    }

                    peekFeatureFlagsChanged(featureFlagsChanged)
                    showRebootSnack()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        advancedOptions.apply {
            paintFlags = Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                childActivityLauncher.launch(Intent(this@ActivityMain, AdvancedOptionsActivity::class.java))
            }
        }

        forceStopGooglePhotos.setOnClickListener {
            utils.forceStopPackage(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, this)
        }

        openGooglePhotos.setOnClickListener {
            utils.openApplication(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, this)
        }

        customizeFeatureFlags.setOnClickListener {
            childActivityLauncher.launch(Intent(this, FeatureCustomize::class.java))
        }

        telegramLink.apply {
            paintFlags = Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                openWebLink(TELEGRAM_GROUP)
            }
        }

        confExport.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.export_config)
                setMessage(R.string.export_config_desc)
                setPositiveButton(R.string.share){_, _ ->
                    shareConfFile()
                }
                setNegativeButton(R.string.save){_, _ ->
                    saveConfFile()
                }
                setNeutralButton(android.R.string.cancel, null)
            }
                .show()
        }

        confImport.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setTitle(R.string.import_config)
                setMessage(R.string.import_config_desc)
                setPositiveButton(android.R.string.ok){_, _ ->
                    importConfFile()
                }
                setNegativeButton(android.R.string.cancel, null)
            }
                .show()
        }

        pref?.apply {
            val thisVersion = ModuleConfig.VERSION_CODE
            if (getInt(PREF_LAST_VERSION, 0) < thisVersion){
                showChangeLog()
                edit().apply {
                    putInt(PREF_LAST_VERSION, thisVersion)
                    apply()
                }
            }
        }

        AsyncTask.execute {
            isUpdateAvailable()?.let { url ->
                runOnUiThread {
                    updateAvailableLink.apply {
                        paintFlags = Paint.UNDERLINE_TEXT_FLAG
                        visibility = View.VISIBLE
                        setOnClickListener {
                            openWebLink(url)
                        }
                    }
                }
            }
        }
    }

    private fun showChangeLog(){
        AlertDialog.Builder(this)
            .setTitle(R.string.version_head)
            .setMessage(R.string.version_desc)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_activity_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId){
            R.id.menu_changelog -> showChangeLog()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun isUpdateAvailable(): String? {

        fun getUpdateStatus(url: String): Boolean {
            var jsonString = ""
            val baos = ByteArrayOutputStream()

            try {
                URL(url).openStream().use { input ->
                    baos.use { output ->
                        input.copyTo(output)
                    }
                    jsonString = baos.toString()
                }
            } catch (_: Exception) {
                return false
            }

            return if (jsonString.isNotBlank()) {
                try {
                    val json = JSONObject(jsonString)
                    val remoteVersion = json.getInt(FIELD_LATEST_VERSION_CODE)
                    ModuleConfig.VERSION_CODE < remoteVersion
                } catch (_: Exception) {
                    false
                }
            } else false
        }

        return when {
            getUpdateStatus(UPDATE_INFO_URL) -> RELEASES_URL
            getUpdateStatus(UPDATE_INFO_URL2) -> RELEASES_URL2
            else -> null
        }
    }

    fun openWebLink(url: String){
        startActivity(Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        })
    }

    private fun shareConfFile(){

        try {
            val confFile = File(cacheDir, CONF_EXPORT_NAME)
            val uriFromFile = Uri.fromFile(confFile)

            confFile.delete()
            utils.writeConfigFile(this, uriFromFile, pref)

            val confFileShareUri =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                    FileProvider.getUriForFile(this, ModuleConfig.APPLICATION_ID, confFile)
                else uriFromFile

            Intent().run {

                action = Intent.ACTION_SEND
                type = "*/*"
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                this.putExtra(Intent.EXTRA_STREAM, confFileShareUri)
                startActivity(Intent.createChooser(this, getString(R.string.share_config_file)))
            }
        }
        catch (e: Exception){
            e.printStackTrace()
            Toast.makeText(this, "${getString(R.string.share_error)}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveConfFile(){
        val openIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, CONF_EXPORT_NAME)
        }
        Toast.makeText(this, R.string.select_a_location, Toast.LENGTH_SHORT).show()
        configCreateLauncher.launch(openIntent)
    }

    private val configCreateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        try {
            if (it.resultCode == Activity.RESULT_OK) {
                utils.writeConfigFile(this, it.data!!.data!!, pref)
                Toast.makeText(this, R.string.export_complete, Toast.LENGTH_SHORT).show()
            }
        }
        catch (e: Exception){
            e.printStackTrace()
            Toast.makeText(this, "${getString(R.string.share_error)}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importConfFile(){
        val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        configOpenLauncher.launch(openIntent)
    }

    private val configOpenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        try {
            if (it.resultCode == Activity.RESULT_OK) {
                utils.readConfigFile(this, it.data!!.data!!, pref)
                Toast.makeText(this, R.string.import_complete, Toast.LENGTH_SHORT).show()
                restartActivity()
            }
        }
        catch (e: Exception){
            e.printStackTrace()
            Toast.makeText(this, "${getString(R.string.read_error)}: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

}

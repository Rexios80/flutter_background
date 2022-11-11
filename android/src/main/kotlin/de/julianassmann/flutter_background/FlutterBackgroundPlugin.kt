package de.julianassmann.flutter_background

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry

class FlutterBackgroundPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private var methodChannel: MethodChannel? = null
    private var activity: Activity? = null
    private var permissionHandler: PermissionHandler? = null
    private var context: Context? = null

    companion object {
        @JvmStatic
        val NOTIFICATION_TITLE_KEY = "android.notificationTitle"

        @JvmStatic
        val NOTIFICATION_ICON_NAME_KEY = "android.notificationIconName"

        @JvmStatic
        val NOTIFICATION_ICON_DEF_TYPE_KEY = "android.notificationIconDefType"

        @JvmStatic
        val NOTIFICATION_TEXT_KEY = "android.notificationText"

        @JvmStatic
        val NOTIFICATION_IMPORTANCE_KEY = "android.notificationImportance"

        @JvmStatic
        val ENABLE_WIFI_LOCK_KEY = "android.enableWifiLock"

        @JvmStatic
        val SHOULD_REQUEST_BATTERY_OPTIMIZATIONS_OFF_KEY =
            "android.shouldRequestBatteryOptimizationsOff"

        @JvmStatic
        val FOREGROUND_SERVICE_TYPE_KEY = "android.foregroundServiceType"

        @JvmStatic
        var notificationTitle: String = "flutter_background foreground service"

        @JvmStatic
        var notificationText: String = "Keeps the flutter app running in the background"

        @JvmStatic
        var notificationImportance: Int = NotificationCompat.PRIORITY_DEFAULT

        @JvmStatic
        var notificationIconName: String = "ic_launcher"

        @JvmStatic
        var notificationIconDefType: String = "mipmap"

        @JvmStatic
        var enableWifiLock: Boolean = true

        @JvmStatic
        var shouldRequestBatteryOptimizationsOff: Boolean = true

        @JvmStatic
        var foregroundServiceType = 0

        fun loadNotificationConfiguration(context: Context?) {
            val sharedPref = context?.getSharedPreferences(
                context.packageName + "_preferences", Context.MODE_PRIVATE
            )
            notificationTitle = sharedPref?.getString(NOTIFICATION_TITLE_KEY, notificationTitle)
                ?: notificationTitle
            notificationText =
                sharedPref?.getString(NOTIFICATION_TEXT_KEY, notificationText) ?: notificationText
            notificationImportance =
                sharedPref?.getInt(NOTIFICATION_IMPORTANCE_KEY, notificationImportance)
                    ?: notificationImportance
            notificationIconName =
                sharedPref?.getString(NOTIFICATION_ICON_NAME_KEY, notificationIconName)
                    ?: notificationIconName
            notificationIconDefType =
                sharedPref?.getString(NOTIFICATION_ICON_DEF_TYPE_KEY, notificationIconDefType)
                    ?: notificationIconDefType
            enableWifiLock = sharedPref?.getBoolean(ENABLE_WIFI_LOCK_KEY, false) ?: false
            foregroundServiceType = sharedPref?.getInt(FOREGROUND_SERVICE_TYPE_KEY, 0) ?: 0
        }

        fun saveNotificationConfiguration(context: Context?) {
            val sharedPref = context?.getSharedPreferences(
                context.packageName + "_preferences", Context.MODE_PRIVATE
            )
            with(sharedPref?.edit()) {
                this?.putString(NOTIFICATION_TITLE_KEY, notificationTitle)
                this?.putString(NOTIFICATION_TEXT_KEY, notificationText)
                this?.putInt(NOTIFICATION_IMPORTANCE_KEY, notificationImportance)
                this?.putString(NOTIFICATION_ICON_NAME_KEY, notificationIconName)
                this?.putString(NOTIFICATION_ICON_DEF_TYPE_KEY, notificationIconDefType)
                this?.putBoolean(ENABLE_WIFI_LOCK_KEY, enableWifiLock)
                this?.putInt(FOREGROUND_SERVICE_TYPE_KEY, foregroundServiceType)
                this?.apply()
            }
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result) {

        // System.out.println(call.method)
        // System.out.flush()

        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }

            "hasPermissions" -> {
                val hasPermissions =
                    permissionHandler!!.isIgnoringBatteryOptimizations() && permissionHandler!!.isWakeLockPermissionGranted()
                result.success(hasPermissions)
            }

            "initialize" -> {
                // Set static values so the IsolateHolderService can use them later on to configure the notification
                notificationImportance =
                    call.argument<Int>(NOTIFICATION_IMPORTANCE_KEY) ?: notificationImportance
                notificationTitle =
                    call.argument<String>(NOTIFICATION_TITLE_KEY) ?: notificationTitle
                notificationText = call.argument<String>(NOTIFICATION_TEXT_KEY) ?: notificationText
                notificationIconName =
                    call.argument<String>(NOTIFICATION_ICON_NAME_KEY) ?: notificationIconName
                notificationIconDefType =
                    call.argument<String>(NOTIFICATION_ICON_DEF_TYPE_KEY) ?: notificationIconDefType
                enableWifiLock = call.argument<Boolean>(ENABLE_WIFI_LOCK_KEY) ?: enableWifiLock
                shouldRequestBatteryOptimizationsOff =
                    call.argument<Boolean>(SHOULD_REQUEST_BATTERY_OPTIMIZATIONS_OFF_KEY)
                        ?: shouldRequestBatteryOptimizationsOff
                foregroundServiceType =
                    call.argument<Int>(FOREGROUND_SERVICE_TYPE_KEY) ?: foregroundServiceType

                saveNotificationConfiguration(context)

                if (permissionHandler!!.isWakeLockPermissionGranted() && (!shouldRequestBatteryOptimizationsOff || permissionHandler!!.isIgnoringBatteryOptimizations())) {
                    result.success(true)
                    return
                }

                // Ensure wake lock permissions are granted
                if (!permissionHandler!!.isWakeLockPermissionGranted()) {
                    result.error(
                        "PermissionError",
                        "Please add the WAKE_LOCK permission to the AndroidManifest.xml in order to use background_sockets.",
                        ""
                    )
                    return
                }

                // Ensure ignoring battery optimizations is enabled if requested
                if (FlutterBackgroundPlugin.shouldRequestBatteryOptimizationsOff && !permissionHandler!!.isIgnoringBatteryOptimizations()) {
                    if (activity != null) {
                        permissionHandler!!.requestBatteryOptimizationsOff(result, activity!!)
                    } else {
                        result.error(
                            "NoActivityError",
                            "The plugin is not attached to an activity",
                            "The plugin is not attached to an activity. This is required in order to request battery optimization to be off."
                        )
                    }
                }
            }

            "enableBackgroundExecution" -> {
                // Ensure all the necessary permissions are granted
                if (!permissionHandler!!.isWakeLockPermissionGranted()) {
                    result.error(
                        "PermissionError",
                        "Please add the WAKE_LOCK permission to the AndroidManifest.xml in order to use background_sockets.",
                        ""
                    )
                    return
                } else if (shouldRequestBatteryOptimizationsOff && !permissionHandler!!.isIgnoringBatteryOptimizations()) {
                    result.error(
                        "PermissionError", "The battery optimizations are not turned off.", ""
                    )
                } else {
                    val intent = Intent(context, IsolateHolderService::class.java)
                    intent.action = IsolateHolderService.ACTION_START
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context!!.startForegroundService(intent)
                    } else {
                        context!!.startService(intent)
                    }
                    result.success(true)
                }
            }

            "disableBackgroundExecution" -> {
                val intent = Intent(context!!, IsolateHolderService::class.java)
                intent.action = IsolateHolderService.ACTION_SHUTDOWN
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context!!.startForegroundService(intent)
                } else {
                    context!!.startService(intent)
                }
                result.success(true)
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        startListening(binding.applicationContext, binding.binaryMessenger)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        stopListening()
    }

    override fun onDetachedFromActivity() {
        stopListeningToActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        startListeningToActivity(
            binding.activity,
            binding::addActivityResultListener,
            binding::addRequestPermissionsResultListener
        )
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    private fun startListening(applicationContext: Context, messenger: BinaryMessenger) {
        methodChannel = MethodChannel(
            messenger, "flutter_background"
        )
        methodChannel!!.setMethodCallHandler(this)
        context = applicationContext
    }

    private fun stopListening() {
        methodChannel!!.setMethodCallHandler(null)
        methodChannel = null
        context = null
    }

    private fun startListeningToActivity(
        activity: Activity,
        addActivityResultListener: ((PluginRegistry.ActivityResultListener) -> Unit),
        addRequestPermissionResultListener: ((PluginRegistry.RequestPermissionsResultListener) -> Unit)
    ) {
        this.activity = activity
        permissionHandler = PermissionHandler(
            activity.applicationContext,
            addActivityResultListener,
            addRequestPermissionResultListener
        )
    }

    private fun stopListeningToActivity() {
        this.activity = null
        permissionHandler = null
    }
}

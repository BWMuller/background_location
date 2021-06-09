package com.almoullim.background_location

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.Handler
import android.util.Log
import android.widget.Toast
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.almoullim.background_location.Utils
import com.almoullim.background_location.LocationUpdatesService
import com.almoullim.background_location.R
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar

class BackgroundLocationPlugin() : MethodCallHandler, PluginRegistry.RequestPermissionsResultListener {

    private lateinit var registrar: Registrar
    private lateinit var channel: MethodChannel
    private var myReceiver: MyReceiver? = null

    //    private var mService: LocationUpdatesService? = null
    private var mBound: Boolean = false

    companion object {

        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "almoullim.com/background_location")
            channel.setMethodCallHandler(BackgroundLocationPlugin(registrar, channel))
        }

        private const val TAG = "com.almoullim.Log.Tag"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    }

    constructor(registrar: Registrar, channel: MethodChannel) : this() {
        this.registrar = registrar
        this.channel = channel


        myReceiver = MyReceiver()

        if (Utils.requestingLocationUpdates(registrar.activeContext())) {
            if (!checkPermissions()) {
                requestPermissions()
            }
        }
        LocalBroadcastManager.getInstance(registrar.activeContext()).registerReceiver(
            myReceiver!!,
            IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
        )
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when {
            call.method == "stop_location_service" -> {
                LocalBroadcastManager.getInstance(registrar.activeContext()).unregisterReceiver(myReceiver!!)

                val intent = Intent(registrar.activeContext(), LocationUpdatesService::class.java)
                intent.setAction("${registrar.activeContext().packageName}.service_requests")
                intent.putExtra(LocationUpdatesService.ACTION_SERVICE_REQUEST, LocationUpdatesService.ACTION_STOP_FOREGROUND_SERVICE)
                LocalBroadcastManager.getInstance(registrar.activeContext()).sendBroadcast(intent)
                result.success(0);
            }
            call.method == "start_location_service" -> {
                LocalBroadcastManager.getInstance(registrar.activeContext()).registerReceiver(
                    myReceiver!!,
                    IntentFilter(LocationUpdatesService.ACTION_BROADCAST)
                )
                if (!mBound) {
                    var locationCallback: Long? = 0L
                    try {
                        locationCallback = call.argument("locationCallback")
                    } catch (ex: Throwable) {
                    }

                    var callbackHandle: Long? = 0L
                    try {
                        callbackHandle = call.argument("callbackHandle")
                    } catch (ex: Throwable) {
                    }

                    val startOnBoot: Boolean? = call.argument("startOnBoot") ?: false
                    val interval: Int? = call.argument("interval")
                    val fastestInterval: Int? = call.argument("fastest_interval")
                    val priority: Int? = call.argument("priority")
                    val distanceFilter: Double? = call.argument("distance_filter")
                    val intent = Intent(registrar.activeContext(), LocationUpdatesService::class.java);
                    intent.setAction(LocationUpdatesService.ACTION_START_FOREGROUND_SERVICE)
                    intent.putExtra("startOnBoot", startOnBoot)
                    intent.putExtra("interval", interval?.toLong())
                    intent.putExtra("fastest_interval", fastestInterval?.toLong())
                    intent.putExtra("priority", priority)
                    intent.putExtra("distance_filter", distanceFilter)
                    intent.putExtra("callbackHandle", callbackHandle)
                    intent.putExtra("locationCallback", locationCallback)

                    ContextCompat.startForegroundService(registrar.activeContext(), intent)
                }

                result.success(0);
            }
            call.method == "set_android_notification" -> {
                val channelID: String? = call.argument("channelID");
                val notificationTitle: String? = call.argument("title");
                val notificationMessage: String? = call.argument("message");
                val notificationIcon: String? = call.argument("icon");
                val actionText: String? = call.argument("actionText");
                var callback: Long = 0L
                try {
                    callback = call.argument("actionCallback") ?: 0L
                } catch (ex: Throwable) {
                }

                if (channelID != null) LocationUpdatesService.NOTIFICATION_CHANNEL_ID = channelID
                if (notificationTitle != null) LocationUpdatesService.NOTIFICATION_TITLE = notificationTitle
                if (notificationMessage != null) LocationUpdatesService.NOTIFICATION_MESSAGE = notificationMessage
                if (notificationIcon != null) LocationUpdatesService.NOTIFICATION_ICON = notificationIcon
                if (actionText != null) LocationUpdatesService.NOTIFICATION_ACTION = actionText
                if (callback != 0L) LocationUpdatesService.NOTIFICATION_ACTION_CALLBACK = callback

                val pref = registrar.activeContext().getSharedPreferences("backgroundLocationPreferences", Context.MODE_PRIVATE)
                if (pref.getBoolean("locationActive", false)) {
                    val intent = Intent(registrar.activeContext(), LocationUpdatesService::class.java)
                    intent.setAction("${registrar.activeContext().packageName}.service_requests")
                    intent.putExtra(LocationUpdatesService.ACTION_SERVICE_REQUEST, LocationUpdatesService.ACTION_UPDATE_NOTIFICATION)
                    LocalBroadcastManager.getInstance(registrar.activeContext()).sendBroadcast(intent)
                }

                result.success(0);
            }
            call.method == "is_service_running" -> {
                val manager: ActivityManager = registrar.activeContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                    if (LocationUpdatesService::class.java.getName() == service.service.getClassName()) {
                        if (service.foreground)
                            result.success(1)
                        else
                            result.success(0)
                        return
                    }
                }
                result.success(0);
            }
            else -> result.notImplemented()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?): Boolean {

        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            when {
                grantResults!!.isEmpty() -> Log.i(TAG, "User interaction was cancelled.")
//                grantResults[0] == PackageManager.PERMISSION_GRANTED -> mService!!.requestLocationUpdates()
                else -> Toast.makeText(registrar.activeContext(), R.string.permission_denied_explanation, Toast.LENGTH_LONG).show()
            }
        }
        return true
    }

    private inner class MyReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(LocationUpdatesService.EXTRA_LOCATION)
            if (location != null) {
                val locationMap = HashMap<String, Any>()
                locationMap["latitude"] = location.latitude
                locationMap["longitude"] = location.longitude
                locationMap["altitude"] = location.altitude
                locationMap["accuracy"] = location.accuracy.toDouble()
                locationMap["bearing"] = location.bearing.toDouble()
                locationMap["speed"] = location.speed.toDouble()
                locationMap["time"] = location.time.toDouble()
                locationMap["is_mock"] = location.isFromMockProvider
                channel.invokeMethod("location", locationMap, null)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            registrar.activeContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            registrar.activity(),
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (shouldProvideRationale) {

            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            Toast.makeText(registrar.activeContext(), R.string.permission_rationale, Toast.LENGTH_LONG).show()
        } else {
            Log.i(TAG, "Requesting permission")
            ActivityCompat.requestPermissions(
                registrar.activity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }
}

package com.almoullim.background_location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import io.flutter.FlutterInjector
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import io.flutter.view.FlutterCallbackInformation

class LocationUpdatesService : Service() {

    private val mBinder = LocalBinder()
    private var mNotificationManager: NotificationManager? = null
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mLocationCallbackHandle: Long = 0L
    private var mLocationCallback: LocationCallback? = null
    private var mLocation: Location? = null
    private var backgroundEngine: FlutterEngine? = null

    override fun onBind(intent: Intent?): IBinder? {
        val interval = intent?.getLongExtra("interval", 0L) ?: 0L
        val fastestInterval = intent?.getLongExtra("fastest_interval", 0L) ?: 0L
        val priority = intent?.getIntExtra("priority", 0) ?: 0
        val distanceFilter = intent?.getDoubleExtra("distance_filter", 0.0) ?: 0.0
        createLocationRequest(interval, fastestInterval, priority, distanceFilter)
        return mBinder
    }

    companion object {

        var NOTIFICATION_CHANNEL_ID = "channel_01"
        var NOTIFICATION_TITLE = "Background service is running"
        var NOTIFICATION_MESSAGE = "Background service is running"
        var NOTIFICATION_ICON = "@mipmap/ic_launcher"
        var NOTIFICATION_ACTION: String? = null
        var NOTIFICATION_ACTION_CALLBACK: Long? = null

        val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"
        val ACTION_UPDATE_NOTIFICATION = "ACTION_UPDATE_NOTIFICATION"
        val ACTION_NOTIFICATION_ACTIONED = "ACTION_NOTIFICATION_ACTIONED"

        private val PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice"
        private val TAG = LocationUpdatesService::class.java.simpleName
        internal val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        internal val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        private val EXTRA_STARTED_FROM_NOTIFICATION = "$PACKAGE_NAME.started_from_notification"
        private val NOTIFICATION_ID = 12345678
    }

    private val notification: NotificationCompat.Builder
        get() {

            val intent = Intent(this, getMainActivityClass(this))
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)
            intent.action = "Localisation"
            //intent.setClass(this, getMainActivityClass(this))
            val pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            val builder = NotificationCompat.Builder(this, "BackgroundLocation")
                .setContentTitle(NOTIFICATION_TITLE)
                .setOngoing(true)
                .setSound(null)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(resources.getIdentifier(NOTIFICATION_ICON, "mipmap", packageName))
                .setWhen(System.currentTimeMillis())
                .setStyle(NotificationCompat.BigTextStyle().bigText(NOTIFICATION_MESSAGE))
                .setContentIntent(pendingIntent)

            if (NOTIFICATION_ACTION?.isNotEmpty() == true && NOTIFICATION_ACTION_CALLBACK != null) {
                val actionIntent = Intent(this, LocationUpdatesService::class.java)
                actionIntent.putExtra("ARG_CALLBACK", NOTIFICATION_ACTION_CALLBACK ?: 0L)
                actionIntent.action = ACTION_NOTIFICATION_ACTIONED

                val action = NotificationCompat.Action.Builder(
                    0, NOTIFICATION_ACTION!!, PendingIntent.getService(
                        this,
                        0,
                        actionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
                builder.addAction(action)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(NOTIFICATION_CHANNEL_ID)
            }

            return builder
        }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action: String? = intent.getAction()
            when (action) {
                ACTION_START_FOREGROUND_SERVICE -> triggerForegroundServiceStart(intent)
                ACTION_STOP_FOREGROUND_SERVICE -> triggerForegroundServiceStop()
                ACTION_UPDATE_NOTIFICATION -> updateNotification()
                ACTION_NOTIFICATION_ACTIONED -> onNotificationActionClick(intent)
                else -> {
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun triggerForegroundServiceStart(intent: Intent) {

        val interval = intent.getLongExtra("interval", 0L) ?: 0L
        val fastestInterval = intent.getLongExtra("fastest_interval", 0L) ?: 0L
        val priority = intent.getIntExtra("priority", 0) ?: 0
        val distanceFilter = intent.getDoubleExtra("distance_filter", 0.0) ?: 0.0
        createLocationRequest(interval, fastestInterval, priority, distanceFilter)
        setLocationCallback(intent.getLongExtra("locationCallback", 0L) ?: 0L)

        val callbackHandle = intent.getLongExtra("callbackHandle", 0L) ?: 0L
        if (callbackHandle != 0L && backgroundEngine == null) {
            val callbackInfo = FlutterCallbackInformation.lookupCallbackInformation(callbackHandle)

            // We need flutter engine to handle callback, so if it is not available we have to create a
            // Flutter engine without any view
            backgroundEngine = FlutterEngine(this)

            val args = DartExecutor.DartCallback(
                this.assets,
                FlutterInjector.instance().flutterLoader().findAppBundlePath(),
                callbackInfo
            )
            backgroundEngine?.dartExecutor?.executeDartCallback(args)
        }

        if (mFusedLocationClient == null) {
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            mLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult?) {
                    super.onLocationResult(locationResult)
                    onNewLocation(locationResult!!.lastLocation)
                }
            }

            getLastLocation()

            mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val mChannel = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW)
                mChannel.setSound(null, null)
                mNotificationManager?.createNotificationChannel(mChannel)
            }

            startForeground(NOTIFICATION_ID, notification.build())
        }

        requestLocationUpdates()
    }

    fun requestLocationUpdates() {
        Utils.setRequestingLocationUpdates(this, true)
        try {
            mFusedLocationClient?.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback!!, Looper.myLooper()
            )
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, false)
        }
    }

    fun updateNotification() {
        //NOTIFICATION_TITLE = title
        //notification.setContentTitle(title)
        var notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification.build())
    }

    fun triggerForegroundServiceStop() {
        if (mFusedLocationClient != null) {
            stopForeground(true)
            stopSelf()
        }
    }

    private fun getLastLocation() {
        try {
            mFusedLocationClient?.lastLocation
                ?.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        mLocation = task.result
                    } else {
                    }
                }
        } catch (unlikely: SecurityException) {
        }
    }

    private fun onNewLocation(location: Location) {
        mLocation = location
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        val backgroundChannel = MethodChannel(backgroundEngine?.dartExecutor?.binaryMessenger, "almoullim.com/background_location_service")

        val locationMap = HashMap<String, Any>()
        locationMap["latitude"] = location.latitude
        locationMap["longitude"] = location.longitude
        locationMap["altitude"] = location.altitude
        locationMap["accuracy"] = location.accuracy.toDouble()
        locationMap["bearing"] = location.bearing.toDouble()
        locationMap["speed"] = location.speed.toDouble()
        locationMap["time"] = location.time.toDouble()
        locationMap["is_mock"] = location.isFromMockProvider

        val result: HashMap<Any, Any> =
            hashMapOf(
                "ARG_LOCATION" to locationMap,
                "ARG_CALLBACK" to mLocationCallbackHandle
            )

        Looper.getMainLooper()?.let {
            Handler(it)
                .post {
                    backgroundChannel.invokeMethod("BCM_LOCATION", result)
                }
        }
    }

    private fun onNotificationActionClick(intent: Intent) {
        getLastLocation();
        val backgroundChannel = MethodChannel(backgroundEngine?.dartExecutor?.binaryMessenger, "almoullim.com/background_location_service")

        val callback = intent.getLongExtra("ARG_CALLBACK", 0L) ?: 0L

        val locationMap = HashMap<String, Any>()
        val location = mLocation;
        if (location != null) {
            locationMap["latitude"] = location.latitude
            locationMap["longitude"] = location.longitude
            locationMap["altitude"] = location.altitude
            locationMap["accuracy"] = location.accuracy.toDouble()
            locationMap["bearing"] = location.bearing.toDouble()
            locationMap["speed"] = location.speed.toDouble()
            locationMap["time"] = location.time.toDouble()
            locationMap["is_mock"] = location.isFromMockProvider
        }

        val result: HashMap<Any, Any> =
            hashMapOf(
                "ARG_LOCATION" to locationMap,
                "ARG_CALLBACK" to callback
            )

        Looper.getMainLooper()?.let {
            Handler(it)
                .post {
                    backgroundChannel.invokeMethod("BCM_NOTIFICATION_ACTION", result)
                }
        }
    }

    private fun setLocationCallback(callback: Long) {
        mLocationCallbackHandle = callback;
    }

    private fun createLocationRequest(interval: Long, fastestInterval: Long, priority: Int, distanceFilter: Double) {
        mLocationRequest = LocationRequest()
        mLocationRequest?.interval = interval
        mLocationRequest?.fastestInterval = fastestInterval
        if (priority == 0)
            mLocationRequest?.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        if (priority == 1)
            mLocationRequest?.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        if (priority == 2)
            mLocationRequest?.priority = LocationRequest.PRIORITY_LOW_POWER
        if (priority == 3)
            mLocationRequest?.priority = LocationRequest.PRIORITY_NO_POWER
        mLocationRequest?.smallestDisplacement = distanceFilter.toFloat()
    }

    inner class LocalBinder : Binder() {

        internal val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mFusedLocationClient?.removeLocationUpdates(mLocationCallback!!)
            Utils.setRequestingLocationUpdates(this, false)
            mNotificationManager?.cancel(NOTIFICATION_ID)
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, true)
        }
    }

    private fun getMainActivityClass(context: Context): Class<*>? {
        val packageName = context.packageName
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        val className = launchIntent?.component?.className ?: return null

        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            null
        }
    }
}

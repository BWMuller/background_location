package com.almoullim.background_location

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*


class LocationUpdatesService : Service() {

    private val mBinder = LocalBinder()
    private var mNotificationManager: NotificationManager? = null
    private var mLocationRequest: LocationRequest? = null
    private var mFusedLocationClient: FusedLocationProviderClient? = null
    private var mLocationCallback: LocationCallback? = null
    private var mLocation: Location? = null

    override fun onBind(intent: Intent?): IBinder? {
        val interval = intent?.getLongExtra("interval", 0L) ?: 0L
        val fastestInterval = intent?.getLongExtra("fastest_interval", 0L) ?: 0L
        val priority = intent?.getIntExtra("priority", 0) ?: 0
        val distanceFilter = intent?.getDoubleExtra("distance_filter", 0.0) ?: 0.0
        createLocationRequest(interval, fastestInterval, priority, distanceFilter)
        return mBinder
    }

    companion object {
        var NOTIFICATION_TITLE = "Background service is running"
        var NOTIFICATION_MESSAGE = "Background service is running"
        var NOTIFICATION_ICON ="@mipmap/ic_launcher"

        val ACTION_START_FOREGROUND_SERVICE = "ACTION_START_FOREGROUND_SERVICE"
        val ACTION_STOP_FOREGROUND_SERVICE = "ACTION_STOP_FOREGROUND_SERVICE"
        val ACTION_UPDATE_NOTIFICATION = "ACTION_UPDATE_NOTIFICATION"

        private val PACKAGE_NAME = "com.google.android.gms.location.sample.locationupdatesforegroundservice"
        private val TAG = LocationUpdatesService::class.java.simpleName
        private val CHANNEL_ID = "channel_01"
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setChannelId(CHANNEL_ID)
            }

            return builder
        }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action: String? = intent.getAction()
            when (action) {
                ACTION_START_FOREGROUND_SERVICE -> triggerForegroundServiceStart(intent)
                ACTION_STOP_FOREGROUND_SERVICE -> triggerForegroundServiceStop()
                ACTION_UPDATE_NOTIFICATION -> updateNotification()
                else -> {}
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
            val name = "Application Name"
            val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            mChannel.setSound(null, null)
            mNotificationManager?.createNotificationChannel(mChannel)
        }

        startForeground(NOTIFICATION_ID, notification.build())

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
        stopForeground(true)
        stopSelf()
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

import 'dart:async';
import 'dart:developer';
import 'dart:io' show Platform;
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:permission_handler/permission_handler.dart';

import 'background_callback.dart';

enum LocationPriority {
  /// The best level of accuracy available.
  PRIORITY_HIGH_ACCURACY,
  // Accurate to within one hundred meters.
  PRIORITY_BALANCED_POWER_ACCURACY,
  // Accurate to within ten meters of the desired target.
  PRIORITY_LOW_POWER,
  // The level of accuracy used when an app isn’t authorized for full accuracy location data.
  PRIORITY_NO_POWER,
}

typedef LocationCallback = void Function(Location value);
typedef OptLocationCallback = void Function(Location? value);

/// BackgroundLocation plugin to get background
/// lcoation updates in iOS and Android
class BackgroundLocation {
  // The channel to be used for communication.
  // This channel is also refrenced inside both iOS and Abdroid classes
  static const MethodChannel _channel =
  MethodChannel('almoullim.com/background_location');

  /// Stop receiving location updates
  static stopLocationService() async {
    return await _channel.invokeMethod('stop_location_service');
  }

  /// Start receiving location updated
  static startLocationService({
    int interval = 1000,
    int fastestInterval = 500,
    double distanceFilter = 0.0,
    LocationPriority priority = LocationPriority.PRIORITY_HIGH_ACCURACY,
    LocationCallback? backgroundCallback = null,
  }) async {
    var callbackHandle =
    PluginUtilities.getCallbackHandle(callbackHandler)!.toRawHandle();
    var locationCallback = 0;
    if (backgroundCallback != null) {
      try {
        locationCallback =
            PluginUtilities.getCallbackHandle(backgroundCallback)!
                .toRawHandle();
      } catch (ex, stack) {
        log("Error getting callback handle", error: ex, stackTrace: stack);
      }
    }

    return await _channel
        .invokeMethod('start_location_service', <String, dynamic>{
      'callbackHandle': callbackHandle,
      'locationCallback': locationCallback,
      'interval': interval,
      'fastest_interval': fastestInterval,
      'priority': priority.index,
      'distance_filter': distanceFilter,
    });
  }

  static setAndroidNotification({
    String? channelID,
    String? title,
    String? message,
    String? icon,
    String? actionText,
    OptLocationCallback? actionCallback = null,
  }) async {
    if (Platform.isAndroid) {
      var callback = 0;
      if (actionCallback != null) {
        try {
          callback =
              PluginUtilities.getCallbackHandle(actionCallback)!
                  .toRawHandle();
        } catch (ex, stack) {
          log("Error getting callback handle", error: ex, stackTrace: stack);
        }
      }
      return await _channel.invokeMethod('set_android_notification',
          <String, dynamic>{
            'channelID': channelID,
            'title': title,
            'message': message,
            'icon': icon,
            'actionText': actionText,
            'actionCallback': callback,
          });
    } else {
      //return Promise.resolve();
    }
  }

  /// Ask the user for location permissions
  // ignore: always_declare_return_types
  static getPermissions({Function? onGranted, Function? onDenied}) async {
    await Permission.locationWhenInUse.request();
    if (await Permission.locationWhenInUse.isGranted) {
      if (onGranted != null) {
        onGranted();
      }
    } else if (await Permission.locationWhenInUse.isDenied ||
        await Permission.locationWhenInUse.isPermanentlyDenied ||
        await Permission.locationWhenInUse.isRestricted) {
      if (onDenied != null) {
        onDenied();
      }
    }
  }

  /// Check what the current permissions status is
  static Future<PermissionStatus> checkPermissions() async {
    var permission = await Permission.locationWhenInUse.status;
    return permission;
  }

  /// Register a function to recive location updates as long as the location
  /// service has started
  static StreamController<Location> getLocationUpdates() {
    StreamController<Location> streamController = new StreamController();
    // add a handler on the channel to recive updates from the native classes
    _channel.setMethodCallHandler((MethodCall methodCall) async {
      if (methodCall.method == 'location') {
        if (streamController.isClosed) return;
        var locationData = Map.from(methodCall.arguments);
        streamController.add(Location(
          latitude: locationData['latitude'],
          longitude: locationData['longitude'],
          altitude: locationData['altitude'],
          accuracy: locationData['accuracy'],
          bearing: locationData['bearing'],
          speed: locationData['speed'],
          time: locationData['time'],
          isMock: locationData['is_mock'],
        ));
      }
    });
    return streamController;
  }
}

/// about the user current location
class Location {
  final double latitude;
  final double longitude;
  final double altitude;
  final double bearing;
  final double accuracy;
  final double speed;
  final double time;
  final bool isMock;

  Location({required this.longitude,
    required this.latitude,
    required this.altitude,
    required this.accuracy,
    required this.bearing,
    required this.speed,
    required this.time,
    required this.isMock});

  factory Location.fromJson(Map<dynamic, dynamic> json) {
    bool isLocationMocked = Platform.isAndroid ? json['is_mock'] : false;
    return Location(
      latitude: json['latitude'],
      longitude: json['longitude'],
      altitude: json['altitude'],
      bearing: json['bearing'],
      accuracy: json['accuracy'],
      speed: json['speed'],
      time: json['time'],
      isMock: isLocationMocked,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'latitude': latitude,
      'longitude': longitude,
      'altitude': altitude,
      'bearing': bearing,
      'accuracy': accuracy,
      'speed': speed,
      'time': time,
      'is_mock': isMock
    };
  }
}

import 'dart:async';
import 'dart:io' show Platform;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

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
  }) async {
    return await _channel.invokeMethod('start_location_service',
        <String, dynamic>{
          'interval': interval,
          'fastest_interval': fastestInterval,
          'priority': priority.index,
          'distance_filter': distanceFilter,
        });
  }

  static setAndroidNotification(
      {String? title, String? message, String? icon}) async {
    if (Platform.isAndroid) {
      return await _channel.invokeMethod('set_android_notification',
          <String, dynamic>{'title': title, 'message': message, 'icon': icon});
    } else {
      //return Promise.resolve();
    }
  }

  /// Get the current location once.
  Future<Location> getCurrentLocation() async {
    var completer = Completer<Location>();

    var _location = Location();
    await getLocationUpdates((location) {
      _location.latitude = location.latitude;
      _location.longitude = location.longitude;
      _location.accuracy = location.accuracy;
      _location.altitude = location.altitude;
      _location.bearing = location.bearing;
      _location.speed = location.speed;
      _location.time = location.time;
      completer.complete(_location);
    });

    return completer.future;
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
  static getLocationUpdates(Function(Location) location) {
    // add a handler on the channel to recive updates from the native classes
    _channel.setMethodCallHandler((MethodCall methodCall) async {
      if (methodCall.method == 'location') {
        var locationData = Map.from(methodCall.arguments);
        // Call the user passed function
        location(
          Location(
              latitude: locationData['latitude'],
              longitude: locationData['longitude'],
              altitude: locationData['altitude'],
              accuracy: locationData['accuracy'],
              bearing: locationData['bearing'],
              speed: locationData['speed'],
              time: locationData['time'],
              isMock: locationData['is_mock']),
        );
      }
    });
  }
}

/// about the user current location
class Location {
  double? latitude;
  double? longitude;
  double? altitude;
  double? bearing;
  double? accuracy;
  double? speed;
  double? time;
  bool? isMock;

  Location(
      {@required this.longitude,
      @required this.latitude,
      @required this.altitude,
      @required this.accuracy,
      @required this.bearing,
      @required this.speed,
      @required this.time,
      @required this.isMock});

  toMap() {
    var obj = {
      'latitude': latitude,
      'longitude': longitude,
      'altitude': altitude,
      'bearing': bearing,
      'accuracy': accuracy,
      'speed': speed,
      'time': time,
      'is_mock': isMock
    };
    return obj;
  }
}

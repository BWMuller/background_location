import 'dart:async';
import 'dart:developer';

import 'package:background_location/background_location.dart';
import 'package:flutter/material.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String latitude = 'waiting...';
  String longitude = 'waiting...';
  String altitude = 'waiting...';
  String accuracy = 'waiting...';
  String bearing = 'waiting...';
  String speed = 'waiting...';
  String time = 'waiting...';
  StreamController<Location>? stream = null;

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Background Location Service'),
        ),
        body: Center(
          child: ListView(
            children: <Widget>[
              locationData('Latitude: ' + latitude),
              locationData('Longitude: ' + longitude),
              locationData('Altitude: ' + altitude),
              locationData('Accuracy: ' + accuracy),
              locationData('Bearing: ' + bearing),
              locationData('Speed: ' + speed),
              locationData('Time: ' + time),
              ElevatedButton(
                  onPressed: () async {
                    await BackgroundLocation.setAndroidNotification(
                      title: 'Background service is running',
                      message: 'Background location in progress',
                      icon: '@mipmap/ic_launcher',
                      actionText: "Press Me!",
                      actionCallback: actionCallback,
                    );
                    //await BackgroundLocation.setAndroidConfiguration(1000);
                    await BackgroundLocation.startLocationService(
                      startOnBoot: true,
                      distanceFilter: 20,
                      backgroundCallback: locationCallback,
                    );
                    stream = BackgroundLocation.getLocationUpdates();
                    stream?.stream.listen((location) {
                      setState(() {
                        latitude = location.latitude.toString();
                        longitude = location.longitude.toString();
                        accuracy = location.accuracy.toString();
                        altitude = location.altitude.toString();
                        bearing = location.bearing.toString();
                        speed = location.speed.toString();
                        time = DateTime.fromMillisecondsSinceEpoch(
                                location.time.toInt())
                            .toString();
                      });
                    });
                  },
                  child: Text('Start Location Service')),
              ElevatedButton(
                  onPressed: () async {
                    await BackgroundLocation.setAndroidNotification(
                      title: 'Background service is still running',
                      message:
                          'Background location in progress ${DateTime.now().toIso8601String()}',
                      icon: '@mipmap/ic_launcher',
                      actionText: "Press Me!!!!!",
                    );
                  },
                  child: Text('Update notification')),
              ElevatedButton(
                  onPressed: () {
                    BackgroundLocation.isServiceRunning().then((value) => log("Is Running: $value"));
                  },
                  child: Text('Check service')),
              ElevatedButton(
                  onPressed: () {
                    BackgroundLocation.stopLocationService();
                  },
                  child: Text('Stop Location Service')),
            ],
          ),
        ),
      ),
    );
  }

  Widget locationData(String data) {
    return Text(
      data,
      style: TextStyle(
        fontWeight: FontWeight.bold,
        fontSize: 18,
      ),
      textAlign: TextAlign.center,
    );
  }

  @override
  void dispose() {
    BackgroundLocation.stopLocationService();
    stream?.close();
    super.dispose();
  }
}

void actionCallback(Location? location) {
  log("Action pressed: $location");
}

void locationCallback(List<Location> locations) {
  log("Got Main location: $locations");
  // setState(() {
  //   latitude = location.latitude.toString();
  //   longitude = location.longitude.toString();
  //   accuracy = location.accuracy.toString();
  //   altitude = location.altitude.toString();
  //   bearing = location.bearing.toString();
  //   speed = location.speed.toString();
  //   time = DateTime.fromMillisecondsSinceEpoch(
  //       location.time.toInt())
  //       .toString();
  // });
}

import 'dart:developer';
import 'dart:ui';

import 'package:background_location/background_location.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

const BACKGROUND_CHANNEL_ID = "BACKGROUND_CHANNEL_ID";
const ARG_CALLBACK = "ARG_CALLBACK";
const ARG_LOCATION = "ARG_LOCATION";
const BCM_LOCATION = "BCM_LOCATION";
const BCM_NOTIFICATION_ACTION = "BCM_NOTIFICATION_ACTION";

@pragma('vm:entry-point')
void callbackHandler() {
  const MethodChannel _backgroundChannel = MethodChannel(BACKGROUND_CHANNEL_ID);
  WidgetsFlutterBinding.ensureInitialized();

  _backgroundChannel.setMethodCallHandler((MethodCall call) async {
    if (BCM_LOCATION == call.method) {
      final Map<dynamic, dynamic> args = call.arguments;

      int callbackArg = args[ARG_CALLBACK] ?? 0;
      if (callbackArg != 0) {
        final Function? callback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(callbackArg));
        if (callback != null)
          callback(Location.fromJson(args[ARG_LOCATION]));
      } else {
        log("BGLocationCallback: $args");
      }
    } else if (BCM_NOTIFICATION_ACTION == call.method) {
      final Map<dynamic, dynamic> args = call.arguments;

      int callbackArg = args[ARG_CALLBACK] ?? 0;
      if (callbackArg != 0) {
        final Function? callback = PluginUtilities.getCallbackFromHandle(CallbackHandle.fromRawHandle(callbackArg));
        if (callback != null)
          callback();
      } else {
        log("BGActionCallback: $args");
      }
    }
  });
  _backgroundChannel.invokeMethod("initialized");
}

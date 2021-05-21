import Flutter
import UIKit
import CoreLocation

public class SwiftBackgroundLocationPlugin: NSObject, FlutterPlugin, CLLocationManagerDelegate {
    static var locationManager: CLLocationManager?
    static var channel: FlutterMethodChannel?

    static var engine: FlutterEngine?
    static var backgroundChannel: FlutterMethodChannel?
    static var locationCallback: Int64?
    static var callbackHandle: Int64?

    public static func register(with registrar: FlutterPluginRegistrar) {
        let instance = SwiftBackgroundLocationPlugin()
        
        SwiftBackgroundLocationPlugin.channel = FlutterMethodChannel(name: "almoullim.com/background_location", binaryMessenger: registrar.messenger())
        registrar.addMethodCallDelegate(instance, channel: SwiftBackgroundLocationPlugin.channel!)
        SwiftBackgroundLocationPlugin.channel?.setMethodCallHandler(instance.handle)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        SwiftBackgroundLocationPlugin.locationManager = CLLocationManager()
        SwiftBackgroundLocationPlugin.locationManager?.delegate = self
        SwiftBackgroundLocationPlugin.locationManager?.requestAlwaysAuthorization()

        SwiftBackgroundLocationPlugin.locationManager?.allowsBackgroundLocationUpdates = true
        if #available(iOS 11.0, *) {
            SwiftBackgroundLocationPlugin.locationManager?.showsBackgroundLocationIndicator = true;
        }
        SwiftBackgroundLocationPlugin.locationManager?.pausesLocationUpdatesAutomatically = false

        SwiftBackgroundLocationPlugin.channel?.invokeMethod("location", arguments: "method")

        if (call.method == "start_location_service") {
            SwiftBackgroundLocationPlugin.channel?.invokeMethod("location", arguments: "start_location_service")
            
            let args = call.arguments as? Dictionary<String, Any>
            SwiftBackgroundLocationPlugin.locationCallback = args?["locationCallback"] as? Int64
            SwiftBackgroundLocationPlugin.callbackHandle = args?["callbackHandle"] as? Int64
            let distanceFilter = args?["distance_filter"] as? Double
            let priority = args?["priority"] as? Int

            guard let handle = SwiftBackgroundLocationPlugin.callbackHandle,
                  let flutterCallbackInformation = FlutterCallbackCache.lookupCallbackInformation(handle) else {
                return
            }

            SwiftBackgroundLocationPlugin.engine = FlutterEngine(name: "almoullim.com/background_location_thread", project: nil, allowHeadlessExecution: true)
            SwiftBackgroundLocationPlugin.engine!.run(withEntrypoint: flutterCallbackInformation.callbackName, libraryURI: flutterCallbackInformation.callbackLibraryPath)
            SwiftBackgroundLocationPlugin.engine!.registrar(forPlugin: "SwiftBackgroundLocationPlugin")

            SwiftBackgroundLocationPlugin.backgroundChannel = FlutterMethodChannel(name: "almoullim.com/background_location_service", binaryMessenger: SwiftBackgroundLocationPlugin.engine!.binaryMessenger)

            SwiftBackgroundLocationPlugin.locationManager?.distanceFilter = distanceFilter ?? 0

            if (priority == 0) {
                SwiftBackgroundLocationPlugin.locationManager?.desiredAccuracy = kCLLocationAccuracyBest
            }
            if (priority == 1) {
                SwiftBackgroundLocationPlugin.locationManager?.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
            }
            if (priority == 2) {
                SwiftBackgroundLocationPlugin.locationManager?.desiredAccuracy = kCLLocationAccuracyHundredMeters
            }
            if (priority == 3) {
                if #available(iOS 14.0, *) {
                    SwiftBackgroundLocationPlugin.locationManager?.desiredAccuracy = kCLLocationAccuracyReduced
                } else {
                    // Fallback on earlier versions
                }
            }

            SwiftBackgroundLocationPlugin.locationManager?.startUpdatingLocation() 
        } else if (call.method == "stop_location_service") {
            SwiftBackgroundLocationPlugin.channel?.invokeMethod("location", arguments: "stop_location_service")
            SwiftBackgroundLocationPlugin.locationManager?.stopUpdatingLocation()
        }
        result(true)
    }
    
    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        if status == .authorizedAlways {
           
        }
    }
    
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let location = [
            "speed": locations.last!.speed,
            "altitude": locations.last!.altitude,
            "latitude": locations.last!.coordinate.latitude,
            "longitude": locations.last!.coordinate.longitude,
            "accuracy": locations.last!.horizontalAccuracy,
            "bearing": locations.last!.course,
            "time": locations.last!.timestamp.timeIntervalSince1970 * 1000,
            "is_mock": false
        ] as [String : Any]

        SwiftBackgroundLocationPlugin.channel?.invokeMethod("location", arguments: location)


        let result = [
            "ARG_LOCATION": location,
            "ARG_CALLBACK": SwiftBackgroundLocationPlugin.locationCallback ?? 0
        ] as [String : Any]
        SwiftBackgroundLocationPlugin.backgroundChannel?.invokeMethod("BCM_LOCATION", arguments: result)
    }
}

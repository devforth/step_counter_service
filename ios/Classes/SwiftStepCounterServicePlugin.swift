import Flutter
import UIKit
import AVKit
import BackgroundTasks
import CoreMotion

public class SwiftStepCounterServicePlugin: FlutterPluginAppLifeCycleDelegate, FlutterPlugin {
    public static var taskIdentifier = "id.devforth.background.refresh"
    var mainChannel: FlutterMethodChannel? = nil
    public override func application(_ application: UIApplication, performFetchWithCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) -> Bool {
        print("Background handler is enabled")
        let defaults = UserDefaults.standard
        let callbackHandle = defaults.object(forKey: "background_callback_handle") as? Int64
        if (callbackHandle == nil){
            print("Background handler is disabled")
            completionHandler(.noData)
            return true
        }
        
        let worker = FlutterBackgroundFetchWorker(task: completionHandler)
        
        worker.onCompleted = {
            print("Background Fetch Completed")
        }
        worker.run()
        return true
    }

    public override func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [AnyHashable : Any] = [:]) -> Bool {
        UIApplication.shared.setMinimumBackgroundFetchInterval(UIApplication.backgroundFetchIntervalMinimum)
        if #available(iOS 13.0, *) {
            SwiftStepCounterServicePlugin.registerTaskIdentifier(taskIdentifier: SwiftStepCounterServicePlugin.taskIdentifier)
        }
        return true
    }

    public func applicationDidEnterBackground(_ application: UIApplication) {
        if #available(iOS 13.0, *){
            SwiftStepCounterServicePlugin.scheduleAppRefresh()
        }
    }

    @available(iOS 13.0, *)
    public static func registerTaskIdentifier(taskIdentifier: String) {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskIdentifier, using: nil) { task in
            self.handleAppRefresh(task: task as! BGAppRefreshTask)
        }
    }

    @available(iOS 13.0, *)
    private static func scheduleAppRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: SwiftStepCounterServicePlugin.taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)

        do {
            // cancel old schedule
            BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: SwiftStepCounterServicePlugin.taskIdentifier)

            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Could not schedule app refresh: \(error)")
        }
    }

    @available(iOS 13.0, *)
    private static func handleAppRefresh(task: BGAppRefreshTask) {
        let defaults = UserDefaults.standard
        let callbackHandle = defaults.object(forKey: "background_callback_handle") as? Int64
        if (callbackHandle == nil){
            print("Background handler is disabled")
            return
        }
        
        let operationQueue = OperationQueue()
        let operation = FlutterBackgroundRefreshAppOperation(
            task: task
        )
        
        operation.completionBlock = {
            scheduleAppRefresh()
        }
        
        operationQueue.addOperation(operation)
    }

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(
            name: "id.devforth/step_counter_service_ios",
            binaryMessenger: registrar.messenger(),
            codec: FlutterJSONMethodCodec())

        let instance = SwiftStepCounterServicePlugin()
        instance.mainChannel = channel
        registrar.addMethodCallDelegate(instance, channel: channel)
        registrar.addApplicationDelegate(instance)
    }

    private func autoStart(isForeground: Bool) {
        let defaults = UserDefaults.standard
        let autoStart = defaults.bool(forKey: "auto_start")
        if (autoStart) {
            // self.runForegroundWorker()
            self.runListenerSteps()
        }
    }

    fileprivate var stepsWorker: StepCounter? = nil
    private func runListenerSteps() {
        if (stepsWorker != nil){
            return
        }

        let defaults = UserDefaults.standard
        let callbackHandle = defaults.object(forKey: "step_worker_callback_handle") as? Int64
        if (callbackHandle == nil){
            print("steps service is disabled")
            return
        }
        
        stepsWorker = StepCounter(mainChannel: self.mainChannel!)
        stepsWorker?.onTerminated = {
            self.stepsWorker = nil
        }
        stepsWorker?.run()
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        if (call.method == "configure") {
            let args = call.arguments as? Dictionary<String, Any>
            let foregroundCallbackHandleID = args?["foreground_handle"] as? NSNumber
            let backgroundCallbackHandleID = args?["background_handle"] as? NSNumber
            let autoStart = args?["auto_start"] as? Bool ?? true
            
            let defaults = UserDefaults.standard
            defaults.set(foregroundCallbackHandleID?.int64Value, forKey: "step_worker_callback_handle")
            defaults.set(backgroundCallbackHandleID?.int64Value, forKey: "background_callback_handle")
            defaults.set(autoStart, forKey: "auto_start")
            
            if (autoStart && (foregroundCallbackHandleID != nil)){
                self.autoStart(isForeground: true)
            }
            
            result(true)
            return
        }
        if (call.method == "isServiceRunning") {
            let value = self.stepsWorker != nil;
            result(value);
            return;
        }
        
        if (call.method == "checkSensorAvailability") {
            let isAvailable: Bool = StepCounter.checkSensorAvailability();
            result(isAvailable);
            return;
        }
        
    }
}

typealias VoidInputVoidReturnBlock = () -> Void

private class FlutterBackgroundFetchWorker {
    let entrypointName = "backgroundEntrypoint"
    let uri = "package:step_counter_service/step_counter_service_ios.dart"
    let engine = FlutterEngine(name: "BackgroundHandleFlutterEngine")

    var onCompleted: VoidInputVoidReturnBlock?
    var task: ((UIBackgroundFetchResult) -> Void)
    var channel: FlutterMethodChannel?

    init(task: @escaping (UIBackgroundFetchResult) -> Void){
        self.task = task
    }

    public func run() {
        print("Running background fetch")
        let defaults = UserDefaults.standard
        let callbackHandle = defaults.object(forKey: "background_callback_handle") as? Int64
        if (callbackHandle == nil){
            print("No callback handle for background")
            return
        }
        
        let isRunning = engine.run(withEntrypoint: entrypointName, libraryURI: uri, initialRoute: nil, entrypointArgs: [String(callbackHandle!)])
        
        if (isRunning){
            StepCounterServicePlugin.register(engine)
            
            let binaryMessenger = engine.binaryMessenger
            channel = FlutterMethodChannel(name: "id.devforth/step_counter_service_ios_bg", binaryMessenger: binaryMessenger, codec: FlutterJSONMethodCodec())
            channel?.setMethodCallHandler(handleMethodCall)
        }
    }
    
    public func cancel(){
        self.engine.destroyContext()
        self.task(.failed)
        self.onCompleted?()
    }
    
    private func handleMethodCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        if (call.method == "setBackgroundFetchResult") {
            let result = call.arguments as? Bool ?? false
            
            if (result) {
                self.task(.newData)
            } else {
                self.task(.noData)
            }
            
            self.engine.destroyContext()
            self.onCompleted?()
            print("Flutter Background Service Completed")
        }
    }
}

@available(iOS 13, *)
private class FlutterBackgroundRefreshAppWorker {
    let entrypointName = "backgroundEntrypoint"
    let uri = "package:step_counter_service/step_counter_service_ios.dart"
    let engine = FlutterEngine(name: "BackgroundHandleFlutterEngine")
    
    var onCompleted: VoidInputVoidReturnBlock?
    var task: BGAppRefreshTask
    var channel: FlutterMethodChannel?
    
    init(task: BGAppRefreshTask){
        self.task = task
    }
    
    public func run() {
        let defaults = UserDefaults.standard
        let callbackHandle = defaults.object(forKey: "background_callback_handle") as? Int64
        if (callbackHandle == nil){
            print("No callback handle for background")
            return
        }
        
        let isRunning = engine.run(withEntrypoint: entrypointName, libraryURI: uri, initialRoute: nil, entrypointArgs: [String(callbackHandle!)])
        
        if (isRunning){
            StepCounterServicePlugin.register(engine)
            
            let binaryMessenger = engine.binaryMessenger
            channel = FlutterMethodChannel(name: "id.devforth/step_counter_service_ios_bg", binaryMessenger: binaryMessenger, codec: FlutterJSONMethodCodec())
            channel?.setMethodCallHandler(handleMethodCall)
        }
    }
    
    public func cancel(){
        DispatchQueue.main.async {
            self.engine.destroyContext()
        }
        
        self.task.setTaskCompleted(success: false)
        self.onCompleted?()
    }
    
    private func handleMethodCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        if (call.method == "setBackgroundFetchResult") {
            let result = call.arguments as? Bool ?? false
            self.task.setTaskCompleted(success: result)
            
            DispatchQueue.main.async {
                self.engine.destroyContext()
            }
            
            self.onCompleted?()
            print("Flutter Background Service Completed")
        }
    }
}

@available(iOS 13, *)
class FlutterBackgroundRefreshAppOperation: Operation {
    var task: BGAppRefreshTask
    fileprivate var worker: FlutterBackgroundRefreshAppWorker?
    init(task: BGAppRefreshTask) {
        self.task = task
    }
    override func main() {
        let semaphore = DispatchSemaphore(value: 0)
        DispatchQueue.main.async {
            self.worker = FlutterBackgroundRefreshAppWorker(task: self.task)
            self.worker?.onCompleted = {
                semaphore.signal()
            }
            self.task.expirationHandler = {
                self.worker?.cancel()
            }
            self.worker?.run()
        }
        
        semaphore.wait()
    }
}

class StepCounter {
    private let pedometer = CMPedometer()
    let entrypointName = "foregroundEntrypoint"
    let uri = "package:step_counter_service/step_counter_service_ios.dart"
    let engine = FlutterEngine(name: "StepCounterFlutterEngine")
    
    var channel: FlutterMethodChannel?
    var mainChannel: FlutterMethodChannel
    var onTerminated: VoidInputVoidReturnBlock?

    init(mainChannel: FlutterMethodChannel){
        self.mainChannel = mainChannel
    }

    public func run() {
        
        let defaults: UserDefaults = UserDefaults.standard
        let callbackHandle = defaults.object(forKey: "step_worker_callback_handle") as? Int64
        if (callbackHandle == nil){
            print("No callback handle for foreground")
            return
        }

        let isRunning = engine.run(withEntrypoint: entrypointName, libraryURI: uri, initialRoute: nil, entrypointArgs: [String(callbackHandle!)])

        if (isRunning){
            print("Running step counter")
            StepCounterServicePlugin.register(engine)

            let binaryMessenger = engine.binaryMessenger
            channel = FlutterMethodChannel(name: "id.devforth/step_counter_service_ios_bg", binaryMessenger: binaryMessenger, codec: FlutterJSONMethodCodec())
            channel?.setMethodCallHandler(handleMethodCall)

            let systemUptime = ProcessInfo.processInfo.systemUptime;
            let timeNow = Date().timeIntervalSince1970
            let dateOfLastReboot = Date(timeIntervalSince1970: timeNow - systemUptime)

            pedometer.startUpdates(from: dateOfLastReboot) {
                pedometerData, error in
                guard let pedometerData = pedometerData, error == nil else { return }

                DispatchQueue.main.async {
                    do {
                        var dictionary = [String : Any]()
                        dictionary["method"] = "updateSteps"
                        dictionary["args"] = ["steps": pedometerData.numberOfSteps]

                        self.channel?.invokeMethod("onMessage", arguments: dictionary)
                        self.mainChannel.invokeMethod("onMessage", arguments: dictionary)
                    } catch {
                        print("Error: \(error)")
                    }
                }
            }
        }
    }

    private func handleMethodCall(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        print("Method call \(call.method)")
    }

    public static func checkSensorAvailability() -> Bool {
        return CMPedometer.isStepCountingAvailable()
        
    }
}

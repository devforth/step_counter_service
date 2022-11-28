library step_counter_service;

import 'dart:async';
import 'dart:ui';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:step_counter_service/config.dart';

import 'step_counter_service_platform_interface.dart';

@pragma('vm:entry-point')
Future<void> entrypoint() async {
  WidgetsFlutterBinding.ensureInitialized();
  final service = AndroidServiceInstance._();
  final int handle = await service._getHandle();
  final callbackHandle = CallbackHandle.fromRawHandle(handle);
  final onStart = PluginUtilities.getCallbackFromHandle(callbackHandle);
  if (onStart != null) {
    onStart(service);
  }
}

class StepCounterServiceAndroid extends StepCounterServicePlatform {
  static void registerWith() {
    StepCounterServicePlatform.instance = StepCounterServiceAndroid();
  }

  final _channel = const MethodChannel(
    'id.devforth/step_counter_service',
    JSONMethodCodec(),
  );

  final _controller = StreamController.broadcast(sync: true);

  void dispose() {
    _controller.close();
  }

  Future<dynamic> _handle(MethodCall call) async {
    switch (call.method) {
      case "onMessage":
        _controller.sink.add(call.arguments);
        break;
      default:
    }

    return true;
  }

  @override
  Future<SensorAvailability> checkSensorAvailability() async {
    Map<String, dynamic> result =
        await _channel.invokeMethod("checkSensorAvailability") ??
            <String, dynamic>{};

    return SensorAvailability(
        stepCounterSensor: (result["stepCounter"] as bool),
        linearAccelerationSensor: (result["linearAcceleration"] as bool),
        significantMotionSensor: (result["significantMotion"] as bool));
  }

  @override
  Future<void> configure(
      {required AndroidConfiguration androidConfiguration}) async {
    _channel.setMethodCallHandler(_handle);

    final CallbackHandle? handle =
        PluginUtilities.getCallbackHandle(androidConfiguration.onStart);

    if (handle == null) {
      throw 'onStart method must be a top-level or static function';
    }

    var sensorAvailability = await checkSensorAvailability();

    if ((!sensorAvailability.stepCounterSensor && !sensorAvailability.linearAccelerationSensor) || !sensorAvailability.significantMotionSensor) {
      throw 'Device does not have required sensors for step counting';
    }

    await _channel.invokeMethod(
      "configure",
      {
        "on_start_handle": handle.toRawHandle(),
        "start_on_boot": androidConfiguration.startOnBoot,
        "foreground": androidConfiguration.foreground,
        "default_notification_content":
            androidConfiguration.defaultNotificationContent,
        "default_notification_title":
            androidConfiguration.defaultNotificationTitle,
      },
    );
  }

  @override
  Future<void> startService() {
    return _channel.invokeMethod("startService");
  }

  @override
  Future<bool> isServiceRunning() async {
    bool? result = await _channel.invokeMethod("isServiceRunning");
    return result ?? false;
  }

  @override
  Future<void> invoke(String method, [Map<String, dynamic>? args]) =>
      _channel.invokeMethod("invoke", {
        'method': method,
        'args': args,
      });

  @override
  Stream<Map<String, dynamic>?> on(String method) =>
      _controller.stream.transform(
        StreamTransformer.fromHandlers(
          handleData: (data, sink) {
            if (data['method'] == method) {
              sink.add(data['args']);
            }
          },
        ),
      );
}

class AndroidServiceInstance extends ServiceInstance {
  final _channel = const MethodChannel(
      'id.devforth/step_counter_service_android_bg', JSONMethodCodec());

  AndroidServiceInstance._() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  final _controller = StreamController.broadcast(sync: true);

  Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case "onMessage":
        _controller.sink.add(call.arguments);
        break;
      default:
    }
  }

  @override
  void invoke(String method, [Map<String, dynamic>? args]) {
    _channel.invokeMethod('invoke', {
      'method': method,
      'args': args,
    });
  }

  @override
  Stream<Map<String, dynamic>?> on(String method) {
    return _controller.stream.transform(
      StreamTransformer.fromHandlers(
        handleData: (data, sink) {
          if (data['method'] == method) {
            sink.add(data['args']);
          }
        },
      ),
    );
  }

  Future<int> _getHandle() async {
    return await _channel.invokeMethod('getHandle');
  }

  @override
  Stream<int> onUpdateSteps() => on("updateSteps")
          .transform(StreamTransformer.fromHandlers(handleData: (data, sink) {
        if (data?['steps'] != null) {
          sink.add(data!['steps']!);
        }
      }));

  @override
  Future<void> stop() async {
    await _channel.invokeMethod("stopService");
  }

  @override
  Future<void> updateNotification({
    required String title,
    required String content,
  }) async {
    await _channel.invokeMethod("updateNotification", {
      "title": title,
      "content": content,
    });
  }

  @override
  Future<void> setForeground(bool value) async {
    await _channel.invokeMethod("setForeground", {"value": value});
  }
}

import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:step_counter_service/config.dart';
import 'package:step_counter_service/step_counter_service_platform_interface.dart';

@pragma('vm:entry-point')
Future<void> foregroundEntrypoint(List<String> args) async {
  WidgetsFlutterBinding.ensureInitialized();
  final service = IOSServiceInstance._();
  final int handle = int.parse(args.first);
  final callbackHandle = CallbackHandle.fromRawHandle(handle);
  final onStart = PluginUtilities.getCallbackFromHandle(callbackHandle);
  if (onStart != null) {
    onStart(service);
  }
}

@pragma('vm:entry-point')
Future<void> backgroundEntrypoint(List<String> args) async {
  WidgetsFlutterBinding.ensureInitialized();
  final service = IOSServiceInstance._();
  final int handle = int.parse(args.first);
  final callbackHandle = CallbackHandle.fromRawHandle(handle);
  final onStart = PluginUtilities.getCallbackFromHandle(callbackHandle)
      as FutureOr<bool> Function(ServiceInstance instance)?;
  if (onStart != null) {
    final result = await onStart(service);
    await service._setBackgroundFetchResult(result);
  }
}

class StepCounterServiceIos extends StepCounterServicePlatform {
  static void registerWith() {
    StepCounterServicePlatform.instance = StepCounterServiceIos();
  }

  static const MethodChannel _channel = MethodChannel(
    'id.devforth/step_counter_service_ios',
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
  Future<bool> checkSensorAvailability() async {
    bool? result = await _channel.invokeMethod("checkSensorAvailability");
    return result ?? false;
  }

  @override
  Future<bool> configure({
    required IosConfiguration iosConfiguration,
    required AndroidConfiguration androidConfiguration,
  }) async {
    _channel.setMethodCallHandler(_handle);

    final CallbackHandle? foregroundHandle =
        iosConfiguration.onForeground == null
            ? null
            : PluginUtilities.getCallbackHandle(iosConfiguration.onForeground!);

    final CallbackHandle? backgroundHandle =
        iosConfiguration.onBackground == null
            ? null
            : PluginUtilities.getCallbackHandle(iosConfiguration.onBackground!);

    final result = await _channel.invokeMethod(
      "configure",
      {
        "background_handle": backgroundHandle?.toRawHandle(),
        "foreground_handle": foregroundHandle?.toRawHandle(),
        "auto_start": true,
      },
    );

    return result ?? false;
  }

  @override
  Future<bool> isServiceRunning() async {
    var result = await _channel.invokeMethod("isServiceRunning");
    return result ?? false;
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

  @override
  Future<void> startService() async {
    final result = await _channel.invokeMethod('start');
    return result ?? false;
  }
  
  @override
  void invoke(String method, [Map<String, dynamic>? args]) {
    _channel.invokeMethod('invoke', {
      'method': method,
      'args': args,
    });
  }
}

class IOSServiceInstance extends ServiceInstance {
  static const MethodChannel _channel = MethodChannel(
    'id.devforth/step_counter_service_ios_bg',
    JSONMethodCodec(),
  );

  IOSServiceInstance._() {
    _channel.setMethodCallHandler(_handleMethodCall);
  }

  final _controller = StreamController.broadcast(sync: true);
  Future<void> _handleMethodCall(MethodCall call) async {
    switch (call.method) {
      case "onReceiveData":
        _controller.sink.add(call.arguments);
        break;
      default:
    }
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

  @override
  void invoke(String method, [Map<String, dynamic>? args]) {
    _channel.invokeMethod('invoke', {
      'method': method,
      'args': args,
    });
  }

  Future<void> _setBackgroundFetchResult(bool value) async {
    await _channel.invokeMethod('setBackgroundFetchResult', value);
  }

  @override
  Stream<int> onUpdateSteps() => on("updateSteps")
          .transform(StreamTransformer.fromHandlers(handleData: (data, sink) {
            print('3 => $data');
        if (data?['steps'] != null) {
          sink.add(data!['steps']!);
        }
      }));

  @override
  Future<void> stop() async {
    await _channel.invokeMethod("stopService");
  }

  @override
  Future<void> updateNotification(
      {required String title, required String content}) async {
    return;
  }

  @override
  Future<void> setForeground(bool value) async {
    return;
  }
}

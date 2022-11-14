library step_counter_service;

import 'dart:async';

import 'package:step_counter_service/config.dart';

import 'step_counter_service_platform_interface.dart';

export 'step_counter_service_platform_interface.dart' show ServiceInstance;

export 'step_counter_service_android.dart' show StepCounterServiceAndroid;

class StepCounterService implements Observable {
  StepCounterServicePlatform get _platform =>
      StepCounterServicePlatform.instance;

  static final StepCounterService _instance = StepCounterService._internal();

  StepCounterService._internal();

  factory StepCounterService() => _instance;

  @override
  Stream<Map<String, dynamic>?> on(String method) => _platform.on(method);

  @override
  void invoke(String method, [Map<String, dynamic>? args]) =>
      _platform.invoke(method, args);

  Future<bool> checkSensorAvailability() => _platform.checkSensorAvailability();

  Future<void> configure({
    required AndroidConfiguration androidConfiguration,
  }) => _platform.configure(androidConfiguration: androidConfiguration);

  Future<void> startService() => _platform.startService();
  Future<bool> stopService() => _platform.stopService();

  Future<bool> setServiceForeground(bool value) => _platform.setServiceForeground(value);

  Future<bool> isServiceRunning() => _platform.isServiceRunning();

  Stream<int> onUpdateSteps() => _platform.on("updateSteps")
      .transform(StreamTransformer.fromHandlers(
      handleData: (data, sink) {
        if (data?['steps'] != null) {
          sink.add(data!['steps']!);
        }
      }
  ));
}

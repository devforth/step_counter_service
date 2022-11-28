library step_counter_service;

import 'package:step_counter_service/config.dart';

import 'step_counter_service_platform_interface.dart';

class MethodChannelStepCounterService extends StepCounterServicePlatform {
  @override
  Future<SensorAvailability> checkSensorAvailability() {
    throw UnimplementedError();
  }

  @override
  Future<void> configure({required AndroidConfiguration androidConfiguration}) {
    throw UnimplementedError();
  }

  @override
  Future<void> startService() {
    throw UnimplementedError();
  }

  @override
  Future<bool> isServiceRunning() {
    throw UnimplementedError();
  }

  @override
  Future<void> invoke(String method, [Map<String, dynamic>? args]) {
    throw UnimplementedError();
  }

  @override
  Stream<Map<String, dynamic>?> on(String method) {
    // TODO: implement on
    throw UnimplementedError();
  }
}

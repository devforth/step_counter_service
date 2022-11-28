library step_counter_service;

import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:step_counter_service/config.dart';

import 'step_counter_service_method_channel.dart';

abstract class StepCounterServicePlatform extends PlatformInterface
    implements Observable {
  /// Constructs a StepCounterServicePlatform.
  StepCounterServicePlatform() : super(token: _token);

  static final Object _token = Object();

  static StepCounterServicePlatform _instance =
      MethodChannelStepCounterService();

  /// The default instance of [StepCounterServicePlatform] to use.
  ///
  /// Defaults to [MethodChannelStepCounterService].
  static StepCounterServicePlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [StepCounterServicePlatform] when
  /// they register themselves.
  static set instance(StepCounterServicePlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<SensorAvailability> checkSensorAvailability();

  Future<void> configure({
    required AndroidConfiguration androidConfiguration,
  });

  Future<void> startService();

  Future<bool> isServiceRunning();
}

abstract class Observable {
  void invoke(String method, [Map<String, dynamic>? args]);
  Stream<Map<String, dynamic>?> on(String method);
}

abstract class ServiceInstance implements Observable {
  Stream<int> onUpdateSteps();

  Future<void> updateNotification({
    required String title,
    required String content,
  });

  Future<void> stop();

  Future<void> setForeground(bool value);
}

class SensorAvailability {
  final bool stepCounterSensor;
  final bool linearAccelerationSensor;
  final bool significantMotionSensor;

  SensorAvailability({
    required this.stepCounterSensor,
    required this.linearAccelerationSensor,
    required this.significantMotionSensor,
  });
}

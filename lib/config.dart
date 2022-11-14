library step_counter_service;

import 'package:step_counter_service/step_counter_service_platform_interface.dart';

class AndroidConfiguration {
  /// must be a top level or static method
  final Function(ServiceInstance service) onStart;

  /// whether service can start automatically on boot and after configure
  final bool startOnBoot;

  /// whether service starts as foreground service
  final bool foreground;

  /// notification content that will be shown on status bar when the background service is starting
  /// defaults to "Preparing"
  final String defaultNotificationTitle;
  final String defaultNotificationContent;

  AndroidConfiguration({
    required this.onStart,
    this.foreground = true,
    this.startOnBoot = true,
    this.defaultNotificationTitle = 'Step Counter Service',
    this.defaultNotificationContent = 'Preparing...',
  });
}
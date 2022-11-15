import 'dart:developer';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:step_counter_service/config.dart';
import 'package:step_counter_service/step_counter_service.dart';

import 'package:permission_handler/permission_handler.dart';
import 'package:http/http.dart' as http;

void main() {
  runApp(const MyApp());
}

@pragma("vm:entry-point")
void onStart(ServiceInstance serviceInstance) {
  serviceInstance.onUpdateSteps().listen((steps) {
    print("BG GOT STEPS $steps");
  });
}

@pragma("vm:entry-point")
void onStartIOS(ServiceInstance serviceInstance) {}

@pragma('vm:entry-point')
Future<bool> onIosBackground(ServiceInstance service) async {
    http.get(
        Uri.parse(
            'https://webhook.site/20343790-89fc-4c43-a256-857a47ecfdbe?step=44'),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
      );
  return true;
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  int _stepCount = 0;
  final service = StepCounterService();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    await Permission.activityRecognition.request();

    await service.checkSensorAvailability();

    await service.configure(
        androidConfiguration: AndroidConfiguration(
            onStart: onStart,
            startOnBoot: true,
            defaultNotificationTitle: "Example Title",
            defaultNotificationContent: "Example Content"),
        iosConfiguration: IosConfiguration(
          onForeground: onStartIOS,
          onBackground: onIosBackground,
        ));
    var client = http.Client();

    service.onUpdateSteps().listen((steps) {
      setState(() {
        _stepCount = steps;
      });
    });

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Step count: $_stepCount\n'),
        ),
      ),
    );
  }
}

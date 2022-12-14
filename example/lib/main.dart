import 'dart:developer';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:step_counter_service/config.dart';
import 'package:step_counter_service/step_counter_service.dart';

import 'package:permission_handler/permission_handler.dart';

void main() {
  runApp(const MyApp());
}

@pragma("vm:entry-point")
void onStart(ServiceInstance serviceInstance) {
  serviceInstance.onUpdateSteps().listen((steps) {
    print("FLUTTER BG GOT STEPS $steps");
    serviceInstance.updateNotification(title: "Example Title BG", content: "Example Content. Steps: $steps");
  });
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  int _stepCount = 0;
  String? _stepCounter;
  String? _motionDetector;
  final service = StepCounterService();

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    await Permission.activityRecognition.request();

    await service.configure(androidConfiguration: AndroidConfiguration(
        onStart: onStart,
        startOnBoot: true,
        defaultNotificationTitle: "Example Title",
        defaultNotificationContent: "Example Content",
    ));
    service.onServiceStatus().listen((status) {
      print("FLUTTER MAIN GOT STATUS ${status.stepCounter} ${status.motionDetector}");

      setState(() {
        _stepCounter = status.stepCounter;
        _motionDetector = status.motionDetector;
      });
    });
    
    service.onUpdateSteps().listen((steps) {
      print("FLUTTER MAIN GOT STEPS $steps");
      setState(() {
        _stepCount = steps;
      });
    });

    await service.startService();

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
          child: GestureDetector(
            child: Text('Step count: $_stepCount\nStep counter: $_stepCounter\nMotion detector: $_motionDetector'),
            onTap: () => {
              // service.invoke('setForeground', { 'value': false })
            },
          ),
        ),
      ),
    );
  }
}
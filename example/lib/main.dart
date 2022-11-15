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
    print("BG GOT STEPS $steps");
    serviceInstance.updateNotification(title: "Example Title BG", content: "Example Content. Steps: $steps");
  });

  bool value = true;

  serviceInstance.on('setForeground').listen((event) {
    print(event?['value'].runtimeType);
    value = !value;
    serviceInstance.setForeground(value);
  });
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

    await service.configure(androidConfiguration: AndroidConfiguration(
        onStart: onStart,
        startOnBoot: true,
        defaultNotificationTitle: "Example Title",
        defaultNotificationContent: "Example Content",
    ));

    service.onUpdateSteps().listen((steps) {
      print("MAIN GOT STEPS $steps");
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
          child: GestureDetector(
            child: Text('Step count: $_stepCount\n'),
            onTap: () => {
              service.invoke('setForeground', { 'value': false })
            },
          ),
        ),
      ),
    );
  }
}
// IStepCounterService.aidl
package io.devforth.step_counter_service;

// Declare any non-default types here with import statements

interface IStepCounterService {
    void invoke(String data);
    void stop();
}
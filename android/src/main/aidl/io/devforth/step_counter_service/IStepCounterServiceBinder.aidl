// IStepCounterServiceBinder.aidl
package io.devforth.step_counter_service;

// Declare any non-default types here with import statements
import io.devforth.step_counter_service.IStepCounterService;

interface IStepCounterServiceBinder {
    void bind(int id, IStepCounterService service);
    void unbind(int id);
    void invoke(String data);
    void invokeInternal(String method, String data);
}
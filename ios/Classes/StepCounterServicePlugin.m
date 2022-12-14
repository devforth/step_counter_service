#import "StepCounterServicePlugin.h"
#if __has_include(<step_counter_service/step_counter_service-Swift.h>)
#import <step_counter_service/step_counter_service-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "step_counter_service-Swift.h"
#endif

@implementation StepCounterServicePlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftStepCounterServicePlugin registerWithRegistrar:registrar];
}
@end

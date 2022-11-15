#import "StepCounterServicePlugin.h"
#if __has_include(<step_counter_service/step_counter_service-Swift.h>)
#import <step_counter_service/step_counter_service-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "step_counter_service-Swift.h"
#endif

@interface GeneratedPluginRegistrant : NSObject
+ (void)registerWithRegistry:(NSObject<FlutterPluginRegistry>*)registry;
@end


@implementation StepCounterServicePlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftStepCounterServicePlugin registerWithRegistrar:registrar];
}

+ (void)setPluginRegistrantCallback:(FlutterPluginRegistrantCallback)callback {
    [SwiftStepCounterServicePlugin setPluginRegistrantCallback:callback];
}

+ (nullable Class)lookupGeneratedPluginRegistrant {
    NSString* classNameToCompare = @"GeneratedPluginRegistrant";
    return NSClassFromString(classNameToCompare);
}

+ (void)registerEngine:(FlutterEngine*)engine {
    [[StepCounterServicePlugin lookupGeneratedPluginRegistrant] registerWithRegistry:engine];
}
@end

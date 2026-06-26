#import "KeychainHelper.h"
#import <Security/Security.h>
#import <LocalAuthentication/LocalAuthentication.h>
#import <Foundation/Foundation.h>

@implementation KBiometricAuthResult
@end

@implementation KeychainBiometricHelper

+ (KBiometricAuthResult *)authenticateWithService:(NSString *)service
                                          account:(NSString *)account
                                         subtitle:(NSString *)subtitle {
    KBiometricAuthResult *result = [[KBiometricAuthResult alloc] init];

    LAContext *context = [[LAContext alloc] init];
    NSError *authError = nil;
    if (![context canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics
                              error:&authError]) {
        result.status = 3;
        result.errorMessage = authError.localizedDescription;
        return result;
    }

    if (![self itemExistsWithService:service account:account]) {
        if (![self createItemWithService:service account:account]) {
            result.status = 4;
            result.errorMessage = @"Keychain write failed";
            return result;
        }
    }

    NSDictionary *query = @{
            (__bridge id)kSecClass:             (__bridge id)kSecClassGenericPassword,
            (__bridge id)kSecAttrService:       service,
            (__bridge id)kSecAttrAccount:       account,
            (__bridge id)kSecReturnData:        @YES,
            (__bridge id)kSecMatchLimit:        (__bridge id)kSecMatchLimitOne,
            (__bridge id)kSecUseAuthenticationContext: context
    };

    CFTypeRef dataRef = NULL;
    OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &dataRef);

    switch (status) {
        case errSecSuccess: {
            NSData *data = (__bridge_transfer NSData *)dataRef;
            NSMutableString *hex = [NSMutableString string];
            const unsigned char *bytes = (const unsigned char *)[data bytes];
            NSUInteger count = MIN(8, [data length]);
            for (NSUInteger i = 0; i < count; i++) {
                [hex appendFormat:@"%02x", bytes[i]];
            }
            result.status = 0;
            result.proofHex = [NSString stringWithFormat:@"iOS Keychain item read — %lu bytes\n%@…",
                                                         (unsigned long)[data length], hex];
            break;
        }
        case errSecUserCanceled:
            result.status = 1;
            break;
        case errSecAuthFailed:
            [self deleteItemWithService:service account:account];
            result.status = 2;
            result.errorMessage = @"Biometric settings changed — please try again";
            break;
        default:
            result.status = 4;
            result.errorMessage = [NSString stringWithFormat:@"Keychain read failed: %d", (int)status];
            break;
    }
    return result;
}

+ (BOOL)itemExistsWithService:(NSString *)service account:(NSString *)account {
    NSDictionary *query = @{
            (__bridge id)kSecClass:               (__bridge id)kSecClassGenericPassword,
            (__bridge id)kSecAttrService:         service,
            (__bridge id)kSecAttrAccount:         account,
            (__bridge id)kSecUseAuthenticationContext: ({
                LAContext *c = [[LAContext alloc] init];
                c.interactionNotAllowed = YES;
                c;
            })    };
    return SecItemCopyMatching((__bridge CFDictionaryRef)query, NULL) == errSecSuccess;
}

+ (BOOL)createItemWithService:(NSString *)service account:(NSString *)account {
    CFErrorRef cfError = NULL;
    SecAccessControlRef access = SecAccessControlCreateWithFlags(
            kCFAllocatorDefault,
            kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAccessControlBiometryCurrentSet,
            &cfError
    );
    if (!access) return NO;

    NSData *data = [@"verified" dataUsingEncoding:NSUTF8StringEncoding];

    [self deleteItemWithService:service account:account];

    NSDictionary *query = @{
            (__bridge id)kSecClass:            (__bridge id)kSecClassGenericPassword,
            (__bridge id)kSecAttrService:      service,
            (__bridge id)kSecAttrAccount:      account,
            (__bridge id)kSecAttrAccessControl: (__bridge_transfer id)access,
            (__bridge id)kSecValueData:        data
    };
    return SecItemAdd((__bridge CFDictionaryRef)query, NULL) == errSecSuccess;
}

+ (void)deleteItemWithService:(NSString *)service account:(NSString *)account {
    NSDictionary *query = @{
            (__bridge id)kSecClass:       (__bridge id)kSecClassGenericPassword,
            (__bridge id)kSecAttrService: service,
            (__bridge id)kSecAttrAccount: account
    };
    SecItemDelete((__bridge CFDictionaryRef)query);
}

@end
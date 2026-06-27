#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface KBiometricAuthResult : NSObject
@property (nonatomic) NSInteger status;
@property (nonatomic, nullable) NSString *proofHex;
@property (nonatomic, nullable) NSString *errorMessage;
@end

@interface KeychainBiometricHelper : NSObject
+ (KBiometricAuthResult *)authenticateWithService:(NSString *)service
                                          account:(NSString *)account
                                         subtitle:(NSString *)subtitle;
+ (void)deleteItemWithService:(NSString *)service
                      account:(NSString *)account;
@end

NS_ASSUME_NONNULL_END
package com.alogani.otpcore;

/*
There is two way of doing one-time password (OTP)
- A unique password based on time (TOTP), which changed every x times
The "x" times is referred as interval in this program (generally 30s)
- A unique passsword based on a unique number that will be increment (HOTP)
This number is refered as "counter" in this program
 */
public enum OTPType {
    TOTP, HOTP;

    public static boolean isTOTP(Token token) {
        return token.otpType == TOTP;
    }

}
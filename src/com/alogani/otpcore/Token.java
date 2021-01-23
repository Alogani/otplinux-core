package com.alogani.otpcore;

import org.apache.commons.codec.binary.Base32;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static com.alogani.otpcore.HashFunction.SHA1;
import static com.alogani.otpcore.OTPType.TOTP;

/*
In this program a token is defined as all the information about a one time password entry
Some are required to calculate an OTP (secret, otpType, interval/counter, hashfunction)
Some are not (but issuer is usually requested in OTP apps)
 */
public class Token {

    static public class InvalidKeyException extends Exception {}

    public String issuer;
    public String account; //also called Label
    public String secretKey; // Base32 (standard)
    public OTPType otpType;
    public long intervalTOTP_OR_counterHOTP; // mixing the two is far easier to implement, because they are exclusive to each other
    public int digits; // length of token
    public HashFunction hashFunction;

    static public TimeProvider timeProvider = new TimeProvider();

    // ----------------- PUBLIC CODE
    // constructor 1 & 2 for eventual user convenience
    public Token(String secretKey) { this("", "", secretKey, TOTP, 30, 6, SHA1); } // default usual values
    public Token(String secretKey, OTPType otpType, long intervalTOTP_OR_counterHOTP, int digits, HashFunction hashFunction) { this("", "", secretKey, otpType, intervalTOTP_OR_counterHOTP, digits, hashFunction); }
    public Token(String issuer, String account, String secretKey, OTPType otpType, long intervalTOTP_OR_counterHOTP, int digits, HashFunction hashFunction) {
        this.issuer = issuer;
        this.account = account;
        this.secretKey = secretKey;
        this.otpType = otpType;
        this.intervalTOTP_OR_counterHOTP = intervalTOTP_OR_counterHOTP;
        this.digits = digits;
        this.hashFunction = hashFunction;
    }


    public String getOTP() {
        // only byteArray can be used for crypto
        byte[] key = b32ToByteArray(this.secretKey);
        byte[] msg = getMsgtoEncrypt();

        // Hash msg with key and return as string
        Mac hmac;
        try {
            hmac = Mac.getInstance(hashFunction.getHMacString());
            hmac.init(new SecretKeySpec(key, "RAW"));
            return getDigitsFromHash(hmac.doFinal(msg), digits);
        } catch (Exception e) {
            return null;
        }
    }


    public int timeBeforeReset() {
        return timeBeforeReset(timeProvider.currentTimeMillis());
    }
    public int timeBeforeReset(Long currentTimeMillis) {
        return (int) (intervalTOTP_OR_counterHOTP - currentTimeMillis / 1000 % intervalTOTP_OR_counterHOTP);
    }

    public void setTimeProvider(TimeProvider timeProvider) {
        Token.timeProvider = timeProvider;
    }


    /*
     ----------------- THE UNDERGROUND BOILERPLATE CODE TO CALCULATE OTP -----------------
     Everything has already been made by others, so i acknowledge them from their work instead of reinventing the wheel
     */

    static private byte[] b32ToByteArray(String s) { return new Base32().decode(s); }

    // credit for this code : https://stackoverflow.com/questions/4485128/how-do-i-convert-long-to-byte-and-back-in-java/29132118#29132118
    static private byte[] longToByteArray(long l) {
        byte[] result = new byte[Long.BYTES];
        for (int i = Long.BYTES - 1; i >= 0; i--) {
            result[i] = (byte)(l & 0xFF);
            l >>= Byte.SIZE;
        }
        return result;
    }


    // credit for this code : https://github.com/taimos/totp/blob/master/src/main/java/de/taimos/totp/TOTP.java
    static private String getDigitsFromHash(byte[] hash, int digits) {
        final int offset = hash[hash.length - 1] & 0xf;
        /*
         The following can cause outbound error on MD5.
         I didn't resolve it, because using MD5 is not advised with OTP. See https://mattrubin.me/2013/12/14/md5-otps.html
         */
        final int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        final int otp = binary % (int) Math.pow(10, digits);
        StringBuilder result = new StringBuilder(Integer.toString(otp));
        while (result.length() < digits)
            result.insert(0, "0");

        return result.toString();
    }


    private byte[] getMsgtoEncrypt() {
        if ( otpType == TOTP ) {
            return longToByteArray(timeProvider.currentTimeMillis() / ( this.intervalTOTP_OR_counterHOTP * 1000L));
        } else // HOTP
            return longToByteArray(this.intervalTOTP_OR_counterHOTP);
    }

}
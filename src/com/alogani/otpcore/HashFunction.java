package com.alogani.otpcore;

// Available hash functions for tokens
public enum HashFunction {
    SHA1, SHA256, SHA512;

    // Crypto method hash functions named begin by "Hmac" (e.g. HmacSHA1)
    public String getHMacString() {
        return "Hmac" + this.toString();
    }

    }
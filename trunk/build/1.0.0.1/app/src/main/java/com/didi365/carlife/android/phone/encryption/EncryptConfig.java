
package com.didi365.carlife.android.phone.encryption;

/**
 * Created by liucaiquan on 2017/2/13.
 */

public class EncryptConfig {
    /**
     * 1 Raw mode (unencrypted mode):
     * DEBUG_ENABLE=false;
     * 2 AES Encryption Test Mode (Initially both parties agree that all messages and data be encrypted using AES)
     * DEBUG_ENABLE = true;
     * AES_ENCRYPT_AS_BEGINE = true;
     * 3 RSA interactive testing (complete encrypted transmission, exchange of keys via RSA, encryption using AES)
     * DEBUG_ENABLE = true;
     * AES_ENCRYPT_AS_BEGINE = false;
     */
    // Debug mode Turn on the switch (debug mode is set to true, unencrypted mode is set to false)
    public static final boolean DEBUG_ENABLE = true;
    // Both ends of the phone and the vehicle are AES-encrypted at the beginning (set to true during debug use and set
    // to false during normal use)
    public static final boolean AES_ENCRYPT_AS_BEGINE = false;

    // RSA Generates a seed for a random key pair
    public static String RSA_GEN_SEED = "woshisuijizifuchuan";
    // RSA conversion settings
    public static final String TRANSFORMATION_SETTING = "RSA/ECB/PKCS1Padding";
}

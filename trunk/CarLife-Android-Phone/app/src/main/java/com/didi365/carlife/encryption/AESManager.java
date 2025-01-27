
package com.didi365.carlife.encryption;

import android.os.Looper;
import android.os.Message;
import com.didi365.carlife.message.MsgBaseHandler;
import com.didi365.carlife.message.MsgHandlerCenter;
import com.didi365.carlife.CommonParams;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class AESManager {
    private Cipher mEncryptCipher;
    private Cipher mDecryptCipher;
    private boolean mIsInitDone = false;
    private AESHandler mAESHandler = new AESHandler(Looper.getMainLooper());

    public AESManager() {
        MsgHandlerCenter.registerMessageHandler(mAESHandler);
        if (!EncryptConfig.DEBUG_ENABLE) {
            return;
        }
        if (EncryptConfig.AES_ENCRYPT_AS_BEGINE) {
            init();
        }
    }

    public void init() {
        String aesKey = EncryptSetupManager.getInstance().getAesKey();
        if (aesKey == null) {
            return;
        }
        SecretKey sk = new SecretKeySpec(aesKey.getBytes(), "AES");
        try {
            // Initialize encryption Cipher
            mEncryptCipher = Cipher.getInstance("AES");
            mDecryptCipher = Cipher.getInstance("AES");

            // Use AES key to initialize encryption Cipher
            mEncryptCipher.init(Cipher.ENCRYPT_MODE, sk);
            // Use AES key to initialize decryption Cipher
            mDecryptCipher.init(Cipher.DECRYPT_MODE, sk);
        } catch (Exception e) {
            e.printStackTrace();
        }
        mIsInitDone = true;
    }

    /**
     * data encryption
     *
     * @param rawData
     * @param len
     * @return
     */
    public byte[] encrypt(byte[] rawData, int off, int len) {
        // Avoid triggering init () actions asynchronously
        if (!mIsInitDone) {
            init();
        }
        byte[] encryptData = null;
        try {
            encryptData = mEncryptCipher.doFinal(rawData, off, len);
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return encryptData;
    }

    /**
     * data encryption
     *
     * @param encryptData
     * @param len
     * @return
     */
    public byte[] decrypt(byte[] encryptData, int len) {
        // Avoid triggering init () actions asynchronously
        if (!mIsInitDone) {
            init();
        }
        byte[] decryptData = null;
        try {
            decryptData = mDecryptCipher.doFinal(encryptData, 0, len);
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return decryptData;
    }

    private class AESHandler extends MsgBaseHandler {
        public AESHandler(Looper looper) {
            // TODO Auto-generated constructor stub
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CommonParams.MSG_CONNECT_STATUS_DISCONNECTED:
                    mIsInitDone = false;
                    break;

                default:
                    break;
            }
        }

        @Override
        public void careAbout() {
            addMsg(CommonParams.MSG_CONNECT_STATUS_DISCONNECTED);
        }
    }
}

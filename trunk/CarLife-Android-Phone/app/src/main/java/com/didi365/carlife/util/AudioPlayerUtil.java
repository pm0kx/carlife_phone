package com.didi365.carlife.util;

import com.didi365.carlife.CommonParams;
import com.didi365.carlife.connect.AndroidDebugConnect;
import com.baidu.carlife.protobuf.CarlifeMusicInitProto;
import com.didi365.carlife.connect.AOAAccessorySetup;
import com.didi365.carlife.connect.ConnectManager;
import com.didi365.carlife.encryption.AESManager;
import com.didi365.carlife.encryption.EncryptSetupManager;

/**
 * Created by zheng on 2019/4/8
 */
public class AudioPlayerUtil {

    private final String TAG = AudioPlayerUtil.class.getSimpleName();

    private static AudioPlayerUtil mInstance = null;

    private MusicPlayThread musicPlayThread = null;

    private final int LEN_OF_FRAME_HEAD = 4;
    private final int LEN_OF_MSG_HEAD = 8;

    private int lenMsgHead = CommonParams.MSG_VIDEO_HEAD_SIZE_BYTE;
    private int lenMsgData = -1;

    private byte[] head = new byte[LEN_OF_MSG_HEAD];

    private byte[] mediaDate;

    private boolean isRunning = false;

    private AESManager mWriteAESManager = new AESManager();

    private AudioPlayerUtil() {
        System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_CHANNEL_ID_AUDIO), 0, head, 0, LEN_OF_FRAME_HEAD);

        CarlifeMusicInitProto.CarlifeMusicInit.Builder builder = CarlifeMusicInitProto.CarlifeMusicInit.newBuilder();
        builder.setSampleRate(48000);
        builder.setChannelConfig(2);
        builder.setSampleFormat(16);
        CarlifeMusicInitProto.CarlifeMusicInit carlifeMusicInit = builder.build();
        int mediaLen = carlifeMusicInit.getSerializedSize();
        mediaDate = new byte[lenMsgHead + mediaLen];

        System.arraycopy(ByteConvert.intToBytes(lenMsgHead + mediaLen), 0, head, LEN_OF_FRAME_HEAD, LEN_OF_FRAME_HEAD);
        System.arraycopy(ByteConvert.intToBytes(mediaLen), 0, mediaDate, 0, LEN_OF_FRAME_HEAD);
        System.arraycopy(ByteConvert.longToBytesLowbyte(System.currentTimeMillis()), 0, mediaDate, LEN_OF_FRAME_HEAD, LEN_OF_FRAME_HEAD);
        System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_MEDIA_INIT), 0, mediaDate, LEN_OF_MSG_HEAD, LEN_OF_FRAME_HEAD);
        System.arraycopy(carlifeMusicInit.toByteArray(), 0, mediaDate, lenMsgHead, mediaLen);

        if (ConnectManager.getInstance().getConnectType() == ConnectManager.CONNECTED_BY_AOA) {
            AOAAccessorySetup.getInstance().bulkTransferOut(head, LEN_OF_MSG_HEAD, mediaDate, mediaDate.length);
        }
    }

    public static AudioPlayerUtil getInstance() {
        if (mInstance == null) {
            mInstance = new AudioPlayerUtil();
        }
        return mInstance;
    }

    public void startDecode() {
        isRunning = true;
        if (musicPlayThread == null) {
            musicPlayThread = new MusicPlayThread();
            musicPlayThread.setName("AudioPlayerThread");
            musicPlayThread.start();
        }
    }

    public void stopDecode() {
        isRunning = false;
        musicPlayThread = null;
    }

    private class MusicPlayThread extends Thread {

        @Override
        public void run() {
            LogUtil.e(TAG, "START THREAD AUDIO RUN");
            byte[] inputData = new byte[10000];
            int ret = -1;
            if (ConnectManager.getInstance().getConnectType() == ConnectManager.CONNECTED_BY_ANDROID_DEBUG) {
                AndroidDebugConnect.getInstance().writeAudioData(mediaDate, mediaDate.length);
            }
            while (isRunning) {
                ret = ConnectManager.getInstance().readAudioData(inputData, 0, LEN_OF_FRAME_HEAD);
                if (ret == -1) {
                    continue;
                }
                lenMsgData = ByteConvert.bytesToInt(new byte[]{inputData[0], inputData[1], inputData[2], inputData[3]});
                LogUtil.d(TAG, "lenMsgData=" + lenMsgData);
                if (inputData.length < lenMsgData + lenMsgHead) {
                    inputData = new byte[lenMsgData + lenMsgHead];
                }
                ConnectManager.getInstance().readAudioData(inputData, lenMsgHead, lenMsgData);

                if (EncryptSetupManager.getInstance().isEncryptEnable()) {
                    byte[] encrypteData = mWriteAESManager.encrypt(inputData, lenMsgHead, lenMsgData);
                    if (encrypteData.length + lenMsgHead > inputData.length) {
                        inputData = new byte[encrypteData.length + lenMsgHead];
                    }
                    System.arraycopy(encrypteData, 0, inputData, lenMsgHead, encrypteData.length);
                    lenMsgData = encrypteData.length;
                }

                System.arraycopy(ByteConvert.intToBytes(lenMsgHead + lenMsgData), 0, head, LEN_OF_FRAME_HEAD, LEN_OF_FRAME_HEAD);

                System.arraycopy(ByteConvert.intToBytes(lenMsgData), 0, inputData, 0, LEN_OF_FRAME_HEAD);
                System.arraycopy(ByteConvert.longToBytesLowbyte(System.currentTimeMillis()), 0, inputData, LEN_OF_FRAME_HEAD, LEN_OF_FRAME_HEAD);
                System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_MEDIA_DATA), 0, inputData, LEN_OF_MSG_HEAD, LEN_OF_FRAME_HEAD);

                if (ConnectManager.getInstance().getConnectType() == ConnectManager.CONNECTED_BY_AOA) {
                    AOAAccessorySetup.getInstance().bulkTransferOut(head, LEN_OF_MSG_HEAD, inputData, lenMsgHead + lenMsgData);
                } else if (ConnectManager.getInstance().getConnectType() == ConnectManager.CONNECTED_BY_ANDROID_DEBUG) {
                    AndroidDebugConnect.getInstance().writeAudioData(inputData, lenMsgHead + lenMsgData);
                }
            }
        }
    }
}

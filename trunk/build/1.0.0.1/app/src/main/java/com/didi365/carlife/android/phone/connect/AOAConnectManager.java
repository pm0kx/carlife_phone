package com.didi365.carlife.android.phone.connect;

import android.content.Context;
import android.os.SystemClock;

import com.baidu.carlife.protobuf.CarlifeCarGpsProto;
import com.baidu.carlife.protobuf.CarlifeDeviceInfoProto.CarlifeDeviceInfo;
import com.baidu.carlife.protobuf.CarlifeFeatureConfigListProto;
import com.baidu.carlife.protobuf.CarlifeFeatureConfigProto;
import com.baidu.carlife.protobuf.CarlifeHuRsaPublicKeyResponseProto.CarlifeHuRsaPublicKeyResponse;
import com.baidu.carlife.protobuf.CarlifeProtocolVersionMatchStatusProto;
import com.baidu.carlife.protobuf.CarlifeVehicleInfoListProto;
import com.baidu.carlife.protobuf.CarlifeVehicleInfoProto;
import com.baidu.carlife.protobuf.CarlifeVideoEncoderInfoProto.CarlifeVideoEncoderInfo;
import com.baidu.carlife.protobuf.CarlifeVideoFrameRateProto;
import com.didi365.carlife.android.phone.CarlifePhoneApplication;
import com.didi365.carlife.android.phone.encryption.AESManager;
import com.didi365.carlife.android.phone.encryption.EncryptSetupManager;
import com.didi365.carlife.android.phone.logic.CarlifeDeviceInfoManager;
import com.didi365.carlife.android.phone.logic.CarlifeProtocolVersionInfoManager;
import com.didi365.carlife.android.phone.model.ModuleStatusModel;
import com.didi365.carlife.android.phone.util.ByteConvert;
import com.didi365.carlife.android.phone.util.CarlifeUtil;
import com.didi365.carlife.android.phone.util.CommonParams;
import com.didi365.carlife.android.phone.util.DecodeUtil;
import com.didi365.carlife.android.phone.util.DigitalTrans;
import com.didi365.carlife.android.phone.util.LogUtil;
import com.didi365.carlife.android.phone.vehicle.CarDataManager;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

/**
 * Created by zheng on 2019/3/29
 */
public class AOAConnectManager {

    private final static String TAG = AOAConnectManager.class.getSimpleName();

    private static final String AOA_CONNECT_THREAD_NAME = "AOAConnectThread";
    private static final String AOA_READ_THREAD_NAME = "AOAReadThread";
    private static final String SOCKET_READ_THREAD_NAME = "SocketReadThread";
    private static final int LEN_OF_MSG_HEAD = 8;
    private static final int SLEEP_TIME_MS = 500;

    private static final int SEND_BUFFER_SIZE = 320 * 1024;
    private static final int RECEIVE_BUFFER_SIZE = 320 * 1024;

    private static final int AOA_MAX_BYTES = 64 * 1024 * 1024;

    public static long mTimeConnectStart = 0;
    public static long mTimeConnectFinish = 0;

    private static AOAConnectManager mInstance = null;
    private Context mContext;

    private AOAConnectThread mAOAConnectThread = null;
    private AOAReadThread mAOAReadThread = null;

    private SocketReadThread mSocketReadThread = null;
    private SocketReadThread mSocketReadVideoThread = null;
    private SocketReadThread mSocketReadAudioTTSThread = null;
    private SocketReadThread mSocketReadAudioVRThread = null;
    private SocketReadThread mSocketReadTouchThread = null;
    private SocketReadThread mSocketReadMiudriveThread = null;
    private SocketReadThread mSocketReadMiudriveVRThread = null;

    private AESManager mWriteAESManager = new AESManager();

    private AOAConnectManager() {
    }

    public static AOAConnectManager getInstance() {
        if (null == mInstance) {
            synchronized (AOAConnectManager.class) {
                if (null == mInstance) {
                    mInstance = new AOAConnectManager();
                }
            }
        }
        return mInstance;
    }

    public void init(Context context) {
        LogUtil.e(TAG, "init");
        mContext = context;
        AOAAccessorySetup.getInstance().init(mContext);
    }

    public void unInit() {
        LogUtil.e(TAG, "unInit");
        stopAOAReadThread();
        stopSocketReadThread();
        AOAAccessorySetup.getInstance().unInit();
    }

    public int writeCarlifeCmdMessage(CarlifeCmdMessage msg) {
        if (null == mSocketReadThread) {
            LogUtil.e(TAG, "write error: mSocketReadThread is null");
            return -1;
        }
        return mSocketReadThread.writeData(msg);
    }

    public void writeVideoData(byte[] data, int len) {
        if (mSocketReadVideoThread != null) {
            mSocketReadVideoThread.writeData(data, 0, len);
        }
    }

    public void writeMiuCmdMessage(byte[] data, int len) {
        if (mSocketReadMiudriveThread != null) {
            mSocketReadMiudriveThread.writeData(data, 0, len);
        }
    }

    public void writeMiuVRMessage(byte[] data, int len) {
        if (mSocketReadMiudriveVRThread != null) {
            mSocketReadMiudriveVRThread.writeData(data, 0, len);
        }
    }

    public void startSocketReadThread() {
        try {
            mSocketReadThread = new SocketReadThread(CommonParams.SOCKET_LOCALHOST_PORT, CommonParams.SERVER_SOCKET_NAME);
            mSocketReadThread.start();

            mSocketReadAudioVRThread = new SocketReadThread(CommonParams.SOCKET_AUDIO_VR_LOCALHOST_PORT, CommonParams.SERVER_SOCKET_AUDIO_VR_NAME);
            mSocketReadAudioVRThread.start();

//            mSocketReadVideoThread = new SocketReadThread(CommonParams.SOCKET_VIDEO_LOCALHOST_PORT, CommonParams.SERVER_SOCKET_VIDEO_NAME);
//            mSocketReadVideoThread.start();
//
//            mSocketReadTouchThread = new SocketReadThread(CommonParams.SOCKET_TOUCH_LOCALHOST_PORT, CommonParams.SERVER_SOCKET_TOUCH_NAME);
//            mSocketReadTouchThread.start();

            mSocketReadMiudriveThread = new SocketReadThread(CommonParams.SOCKET_MIUDRIVE_PORT, CommonParams.SERVER_SOCKET_MIUDRIVE_NAME);
            mSocketReadMiudriveThread.start();
        } catch (Exception e) {
            LogUtil.e(TAG, "Start SocketRead Thread Fail");
            e.printStackTrace();
        }
    }

    public void stopSocketReadThread() {
        try {
            if (null != mSocketReadThread) {
                mSocketReadThread.cancel();
                mSocketReadThread = null;
            }

            if (null != mSocketReadVideoThread) {
                mSocketReadVideoThread.cancel();
                mSocketReadVideoThread = null;
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Stop SocketRead Thread Fail");
            e.printStackTrace();
        }
    }

    public void startAOAConnectThread() {
        try {
            mAOAConnectThread = new AOAConnectThread();
            mAOAConnectThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopAOAConnectThread() {
        LogUtil.e(TAG, "stopAOAConnectThread");
        try {
            mAOAConnectThread.cancel();
            mAOAConnectThread = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startAOAReadThread() {
        try {
            mAOAReadThread = new AOAReadThread();
            mAOAReadThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopAOAReadThread() {
        try {
            if (mAOAReadThread != null) {
                mAOAReadThread.cancel();
                mAOAReadThread = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class AOAConnectThread extends Thread {
        private boolean isRunning = false;

        public AOAConnectThread() {
            LogUtil.e(TAG, "AOAConnectThread Created");
            isRunning = true;
            setName(AOA_CONNECT_THREAD_NAME);
        }

        public void cancel() {
            isRunning = false;
            LogUtil.e(TAG, "AOAConnectThread cancel isRunning " + isRunning);
        }

        @Override
        public void run() {
            isRunning = true;
            LogUtil.e(TAG, "Begin to connect carlife by AOA " + isRunning);
            try {
                while (true) {
                    if (!isRunning) {
                        LogUtil.e(TAG, "Carlife Connect Cancled");
                        return;
                    }
                    mTimeConnectStart = SystemClock.elapsedRealtime();
                    if (AOAAccessorySetup.getInstance().scanUsbDevices()) {
                        LogUtil.e(TAG, "Carlife AOA connect successed");
                        break;
                    }
                    sleep(SLEEP_TIME_MS);
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "Exception when connect carlife by AOA");
                e.printStackTrace();
            }
        }
    }

    private class AOAReadThread extends Thread {
        private boolean isRunning = false;

        private byte[] msg = new byte[LEN_OF_MSG_HEAD];
        private byte[] msgHead = new byte[LEN_OF_MSG_HEAD];
        private int typeMsg = -1;
        private int lenMsg = -1;
        private int ret = -1;

        public AOAReadThread() {
            LogUtil.e(TAG, "AOAReadThread Created");
            setName(AOA_READ_THREAD_NAME);
        }

        public void cancel() {
            isRunning = false;
        }

        @Override
        public void run() {
            isRunning = true;
            LogUtil.e(TAG, "Begin to read data by AOA");
            try {
                while (isRunning) {
                    if (!isRunning) {
                        LogUtil.e(TAG, "read data cancled");
                        return;
                    }
                    ret = AOAAccessorySetup.getInstance().bulkTransferIn(msgHead, LEN_OF_MSG_HEAD);
                    if (ret < 0) {
                        LogUtil.e(TAG, "bulkTransferIn fail 1");
                        break;
                    } else if (ret == 0) {
                        continue;
                    }
                    typeMsg = ByteConvert.bytesToInt(new byte[]{msgHead[0], msgHead[1], msgHead[2], msgHead[3]});
                    lenMsg = ByteConvert.bytesToInt(new byte[]{msgHead[4], msgHead[5], msgHead[6], msgHead[7]});
                    LogUtil.e(TAG, "typeMsg = " + typeMsg + ", lenMsg = " + lenMsg);

                    LogUtil.e(TAG, "MSG_CHANNEL HEAD " + ByteConvert.printHexString(msgHead, LEN_OF_MSG_HEAD));

                    if (typeMsg < 1 || typeMsg > 6 || lenMsg < 8 || lenMsg > AOA_MAX_BYTES) {
                        LogUtil.e(TAG, "typeMsg or lenMsg is error");
                        break;
                    }
                    msg = new byte[LEN_OF_MSG_HEAD];
                    if (msg.length < lenMsg) {
                        msg = new byte[lenMsg];
                    }

                    if (AOAAccessorySetup.getInstance().bulkTransferIn(msg, lenMsg) < 0) {
                        LogUtil.e(TAG, "bulkTransferIn fail 2");
                        break;
                    }
                    switch (typeMsg) {
                        case CommonParams.MSG_CHANNEL_ID:
                            LogUtil.e(TAG, "MSG_CHANNEL_ID MSG " + ByteConvert.printHexString(msg, msg.length));
                            ConnectManager.getInstance().writeCmdData(msg, lenMsg);
                            break;
                        case CommonParams.MSG_CHANNEL_ID_VIDEO:
                            LogUtil.e(TAG, "MSG_CHANNEL_ID_VIDEO MSG " + ByteConvert.printHexString(msg, msg.length));
                            break;
                        case CommonParams.MSG_CHANNEL_ID_AUDIO:
                            break;
                        case CommonParams.MSG_CHANNEL_ID_AUDIO_TTS:
                            break;
                        case CommonParams.MSG_CHANNEL_ID_AUDIO_VR:
                            LogUtil.e(TAG, "MSG_CHANNEL_ID_AUDIO_VR MSG");
                            ConnectManager.getInstance().writeAudioVRData(msg, lenMsg);
                            break;
                        case CommonParams.MSG_CHANNEL_ID_TOUCH:
                            CarlifeCmdMessage carlifeTouchMsg = new CarlifeCmdMessage(false);
                            carlifeTouchMsg.fromByteArray(msg, lenMsg);
                            dumpData("RECV CarlifeTouchMessage", carlifeTouchMsg);
                            ConnectManager.getInstance().writeCarlifeTouchMessage(carlifeTouchMsg);
                            break;
                        default:
                            LogUtil.e(TAG, "AOAReadThread typeMsg error");
                            break;
                    }
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "Exception when read data by AOA");
                e.printStackTrace();
            }
        }
    }

    private static void dumpData(String tag, CarlifeCmdMessage carlifeMsg) {
        String msg = "";
        try {
            msg += "index = " + Integer.toString(carlifeMsg.getIndex());
            msg += ", length = " + Integer.toString(carlifeMsg.getLength());
            msg += ", service_type = 0x" + DigitalTrans.algorismToHEXString(carlifeMsg.getServiceType(), 8);
            msg += ", reserved = 0x" + DigitalTrans.algorismToHEXString(carlifeMsg.getReserved(), 8);
            msg += ", name = " + CommonParams.getMsgName(carlifeMsg.getServiceType());
            LogUtil.e(TAG, "[" + tag + "]" + msg);
        } catch (Exception e) {
            LogUtil.e("TAG", "dumpData get Exception");
            e.printStackTrace();
        }
    }

    private void carlifeMsgProtocol(CarlifeCmdMessage carlifeMsg) {
        switch (carlifeMsg.getServiceType()) {
            case CommonParams.MSG_CMD_HU_PROTOCOL_VERSION:
                try {
                    CarlifeProtocolVersionMatchStatusProto.CarlifeProtocolVersionMatchStatus.Builder builderMatch = CarlifeProtocolVersionMatchStatusProto.CarlifeProtocolVersionMatchStatus.newBuilder();
                    builderMatch.setMatchStatus(CommonParams.PROTOCOL_VERSION_MATCH);
                    CarlifeProtocolVersionMatchStatusProto.CarlifeProtocolVersionMatchStatus mProtocolMatchStatus = builderMatch.build();
                    CarlifeProtocolVersionInfoManager.getInstance().setProtocolMatchStatus(mProtocolMatchStatus);
                    CarlifeProtocolVersionInfoManager.getInstance().sendProtocolMatchStatus();
                } catch (Exception e) {
                    LogUtil.e(TAG, e.getMessage());
                }
                break;
            case CommonParams.MSG_CMD_STATISTIC_INFO:
                EncryptSetupManager.getInstance().requestPublicKey();
                CarlifeDeviceInfoManager.getInstance().sendCarlifeForeground();
                CarlifeDeviceInfoManager.getInstance().sendCarlifeScreenOn();
                break;
            case CommonParams.MSG_CMD_HU_INFO:
                try {
                    CarlifeDeviceInfo deviceInfo = CarlifeDeviceInfo.parseFrom(carlifeMsg.getData());
                    LogUtil.e(TAG, "deviceInfo " + deviceInfo);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                CarlifeDeviceInfoManager.getInstance().sendCarlifeDeviceInfo();
                CarlifeDeviceInfoManager.getInstance().requestFeatureConfig();
                CarDataManager.getInstance().requestSubcribe();
                break;
            case CommonParams.MSG_CMD_VIDEO_ENCODER_INIT:
                CarlifeVideoEncoderInfo videoInfo = null;
                try {
                    videoInfo = CarlifeVideoEncoderInfo.parseFrom(carlifeMsg.getData());
                    LogUtil.e(TAG, "videoInfo " + videoInfo.getWidth() + " " + videoInfo.getHeight() + " " + videoInfo.getFrameRate());
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                    LogUtil.e(TAG, e.getMessage());
                } catch (Exception e) {
                    e.printStackTrace();
                    LogUtil.e(TAG, e.getMessage());
                }
                if (videoInfo != null) {
                    CarlifePhoneApplication.screenWidth = videoInfo.getWidth();
                    CarlifePhoneApplication.screenHeight = videoInfo.getHeight();
                    CarlifePhoneApplication.frameRate = videoInfo.getFrameRate();
                    CarlifeUtil.sendVideoCodecMsg(videoInfo.getWidth(), videoInfo.getHeight(), videoInfo.getFrameRate());
                } else {
                    CarlifeUtil.sendVideoCodecMsg(1024, 600, 25);
                }
                break;
            case CommonParams.MSG_CMD_MODULE_CONTROL:
                CarlifeUtil.sendModuleControlToHu(ModuleStatusModel.CARLIFE_PHONE_MODULE_ID, ModuleStatusModel.PHONE_STATUS_IDLE);
                CarlifeUtil.sendModuleControlToHu(ModuleStatusModel.CARLIFE_NAVI_MODULE_ID, ModuleStatusModel.NAVI_STATUS_IDLE);
                CarlifeUtil.sendModuleControlToHu(ModuleStatusModel.CARLIFE_MUSIC_MODULE_ID, ModuleStatusModel.MUSIC_STATUS_IDLE);
                CarlifeUtil.sendModuleControlToHu(ModuleStatusModel.CARLIFE_VR_MODULE_ID, ModuleStatusModel.VR_STATUS_IDLE);
                CarlifeUtil.sendModuleControlToHu(ModuleStatusModel.CARLIFE_MIC_MODULE_ID, ModuleStatusModel.MIC_STATUS_USE_VEHICLE_MIC);
                break;
            case CommonParams.MSG_CMD_VIDEO_ENCODER_START:
                ConnectClient.getInstance().setIsConnected(true);
                ConnectHeartBeat.getInstance().startConnectHeartBeatTimer();
                DecodeUtil.getInstance().startDecode();
                CarDataManager.getInstance().requestCarVehicle(CarDataManager.MODULE_GPS_DATA, 1, 1);
                break;
            case CommonParams.MSG_CMD_VIDEO_ENCODER_PAUSE:
                DecodeUtil.getInstance().pauseDecode();
                break;
            case CommonParams.MSG_CMD_VIDEO_ENCODER_FRAME_RATE_CHANGE:
                CarlifeVideoFrameRateProto.CarlifeVideoFrameRate videoFrameRate;
                try {
                    videoFrameRate = CarlifeVideoFrameRateProto.CarlifeVideoFrameRate.parseFrom(carlifeMsg.getData());
                    LogUtil.d(TAG, "videoFrameRate==" + videoFrameRate);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case CommonParams.MSG_CMD_HU_RSA_PUBLIC_KEY_RESPONSE:
                CarlifeHuRsaPublicKeyResponse keyResponse;
                try {
                    keyResponse = CarlifeHuRsaPublicKeyResponse.parseFrom(carlifeMsg.getData());
                    EncryptSetupManager.getInstance().setRsaPublicKey(keyResponse.getRsaPublicKey());
                    EncryptSetupManager.getInstance().sendAesKey();
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case CommonParams.MSG_CMD_HU_FEATURE_CONFIG_RESPONSE:
                CarlifeFeatureConfigListProto.CarlifeFeatureConfigList featureConfigList;
                try {
                    featureConfigList = CarlifeFeatureConfigListProto.CarlifeFeatureConfigList.parseFrom(carlifeMsg.getData());
                    List<CarlifeFeatureConfigProto.CarlifeFeatureConfig> configList = featureConfigList.getFeatureConfigList();
                    for (CarlifeFeatureConfigProto.CarlifeFeatureConfig config : configList) {
                        LogUtil.d(TAG, "featureConfig==" + config.getKey() + " " + config.getValue());
                    }
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case CommonParams.MSG_CMD_CAR_DATA_SUBSCRIBE_RSP:
                CarlifeVehicleInfoListProto.CarlifeVehicleInfoList vehicleInfoList;
                try {
                    vehicleInfoList = CarlifeVehicleInfoListProto.CarlifeVehicleInfoList.parseFrom(carlifeMsg.getData());
                    for (CarlifeVehicleInfoProto.CarlifeVehicleInfo vehicleInfo : vehicleInfoList.getVehicleInfoList()) {
                        LogUtil.d(TAG, "vehicleInfo " + vehicleInfo);
                    }
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case CommonParams.MSG_CMD_CAR_GPS:
                CarlifeCarGpsProto.CarlifeCarGps carlifeCarGps;
                try {
                    carlifeCarGps = CarlifeCarGpsProto.CarlifeCarGps.parseFrom(carlifeMsg.getData());
                    LogUtil.d(TAG, "carlifeCarGps " + carlifeCarGps);
                    byte[] carlifeGpsByte = new byte[carlifeMsg.length + CommonParams.MIU_MSG_CMD_HEAD_SIZE];
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MIU_MSG_CMD_TYPE_FIX), 0, carlifeGpsByte, 0, 4);
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MIU_MSG_CMD_GPS_DATA), 3, carlifeGpsByte, 4, 1);
                    System.arraycopy(carlifeMsg.getData(), 0, carlifeGpsByte, CommonParams.MIU_MSG_CMD_HEAD_SIZE, carlifeMsg.length);
                    carlifeGpsByte[0] = (byte) (carlifeGpsByte.length - 1);
                    writeMiuCmdMessage(carlifeGpsByte, carlifeGpsByte.length);
                } catch (InvalidProtocolBufferException e) {
                    e.printStackTrace();
                }
                break;
            case CommonParams.MSG_CMD_HU_AES_REC_RESPONSE:
                EncryptSetupManager.getInstance().setEncryptSwitch(true);
                break;
            case CommonParams.MSG_CMD_HU_AUTHEN_REQUEST:
                CarlifeDeviceInfoManager.getInstance().responseAuthenToHu();
                break;
            case CommonParams.MSG_CMD_HU_AUTHEN_RESULT:
                CarlifeDeviceInfoManager.getInstance().sendAuthenResult();
                break;
        }
    }

    private void carlifeMiudriveMsgProtocol(CarlifeMiuCmdMessage carlifeMiuCmdMessage) {
        if (carlifeMiuCmdMessage.getReserved() == CommonParams.MIU_MSG_CMD_TYPE_FIX) {
            switch (carlifeMiuCmdMessage.getServiceType()) {
                case CommonParams.MIU_MSG_CMD_VOICE_UP:
                    if (mSocketReadMiudriveVRThread == null) {
                        mSocketReadMiudriveVRThread = new SocketReadThread(CommonParams.SOCKET_MIUDRIVE_VR, CommonParams.SERVER_SOCKET_MIUDRIVE_VR_NAME);
                        mSocketReadMiudriveVRThread.start();
                    }
                    CarlifeDeviceInfoManager.getInstance().sendMicRecordStart();
                    break;
                case CommonParams.MIU_MSG_CMD_VOICE_DOWN:
                    if (mSocketReadMiudriveVRThread != null) {
                        mSocketReadMiudriveVRThread.cancel();
                        mSocketReadMiudriveVRThread = null;
                    }
                    CarlifeDeviceInfoManager.getInstance().sendMicRecordEnd();
                    break;
                case CommonParams.MIU_MSG_CMD_MAP_IN:
                    CarDataManager.getInstance().requestCarVehicle(CarDataManager.MODULE_GPS_DATA, 2, 1);
                    break;
                case CommonParams.MIU_MSG_CMD_MAP_OUT:
                    break;
            }
        }
    }

    private class SocketReadThread extends Thread {
        private ServerSocket mServerSocket = null;
        private boolean isRunning = false;
        private int mSocketPort = -1;
        private String mSocketName = null;
        private String mThreadName = null;

        private Socket mSocket = null;
        private BufferedInputStream mInputStream = null;
        private BufferedOutputStream mOutputStream = null;

        private int lenMsgHead = -1;
        private int lenMsgData = -1;

        private byte[] msg = new byte[CommonParams.MSG_VIDEO_HEAD_SIZE_BYTE];
        private byte[] head = new byte[LEN_OF_MSG_HEAD];

        public SocketReadThread(int port, String name) {
            try {
                mThreadName = name + SOCKET_READ_THREAD_NAME;
                setName(mThreadName);
                mSocketPort = port;
                mSocketName = name;
                LogUtil.e(TAG, "Create " + mThreadName + " " + mSocketPort);

                mServerSocket = new ServerSocket(mSocketPort);
                isRunning = true;

                if (mSocketName.equals(CommonParams.SERVER_SOCKET_NAME)) {
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_CHANNEL_ID), 0, head, 0, 4);
                } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_VIDEO_NAME)) {
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_CHANNEL_ID_VIDEO), 0, head, 0, 4);
                } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_AUDIO_NAME)) {
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_CHANNEL_ID_AUDIO), 0, head, 0, 4);
                } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_AUDIO_TTS_NAME)) {
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_CHANNEL_ID_AUDIO_TTS), 0, head, 0, 4);
                } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_AUDIO_VR_NAME)) {
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_CHANNEL_ID_AUDIO_VR), 0, head, 0, 4);
                } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_TOUCH_NAME)) {
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MSG_CHANNEL_ID_TOUCH), 0, head, 0, 4);
                } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_MIUDRIVE_VR_NAME)) {
                    byte[] vrInitByte = new byte[CommonParams.MIU_MSG_CMD_HEAD_SIZE + 2];
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MIU_MSG_CMD_TYPE_FIX), 0, vrInitByte, 0, 4);
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.MIU_MSG_CMD_EVENT_MIC_SUCCESS), 3, vrInitByte, 4, 1);
                    System.arraycopy(ByteConvert.intToBytes(CommonParams.SOCKET_MIUDRIVE_VR), 2, vrInitByte, CommonParams.MIU_MSG_CMD_HEAD_SIZE, 2);
                    vrInitByte[0] = (byte) (vrInitByte.length - 1);
                    LogUtil.d(TAG, "vrInitByte " + ByteConvert.printHexString(vrInitByte, vrInitByte.length));
                    writeMiuCmdMessage(vrInitByte, vrInitByte.length);
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "Create " + mThreadName + " fail " + e.getMessage());
                e.printStackTrace();
            }
        }

        public void cancel() {
            try {
                if (null != mServerSocket) {
                    mServerSocket.close();
                }
                if (null != mSocket) {
                    mSocket.close();
                    mSocket = null;
                }
                if (null != mInputStream) {
                    mInputStream.close();
                    mInputStream = null;
                }
                if (null != mOutputStream) {
                    mOutputStream.close();
                    mOutputStream = null;
                }

                isRunning = false;
            } catch (Exception e) {
                LogUtil.e(TAG, "Close " + mThreadName + " fail");
                e.printStackTrace();
            }
        }

        public int readData(byte[] buffer, int offset, int len) {
            int r = -1;
            try {
                if (null != mInputStream) {
                    int cnt;
                    cnt = len;
                    int dataLen = 0;
                    while (cnt > 0) {
                        r = mInputStream.read(buffer, offset + dataLen, cnt);
                        if (r > 0) {
                            cnt -= r;
                            dataLen += r;
                        } else {
                            LogUtil.e(TAG, mSocketName + " Receive Data Error: ret = " + r);
                            throw new IOException();
                        }
                    }
                    if (dataLen != len) {
                        LogUtil.e(TAG, mSocketName + " Receive Data Error: dataLen = " + dataLen);
                        throw new IOException();
                    }
                    return dataLen;
                } else {
                    LogUtil.e(TAG, mSocketName + " Receive Data Fail, mInputStream is null");
                    throw new IOException();
                }
            } catch (Exception e) {
                LogUtil.e(TAG, mSocketName + " IOException, Receive Data Fail");
                e.printStackTrace();
                return r;
            }
        }

        public int writeData(CarlifeCmdMessage msg) {
            try {
                if (null != mOutputStream) {
                    dumpData("SEND CarlifeMsg CMD", msg);
                    if (EncryptSetupManager.getInstance().isEncryptEnable() && msg.getLength() > 0) {
                        byte[] encryptData = mWriteAESManager.encrypt(msg.getData(), 0, msg.getData().length);
                        if (encryptData == null) {
                            LogUtil.e(TAG, "encrypt failed!");
                            return -1;
                        }
                        msg.setLength(encryptData.length);
                        mOutputStream.write(msg.toByteArray());
                        mOutputStream.flush();
                        if (msg.getLength() > 0) {
                            mOutputStream.write(encryptData);
                            mOutputStream.flush();
                        }
                    } else {
                        mOutputStream.write(msg.toByteArray());
                        mOutputStream.flush();
                        if (msg.getLength() > 0) {
                            mOutputStream.write(msg.getData());
                            mOutputStream.flush();
                        }
                    }
                    return CommonParams.MSG_CMD_HEAD_SIZE_BYTE + msg.getLength();
                } else {
                    LogUtil.e(TAG, mSocketName + " Send Data Fail, mOutputStream is null");
                    throw new IOException();
                }
            } catch (Exception e) {
                LogUtil.e(TAG, mSocketName + " IOException, Send Data Fail");
                ConnectClient.getInstance().setIsConnected(false);
                e.printStackTrace();
                return -1;
            }
        }

        public int writeData(byte[] buffer, int offset, int len) {
            try {
                if (null != mOutputStream) {
                    mOutputStream.write(buffer, offset, len);
                    mOutputStream.flush();
                    return len;
                } else {
                    LogUtil.e(TAG, mSocketName + " Send Data Fail, mOutputStream is null");
                    throw new IOException();
                }
            } catch (Exception e) {
                LogUtil.e(TAG, mSocketName + " IOException, Send Data Fail");
                e.printStackTrace();
                return -1;
            }
        }

        @Override
        public void run() {
            LogUtil.e(TAG, "Begin to listen in " + mThreadName + " " + mSocketPort);
            try {
                if (null != mServerSocket && isRunning) {
                    mSocket = mServerSocket.accept();
                    if (null == mSocket) {
                        LogUtil.d(TAG, "One client connected fail: " + mThreadName);
                    }
                    LogUtil.e(TAG, "One client connected in " + mThreadName);
                    mSocket.setTcpNoDelay(true);
                    mSocket.setSendBufferSize(SEND_BUFFER_SIZE);
                    mSocket.setReceiveBufferSize(RECEIVE_BUFFER_SIZE);

                    mInputStream = new BufferedInputStream(mSocket.getInputStream());
                    mOutputStream = new BufferedOutputStream(mSocket.getOutputStream());
                }
            } catch (Exception e) {
                LogUtil.e(TAG, "Get Exception in " + mThreadName);
                e.printStackTrace();
                return;
            }
            try {
                while (mSocket != null && isRunning) {
                    if (!mSocket.isConnected()) {
                        LogUtil.e(TAG, "socket is disconnected when read data");
                        break;
                    }
                    if (mSocketName.equals(CommonParams.SERVER_SOCKET_NAME)
                            || mSocketName.equals(CommonParams.SERVER_SOCKET_TOUCH_NAME)) {
                        if (readData(msg, 0, CommonParams.MSG_CMD_HEAD_SIZE_BYTE) < 0) {
                            continue;
                        }
                        lenMsgHead = CommonParams.MSG_CMD_HEAD_SIZE_BYTE;
                        lenMsgData = (int) ByteConvert.bytesToShort(new byte[]{msg[0], msg[1]});

                        byte[] carlifeMsgByte = new byte[lenMsgHead + lenMsgData];
                        System.arraycopy(msg, 0, carlifeMsgByte, 0, lenMsgHead);
                        if (msg.length < lenMsgData) {
                            msg = new byte[lenMsgData];
                        }
                        if (readData(msg, 0, lenMsgData) < 0) {
                            continue;
                        }
                        System.arraycopy(msg, 0, carlifeMsgByte, lenMsgHead, lenMsgData);
                        CarlifeCmdMessage carlifeMsg = new CarlifeCmdMessage(false);
                        carlifeMsg.fromByteArray(carlifeMsgByte, carlifeMsgByte.length);
                        dumpData("RECV CarlifeMsg CMD ", carlifeMsg);
                        carlifeMsgProtocol(carlifeMsg);
                    } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_AUDIO_VR_NAME)) {
                        if (readData(msg, 0, CommonParams.MSG_VIDEO_HEAD_SIZE_BYTE) < 0) {
                            continue;
                        }
                        lenMsgHead = CommonParams.MSG_VIDEO_HEAD_SIZE_BYTE;
                        lenMsgData = ByteConvert.bytesToInt(new byte[]{msg[0], msg[1], msg[2], msg[3]});

                        byte[] carlifeVRMsgByte = new byte[lenMsgHead + lenMsgData];
                        System.arraycopy(msg, 0, carlifeVRMsgByte, 0, lenMsgHead);
                        if (msg.length < lenMsgData) {
                            msg = new byte[lenMsgData];
                        }
                        if (readData(msg, 0, lenMsgData) < 0) {
                            continue;
                        }
                        System.arraycopy(msg, 0, carlifeVRMsgByte, lenMsgHead, lenMsgData);
                        CarlifeVRMessage carlifeVRMessage = new CarlifeVRMessage(false);
                        carlifeVRMessage.fromByteArray(carlifeVRMsgByte, carlifeVRMsgByte.length);
                        dumpData("RECV CarlifeVRMsg CMD", carlifeVRMessage);
                        writeMiuVRMessage(carlifeVRMessage.getData(), carlifeVRMessage.length);
                    } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_MIUDRIVE_NAME)) {
                        if (readData(msg, 0, CommonParams.MIU_MSG_CMD_HEAD_SIZE_BYTE) < 0) {
                            continue;
                        }
                        lenMsgHead = CommonParams.MIU_MSG_CMD_HEAD_SIZE_BYTE;
                        lenMsgData = msg[0];
                        byte[] carlifeMiuMsgByte = new byte[lenMsgHead + lenMsgData];
                        System.arraycopy(msg, 0, carlifeMiuMsgByte, 0, lenMsgHead);
                        if (msg.length < lenMsgData) {
                            msg = new byte[lenMsgData];
                        }
                        if (readData(msg, 0, lenMsgData) < 0) {
                            continue;
                        }
                        System.arraycopy(msg, 0, carlifeMiuMsgByte, lenMsgHead, lenMsgData);
                        CarlifeMiuCmdMessage carlifeMiuCmdMessage = new CarlifeMiuCmdMessage(false);
                        carlifeMiuCmdMessage.fromByteArray(carlifeMiuMsgByte, carlifeMiuMsgByte.length);
                        dumpData("RECV CarlifeMiudriveMsg CMD", carlifeMiuCmdMessage);
                        carlifeMiudriveMsgProtocol(carlifeMiuCmdMessage);
                    } else if (mSocketName.equals(CommonParams.SERVER_SOCKET_MIUDRIVE_VR_NAME)) {

                    }
                }
            } catch (Exception ex) {
                LogUtil.e(TAG, mSocketName + " get Exception in ReadThread " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }
}

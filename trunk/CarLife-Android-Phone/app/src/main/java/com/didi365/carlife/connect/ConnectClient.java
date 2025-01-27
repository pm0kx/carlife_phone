
package com.didi365.carlife.connect;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import com.didi365.carlife.message.MsgHandlerCenter;
import com.didi365.carlife.util.CarlifeUtil;
import com.didi365.carlife.CommonParams;
import com.didi365.carlife.util.DigitalTrans;
import com.didi365.carlife.util.LogUtil;

/**
 * Created by zheng on 2019/3/29
 */
public class ConnectClient {

    private static final String TAG = "ConnectClient";
    private static final String CONNECT_CLIENT_HANDLER_THREAD_NAME = "ConnectClientHandlerThread";

    private Context mContext = null;
    private ConnectServiceReceiver mConnectServiceReceiver = null;
    private UsbConnectStateReceiver mUsbConnectStateReceiver = null;

    private ConnectClientHandler mConnectClientHandler = null;

    private Messenger mConnectService = null;
    private Messenger mConnectClient = null;

    private boolean isUsbConnected = false;
    private boolean isConnecting = false;
    private boolean isConnected = false;
    private boolean isBound = false;

    private static ConnectClient mInstance = null;

    private class ConnectClientHandler extends Handler {
        public ConnectClientHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            if (null == msg) {
                return;
            }
            switch (msg.what) {
                case CommonParams.MSG_USB_STATE_MSG:
                    if (msg.arg1 == CommonParams.MSG_USB_STATE_MSG_ON) {
                        isUsbConnected = true;
                        LogUtil.e(TAG, "USB Cable is connected!");
                    } else if (msg.arg1 == CommonParams.MSG_USB_STATE_MSG_OFF) {
                        isUsbConnected = false;
                        LogUtil.e(TAG, "USB Cable is disconnected!");
                        sendConnectStopBroadcast();
                    }
                    break;
                case CommonParams.MSG_CONNECT_SERVICE_MSG:
                    if (msg.arg1 == CommonParams.MSG_CONNECT_SERVICE_MSG_START) {
                        bindConnectService();
                    } else if (msg.arg1 == CommonParams.MSG_CONNECT_SERVICE_MSG_STOP) {
                        unbindConnectService();
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // TODO Auto-generated method stub
            LogUtil.d(TAG, "onServiceConnected");
            isBound = true;

            mConnectService = new Messenger(service);

            Message msg = Message.obtain(null, CommonParams.MSG_REC_REGISTER_CLIENT);
            sendMsgToService(msg);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // TODO Auto-generated method stub
            LogUtil.d(TAG, "onServiceDisconnected");
            isBound = false;
            mConnectService = null;
        }
    };

    public static ConnectClient getInstance() {
        if (null == mInstance) {
            synchronized (ConnectClient.class) {
                if (null == mInstance) {
                    mInstance = new ConnectClient();
                }
            }
        }
        return mInstance;
    }

    private ConnectClient() {
    }

    public void init(Context context) {
        LogUtil.d(TAG, "init");

        mContext = context;
        HandlerThread handlerThread = new HandlerThread(CONNECT_CLIENT_HANDLER_THREAD_NAME);
        handlerThread.start();
        mConnectClientHandler = new ConnectClientHandler(CarlifeUtil.getLooper(handlerThread));
        mConnectClient = new Messenger(mConnectClientHandler);

        mConnectServiceReceiver = new ConnectServiceReceiver(context, mConnectClientHandler);
        mUsbConnectStateReceiver = new UsbConnectStateReceiver(context, mConnectClientHandler);
        try {
            registerConnectServiceReceiver();
            registerUsbConnectStateReceiver();
        } catch (Exception e) {
            LogUtil.e(TAG, "UsbConnectStateManager init fail");
            e.printStackTrace();
        }
    }

    public void uninit() {
        LogUtil.d(TAG, "uninit");
        try {
            unregisterConnectServiceReceiver();
            unregisterUsbConnectStateReceiver();
            unbindConnectService();
        } catch (Exception e) {
            LogUtil.e(TAG, "UsbConnectStateManager uninit fail");
            e.printStackTrace();
        }
    }

    public void sendConnectStartBroadcast() {
        mContext.sendBroadcast(new Intent(ConnectServiceReceiver.CARLIFE_CONNECT_SERVICE_START));
    }

    public void sendConnectStopBroadcast() {
        mContext.sendBroadcast(new Intent(ConnectServiceReceiver.CARLIFE_CONNECT_SERVICE_STOP));
    }

    private void startConnectService() {
        LogUtil.d(TAG, "start ConnectService");
        Intent startIntent = new Intent(mContext, ConnectService.class);
        mContext.startService(startIntent);
    }

    private void stopConnectService() {
        LogUtil.d(TAG, "stop ConnectService");
        Intent stopIntent = new Intent(mContext, ConnectService.class);
        mContext.stopService(stopIntent);
    }

    private void bindConnectService() {
        LogUtil.d(TAG, "bind ConnectService");
        Intent bindIntent = new Intent(mContext, ConnectService.class);
        mContext.bindService(bindIntent, mConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindConnectService() {
        LogUtil.d(TAG, "unbind ConnectService");
        mContext.unbindService(mConnection);

        Message msg = Message.obtain(null, CommonParams.MSG_REC_UNREGISTER_CLIENT);
        sendMsgToService(msg);
    }

    private void registerConnectServiceReceiver() {
        if (null != mConnectServiceReceiver) {
            mConnectServiceReceiver.registerReceiver();
            LogUtil.d(TAG, "register ConnectServiceReceiver");
        }
    }

    private void registerUsbConnectStateReceiver() {
        if (null != mUsbConnectStateReceiver) {
            mUsbConnectStateReceiver.registerReceiver();
            LogUtil.d(TAG, "register UsbConnectStateReceiver");
        }
    }

    private void unregisterConnectServiceReceiver() {
        if (null != mConnectServiceReceiver) {
            mConnectServiceReceiver.unregisterReceiver();
            LogUtil.d(TAG, "unregister ConnectServiceReceiver");
        }
    }

    private void unregisterUsbConnectStateReceiver() {
        if (null != mUsbConnectStateReceiver) {
            mUsbConnectStateReceiver.unregisterReceiver();
            LogUtil.d(TAG, "unregister UsbConnectStateReceiver");
        }
    }

    public boolean sendMsgToService(Message msg) {
        LogUtil.e(TAG, "Send Msg to Service, what = 0x" + DigitalTrans.algorismToHEXString(msg.what, 8));
        if (mConnectService == null) {
            LogUtil.e(TAG, "mConnectService is null");
            return false;
        }

        if (mConnectClient == null) {
            LogUtil.e(TAG, "mConnectClient is null");
            return false;
        }

        try {
            msg.replyTo = mConnectClient;
            mConnectService.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public synchronized void setIsConnecting(boolean is) {
        if (!isConnecting && is) {
            MsgHandlerCenter.dispatchMessage(CommonParams.MSG_CONNECT_STATUS_CONNECTING);
        }
        isConnecting = is;
    }

    public synchronized void setIsConnected(boolean is) {
        if (isConnected && !is) {
            MsgHandlerCenter.dispatchMessage(CommonParams.MSG_CONNECT_STATUS_DISCONNECTED);
        } else if (!isConnected && is) {
            MsgHandlerCenter.dispatchMessage(CommonParams.MSG_CONNECT_CHANGE_PROGRESS_NUMBER, 100, 0, null);
            MsgHandlerCenter.dispatchMessage(CommonParams.MSG_CONNECT_STATUS_CONNECTED);
        }
        isConnected = is;
    }

    public boolean isCarlifeConnecting() {
        return isConnecting;
    }

    public boolean isCarlifeConnected() {
        return isConnected;
    }
}

package com.didi365.carlife.android.phone.connect;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;

import com.didi365.carlife.android.phone.receiver.OpenAccessoryReceiver;
import com.didi365.carlife.android.phone.receiver.UsbDetachedReceiver;
import com.didi365.carlife.android.phone.util.AudioPlayerUtil;
import com.didi365.carlife.android.phone.util.DecodeUtil;
import com.didi365.carlife.android.phone.util.LogUtil;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by zheng on 2019/3/29
 */
public class AOAAccessorySetup implements OpenAccessoryReceiver.OpenAccessoryListener, UsbDetachedReceiver.UsbDetachedListener {

    private final String TAG = AOAAccessorySetup.class.getSimpleName();

    private static AOAAccessorySetup mInstance = null;
    private Context mContext;

    private static final String USB_ACTION = "com.didi365.carlife.phone";

    private UsbManager mUsbManager;
    private OpenAccessoryReceiver mOpenAccessoryReceiver;
    private UsbDetachedReceiver mUsbDetachedReceiver;

    private ParcelFileDescriptor mParcelFileDescriptor;
    private FileInputStream mFileInputStream;
    private FileOutputStream mFileOutputStream;
    private PendingIntent pendingIntent;

    private static final int AOA_MAX_BUFFER_BYTES = 16 * 1024;
    private byte[] mDataBuffer = new byte[AOA_MAX_BUFFER_BYTES];

    private AOAAccessorySetup() {
    }

    public static AOAAccessorySetup getInstance() {
        if (mInstance == null) {
            synchronized (AOAAccessorySetup.class) {
                if (mInstance == null) {
                    mInstance = new AOAAccessorySetup();
                }
            }
        }
        return mInstance;
    }

    public void init(Context context) {
        mContext = context;
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        mUsbDetachedReceiver = new UsbDetachedReceiver(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        mContext.registerReceiver(mUsbDetachedReceiver, filter);

        mOpenAccessoryReceiver = new OpenAccessoryReceiver(this);
        pendingIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(USB_ACTION), 0);
        IntentFilter intentFilter = new IntentFilter(USB_ACTION);
        mContext.registerReceiver(mOpenAccessoryReceiver, intentFilter);
    }

    public void unInit() {
        try {
            if (mParcelFileDescriptor != null) {
                mParcelFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mParcelFileDescriptor = null;
        }
        try {
            if (mFileInputStream != null) {
                mFileInputStream.close();
            }
        } catch (IOException e) {
        } finally {
            mFileInputStream = null;
        }
        try {
            if (mFileOutputStream != null) {
                mFileOutputStream.close();
            }
        } catch (IOException e) {
        } finally {
            mFileOutputStream = null;
        }
    }

    public boolean scanUsbDevices() {
        if (mUsbManager != null) {
            UsbAccessory[] accessories = mUsbManager.getAccessoryList();
            UsbAccessory usbAccessory = (accessories == null ? null : accessories[0]);
            if (usbAccessory != null) {
                if (mUsbManager.hasPermission(usbAccessory)) {
                    return openAccessory(usbAccessory);
                } else {
                    mUsbManager.requestPermission(usbAccessory, pendingIntent);
                }
            }
        }
        return false;
    }

    private boolean openAccessory(UsbAccessory usbAccessory) {
        mParcelFileDescriptor = mUsbManager.openAccessory(usbAccessory);
        if (mParcelFileDescriptor != null) {
            FileDescriptor fileDescriptor = mParcelFileDescriptor.getFileDescriptor();
            mFileInputStream = new FileInputStream(fileDescriptor);
            mFileOutputStream = new FileOutputStream(fileDescriptor);

            AOAConnectManager.getInstance().startSocketReadThread();
            ConnectManager.getInstance().startAllConnectSocket();
            AOAConnectManager.getInstance().startAOAReadThread();
            return true;
        }
        return false;
    }

    public int bulkTransferIn(byte[] data, int len) {
        int ret = -1;
        int cnt = len;
        int readLen = -1;
        int dataLen = 0;
        try {
            if (mFileInputStream == null) {
                LogUtil.e(TAG, "mUsbDeviceConnection or mUsbEndpointIn is null");
                throw new IOException();
            }

            if (len <= AOA_MAX_BUFFER_BYTES) {
                ret = mFileInputStream.read(data, 0, len);
                if (ret < 0) {
                    LogUtil.e(TAG, "bulkTransferIn error 1: ret = " + ret);
                    throw new IOException();
                } else if (ret == 0) {
                    return 0;
                }
                dataLen = ret;
            } else {
                while (cnt > 0) {
                    readLen = cnt > AOA_MAX_BUFFER_BYTES ? AOA_MAX_BUFFER_BYTES : cnt;
                    ret = mFileInputStream.read(mDataBuffer, 0, readLen);
                    if (ret < 0) {
                        LogUtil.e(TAG, "bulkTransferIn error 2: ret = " + ret);
                        throw new IOException();
                    } else if (ret == 0) {
                        continue;
                    }
                    System.arraycopy(mDataBuffer, 0, data, dataLen, ret);
                    cnt -= ret;
                    dataLen += ret;
                }
            }

            if (dataLen != len) {
                LogUtil.e(TAG, "bulkTransferIn error 3: dataLen = " + dataLen + ", len = " + len);
                ret = -1;
                throw new IOException();
            }
            return dataLen;
        } catch (Exception e) {
            LogUtil.e(TAG, "bulkTransferIn catch exception");
            e.printStackTrace();
            return -1;
        }
    }

    public synchronized int bulkTransferOut(byte[] head, int lenHead, byte[] msg, int lenMsg) {
        LogUtil.e(TAG, "bulkTransferOut lenhead " + lenHead + " lenMsg " + lenMsg);
        if (bulkTransferOut(head, lenHead) < 0) {
            LogUtil.e(TAG, "bulkTransferOut fail 1");
            return -1;
        }
        if (bulkTransferOut(msg, lenMsg) < 0) {
            LogUtil.e(TAG, "bulkTransferOut fail 2");
            return -1;
        }
        return lenHead + lenMsg;
    }

    private int bulkTransferOut(byte[] data, int len) {
        int ret = -1;
        int cnt = len;
        int readLen = -1;
        int dataLen = 0;
        try {
            if (mFileOutputStream == null) {
                LogUtil.e(TAG, "mUsbDeviceConnection or mUsbEndpointIn is null");
                throw new IOException();
            }
            if (len <= AOA_MAX_BUFFER_BYTES) {
                mFileOutputStream.write(data, 0, len);
                mFileOutputStream.flush();
                dataLen = len;
            } else {
                while (cnt > 0) {
                    readLen = cnt > AOA_MAX_BUFFER_BYTES ? AOA_MAX_BUFFER_BYTES : cnt;
                    System.arraycopy(data, dataLen, mDataBuffer, 0, readLen);
                    mFileOutputStream.write(mDataBuffer, 0, readLen);
                    mFileOutputStream.flush();
                    ret = readLen;
                    cnt -= ret;
                    dataLen += ret;
                }
            }

            if (dataLen != len) {
                LogUtil.e(TAG, "bulkTransferOut error 3: dataLen = " + dataLen + ", len = " + len);
                ret = -1;
                throw new IOException();
            }
            return dataLen;
        } catch (Exception e) {
            LogUtil.e(TAG, "bulkTransferOut catch exception " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public void openAccessoryModel(UsbAccessory usbAccessory) {
        openAccessory(usbAccessory);
    }

    @Override
    public void openAccessoryError() {

    }

    @Override
    public void usbAttached() {
        LogUtil.e(TAG, "usbAttached");
    }

    @Override
    public void usbDetached() {
        LogUtil.e(TAG, "usbDetached");
        ConnectManager.getInstance().stopConnectThread();
        ConnectManager.getInstance().stopAllConnectSocket();
        DecodeUtil.getInstance().stopDecode();
        AudioPlayerUtil.getInstance().stopDecode();
        ConnectHeartBeat.getInstance().stopConnectHeartBeatTimer();

        ConnectManager.getInstance().startConnectThread("usbDetached");
    }
}

package cegepst.android_ble_uart;

import android.app.Application;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.ArrayList;

public abstract class UartController extends Application implements BleManager.BleManagerListener {

    public static final String UUID_SERVICE = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
    public static final String UUID_RX = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";

    private BleManager mBleManager;
    private BluetoothGattService mUartService;
    private BleUpdatableActivity activity;
    private volatile String buffer = "";

    public void initializeUart() {
        //mDataBuffer = new ArrayList<>();
        mBleManager = BleManager.getInstance(getApplicationContext());
        mBleManager.setBleListener(this);
        onServicesDiscovered();
    }

    public void registerUi(BleUpdatableActivity activity) {
        this.activity = activity;
    }

    public void unregisterUi() {
        this.activity = null;
    }

    @Override
    public void onConnected() {
    }

    @Override
    public void onConnecting() {
    }

    @Override
    public void onDisconnected() {
    }

    @Override
    public void onServicesDiscovered() {
        mUartService = mBleManager.getGattService(UUID_SERVICE);
        mBleManager.enableNotification(mUartService, UUID_RX, true);
        Log.v("TEST", "service discovered");
    }

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                final UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_RX, bytes);
                final String data = BleUtils.bytesToText(dataChunk.getData(), true);
                buffer += data;
                if (buffer.contains("#")) {
                    if (activity != null) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activity.updateUi(buffer);
                                buffer = "";
                            }
                        });
                    }
                }
            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
        Log.v("TEST", "available data (descriptor)");
    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
}

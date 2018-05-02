package cegepst.android_ble_uart;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.UUID;

import static android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;


public class ScanActivity extends AppCompatActivity implements BleManager.BleManagerListener, BleUtils.ResetBluetoothAdapterListener {

    private final static long kMinDelayToUpdateUI = 200;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int kActivityRequestCode_EnableBluetooth = 1;
    private static final int kActivityRequestCode_ConnectedActivity = 3;

    private ListView mScannedDevicesListView;
    private ScanAdapter mScannedDevicesAdapter;
    private long mLastUpdateMillis;
    private TextView mNoDevicesTextView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private AlertDialog mConnectingDialog;
    private BleManager mBleManager;
    private boolean mIsScanPaused = true;
    private BleDevicesScanner mScanner;
    private ArrayList<BluetoothDeviceData> mScannedDevices = new ArrayList<>();
    private BluetoothDeviceData mSelectedDeviceData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        mBleManager = BleManager.getInstance(this);
        mScannedDevicesListView = (ListView) findViewById(R.id.scannedDevicesListView);
        mScannedDevicesAdapter = createScanAdapter(mScannedDevices);
        mScannedDevicesListView.setAdapter(mScannedDevicesAdapter);
        mNoDevicesTextView = (TextView) findViewById(R.id.nodevicesTextView);
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                mScannedDevices.clear();
                startScan(null);

                mSwipeRefreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 500);
            }
        });
        mScannedDevicesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                onClickDeviceConnect(i);
            }
        });

        // Setup when activity is created for the first time
        if (savedInstanceState == null) {
            // Read preferences
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            boolean autoResetBluetoothOnStart = sharedPreferences.getBoolean("pref_resetble", false);
            // Check if bluetooth adapter is available
            final boolean wasBluetoothEnabled = manageBluetoothAvailability();
            final boolean areLocationServicesReadyForScanning = manageLocationServiceAvailabilityForScanning();
            // Reset bluetooth
            if (autoResetBluetoothOnStart && wasBluetoothEnabled && areLocationServicesReadyForScanning) {
                BleUtils.resetBluetoothAdapter(this, this);
            }
        }

        // Request Bluetooth scanning permissions
        requestLocationPermissionIfNeeded();
    }

    protected ScanAdapter createScanAdapter(ArrayList<BluetoothDeviceData> scannedDevices) {
        return new ScanAdapter(this, scannedDevices);
    }

    @Override
    public void onResume() {
        super.onResume();
        mBleManager.setBleListener(this);
        autostartScan();
        updateUI();
    }

    private void autostartScan() {
        if (BleUtils.getBleStatus(this) == BleUtils.STATUS_BLE_ENABLED) {
            mBleManager.disconnect();
            if (mScannedDevices != null) {
                mScannedDevices.clear();
            }
            startScan(null);
        }
    }

    @Override
    public void onPause() {
        if (mScanner != null && mScanner.isScanning()) {
            mIsScanPaused = true;
            stopScanning();
        }
        super.onPause();
    }

    public void onStop() {
        if (mConnectingDialog != null) {
            mConnectingDialog.cancel();
            mConnectingDialog = null;
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        BleUtils.cancelBluetoothAdapterReset(this);
        if (mConnectingDialog != null) {
            mConnectingDialog.cancel();
        }
        super.onDestroy();
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can scan for Bluetooth peripherals");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                    }
                });
                builder.show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Autostart scan
                    autostartScan();
                    // Update UI
                    updateUI();
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Bluetooth Scanning not available");
                    builder.setMessage("Since location access has not been granted, the app will not be able to scan for Bluetooth peripherals");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                break;
            }
            default:
                break;
        }
    }

    private void resumeScanning() {
        if (mIsScanPaused) {
            startScan(null);
            mIsScanPaused = mScanner == null;
        }
    }

    // TODO: À tester (déconnecte / connecte bluetooth)
    private boolean manageBluetoothAvailability() {
        boolean isEnabled = true;

        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.dialog_error_no_ble;
                isEnabled = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.dialog_error_no_bluetooth;
                isEnabled = false;      // it was already off
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                isEnabled = false;      // it was already off
                // if no enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, kActivityRequestCode_EnableBluetooth);
                // execution will continue at onActivityResult()
                break;
            }
        }
        if (errorMessageId != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }

        return isEnabled;
    }

    // TODO: À tester (déconnecte / connecte localisation)
    private boolean manageLocationServiceAvailabilityForScanning() {

        boolean areLocationServiceReady = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            areLocationServiceReady = locationMode != Settings.Secure.LOCATION_MODE_OFF;

            if (!areLocationServiceReady) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.dialog_error_nolocationservices_requiredforscan_marshmallow)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        }

        return areLocationServiceReady;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == kActivityRequestCode_ConnectedActivity) {
            if (resultCode < 0) {
                Toast.makeText(this, R.string.scan_unexpecteddisconnect, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == kActivityRequestCode_EnableBluetooth) {
            if (resultCode == Activity.RESULT_OK) {
                resumeScanning();
            }
        }
    }

    public void onClickDeviceConnect(int scannedDeviceIndex) {
        stopScanning();
        if (mScannedDevices == null) {
            mScannedDevices = new ArrayList<>();
        }
        if (scannedDeviceIndex < mScannedDevices.size()) {
            mSelectedDeviceData = mScannedDevices.get(scannedDeviceIndex);
            BluetoothDevice device = mSelectedDeviceData.device;
            mBleManager.setBleListener(this);
            if (mSelectedDeviceData.type == BluetoothDeviceData.kType_Uart) {
                mBleManager.connect(this, device.getAddress());
            }
        }
    }

    // TODO: Simplifier
    private void startScan(final UUID[] servicesToScan) {
        stopScanning();
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());
        if (BleUtils.getBleStatus(this) == BleUtils.STATUS_BLE_ENABLED) {
            mScanner = new BleDevicesScanner(bluetoothAdapter, servicesToScan, new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    if (mScannedDevices == null) {
                        mScannedDevices = new ArrayList<>();
                    }
                    sortDevice(device, rssi, scanRecord);
                    updateDeviceData();
                }
            });
            mScanner.start();
        }
        updateUI();
    }

    private void updateDeviceData() {
        long currentMillis = SystemClock.uptimeMillis();
        if (currentMillis - mLastUpdateMillis > kMinDelayToUpdateUI) {
            mLastUpdateMillis = currentMillis;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateUI();
                }
            });
        }
    }

    private void sortDevice(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
        for (BluetoothDeviceData deviceData : mScannedDevices) {
            if (deviceData.device.getAddress().equals(device.getAddress())) {
                return;
            }
        }

        BluetoothDeviceData deviceData = new BluetoothDeviceData(device, scanRecord, rssi);
        if (device.getName() != null && device.getName().contains(getString(R.string.scan_device_prefix))) {
            mScannedDevices.add(deviceData);
        }
    }

    private void stopScanning() {
        if (mScanner != null) {
            mScanner.stop();
            mScanner = null;
        }
        updateUI();
    }

    private void updateUI() {
        final boolean isListEmpty = mScannedDevices == null || mScannedDevices.size() == 0;
        mNoDevicesTextView.setVisibility(isListEmpty ? View.VISIBLE : View.GONE);
        mScannedDevicesAdapter.notifyDataSetChanged();
    }

    @Override
    public void resetBluetoothCompleted() {
        Log.v("TEST", "Reset completed -> Resume scanning");
        resumeScanning();
    }

    @Override
    public void onConnected() {
        Log.v("TEST", "connected to device");
        ((UartController) getApplication()).initializeUart();
        finish();
    }

    @Override
    public void onConnecting() {
    }

    @Override
    public void onDisconnected() {
    }

    @Override
    public void onServicesDiscovered() {
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
}
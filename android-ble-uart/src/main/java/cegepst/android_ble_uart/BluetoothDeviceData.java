package cegepst.android_ble_uart;

import android.bluetooth.BluetoothDevice;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class BluetoothDeviceData {
    public BluetoothDevice device;
    public int rssi;
    public byte[] scanRecord;
    public String advertisedName;           // Advertised name
    private String cachedNiceName;
    private String cachedName;

    // Decoded scan record (update R.array.scan_devicetypes if this list is modified)
    public static final int kType_Unknown = 0;
    public static final int kType_Uart = 1;
    public static final int kType_Beacon = 2;
    public static final int kType_UriBeacon = 3;

    public int type;
    public int txPower;
    public ArrayList<UUID> uuids;

    public BluetoothDeviceData(BluetoothDevice device, byte[] scanRecord, int rssi) {
        this.device = device;
        this.scanRecord = scanRecord;
        this.rssi = rssi;
        uuids = new ArrayList<>();
        decodeScanRecords();
    }

    public String getName() {
        if (cachedName == null) {
            cachedName = device.getName();
            if (cachedName == null) {
                cachedName = advertisedName;      // Try to get a name (but it seems that if device.getName() is null, this is also null)
            }
        }

        return cachedName;
    }

    public String getNiceName() {
        if (cachedNiceName == null) {
            cachedNiceName = getName();
            if (cachedNiceName == null) {
                cachedNiceName = device.getAddress();
            }
        }

        return cachedNiceName;
    }

    private void decodeScanRecords() {
        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        int offset = 0;
        type = BluetoothDeviceData.kType_Unknown;

        while (offset < advertisedData.length - 2) {
            int len = advertisedData[offset++];
            if (len == 0) break;
            int type = advertisedData[offset++];
            if (type == 0) break;

            switch (type) {
                case 0x02:          // Partial list of 16-bit UUIDs
                case 0x03: {        // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++] & 0xFF;
                        uuid16 |= (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                }

                case 0x06:          // Partial list of 128-bit UUIDs
                case 0x07: {        // Complete list of 128-bit UUIDs
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            UUID uuid = BleUtils.getUuidFromByteArraLittleEndian(Arrays.copyOfRange(advertisedData, offset, offset + 16));
                            uuids.add(uuid);

                        } catch (IndexOutOfBoundsException e) {
                            //Log.e(TAG, "BlueToothDeviceFilter.parseUUID: " + e.toString());
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 16;
                            len -= 16;
                        }
                    }
                    break;
                }

                case 0x09: {
                    byte[] nameBytes = new byte[len - 1];
                    for (int i = 0; i < len - 1; i++) {
                        nameBytes[i] = advertisedData[offset++];
                    }

                    String name = null;
                    try {
                        name = new String(nameBytes, "utf-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    advertisedName = name;
                    break;
                }

                case 0x0A: {        // TX Power
                    txPower = advertisedData[offset++];
                    break;
                }

                default: {
                    offset += (len - 1);
                    break;
                }
            }
        }
        // Check if Uart is contained in the uuids
        boolean isUart = false;
        for (UUID uuid : uuids) {
            if (uuid.toString().equalsIgnoreCase(UartController.UUID_SERVICE)) {
                isUart = true;
                break;
            }
        }
        if (isUart) {
            type = BluetoothDeviceData.kType_Uart;
        }
    }
}
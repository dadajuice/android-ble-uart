package cegepst.android_ble_uart;

public class UartDataChunk {
    public static final int TRANSFERMODE_TX = 0;
    public static final int TRANSFERMODE_RX = 1;

    private long mTimestamp;        // in millis
    private int mMode;
    private byte[] mData;

    public UartDataChunk(long timestamp, int mode, byte[] bytes) {
        mTimestamp = timestamp;
        mMode = mode;
        mData = bytes;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public int getMode() {
        return mMode;
    }

    public byte[] getData() {
        return mData;
    }
}

package cegepst.android_ble_uart;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;

public class ScanAdapter extends BaseAdapter {

    private Activity activity;
    private ArrayList<BluetoothDeviceData> mFilteredPeripherals;

    public ScanAdapter(Activity activity, ArrayList<BluetoothDeviceData> mFilteredPeripherals) {
        this.activity = activity;
        this.mFilteredPeripherals = mFilteredPeripherals;
    }

    @Override
    public int getCount() {
        return mFilteredPeripherals.size();
    }

    @Override
    public Object getItem(int i) {
        return mFilteredPeripherals.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = activity.getLayoutInflater().inflate(R.layout.layout_scan_item_title, viewGroup, false);
        }

        TextView nameTextView = (TextView) view.findViewById(R.id.nameTextView);
        TextView descriptionTextView = (TextView) view.findViewById(R.id.descriptionTextView);
        ImageView rssiImageView = (ImageView) view.findViewById(R.id.rssiImageView);
        TextView rssiTextView = (TextView) view.findViewById(R.id.rssiTextView);

        BluetoothDeviceData deviceData = mFilteredPeripherals.get(i);
        nameTextView.setText(deviceData.getNiceName());

        descriptionTextView.setVisibility(deviceData.type != BluetoothDeviceData.kType_Unknown ? View.VISIBLE : View.INVISIBLE);
        descriptionTextView.setText(activity.getResources().getStringArray(R.array.scan_devicetypes)[deviceData.type]);
        rssiTextView.setText(deviceData.rssi == 127 ? activity.getString(R.string.scan_device_rssi_notavailable) : String.valueOf(deviceData.rssi));

        int rrsiDrawableResource = getDrawableIdForRssi(deviceData.rssi);
        rssiImageView.setImageResource(rrsiDrawableResource);

        return view;
    }

    private int getDrawableIdForRssi(int rssi) {
        int index;
        if (rssi == 127 || rssi <= -84) {       // 127 reserved for RSSI not available
            index = 0;
        } else if (rssi <= -72) {
            index = 1;
        } else if (rssi <= -60) {
            index = 2;
        } else if (rssi <= -48) {
            index = 3;
        } else {
            index = 4;
        }

        final int kSignalDrawables[] = {
                R.drawable.signalstrength0,
                R.drawable.signalstrength1,
                R.drawable.signalstrength2,
                R.drawable.signalstrength3,
                R.drawable.signalstrength4};
        return kSignalDrawables[index];
    }
}

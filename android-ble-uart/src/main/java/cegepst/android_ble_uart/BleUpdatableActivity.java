package cegepst.android_ble_uart;

import android.support.v7.app.AppCompatActivity;

public abstract class BleUpdatableActivity extends AppCompatActivity {

    public abstract void updateUi(String receivedData);

    @Override
    protected void onStart() {
        super.onStart();
        ((UartController) getApplication()).registerUi(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Comment for gradle
        ((UartController) getApplication()).unregisterUi();
    }
}

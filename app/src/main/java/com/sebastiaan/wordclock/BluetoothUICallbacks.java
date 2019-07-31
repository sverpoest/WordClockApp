package com.sebastiaan.wordclock;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.app.Fragment;

public abstract class BluetoothUICallbacks extends Fragment
{
    private static final String TAG = "BluetoothUICallbacks";

    @Override
    public void onStart()
    {
        super.onStart();
        IntentFilter filter = new IntentFilter(BluetoothService.BROADCAST);
        getActivity().registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void onStop()
    {
        super.onStop();
        getActivity().unregisterReceiver(mBroadcastReceiver);
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            int broadcastType = intent.getIntExtra(BluetoothService.BC_TYPE, -1);

            switch (broadcastType)
            {
                case BluetoothService.BC_TYPE_LOCATOR:
                    setResultCode(Activity.RESULT_CANCELED);
                    onPlayingLocator();
                    break;
                case BluetoothService.BC_TYPE_CONNECTED:
                    Boolean connected = intent.getBooleanExtra(BluetoothService.BC_CONNECTED, false);
                    onBluetoothConnected(connected);
                    setResultCode(Activity.RESULT_OK);
                    break;
            }
        }
    };

    protected abstract void onPlayingLocator();
    protected abstract void onBluetoothConnected(Boolean connected);
}

package com.sebastiaan.wordclock;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;


public class WordClockFragment extends BluetoothUICallbacks
{
    private BluetoothService mBluetoothService;
    private Boolean mBound;

    private ConnectionButton mConnectButton;
    final Handler h = new Handler();
    private Runnable mConnectionTimeout;
    private Button mSmsButton;
    private Button mStopAudio;

    public static WordClockFragment newInstance()
    {
        return new WordClockFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mConnectionTimeout = new Runnable()
        {
            @Override
            public void run()
            {
                onBluetoothConnected(false);
            }
        };

        Intent intent = new Intent(getActivity(), BluetoothService.class);
        getActivity().bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    private ServiceConnection connection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            BluetoothService.BluetoothServiceBinder binder = (BluetoothService.BluetoothServiceBinder) service;
            mBluetoothService = binder.getService();
            if (mBluetoothService == null)
                return;

            mBound = true;

            if (mBluetoothService.isLocatorPlaying())
                onPlayingLocator();
            onBluetoothConnected(mBluetoothService.isDeviceConnected());
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            mBluetoothService = null;
            mBound = false;
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.wordclock_fragment, container, false);

        mConnectButton = (ConnectionButton) v.findViewById(R.id.connectButton);
        mSmsButton = (Button) v.findViewById(R.id.smsButton);
        mStopAudio = (Button) v.findViewById(R.id.stopAudio);

        mConnectButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Intent service = new Intent(getActivity(), BluetoothService.class);
                if(mBluetoothService == null || !mBluetoothService.isDeviceConnected())
                    service.setAction(BluetoothService.START_BTSERVICE);
                else
                    service.setAction(BluetoothService.STOP_BTSERVICE);

                mConnectButton.startAnimation();
                h.postDelayed(mConnectionTimeout, 15000);

                getActivity().startService(service);
            }
        });

        mSmsButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                Intent service = new Intent(getActivity(), BluetoothService.class);
                service.setAction(BluetoothService.NOTIFY_SMS);

                getActivity().startService(service);
            }
        });

        mStopAudio.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (mBound)
                    mBluetoothService.stopLocator();
                mStopAudio.setVisibility(View.INVISIBLE);
            }
        });

        return v;
    }

    protected void onBluetoothConnected(Boolean connected)
    {
        h.removeCallbacks(mConnectionTimeout);
        if(connected)
            mConnectButton.startReverseAnimation("Disconnect");
        else
            mConnectButton.startReverseAnimation("Connect");

        mSmsButton.setEnabled(connected);

    };

    @Override
    protected void onPlayingLocator()
    {
        mStopAudio.setVisibility(View.VISIBLE);
    }


}

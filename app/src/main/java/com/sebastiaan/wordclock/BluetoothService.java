package com.sebastiaan.wordclock;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class BluetoothService extends Service
{
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();
    private static Boolean mBTConnected = false;
    private static int mServiceCounter = 0;

    private MediaPlayer mMediaPlayer;
    private AudioManager mAudioManager;

    private final IBinder binder = new BluetoothServiceBinder();
    public static final String BROADCAST = "com.sebastiaan.wordclock.bluetoothservice.BROADCAST";
    public static final String BC_TYPE = "TYPE";
    public static final int BC_TYPE_LOCATOR = 0;
    public static final int BC_TYPE_CONNECTED = 1;
    public static final String BC_CONNECTED = "CONNECTED";

    private static final String TAG = "WCBluetoothService";
    private static final String CHANNEL = "WCChannel";
    private static final String DEVICE_NAME = "WORDCLOCK";
    private static final int NOTIFICATION_ID = 101;

    public static final String MAIN_ACTION = "com.sebastiaan.wordclock.bluetoothservice.action.main";
    public static final String START_BTSERVICE = "com.sebastiaan.wordclock.bluetoothservice.action.startbtservice";
    public static final String STOP_BTSERVICE = "com.sebastiaan.wordclock.bluetoothservice.action.stopbtservice";

    public static final String NOTIFY_SMS = "com.sebastiaan.wordclock.bluetoothservice.action.notifysms";
    public static final String NOTIFY_SMS_COUNT = "com.sebastiaan.wordclock.bluetoothservice.action.notifysms.count";

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.w(TAG, "Start BluetoothService");

        if (intent.getAction().equals(STOP_BTSERVICE))
        {
            Log.i(TAG, "Received Stop Bluetooth Service Intent");

            unregisterReceiver(mBluetoothReceiver);
            stopGattServer();

            stopForeground(true);
            stopSelf();

            return START_NOT_STICKY;
        }

        if (intent.getAction().equals(START_BTSERVICE))
        {
            Log.i(TAG, "Received Start Bluetooth Service Intent ");

            startForeground(NOTIFICATION_ID, createNotification());

            initializeBluetooth();
        }
        else if (intent.getAction().equals(NOTIFY_SMS))
        {
            byte smsCount = intent.getByteExtra(NOTIFY_SMS_COUNT, (byte) 0);
            notifyRegisteredDevicesNewAlert(AlertNotificationProfile.CATEGORY_SMS_MMS, smsCount);
        }


        return Service.START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Notification createNotification()
    {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(MAIN_ACTION);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Intent disconnectIntent = new Intent(this, BluetoothService.class);
        disconnectIntent.setAction(STOP_BTSERVICE);
        PendingIntent pdisconnectIntent = PendingIntent.getService(this, 0,
                disconnectIntent, 0);


        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel channel = new NotificationChannel(CHANNEL, CHANNEL, NotificationManager.IMPORTANCE_DEFAULT );
        channel.setDescription(CHANNEL);
        channel.setSound(null, null);
        notificationManager.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle("WordClock")
                .setTicker("WordClock")
                .setContentText("BT Connected")
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_previous,
                        "Disconnect", pdisconnectIntent).build();

        return notification;
    }

    @Override
    public void onDestroy()
    {
        Log.w(TAG, "Destroy");

        unregisterReceiver(mBluetoothReceiver);
        stopGattServer();

        super.onDestroy();
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        mAudioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        IntentFilter filter = new IntentFilter(BluetoothService.BROADCAST);
        filter.setPriority(-999);
        registerReceiver(mOnShowNotification, filter);
    }


    public class BluetoothServiceBinder extends Binder
    {
        BluetoothService getService()
        {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return binder;
    }

    private void sendBTBroadcast(int broadcastType)
    {
        Intent i = new Intent(BROADCAST);
        i.putExtra(BC_TYPE, broadcastType);
        i.putExtra(BC_CONNECTED, isDeviceConnected());
        sendOrderedBroadcast(i, null, null, null, Activity.RESULT_OK, null, null);
    }

    private BroadcastReceiver mOnShowNotification = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if(getResultCode() != Activity.RESULT_OK)
                return;

            int broadcastType = intent.getIntExtra(BC_TYPE, BC_TYPE_CONNECTED);
            if(broadcastType == BC_TYPE_CONNECTED)
                return;

            Intent i = new Intent();
            i.setClass(getApplicationContext(), MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);

            setResultCode(Activity.RESULT_CANCELED);
        }
    };

    private void playLocator()
    {
        sendBTBroadcast(BC_TYPE_LOCATOR);

        if(mMediaPlayer != null)
        {
            if(mMediaPlayer.isPlaying())
                mMediaPlayer.stop();
            mMediaPlayer.reset();
            mMediaPlayer.release();
        }

        mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);
        mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_PLAY_SOUND);

        mMediaPlayer = MediaPlayer.create(this, R.raw.locator);
        mMediaPlayer.start();
        mMediaPlayer.setLooping(true);
    }

    public void stopLocator()
    {
        if(mMediaPlayer.isPlaying())
            mMediaPlayer.stop();
    }

    public Boolean isLocatorPlaying()
    {
        return mMediaPlayer != null && mMediaPlayer.isPlaying();
    }
    public Boolean isDeviceConnected() { return mBTConnected; }

    private void initializeBluetooth()
    {
        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();

        if(!checkBluetoothSupport(bluetoothAdapter))
        {
            Toast.makeText(getApplicationContext(), "BluetoothLE not supported", Toast.LENGTH_LONG).show();

            sendBTBroadcast(BC_TYPE_CONNECTED);

            stopSelf();
            stopForeground(true);
        }

        // Register for system Bluetooth events
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mBluetoothReceiver, filter);

        if (bluetoothAdapter.isEnabled())
            startGattServer();
        else
        {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        }
    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System {@link BluetoothAdapter}.
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startGattServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopGattServer();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    private void startGattServer()
    {
        if(mBluetoothGattServer == null)
        {
            mBluetoothGattServer = mBluetoothManager.openGattServer(this, mGattServerCallback);
            if (mBluetoothGattServer == null)
            {
                Log.w(TAG, "Unable to create GATT server");
                return;
            }
            mServiceCounter = 0;
        }
        addServicesAndConnect();
    }

    private void connectDevice()
    {
        Boolean found = mBTConnected;
        Boolean connected = mBTConnected;
        if(!mBTConnected)
        {
            BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
            Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
            Iterator<BluetoothDevice> iterator = devices.iterator();

            while (iterator.hasNext())
            {
                BluetoothDevice device = iterator.next();
                String name = device.getName().toUpperCase();
                if (name.contains(DEVICE_NAME))
                {
                    if (mBluetoothGattServer.connect(device, true))
                        found = connected = true;
                    else
                        found = true;
                }
            }
        }

        if(!found)
            Toast.makeText(getApplicationContext(), "WordClock not found in devices", Toast.LENGTH_LONG).show();
        else if(!connected)
            Toast.makeText(getApplicationContext(), "Unable to connect to WordClock", Toast.LENGTH_LONG).show();
    }

    private void stopGattServer()
    {
        if(mBluetoothGattServer == null)
            return;

        mBluetoothGattServer.close();
        mBluetoothGattServer = null;

        mBTConnected = false;

        sendBTBroadcast(BC_TYPE_CONNECTED);
    }

    /**
     * Send a time service notification to any devices that are subscribed
     * to the characteristic.
     */
    private void notifyRegisteredDevicesTime(long timestamp, byte adjustReason) {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered");
            return;
        }
        byte[] exactTime = TimeProfile.getExactTime(timestamp, adjustReason);

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");

        BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
                .getService(TimeProfile.TIME_SERVICE)
                .getCharacteristic(TimeProfile.CURRENT_TIME);
        timeCharacteristic.setValue(exactTime);

        for (BluetoothDevice device : mRegisteredDevices)
            mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
    }

    /**
     * Send a new alert notification to any devices that are subscribed
     * to the characteristic.
     */
    private void notifyRegisteredDevicesNewAlert(byte category, byte count) {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered");
            return;
        }
        byte[] alertValue = AlertNotificationProfile.getAlertValue(category, count);

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic alertCharacteristic = mBluetoothGattServer
                    .getService(AlertNotificationProfile.ALERT_NOTIFICATION_SERVICE)
                    .getCharacteristic(AlertNotificationProfile.NEW_ALERT);
            alertCharacteristic.setValue(alertValue);
            mBluetoothGattServer.notifyCharacteristicChanged(device, alertCharacteristic, false);
        }
    }

    private void addServicesAndConnect()
    {
        // We have to wait for onServiceAdded to add a second service, so the callback will perform
        // the next call of this function. If no more services need to be added, we connect.
        switch(mServiceCounter++)
        {
            case 0:
                mBluetoothGattServer.addService(TimeProfile.createTimeService());
                break;
            case 1:
                mBluetoothGattServer.addService(AlertNotificationProfile.createAlertNotificationService());
                break;
            default:
                connectDevice();
        }
    }

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback()
    {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service)
        {
            super.onServiceAdded(status, service);

            addServicesAndConnect();
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState)
        {
            if (newState == BluetoothProfile.STATE_CONNECTED)
                Log.i(TAG, "BluetoothDevice CONNECTED: " + device);
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);

                mBTConnected = false;
                sendBTBroadcast(BC_TYPE_CONNECTED);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic)
        {
            Log.w(TAG, "onCharacteristicReadRequest");

            long now = System.currentTimeMillis();

            if (TimeProfile.CURRENT_TIME.equals(characteristic.getUuid()))
            {
                Log.i(TAG, "Read CurrentTime");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        TimeProfile.getExactTime(now, TimeProfile.ADJUST_NONE));
            }
            else if (TimeProfile.LOCAL_TIME_INFO.equals(characteristic.getUuid()))
            {
                Log.i(TAG, "Read LocalTimeInfo");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        TimeProfile.getLocalTimeInfo(now));
            }
            else if (AlertNotificationProfile.SUPPORTED_NEW_ALERT_CATEGORY.equals(characteristic.getUuid()))
            {
                // We piggy-back on to this event to play the locator sound
                playLocator();

                Log.i(TAG, "Read SupporedNewAlertCategory");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        AlertNotificationProfile.getSupportedAlertCategoryValue(
                                false,
                                false,
                                false,
                                true,
                                false,
                                true,
                                false,
                                true,
                                false,
                                true));
            }
            else if (AlertNotificationProfile.SUPORTED_UNREAD_ALERT_CATEGORY.equals(characteristic.getUuid()))
            {
                Log.i(TAG, "Read SupporedNewAlertCategory");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        AlertNotificationProfile.getSupportedAlertCategoryValue(
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false,
                                false));
            }
            else
            {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor)
        {
            if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid()))
            {
                Log.d(TAG, "Config descriptor read");
                byte[] returnValue;
                if (mRegisteredDevices.contains(device))
                {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                }
                else
                {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        returnValue);
            }
            else
            {
                Log.w(TAG, "Unknown descriptor read request");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value)
        {
            boolean isAlert = AlertNotificationProfile.NEW_ALERT.equals(descriptor.getCharacteristic().getUuid());

            if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value))
            {
                Log.d(TAG, "Subscribe device to " + (isAlert?"alert":"") + " notifications: " + device);
                mRegisteredDevices.add(device);

                mBTConnected = true;
                sendBTBroadcast(BC_TYPE_CONNECTED);
            }
            else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value))
            {
                Log.d(TAG, "Unsubscribe device from " + (isAlert?"alert":"") + " notifications: " + device);
                mRegisteredDevices.remove(device);

                mBTConnected = false;
                sendBTBroadcast(BC_TYPE_CONNECTED);
            }

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        null);
            }
        }
    };


}

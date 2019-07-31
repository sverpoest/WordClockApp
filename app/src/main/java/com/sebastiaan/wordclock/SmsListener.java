package com.sebastiaan.wordclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsListener extends BroadcastReceiver
{
    private SharedPreferences mSharedPreferences;
    private static final String TAG = "SMSLISTENER";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if(intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED"))
        {
            Bundle bundle = intent.getExtras();
            if(bundle != null)
            {
                Object[] pdus = (Object[]) bundle.get("pdus");
                SmsMessage[] msgs = new SmsMessage[pdus.length];
                for(int i = 0; i < pdus.length; ++i)
                {
                    msgs[i] = getIncomingMessage(pdus[i], bundle);
                    Log.i(TAG, "Message From: " + msgs[i].getOriginatingAddress() + " Message: " + msgs[i].getMessageBody());
                }

                Intent service = new Intent(context, BluetoothService.class);
                service.setAction(BluetoothService.NOTIFY_SMS);
                service.putExtra(BluetoothService.NOTIFY_SMS_COUNT, (byte) pdus.length);

                context.startService(service);
            }
        }
    }

    private SmsMessage getIncomingMessage(Object aObject, Bundle bundle)
    {
        SmsMessage currentSMS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            String format = bundle.getString("format");
            currentSMS = SmsMessage.createFromPdu((byte[]) aObject, format);
        }
        else
            currentSMS = SmsMessage.createFromPdu((byte[]) aObject);
        return currentSMS;
    }
}

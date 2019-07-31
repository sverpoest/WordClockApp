/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sebastiaan.wordclock;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.Calendar;
import java.util.UUID;

/**
 * Implementation of the Bluetooth GATT Time Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
public class AlertNotificationProfile
{
    private static final String TAG = AlertNotificationProfile.class.getSimpleName();

    /* Alert Notification Service UUID */
    public static UUID ALERT_NOTIFICATION_SERVICE = UUID.fromString("00001811-0000-1000-8000-00805f9b34fb");

    /* Mandatory Supported New Alert Category Characteristic */
    public static UUID SUPPORTED_NEW_ALERT_CATEGORY = UUID.fromString("00002a47-0000-1000-8000-00805f9b34fb");
    /* Mandatory New Alert Characteristic */
    public static UUID NEW_ALERT = UUID.fromString("00002a46-0000-1000-8000-00805f9b34fb");
    /* Mandatory Supported Unread Alert Category Characteristic */
    public static UUID SUPORTED_UNREAD_ALERT_CATEGORY = UUID.fromString("00002a48-0000-1000-8000-00805f9b34fb");
    /* Mandatory Unread Alert Status Characteristic */
    public static UUID UNREAD_ALERT_STATUS = UUID.fromString("00002a45-0000-1000-8000-00805f9b34fb");
    /* Mandatory Alert Notification Control Point Characteristic */
    public static UUID ALERT_NOTIFICATION_CONTROL_POINT = UUID.fromString("00002a44-0000-1000-8000-00805f9b34fb");

    /* Mandatory Client Characteristic Config Descriptor */
    public static UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /* Category ID flags */
    public static final byte CATEGORY_SIMPLE    = 0x0;
    public static final byte CATEGORY_EMAIL     = 0x1;
    public static final byte CATEGORY_NEWS      = 0x2;
    public static final byte CATEGORY_CALL      = 0x3;
    public static final byte CATEGORY_MISS_CALL = 0x4;
    public static final byte CATEGORY_SMS_MMS   = 0x5;
    public static final byte CATEGORY_VOICEMAIL = 0x6;
    public static final byte CATEGORY_SCHEDULE  = 0x7;
    public static final byte CATEGORY_PRIORITY  = 0x8;
    public static final byte CATEGORY_IM        = 0x9;
    /**
     * Return a configured {@link BluetoothGattService} instance for the
     * Alert Notification Service.
     */
    public static BluetoothGattService createAlertNotificationService()
    {
        BluetoothGattService service = new BluetoothGattService(ALERT_NOTIFICATION_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Client Characteristic Config Descriptor

        // Supported New Alert Category characteristic
        BluetoothGattCharacteristic supportedCategory = new BluetoothGattCharacteristic(SUPPORTED_NEW_ALERT_CATEGORY,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        // New Alert characteristic
        BluetoothGattCharacteristic newAlert = new BluetoothGattCharacteristic(NEW_ALERT,
                // Supports notifications
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0);
        BluetoothGattDescriptor alertConfigDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        //newAlert.addDescriptor(alertConfigDescriptor);

        // Supported Unread Alert Category
        BluetoothGattCharacteristic unreadAlertCat = new BluetoothGattCharacteristic(SUPORTED_UNREAD_ALERT_CATEGORY,
                //Read-only characteristic
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);

        /*
        // Unread Alert Status
        BluetoothGattCharacteristic unreadAlert = new BluetoothGattCharacteristic(UNREAD_ALERT_STATUS,
                // Supports notifications
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                0);
        BluetoothGattDescriptor statusConfigDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        unreadAlert.addDescriptor(statusConfigDescriptor);
        */

        // Alert Notification Control Point
        BluetoothGattCharacteristic notificationControl = new BluetoothGattCharacteristic(ALERT_NOTIFICATION_CONTROL_POINT,
                //Write characteristic
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(supportedCategory);
        service.addCharacteristic(newAlert);
        service.addCharacteristic(unreadAlertCat);
        //service.addCharacteristic(unreadAlert);
        service.addCharacteristic(notificationControl);

        return service;
    }

    /**
     * Construct the field values for a New Alert characteristic
     */
    public static byte[] getAlertValue(byte category, byte numAlerts)
    {
        byte[] field = new byte[2];

        field[0] = category;
        field[1] = numAlerts;

        return field;
    }

    /**
     * Construct the field values for a Supported New Alert Category characteristic
     */
    public static byte[] getSupportedAlertCategoryValue(boolean simple,
                                                        boolean email,
                                                        boolean news,
                                                        boolean call,
                                                        boolean missedCall,
                                                        boolean smsmms,
                                                        boolean voicemail,
                                                        boolean schedule,
                                                        boolean priority,
                                                        boolean im)
    {
        byte[] field = new byte[2];
        field[0] = 0;
        field[1] = 0;

        if(simple)
            field[0] |= 0b00000001;
        if(email)
            field[0] |= 0b00000010;
        if(news)
            field[0] |= 0b00000100;
        if(call)
            field[0] |= 0b00001000;
        if(missedCall)
            field[0] |= 0b00010000;
        if(smsmms)
            field[0] |= 0b00100000;
        if(voicemail)
            field[0] |= 0b01000000;
        if(schedule)
            field[0] |= 0b10000000;
        if(priority)
            field[1] |= 0b00000001;
        if(im)
            field[1] |= 0b00000010;

        return field;
    }
}

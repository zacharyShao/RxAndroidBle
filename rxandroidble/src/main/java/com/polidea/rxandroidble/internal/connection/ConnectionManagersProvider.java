package com.polidea.rxandroidble.internal.connection;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationConnect;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationDisconnect;
import com.polidea.rxandroidble.internal.util.BleConnectionCompat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Actions;
import rx.schedulers.Schedulers;

public class ConnectionManagersProvider {

    public static class ConnectionManagers {

        public final RxBleGattCallback rxBleGattCallback;

        public final RxBleRadioOperationConnect connectOperation;

        public final RxBleRadioOperationDisconnect disconnectOperation;

        public ConnectionManagers(RxBleGattCallback rxBleGattCallback, RxBleRadioOperationConnect connectOperation,
                                  RxBleRadioOperationDisconnect disconnectOperation) {
            this.rxBleGattCallback = rxBleGattCallback;
            this.connectOperation = connectOperation;
            this.disconnectOperation = disconnectOperation;
        }
    }

    public ConnectionManagers provide(Context context,
                                      BluetoothDevice bluetoothDevice,
                                      boolean autoConnect,
                                      BleConnectionCompat connectionCompat,
                                      RxBleGattCallback gattCallback) {
        final AtomicReference<BluetoothGatt> bluetoothGattAtomicReference = new AtomicReference<>();
        RxBleRadioOperationConnect operationConnect = new RxBleRadioOperationConnect(bluetoothDevice,
                gattCallback,
                connectionCompat,
                autoConnect,
                35,
                TimeUnit.SECONDS,
                Schedulers.computation());
        final RxBleRadioOperationDisconnect operationDisconnect = new RxBleRadioOperationDisconnect(
                gattCallback,
                bluetoothDevice.getAddress(),
                bluetoothGattAtomicReference,
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE),
                AndroidSchedulers.mainThread()
        );
        // getBluetoothGatt completed when the connection is unsubscribed
        operationConnect.getBluetoothGatt().subscribe(
                new Action1<BluetoothGatt>() {
                    @Override
                    public void call(BluetoothGatt newValue) {
                        bluetoothGattAtomicReference.set(newValue);
                    }
                },
                Actions.<Throwable>toAction1(Actions.empty())
        );
        return new ConnectionManagers(gattCallback, operationConnect, operationDisconnect);
    }
}

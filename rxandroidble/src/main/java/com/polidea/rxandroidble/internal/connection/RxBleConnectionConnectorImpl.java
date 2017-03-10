package com.polidea.rxandroidble.internal.connection;

import static com.polidea.rxandroidble.internal.connection.ConnectionManagersProvider.ConnectionManagers;
import static com.polidea.rxandroidble.internal.util.ObservableUtil.justOnNext;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import com.polidea.rxandroidble.RxBleAdapterStateObservable.BleAdapterState;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble.internal.DeviceDependencies;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationDisconnect;
import rx.Observable;
import rx.Subscription;
import rx.functions.Action0;
import rx.functions.Actions;
import rx.functions.Func0;
import rx.functions.Func1;

public class RxBleConnectionConnectorImpl implements RxBleConnection.Connector {

    private final DeviceDependencies deviceDependencies;

    public RxBleConnectionConnectorImpl(DeviceDependencies deviceDependencies) {

        this.deviceDependencies = deviceDependencies;
    }

    @Override
    public Observable<RxBleConnection> prepareConnection(final Context context, final boolean autoConnect) {
        return Observable.defer(new Func0<Observable<RxBleConnection>>() {
            @Override
            public Observable<RxBleConnection> call() {
                final BluetoothDevice bluetoothDevice = deviceDependencies.getBluetoothDevice();
                if (!deviceDependencies.getBluetoothAdapterWrapper().isBluetoothEnabled()) {
                    return Observable.error(new BleDisconnectedException(bluetoothDevice.getAddress()));
                }

                final ConnectionManagers connectionManagers = deviceDependencies.getConnectionManagers(autoConnect);

                return Observable.merge(
                        deviceDependencies.getRadio().queue(connectionManagers.connectOperation),
                        deviceDependencies.getAdapterStateObservable()
                                .filter(new Func1<BleAdapterState, Boolean>() {
                                    @Override
                                    public Boolean call(BleAdapterState bleAdapterState) {
                                        return !bleAdapterState.isUsable();
                                    }
                                })
                                .flatMap(new Func1<BleAdapterState, Observable<BluetoothGatt>>() {
                                    @Override
                                    public Observable<BluetoothGatt> call(BleAdapterState bleAdapterState) {
                                        return Observable.error(new BleDisconnectedException(bluetoothDevice.getAddress()));
                                    }
                                })
                )
                        .first()
                        .flatMap(new Func1<BluetoothGatt, Observable<RxBleConnection>>() {
                            @Override
                            public Observable<RxBleConnection> call(BluetoothGatt bluetoothGatt) {
                                return emitConnectionWithoutCompleting(connectionManagers.rxBleGattCallback, bluetoothGatt);
                            }
                        })
                        .mergeWith(connectionManagers.rxBleGattCallback.<RxBleConnection>observeDisconnect())
                        .doOnUnsubscribe(new Action0() {
                            @Override
                            public void call() {
                                enqueueDisconnectOperation(connectionManagers.disconnectOperation);
                            }
                        });
            }
        });
    }

    private Observable<RxBleConnection> emitConnectionWithoutCompleting(RxBleGattCallback gattCallback, BluetoothGatt bluetoothGatt) {
        return justOnNext(new RxBleConnectionImpl(new ConnectionDependenciesImpl(deviceDependencies, bluetoothGatt, gattCallback)))
                .cast(RxBleConnection.class);
    }

    private Subscription enqueueDisconnectOperation(RxBleRadioOperationDisconnect operationDisconnect) {
        return deviceDependencies.getRadio()
                .queue(operationDisconnect)
                .subscribe(
                        Actions.empty(),
                        Actions.<Throwable>toAction1(Actions.empty())
                );
    }

}

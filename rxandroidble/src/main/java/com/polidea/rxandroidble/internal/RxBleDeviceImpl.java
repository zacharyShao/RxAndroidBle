package com.polidea.rxandroidble.internal;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.exceptions.BleAlreadyConnectedException;

import java.util.concurrent.atomic.AtomicBoolean;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func0;
import rx.subjects.BehaviorSubject;

import static com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState.CONNECTED;
import static com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState.CONNECTING;
import static com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState.DISCONNECTED;

class RxBleDeviceImpl implements RxBleDevice {

    private final DeviceDependencies deviceDependencies;
    private final BehaviorSubject<RxBleConnection.RxBleConnectionState> connectionStateSubject = BehaviorSubject.create(DISCONNECTED);
    private AtomicBoolean isConnected = new AtomicBoolean(false);

    public RxBleDeviceImpl(DeviceDependencies deviceDependencies) {
        this.deviceDependencies = deviceDependencies;
    }

    @Override
    public Observable<RxBleConnection.RxBleConnectionState> observeConnectionStateChanges() {
        return connectionStateSubject.distinctUntilChanged();
    }

    @Override
    public RxBleConnection.RxBleConnectionState getConnectionState() {
        return observeConnectionStateChanges().toBlocking().first();
    }

    @Override
    public Observable<RxBleConnection> establishConnection(final Context context, final boolean autoConnect) {
        return Observable.defer(new Func0<Observable<RxBleConnection>>() {
            @Override
            public Observable<RxBleConnection> call() {

                if (isConnected.compareAndSet(false, true)) {
                    return deviceDependencies.getConnector().prepareConnection(context, autoConnect)
                            .doOnSubscribe(new Action0() {
                                @Override
                                public void call() {
                                    connectionStateSubject.onNext(CONNECTING);
                                }
                            })
                            .doOnNext(new Action1<RxBleConnection>() {
                                @Override
                                public void call(RxBleConnection rxBleConnection) {
                                    connectionStateSubject.onNext(CONNECTED);
                                }
                            })
                            .doOnUnsubscribe(new Action0() {
                                @Override
                                public void call() {
                                    connectionStateSubject.onNext(DISCONNECTED);
                                    isConnected.set(false);
                                }
                            });
                } else {
                    return Observable.error(new BleAlreadyConnectedException(deviceDependencies.getBluetoothDevice().getAddress()));
                }
            }
        });
    }

    @Override
    public String getName() {
        return deviceDependencies.getBluetoothDevice().getName();
    }

    @Override
    public String getMacAddress() {
        return deviceDependencies.getBluetoothDevice().getAddress();
    }

    @Override
    public BluetoothDevice getBluetoothDevice() {
        return deviceDependencies.getBluetoothDevice();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RxBleDeviceImpl)) {
            return false;
        }

        RxBleDeviceImpl that = (RxBleDeviceImpl) o;
        return deviceDependencies.getBluetoothDevice().equals(that.getBluetoothDevice());
    }

    @Override
    public int hashCode() {
        return deviceDependencies.getBluetoothDevice().hashCode();
    }

    @Override
    public String toString() {
        final BluetoothDevice bluetoothDevice = deviceDependencies.getBluetoothDevice();
        return "RxBleDeviceImpl{" + "bluetoothDevice=" + bluetoothDevice.getName() + '(' + bluetoothDevice.getAddress() + ')' + '}';
    }
}

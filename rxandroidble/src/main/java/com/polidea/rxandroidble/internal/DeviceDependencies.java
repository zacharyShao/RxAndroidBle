package com.polidea.rxandroidble.internal;


import android.bluetooth.BluetoothDevice;
import com.polidea.rxandroidble.RxBleAdapterStateObservable;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.internal.connection.ConnectionManagersProvider;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;
import rx.Observable;

public interface DeviceDependencies {

    RxBleRadio getRadio();

    BluetoothDevice getBluetoothDevice();

    RxBleConnection.Connector getConnector();

    ConnectionManagersProvider getOperationsProvider();

    RxBleAdapterWrapper getBluetoothAdapterWrapper();

    Observable<RxBleAdapterStateObservable.BleAdapterState> getAdapterStateObservable();

    ConnectionManagersProvider.ConnectionManagers getConnectionManagers(boolean autoConnect);
}

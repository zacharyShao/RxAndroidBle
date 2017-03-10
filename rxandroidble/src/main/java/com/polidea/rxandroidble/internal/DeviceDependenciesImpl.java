package com.polidea.rxandroidble.internal;


import android.bluetooth.BluetoothDevice;
import com.polidea.rxandroidble.RxBleAdapterStateObservable;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.internal.connection.ConnectionManagersProvider;
import com.polidea.rxandroidble.internal.connection.RxBleConnectionConnectorImpl;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;
import rx.Observable;

class DeviceDependenciesImpl implements DeviceDependencies {

    private final ClientDependencies clientDependencies;
    private final BluetoothDevice bluetoothDevice;
    private final RxBleConnectionConnectorImpl connector;

    DeviceDependenciesImpl(final ClientDependencies clientDependencies, BluetoothDevice bluetoothDevice) {
        this.clientDependencies = clientDependencies;
        this.bluetoothDevice = bluetoothDevice;
        this.connector = new RxBleConnectionConnectorImpl(this);
    }

    @Override
    public RxBleRadio getRadio() {
        return clientDependencies.getRadio();
    }

    @Override
    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    @Override
    public RxBleConnection.Connector getConnector() {
        return connector;
    }

    @Override
    public ConnectionManagersProvider getOperationsProvider() {
        return new ConnectionManagersProvider();
    }

    @Override
    public RxBleAdapterWrapper getBluetoothAdapterWrapper() {
        return clientDependencies.getBluetoothAdapterWrapper();
    }

    @Override
    public Observable<RxBleAdapterStateObservable.BleAdapterState> getAdapterStateObservable() {
        return clientDependencies.getAdapterStateObservable();
    }

    @Override
    public ConnectionManagersProvider.ConnectionManagers getConnectionManagers(boolean autoConnect) {
        return getOperationsProvider().provide(
                clientDependencies.getApplicationContext(),
                bluetoothDevice,
                autoConnect,
                clientDependencies.getConnectionCompat(),
                clientDependencies.getBleGattCallbackProvider().provide()
        );
    }
}

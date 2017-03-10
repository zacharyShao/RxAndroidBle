package com.polidea.rxandroidble.internal;

import android.bluetooth.BluetoothDevice;
import com.polidea.rxandroidble.RxBleDevice;
import com.polidea.rxandroidble.internal.cache.RxBleDeviceCache;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;
import java.util.Map;

public class RxBleDeviceProvider {

    private final Map<String, RxBleDevice> availableDevices = new RxBleDeviceCache();
    private final ClientDependencies clientDependencies;

    public RxBleDeviceProvider(ClientDependencies clientDependencies) {
        this.clientDependencies = clientDependencies;
    }

    public RxBleDevice getBleDevice(String macAddress) {
        final RxBleDevice rxBleDevice = availableDevices.get(macAddress);

        if (rxBleDevice != null) {
            return rxBleDevice;
        }

        synchronized (availableDevices) {
            final RxBleDevice secondCheckRxBleDevice = availableDevices.get(macAddress);

            if (secondCheckRxBleDevice != null) {
                return secondCheckRxBleDevice;
            }

            final RxBleAdapterWrapper rxBleAdapterWrapper = clientDependencies.getBluetoothAdapterWrapper();

            final BluetoothDevice bluetoothDevice = rxBleAdapterWrapper.getRemoteDevice(macAddress);
            final DeviceDependenciesImpl deviceDependencies = new DeviceDependenciesImpl(clientDependencies, bluetoothDevice);
            final RxBleDeviceImpl newRxBleDevice = new RxBleDeviceImpl(deviceDependencies);
            availableDevices.put(macAddress, newRxBleDevice);
            return newRxBleDevice;
        }
    }
}

package com.polidea.rxandroidble;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.polidea.rxandroidble.RxBleAdapterStateObservable.BleAdapterState;
import com.polidea.rxandroidble.exceptions.BleScanException;
import com.polidea.rxandroidble.internal.ClientDependencies;
import com.polidea.rxandroidble.internal.ClientDependenciesImpl;
import com.polidea.rxandroidble.internal.RxBleInternalScanResult;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationScan;
import com.polidea.rxandroidble.internal.util.LocationServicesStatus;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;
import com.polidea.rxandroidble.internal.util.UUIDUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;

class RxBleClientImpl extends RxBleClient {

    private final ClientDependencies clientDependencies;
    private final Map<Set<UUID>, Observable<RxBleScanResult>> queuedScanOperations; // TODO: introduce Scan Manager?

    RxBleClientImpl(ClientDependencies clientDependencies) {
        this.clientDependencies = clientDependencies;
        this.queuedScanOperations = clientDependencies.getQueuedScanOperations();
    }

    static RxBleClientImpl getInstance(@NonNull Context context) {
        return new RxBleClientImpl(new ClientDependenciesImpl(context));
    }

    @Override
    public RxBleDevice getBleDevice(@NonNull String macAddress) {
        return clientDependencies.getDeviceProvider().getBleDevice(macAddress);
    }

    @Override
    public Set<RxBleDevice> getBondedDevices() {
        Set<RxBleDevice> rxBleDevices = new HashSet<>();
        Set<BluetoothDevice> bluetoothDevices = clientDependencies.getBluetoothAdapterWrapper().getBondedDevices();
        for (BluetoothDevice bluetoothDevice : bluetoothDevices) {
            rxBleDevices.add(getBleDevice(bluetoothDevice.getAddress()));
        }

        return rxBleDevices;
    }

    @Override
    public Observable<RxBleScanResult> scanBleDevices(@Nullable UUID... filterServiceUUIDs) {

        final RxBleAdapterWrapper rxBleAdapterWrapper = clientDependencies.getBluetoothAdapterWrapper();
        final LocationServicesStatus locationServicesStatus = clientDependencies.getLocationServicesStatus();

        if (!rxBleAdapterWrapper.hasBluetoothAdapter()) {
            return Observable.error(new BleScanException(BleScanException.BLUETOOTH_NOT_AVAILABLE));
        } else if (!rxBleAdapterWrapper.isBluetoothEnabled()) {
            return Observable.error(new BleScanException(BleScanException.BLUETOOTH_DISABLED));
        } else if (!locationServicesStatus.isLocationPermissionOk()) {
            return Observable.error(new BleScanException(BleScanException.LOCATION_PERMISSION_MISSING));
        } else if (!locationServicesStatus.isLocationProviderOk()) {
            return Observable.error(new BleScanException(BleScanException.LOCATION_SERVICES_DISABLED));
        } else {
            return initializeScan(filterServiceUUIDs);
        }
    }

    private Observable<RxBleScanResult> initializeScan(@Nullable UUID[] filterServiceUUIDs) {
        final Set<UUID> filteredUUIDs = clientDependencies.getUuidUtil().toDistinctSet(filterServiceUUIDs);

        synchronized (queuedScanOperations) {
            Observable<RxBleScanResult> matchingQueuedScan = queuedScanOperations.get(filteredUUIDs);

            if (matchingQueuedScan == null) {
                matchingQueuedScan = createScanOperation(filterServiceUUIDs);
                queuedScanOperations.put(filteredUUIDs, matchingQueuedScan);
            }

            return matchingQueuedScan;
        }
    }

    private Observable<RxBleInternalScanResult> bluetoothAdapterOffExceptionObservable() {
        return clientDependencies.getAdapterStateObservable()
                .filter(new Func1<BleAdapterState, Boolean>() {
                    @Override
                    public Boolean call(BleAdapterState state) {
                        return state != BleAdapterState.STATE_ON;
                    }
                })
                .first()
                .flatMap(new Func1<BleAdapterState, Observable<? extends RxBleInternalScanResult>>() {
                    @Override
                    public Observable<? extends RxBleInternalScanResult> call(BleAdapterState status) {
                        return Observable.error(new BleScanException(BleScanException.BLUETOOTH_DISABLED));
                    }
                });
    }

    private RxBleScanResult convertToPublicScanResult(RxBleInternalScanResult scanResult) {
        final BluetoothDevice bluetoothDevice = scanResult.getBluetoothDevice();
        final RxBleDevice bleDevice = getBleDevice(bluetoothDevice.getAddress());
        return new RxBleScanResult(bleDevice, scanResult.getRssi(), scanResult.getScanRecord());
    }

    private Observable<RxBleScanResult> createScanOperation(@Nullable final UUID[] filterServiceUUIDs) {
        final UUIDUtil uuidUtil = clientDependencies.getUuidUtil();
        final Set<UUID> filteredUUIDs = uuidUtil.toDistinctSet(filterServiceUUIDs);
        final RxBleRadioOperationScan scanOperation = new RxBleRadioOperationScan(
                filterServiceUUIDs,
                clientDependencies.getBluetoothAdapterWrapper(),
                uuidUtil
        );
        return clientDependencies.getRadio().queue(scanOperation)
                .doOnUnsubscribe(new Action0() {
                    @Override
                    public void call() {

                        synchronized (queuedScanOperations) {
                            scanOperation.stop();
                            queuedScanOperations.remove(filteredUUIDs);
                        }
                    }
                })
                .mergeWith(bluetoothAdapterOffExceptionObservable())
                .map(new Func1<RxBleInternalScanResult, RxBleScanResult>() {
                    @Override
                    public RxBleScanResult call(RxBleInternalScanResult scanResult) {
                        return RxBleClientImpl.this.convertToPublicScanResult(scanResult);
                    }
                })
                .share();
    }
}

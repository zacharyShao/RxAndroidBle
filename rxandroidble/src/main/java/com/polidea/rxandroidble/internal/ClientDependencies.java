package com.polidea.rxandroidble.internal;


import android.content.Context;
import com.polidea.rxandroidble.RxBleAdapterStateObservable;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.internal.connection.RxBleGattCallback;
import com.polidea.rxandroidble.internal.util.BleConnectionCompat;
import com.polidea.rxandroidble.internal.util.LocationServicesStatus;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;
import com.polidea.rxandroidble.internal.util.UUIDUtil;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import rx.Observable;

public interface ClientDependencies {

    Context getApplicationContext();

    RxBleRadio getRadio();

    UUIDUtil getUuidUtil();

    RxBleDeviceProvider getDeviceProvider();

    Map<Set<UUID>, Observable<RxBleScanResult>> getQueuedScanOperations();

    RxBleAdapterWrapper getBluetoothAdapterWrapper();

    Observable<RxBleAdapterStateObservable.BleAdapterState> getAdapterStateObservable();

    LocationServicesStatus getLocationServicesStatus();

    BleConnectionCompat getConnectionCompat();

    RxBleGattCallback.Provider getBleGattCallbackProvider();
}

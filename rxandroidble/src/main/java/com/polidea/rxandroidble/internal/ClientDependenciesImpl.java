package com.polidea.rxandroidble.internal;


import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.location.LocationManager;
import com.polidea.rxandroidble.RxBleAdapterStateObservable;
import com.polidea.rxandroidble.RxBleScanResult;
import com.polidea.rxandroidble.internal.connection.RxBleGattCallback;
import com.polidea.rxandroidble.internal.radio.RxBleRadioImpl;
import com.polidea.rxandroidble.internal.util.BleConnectionCompat;
import com.polidea.rxandroidble.internal.util.CheckerLocationPermission;
import com.polidea.rxandroidble.internal.util.CheckerLocationProvider;
import com.polidea.rxandroidble.internal.util.LocationServicesStatus;
import com.polidea.rxandroidble.internal.util.ProviderApplicationTargetSdk;
import com.polidea.rxandroidble.internal.util.ProviderDeviceSdk;
import com.polidea.rxandroidble.internal.util.RxBleAdapterWrapper;
import com.polidea.rxandroidble.internal.util.UUIDUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import rx.Observable;
import rx.Scheduler;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ClientDependenciesImpl implements ClientDependencies {

    private final Context applicationContext;

    private final RxBleAdapterWrapper rxBleAdapterWrapper;

    private final RxBleRadioImpl rxBleRadio;

    private final RxBleAdapterStateObservable adapterStateObservable;

    private final BleConnectionCompat bleConnectionCompat;

    private final UUIDUtil uuidUtil;

    private final RxBleDeviceProvider deviceProvider;

    private final LocationServicesStatus locationServicesStatus;

    private final ExecutorService executor;

    private final Map<Set<UUID>, Observable<RxBleScanResult>> queuedScanOperations = new HashMap<>();
    private final RxBleGattCallback.Provider bleGattCallbackProvider;


    public ClientDependenciesImpl(Context context) {
        applicationContext = context.getApplicationContext();
        rxBleAdapterWrapper = new RxBleAdapterWrapper(BluetoothAdapter.getDefaultAdapter());
        rxBleRadio = new RxBleRadioImpl(getRxBleRadioScheduler());
        adapterStateObservable = new RxBleAdapterStateObservable(applicationContext);
        bleConnectionCompat = new BleConnectionCompat(context);
        executor = Executors.newSingleThreadExecutor();
        uuidUtil = new UUIDUtil();
        locationServicesStatus = new LocationServicesStatus(
                new CheckerLocationProvider((LocationManager) applicationContext.getSystemService(Context.LOCATION_SERVICE)),
                new CheckerLocationPermission(applicationContext),
                new ProviderDeviceSdk(),
                new ProviderApplicationTargetSdk(applicationContext)
        );
        final Scheduler gattCallbacksProcessingScheduler = Schedulers.from(executor);
        bleGattCallbackProvider = new RxBleGattCallback.Provider() {
            @Override
            public RxBleGattCallback provide() {
                return new RxBleGattCallback(gattCallbacksProcessingScheduler);
            }
        };
        deviceProvider = new RxBleDeviceProvider(this);
    }

    @Override
    public Context getApplicationContext() {
        return applicationContext;
    }

    @Override
    public RxBleRadio getRadio() {
        return rxBleRadio;
    }

    @Override
    public UUIDUtil getUuidUtil() {
        return uuidUtil;
    }

    @Override
    public RxBleDeviceProvider getDeviceProvider() {
        return deviceProvider;
    }

    @Override
    public Map<Set<UUID>, Observable<RxBleScanResult>> getQueuedScanOperations() {
        return queuedScanOperations;
    }

    @Override
    public RxBleAdapterWrapper getBluetoothAdapterWrapper() {
        return rxBleAdapterWrapper;
    }

    @Override
    public Observable<RxBleAdapterStateObservable.BleAdapterState> getAdapterStateObservable() {
        return adapterStateObservable;
    }

    @Override
    public LocationServicesStatus getLocationServicesStatus() {
        return locationServicesStatus;
    }

    @Override
    public BleConnectionCompat getConnectionCompat() {
        return bleConnectionCompat;
    }

    @Override
    public RxBleGattCallback.Provider getBleGattCallbackProvider() {
        return bleGattCallbackProvider;
    }

    /**
     * In some implementations (i.e. Samsung Android 4.3) calling BluetoothDevice.connectGatt()
     * from thread other than main thread ends in connecting with status 133. It's safer to make bluetooth calls
     * on the main thread.
     */
    private static Scheduler getRxBleRadioScheduler() {
        return AndroidSchedulers.mainThread();
    }

    @Override
    protected void finalize() throws Throwable {
        executor.shutdown();
        super.finalize();
    }
}

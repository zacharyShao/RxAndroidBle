package com.polidea.rxandroidble.internal.connection;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.os.Build;
import android.os.DeadObjectException;
import android.support.annotation.RequiresApi;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDeviceServices;
import com.polidea.rxandroidble.RxBleRadioOperationCustom;
import com.polidea.rxandroidble.exceptions.BleDisconnectedException;
import com.polidea.rxandroidble.exceptions.BleException;
import com.polidea.rxandroidble.internal.RxBleRadio;
import com.polidea.rxandroidble.internal.RxBleRadioOperation;
import com.polidea.rxandroidble.internal.DeviceDependencies;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationCharacteristicRead;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationCharacteristicWrite;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationDescriptorRead;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationDescriptorWrite;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationMtuRequest;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationReadRssi;
import com.polidea.rxandroidble.internal.operations.RxBleRadioOperationServicesDiscover;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

class ConnectionDependenciesImpl implements ConnectionDependencies {

    private final DeviceDependencies deviceDependencies;

    private final Scheduler timeoutScheduler = Schedulers.computation();

    private final BluetoothGatt bluetoothGatt;

    private final RxBleGattCallback rxBleGattCallback;

    ConnectionDependenciesImpl(
            DeviceDependencies deviceDependencies,
            BluetoothGatt bluetoothGatt,
            RxBleGattCallback rxBleGattCallback
    ) {
        this.deviceDependencies = deviceDependencies;
        this.bluetoothGatt = bluetoothGatt;
        this.rxBleGattCallback = rxBleGattCallback;
    }

    @Override
    public RxBleRadio getRadio() {
        return deviceDependencies.getRadio();
    }

    @Override
    public BluetoothGatt getBluetoothGatt() {
        return bluetoothGatt;
    }

    @Override
    public RxBleGattCallback getGattCallback() {
        return rxBleGattCallback;
    }

    @Override
    public AtomicReference<Observable<RxBleDeviceServices>> getDiscoveredServicesCache() {
        return new AtomicReference<>();
    }

    @Override
    public RxBleConnection.LongWriteOperationBuilder getNewLongWriteOpBuilder(Callable<Integer> defaultMaxBatchSizeCallable,
                                                                              RxBleConnectionImpl rxBleConnection) {
        return new LongWriteOperationBuilderImpl(
                bluetoothGatt,
                rxBleGattCallback,
                getRadio(),
                defaultMaxBatchSizeCallable,
                rxBleConnection
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public RxBleRadioOperationMtuRequest getNewRequestMtuOperation(int mtu, long timeout, TimeUnit timeUnit) {
        return new RxBleRadioOperationMtuRequest(
                mtu,
                rxBleGattCallback,
                bluetoothGatt,
                timeout,
                timeUnit,
                timeoutScheduler
        );
    }

    @Override
    public RxBleRadioOperationServicesDiscover getNewServiceDiscoveryOperation(long timeout, TimeUnit timeUnit) {
        return new RxBleRadioOperationServicesDiscover(rxBleGattCallback, bluetoothGatt, timeout, timeUnit, timeoutScheduler);
    }

    @Override
    public RxBleRadioOperationCharacteristicWrite getNewWriteCharacteristicOperation(BluetoothGattCharacteristic characteristic,
                                                                                     byte[] data) {
        return new RxBleRadioOperationCharacteristicWrite(rxBleGattCallback, bluetoothGatt, characteristic, data, timeoutScheduler);
    }

    @Override
    public RxBleRadioOperationCharacteristicRead getNewReadCharacteristicOperation(BluetoothGattCharacteristic characteristic) {
        return new RxBleRadioOperationCharacteristicRead(rxBleGattCallback, bluetoothGatt, characteristic, timeoutScheduler);
    }

    @Override
    public RxBleRadioOperationDescriptorRead getNewReadDescriptorOperation(BluetoothGattDescriptor descriptor) {
        return new RxBleRadioOperationDescriptorRead(rxBleGattCallback, bluetoothGatt, descriptor, timeoutScheduler);
    }

    @Override
    public RxBleRadioOperationDescriptorWrite getNewWriteDescriptorOperation(BluetoothGattDescriptor bluetoothGattDescriptor, byte[] data) {
        return new RxBleRadioOperationDescriptorWrite(
                rxBleGattCallback,
                bluetoothGatt,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                bluetoothGattDescriptor,
                data,
                timeoutScheduler
        );
    }

    @Override
    public RxBleRadioOperationReadRssi getNewReadRssiOperation() {
        return new RxBleRadioOperationReadRssi(rxBleGattCallback, bluetoothGatt, timeoutScheduler);
    }

    @Override
    public <T> RxBleRadioOperation<T> getNewAnonymousCustomOperation(final RxBleRadioOperationCustom<T> operation) {
        return new RxBleRadioOperation<T>() {
            @Override
            @SuppressWarnings("ConstantConditions")
            protected void protectedRun() throws Throwable {
                Observable<T> operationObservable = operation.asObservable(bluetoothGatt, rxBleGattCallback, getRadio().scheduler());
                if (operationObservable == null) {
                    throw new IllegalArgumentException("The custom operation asObservable method must return a non-null observable");
                }

                operationObservable
                        .doOnCompleted(new Action0() {
                            @Override
                            public void call() {
                                releaseRadio();
                            }
                        })
                        .subscribe(getSubscriber());
            }

            @Override
            protected BleException provideException(DeadObjectException deadObjectException) {
                return new BleDisconnectedException(deadObjectException, bluetoothGatt.getDevice().getAddress());
            }
        };
    }
}

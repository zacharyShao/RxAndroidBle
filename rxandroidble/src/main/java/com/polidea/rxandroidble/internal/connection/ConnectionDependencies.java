package com.polidea.rxandroidble.internal.connection;


import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import com.polidea.rxandroidble.RxBleConnection;
import com.polidea.rxandroidble.RxBleDeviceServices;
import com.polidea.rxandroidble.RxBleRadioOperationCustom;
import com.polidea.rxandroidble.internal.RxBleRadio;
import com.polidea.rxandroidble.internal.RxBleRadioOperation;
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

interface ConnectionDependencies {

    RxBleRadio getRadio();

    BluetoothGatt getBluetoothGatt();

    RxBleGattCallback getGattCallback();

    RxBleConnection.LongWriteOperationBuilder getNewLongWriteOpBuilder(Callable<Integer> callable, RxBleConnectionImpl rxBleConnection);

    RxBleRadioOperationReadRssi getNewReadRssiOperation();

    RxBleRadioOperationDescriptorWrite getNewWriteDescriptorOperation(BluetoothGattDescriptor bluetoothGattDescriptor, byte[] data);

    RxBleRadioOperationDescriptorRead getNewReadDescriptorOperation(BluetoothGattDescriptor descriptor);

    RxBleRadioOperationCharacteristicWrite getNewWriteCharacteristicOperation(BluetoothGattCharacteristic characteristic, byte[] data);

    RxBleRadioOperationCharacteristicRead getNewReadCharacteristicOperation(BluetoothGattCharacteristic characteristic);

    RxBleRadioOperationServicesDiscover getNewServiceDiscoveryOperation(long timeout, TimeUnit timeUnit);

    RxBleRadioOperationMtuRequest getNewRequestMtuOperation(int mtu, long timeout, TimeUnit timeUnit);

    <T> RxBleRadioOperation<T> getNewAnonymousCustomOperation(RxBleRadioOperationCustom<T> operation);

    AtomicReference<Observable<RxBleDeviceServices>> getDiscoveredServicesCache();
}

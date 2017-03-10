package com.polidea.rxandroidble.internal
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.content.Context
import com.polidea.rxandroidble.RxBleConnection
import com.polidea.rxandroidble.RxBleDevice
import com.polidea.rxandroidble.exceptions.BleAlreadyConnectedException
import com.polidea.rxandroidble.exceptions.BleGattException
import com.polidea.rxandroidble.exceptions.BleGattOperationType
import rx.Observable
import rx.observers.TestSubscriber
import rx.subjects.PublishSubject
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.polidea.rxandroidble.RxBleConnection.RxBleConnectionState.*

public class RxBleDeviceTest extends Specification {

    BluetoothDevice mockBluetoothDevice = Mock BluetoothDevice
    RxBleConnection.Connector mockConnector = Mock RxBleConnection.Connector
    RxBleConnection mockConnection = Mock RxBleConnection
    PublishSubject<RxBleConnection> mockConnectorEstablishConnectionPublishSubject = PublishSubject.create()
    PublishSubject<RxBleConnection.RxBleConnectionState> connectionStatePublishSubject = PublishSubject.create()
    @Shared BluetoothGatt mockBluetoothGatt = Mock BluetoothGatt
    def deviceDependencies = Mock DeviceDependencies
    RxBleDevice objectUnderTest
    TestSubscriber deviceConnectionStateSubscriber = new TestSubscriber()

    def setup() {
        mockConnector.prepareConnection(_, _) >> mockConnectorEstablishConnectionPublishSubject
        deviceDependencies.getConnector() >> mockConnector
        deviceDependencies.getBluetoothDevice() >> mockBluetoothDevice
        objectUnderTest = new RxBleDeviceImpl(deviceDependencies)
    }

    def "should return the BluetoothDevice name"() {

        given:
        mockBluetoothDevice.name >> "testName"

        expect:
        objectUnderTest.getName() == "testName"
    }

    def "should return the BluetoothDevice address"() {

        given:
        mockBluetoothDevice.address >> "aa:aa:aa:aa:aa:aa"

        expect:
        objectUnderTest.getMacAddress() == "aa:aa:aa:aa:aa:aa"
    }

    def "equals() should return true when compared to a different RxBleDevice instance with the same underlying BluetoothDevice"() {

        given:
        def differentDeviceDependencies = Mock(DeviceDependencies)
        differentDeviceDependencies.getBluetoothDevice() >> mockBluetoothDevice
        def differentRxBleDeviceWithSameBluetoothDevice = new RxBleDeviceImpl(differentDeviceDependencies)

        expect:
        objectUnderTest.equals(differentRxBleDeviceWithSameBluetoothDevice)
    }

    def "hashCode() should return the same value as a different RxBleDevice instance hashCode() with the same underlying BluetoothDevice"() {

        given:
        def differentDeviceDependencies = Mock(DeviceDependencies)
        differentDeviceDependencies.getBluetoothDevice() >> mockBluetoothDevice
        def differentRxBleDevice = new RxBleDeviceImpl(differentDeviceDependencies)

        expect:
        objectUnderTest.hashCode() == differentRxBleDevice.hashCode()
    }

    @Unroll
    def "establishConnection() should call RxBleConnection.Connector.prepareConnection() #id"() {

        when:
        objectUnderTest.establishConnection(theContext, theAutoConnectValue).subscribe()

        then:
        1 * mockConnector.prepareConnection(theContext, theAutoConnectValue) >> connectionStatePublishSubject

        where:
        theContext    | theAutoConnectValue
        null          | true
        null          | false
        Mock(Context) | true
        Mock(Context) | false
    }

    def "should emit DISCONNECTED when subscribed and RxBleDevice was not connected yet"() {

        when:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)

        then:
        deviceConnectionStateSubscriber.assertValue(DISCONNECTED)
    }

    def "should emit CONNECTING when subscribed and establishConnection() was subscribed"() {

        given:
        startConnecting()

        when:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)

        then:
        deviceConnectionStateSubscriber.assertValue(CONNECTING)
    }

    def "should emit DISCONNECTED, CONNECTING state on subscribing to establishConnection()"() {

        given:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)

        when:
        startConnecting()

        then:
        deviceConnectionStateSubscriber.assertValues(DISCONNECTED, CONNECTING)
    }

    def "should emit CONNECTED when subscribed after establishConnection() has emitted"() {

        given:
        startConnecting()
        notifyConnectionWasEstablished()

        when:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)

        then:
        deviceConnectionStateSubscriber.assertValue(CONNECTED)
    }

    def "should emit CONNECTING and CONNECTED state when subscribed after subscribing establishConnection() and before it emits RxBleConnection"() {

        given:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)

        when:
        startConnecting()
        notifyConnectionWasEstablished()

        then:
        deviceConnectionStateSubscriber.assertValues(DISCONNECTED, CONNECTING, CONNECTED)
    }

    def "should emit DISCONNECTED state on unsubscribing from establishConnection()"() {

        given:
        def connectionTestSubscriber = new TestSubscriber()
        rxStartConnecting().subscribe(connectionTestSubscriber)
        notifyConnectionWasEstablished()
        connectionTestSubscriber.unsubscribe()

        when:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)

        then:
        deviceConnectionStateSubscriber.assertValue(DISCONNECTED)
    }

    def "should emit DISCONNECTED state when connection was broken"() {

        given:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)
        rxStartConnecting().subscribe({ RxBleConnection ignored -> }, { Throwable ignored -> })
        notifyConnectionWasEstablished()

        when:
        dropConnection()

        then:
        deviceConnectionStateSubscriber.assertValues(DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTED)
    }

    def "should not propagate RxBleConnection.getConnectionState() errors"() {
        given:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)
        startConnecting()
        notifyConnectionWasEstablished()

        when:
        emitConnectionStateErrorThroughConnectionStatus()

        then:
        deviceConnectionStateSubscriber.assertNoErrors()
    }

    def "should not unsubscribe if connection was dropped"() {
        given:
        objectUnderTest.observeConnectionStateChanges().subscribe(deviceConnectionStateSubscriber)
        startConnecting()
        notifyConnectionWasEstablished()

        when:
        emitConnectionStateErrorThroughConnectionStatus()

        then:
        deviceConnectionStateSubscriber.assertNoTerminalEvent()
    }

    def "should emit connection and stay subscribed after it was established"() {

        given:
        def testSubscriber = new TestSubscriber()

        when:
        rxStartConnecting().subscribe(testSubscriber)
        notifyConnectionWasEstablished()

        then:
        testSubscriber.assertSubscribed()
        testSubscriber.assertValueCount 1
    }

    def "should emit error if already connected"() {

        given:
        def testSubscriber = new TestSubscriber()
        rxStartConnecting().subscribe()
        notifyConnectionWasEstablished()

        when:
        rxStartConnecting().subscribe(testSubscriber)

        then:
        testSubscriber.assertError BleAlreadyConnectedException
    }

    def "should create new connection if previous connection was established and released before second subscriber has subscribed"() {

        given:
        def firstSubscriber = new TestSubscriber()
        def secondSubscriber = new TestSubscriber()
        def subscription = rxStartConnecting().subscribe(firstSubscriber)
        notifyConnectionWasEstablished()
        subscription.unsubscribe()

        when:
        objectUnderTest.establishConnection(Mock(Context), false).subscribe(secondSubscriber)

        then:
        firstSubscriber.assertValueCount 1
        firstSubscriber.assertReceivedOnNextNot(secondSubscriber.onNextEvents)
    }

    def "should unsubscribe from connection if it was dropped"() {

        given:
        def connectionTestSubscriber = new TestSubscriber()
        rxStartConnecting().subscribe(connectionTestSubscriber)
        notifyConnectionWasEstablished()

        when:
        dropConnection()

        then:
        connectionTestSubscriber.isUnsubscribed()
    }

    def "should return DISCONNECTED when RxBleDevice was not connected yet"() {

        when:
        def connectionState = objectUnderTest.getConnectionState()

        then:
        connectionState == DISCONNECTED
    }

    def "should return CONNECTING when establishConnection() was subscribed"() {

        given:
        startConnecting()

        when:
        def connectionState = objectUnderTest.getConnectionState()

        then:
        connectionState == CONNECTING
    }

    def "should return CONNECTED after establishConnection() has emitted"() {

        given:
        startConnecting()
        notifyConnectionWasEstablished()

        when:
        def connectionState = objectUnderTest.getConnectionState()

        then:
        connectionState == CONNECTED
    }

    def "should return DISCONNECTED after establishConnection() has emitted and connection terminated with an error"() {
        given:
        startConnecting()
        notifyConnectionWasEstablished()
        dropConnection()

        when:
        def connectionState = objectUnderTest.getConnectionState()

        then:
        connectionState == DISCONNECTED
    }

    def "should return initial BluetoothDevice on getBluetoothDevice()"() {

        expect:
        objectUnderTest.getBluetoothDevice() == mockBluetoothDevice
    }

    public void startConnecting() {
        rxStartConnecting().subscribe({}, {})
    }

    public Observable<RxBleConnection> rxStartConnecting() {
        return objectUnderTest.establishConnection(Mock(Context), false)
    }

    public void notifyConnectionWasEstablished() {
        mockConnectorEstablishConnectionPublishSubject.onNext(mockConnection)
    }

    public void dropConnection() {
        mockConnectorEstablishConnectionPublishSubject.onError(new BleGattException(mockBluetoothGatt, BleGattOperationType.CONNECTION_STATE))
    }

    public void emitConnectionStateErrorThroughConnectionStatus() {
        connectionStatePublishSubject.onError(new Throwable("test"))
    }
}

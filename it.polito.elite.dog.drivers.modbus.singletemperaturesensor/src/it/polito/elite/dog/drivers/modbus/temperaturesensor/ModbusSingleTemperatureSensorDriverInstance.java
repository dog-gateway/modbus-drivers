/*
 * Dog - Device Driver
 * 
 * Copyright (c) 2012-2014 Dario Bonino and Luigi De Russis
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package it.polito.elite.dog.drivers.modbus.temperaturesensor;

import it.polito.elite.dog.core.library.model.CNParameters;
import it.polito.elite.dog.core.library.model.ControllableDevice;
import it.polito.elite.dog.core.library.model.DeviceStatus;
import it.polito.elite.dog.core.library.model.devicecategory.SingleTemperatureSensor;
import it.polito.elite.dog.core.library.model.notification.TemperatureMeasurementNotification;
import it.polito.elite.dog.core.library.model.state.TemperatureState;
import it.polito.elite.dog.core.library.model.statevalue.TemperatureStateValue;
import it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import net.wimpi.modbus.util.SerialParameters;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.device.Device;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Set;

import javax.measure.DecimalMeasure;
import javax.measure.Measure;
import javax.measure.unit.SI;
import javax.measure.unit.UnitFormat;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 */
public class ModbusSingleTemperatureSensorDriverInstance
        extends ModbusDriverInstance implements SingleTemperatureSensor
{
    // the class logger
    private Logger logger;

    public ModbusSingleTemperatureSensorDriverInstance(ModbusNetwork network,
            String gatewayAddress, String gatewayPort, String gatewayProtocol,
            SerialParameters serialParams, BundleContext context,
            ServiceReference<Device> device)
    {
        super(network, gatewayAddress, gatewayPort, gatewayProtocol,
                serialParams, context, device);

        // create a logger
        this.logger = context
                .getService(context.getServiceReference(LoggerFactory.class))
                .getLogger(ModbusSingleTemperatureSensorDriverInstance.class);

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.domotics.model.devicecategory.SingleTemperatureSensor
     * #getTemperature()
     */
    @Override
    public Measure<?, ?> getTemperature()
    {
        return (Measure<?, ?>) this.currentState
                .getState(TemperatureState.class.getSimpleName())
                .getCurrentStateValue()[0].getValue();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.domotics.model.devicecategory.SingleTemperatureSensor
     * #deleteGroup(java.lang.String)
     */
    @Override
    public void deleteGroup(Integer groupID)
    {
        // nothing to do by now... will be handled in the future... may be...

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.domotics.model.devicecategory.SingleTemperatureSensor
     * #storeGroup(java.lang.String)
     */
    @Override
    public void storeGroup(Integer groupID)
    {
        // nothing to do by now... will be handled in the future... may be...

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.domotics.model.devicecategory.SingleTemperatureSensor
     * #notifyNewTemperatureValue(javax.measure.Measure)
     */
    @Override
    public void notifyNewTemperatureValue(Measure<?, ?> temperatureValue)
    {
        // update the state
        TemperatureStateValue tValue = new TemperatureStateValue();
        tValue.setValue(temperatureValue);
        this.currentState.setState(TemperatureState.class.getSimpleName(),
                new TemperatureState(tValue));

        // notify the new measure
        ((SingleTemperatureSensor) this.device)
                .notifyNewTemperatureValue(temperatureValue);

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.domotics.model.devicecategory.TemperatureSensor#getState
     * ()
     */
    @Override
    public DeviceStatus getState()
    {
        return this.currentState;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.polito.elite.dog.core.library.model.devicecategory.
     * SingleTemperatureSensor#updateStatus()
     */
    @Override
    public void updateStatus()
    {
        ((SingleTemperatureSensor) this.device).updateStatus();
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.polito.elite.dog.drivers.modbus.network.ModbusDriver#
     * newMessageFromHouse (it.polito.elite.dog.drivers.modbus.network.info
     * .ModbusRegisterInfo, java.lang.String)
     */
    @Override
    public void newMessageFromHouse(ModbusRegisterInfo register, Object value)
    {
        if (value != null && value instanceof DecimalMeasure)
        {
            // gets the corresponding notification set...
            Set<CNParameters> notificationInfos = this.register2Notification
                    .get(register);

            // handle the notifications
            for (CNParameters notificationInfo : notificationInfos)
            {
                // black magic here...
                String notificationName = notificationInfo.getName();

                // get the hypothetical class method name
                String notifyMethod = "notify"
                        + Character.toUpperCase(notificationName.charAt(0))
                        + notificationName.substring(1);

                // search the method and execute it
                try
                {
                    // log notification
                    this.logger.debug("Device: " + this.device.getDeviceId()
                            + " is notifying " + notificationName + " value:"
                            + value);
                    // get the method

                    Method notify = ModbusSingleTemperatureSensorDriverInstance.class
                            .getDeclaredMethod(notifyMethod, Measure.class);
                    // invoke the method
                    notify.invoke(this, value);
                }
                catch (Exception e)
                {
                    // log the error
                    this.logger.warn("Unable to find a suitable notification "
                            + "method for the datapoint: " + register + ":\n"
                            + e);
                }

                // notify the monitor admin
                this.updateStatus();
            }
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see it.polito.elite.dog.drivers.modbus.network.ModbusDriver#
     * specificConfiguration()
     */
    @Override
    protected void specificConfiguration()
    {
        // prepare the device state map
        this.currentState = new DeviceStatus(device.getDeviceId());

    }

    /*
     * (non-Javadoc)
     * 
     * @see it.polito.elite.dog.drivers.modbus.network.ModbusDriver#
     * addToNetworkDriver (it.polito.elite.dog.drivers.modbus.network.info.
     * ModbusRegisterInfo)
     */
    @Override
    protected void addToNetworkDriver(ModbusRegisterInfo register)
    {
        this.network.addDriver(register, this);
    }

    private void initializeStates()
    {
        // Since this driver handles the device metering according to a well
        // defined interface, we can get the unit of measure from all the
        // notifications handled by this device except from state notifications

        // add unit of measure aliases (to fix notation problems...)
        UnitFormat uf = UnitFormat.getInstance();
        uf.alias(SI.CELSIUS, "C");

        String temperatureUOM = SI.CELSIUS.toString();

        // search the energy unit of measures declared in the device
        // configuration
        for (ModbusRegisterInfo register : this.register2Notification.keySet())
        {
            Set<CNParameters> notificationInfos = this.register2Notification
                    .get(register);

            for (CNParameters notificationInfo : notificationInfos)
            {

                if (notificationInfo.getName().equalsIgnoreCase(
                        TemperatureMeasurementNotification.notificationName))
                {
                    temperatureUOM = register.getXlator().getUnitOfMeasure();
                }
            }
        }

        // create all the states
        TemperatureStateValue tValue = new TemperatureStateValue();
        tValue.setValue(DecimalMeasure.valueOf("0 " + temperatureUOM));
        this.currentState.setState(TemperatureState.class.getSimpleName(),
                new TemperatureState(tValue));
    }

    @Override
    public void notifyJoinedGroup(Integer groupNumber)
    {
        // intentionally left empty
    }

    @Override
    public void notifyLeftGroup(Integer groupNumber)
    {
        // intentionally left empty
    }

    @Override
    protected void setUpDevice(ControllableDevice device)
    {
        // prepare the device state map
        this.currentState = new DeviceStatus(device.getDeviceId());
        // TODO: get the initial state of the device....(states can be updated
        // by reading notification group addresses)
        this.initializeStates();

    }

}

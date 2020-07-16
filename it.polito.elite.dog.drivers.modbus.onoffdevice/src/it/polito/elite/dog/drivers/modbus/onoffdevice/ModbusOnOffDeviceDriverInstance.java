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
package it.polito.elite.dog.drivers.modbus.onoffdevice;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.device.Device;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import it.polito.elite.dog.core.library.model.ControllableDevice;
import it.polito.elite.dog.core.library.model.DeviceStatus;
import it.polito.elite.dog.core.library.model.devicecategory.Buzzer;
import it.polito.elite.dog.core.library.model.devicecategory.Controllable;
import it.polito.elite.dog.core.library.model.devicecategory.Lamp;
import it.polito.elite.dog.core.library.model.devicecategory.MainsPowerOutlet;
import it.polito.elite.dog.core.library.model.devicecategory.OnOffOutput;
import it.polito.elite.dog.core.library.model.devicecategory.SimpleLamp;
import it.polito.elite.dog.core.library.model.state.OnOffState;
import it.polito.elite.dog.core.library.model.state.State;
import it.polito.elite.dog.core.library.model.statevalue.OffStateValue;
import it.polito.elite.dog.core.library.model.statevalue.OnStateValue;
import it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;
import it.polito.elite.dog.drivers.modbus.network.interfaces.DeviceRemovalListener;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import net.wimpi.modbus.util.SerialParameters;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 */
public class ModbusOnOffDeviceDriverInstance extends ModbusDriverInstance
        implements Lamp, SimpleLamp, Buzzer, MainsPowerOutlet
{

    // the class logger
    private Logger logger;

    public ModbusOnOffDeviceDriverInstance(ModbusNetwork network,
            String gatewayAddress, String gatewayPort, String gatewayProtocol,
            SerialParameters serialParams, long requestTimeout, long requestGap,
            BundleContext context, ServiceReference<Device> device,
            DeviceRemovalListener listener)
    {
        super(network, gatewayAddress, gatewayPort, gatewayProtocol,
                serialParams, requestTimeout, requestGap, context, device,
                listener);

        // create a logger
        this.logger = context
                .getService(context.getServiceReference(LoggerFactory.class))
                .getLogger(ModbusOnOffDeviceDriverInstance.class);

    }

    private void initializeStates()
    {
        this.currentState.setState(OnOffState.class.getSimpleName(),
                new OnOffState(new OffStateValue()));

        // read the initial state (should be just one...)
        for (ModbusRegisterInfo register : this.register2Notification.keySet())
        {
            this.network.read(register, this);
        }
    }

    @Override
    public void storeScene(Integer sceneNumber)
    {
        // intentionally left empty
    }

    @Override
    public void deleteScene(Integer sceneNumber)
    {
        // intentionally left empty
    }

    @Override
    public void deleteGroup(Integer groupID)
    {
        // intentionally left empty
    }

    @Override
    public void storeGroup(Integer groupID)
    {
        // intentionally left empty
    }

    @Override
    public void on()
    {
        this.network.write(
                this.register2Notification.keySet().iterator().next(), "true");
    }

    @Override
    public void off()
    {
        this.network.write(
                this.register2Notification.keySet().iterator().next(), "false");
    }

    @Override
    public DeviceStatus getState()
    {
        return this.currentState;
    }

    @Override
    public void newMessageFromHouse(ModbusRegisterInfo dataPointInfo,
            Object value)
    {
        if ((value != null) && (value instanceof Boolean))
        {
            if ((Boolean) value)
            {
                this.changeCurrentState(OnOffState.ON);
            }
            else
            {
                this.changeCurrentState(OnOffState.OFF);
            }
        }

    }

    @Override
    protected void specificConfiguration()
    {
        // prepare the device state map
        this.currentState = new DeviceStatus(device.getDeviceId());
    }

    @Override
    protected void addToNetworkDriver(ModbusRegisterInfo register)
    {
        this.network.addDriver(register, this);
    }

    /**
     * Check if the current state has been changed or never set. In that case,
     * fire a state change message, otherwise it does nothing
     * 
     * @param OnOffValue
     *            OnOffState.ON or OnOffState.OFF
     */
    private void changeCurrentState(String OnOffValue)
    {
        // get the current state
        final State state = this.currentState
                .getState(OnOffState.class.getSimpleName());

        // no current state defined, the first run
        if (state == null)
        {
            updateCurrentState(OnOffValue);
        }
        else
        {
            final String currentStateValue = (String) state
                    .getCurrentStateValue()[0].getValue();

            // if the current states it is different from the new state
            if (!currentStateValue.equalsIgnoreCase(OnOffValue))
            {
                updateCurrentState(OnOffValue);
            }
        }
    }

    /**
     * Update the current state of driver instance and fire a state change
     * message
     * 
     * @param OnOffValue
     *            OnOffState.ON or OnOffState.OFF
     */
    private void updateCurrentState(String OnOffValue)
    {
        State newState;
        // set the new state to on or off...
        if (OnOffValue.equalsIgnoreCase(OnOffState.ON))
        {
            newState = new OnOffState(new OnStateValue());
            this.notifyOn();
        }
        else
        {
            newState = new OnOffState(new OffStateValue());
            this.notifyOff();
        }
        // ... then set the new state for the device and throw a status
        // update
        this.currentState.setState(newState.getStateName(), newState);

        // debug
        this.logger.debug("Device " + this.device.getDeviceId() + " is now "
                + ((OnOffState) newState).getCurrentStateValue()[0].getValue());

        this.updateStatus();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.dog.core.library.model.devicecategory.Lamp#notifyOn()
     */
    @Override
    public void notifyOn()
    {
        if (this.device instanceof Lamp)
        {
            ((Lamp) this.device).notifyOn();
        }
        else if (this.device instanceof Buzzer)
        {
            ((Buzzer) this.device).notifyOn();
        }
        else
            ((OnOffOutput) this.device).notifyOn();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.dog.core.library.model.devicecategory.Lamp#notifyOff()
     */
    @Override
    public void notifyOff()
    {
        if (this.device instanceof Lamp)
        {
            ((Lamp) this.device).notifyOff();
        }
        else if (this.device instanceof Buzzer)
        {
            ((Buzzer) this.device).notifyOff();
        }
        else
            ((OnOffOutput) this.device).notifyOff();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * it.polito.elite.dog.core.library.model.devicecategory.Lamp#updateStatus()
     */
    @Override
    public void updateStatus()
    {
        ((Controllable) this.device).updateStatus();
    }

    @Override
    public void notifyStoredScene(Integer sceneNumber)
    {
        // intentionally left empty
    }

    @Override
    public void notifyDeletedScene(Integer sceneNumber)
    {
        // intentionally left empty
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
        // read the initial state of devices
        this.initializeStates();
    }

}

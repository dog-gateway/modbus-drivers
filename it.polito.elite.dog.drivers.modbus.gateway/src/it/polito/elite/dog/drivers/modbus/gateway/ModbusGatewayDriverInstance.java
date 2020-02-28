/*
 * Dog - Gateway Driver
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
package it.polito.elite.dog.drivers.modbus.gateway;

import it.polito.elite.dog.core.library.model.ControllableDevice;
import it.polito.elite.dog.core.library.model.DeviceStatus;
import it.polito.elite.dog.core.library.model.devicecategory.ModbusGateway;
import it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import it.polito.elite.dog.drivers.modbus.network.protocol.NetworkError;
import net.wimpi.modbus.util.SerialParameters;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.device.Device;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 *
 */
public class ModbusGatewayDriverInstance extends ModbusDriverInstance
        implements ModbusGateway
{
    // the driver logger
    Logger logger;

    // the log identifier, unique for the class
    public static String logId = "[ModbusGatewayDriverInstance]: ";

    public ModbusGatewayDriverInstance(ModbusNetwork network,
            ServiceReference<Device> controllableDevice, String gatewayAddress,
            String gatewayPort, String protocolVariant,
            SerialParameters serialParameters, long requestTimeoutMillis,
            long requestGapMillis, BundleContext context)
    {
        super(network, gatewayAddress, gatewayPort, protocolVariant,
                serialParameters, requestTimeoutMillis, requestGapMillis,
                context, controllableDevice);

        // create a logger
        this.logger = context
                .getService(context.getServiceReference(LoggerFactory.class))
                .getLogger(ModbusGatewayDriverInstance.class);

    }

    @Override
    public synchronized DeviceStatus getState()
    {
        return this.currentState;
    }

    // getGatewayAddress already implemented by the superclass...

    @Override
    public void newMessageFromHouse(ModbusRegisterInfo registerInfo,
            Object string)
    {
        // currently no functionalities are associated to modbus gateways
        // therefore they do not use any datapoint and they do not listen to the
        // house messages...

        // just log
        this.logger.info(
                "Received new message from house involving the register:\n "
                        + registerInfo
                        + "\n No operation is currently supported");

    }

    @Override
    public void updateStatus()
    {
        // intentionally left empty

    }

    @Override
    protected void specificConfiguration()
    {
        // register the gateway driver for diagnostic notifications
        this.network.addGateway(this);
    }

    @Override
    protected void addToNetworkDriver(ModbusRegisterInfo register)
    {
        this.network.addDriver(register, this);

    }

    @Override
    protected void setUpDevice(ControllableDevice device)
    {
        // activate the device
        this.device.setDriver(this);
        // create a new device state (according to the current DogOnt model, no
        // state is actually associated to a Modbus gateway)
        this.currentState = new DeviceStatus(device.getDeviceId());
    }

    @Override
    protected void setReachable(ModbusRegisterInfo dataPointInfo,
            boolean reachable, NetworkError error)
    {
        // set the gateway status
        this.setDeviceOnline(reachable);
        // log
        this.logger.info("Gateway " + this.getGatewayIdentifier() + " is "
                + (reachable ? "reachable" : "not reachable"));
    }

}

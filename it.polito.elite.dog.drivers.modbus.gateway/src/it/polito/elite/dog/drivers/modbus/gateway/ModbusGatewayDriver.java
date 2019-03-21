/*
 * Dog - Gateway Driver
 * 
 * Copyright (c) 2012-2019 Dario Bonino
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
import it.polito.elite.dog.core.library.model.DeviceCostants;
import it.polito.elite.dog.core.library.model.devicecategory.ModbusGateway;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusInfo;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import it.polito.elite.dog.drivers.modbus.network.protocol.ModbusProtocolVariant;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.device.Device;
import org.osgi.service.device.Driver;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A class implementing the functionalities of a generic Modbus gateway, as
 * modeled in DogOnt. It offers ways to trace the number of currently managed
 * gateways and to access the corresponding slaves and registers, this permits
 * multiple-gateway operation in Dog. Currently no gateway-specific functions
 * are available, however in future releases functionalities offered by the real
 * devices will be modeled and implemented here.
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 */
public class ModbusGatewayDriver implements Driver
{

    // The OSGi framework context
    protected BundleContext context;

    // System logger
    Logger logger;

    // the log identifier, unique for the class
    public static String logId = "[ModbusGatewayDriver]: ";

    // a reference to the network driver (currently not used by this driver
    // version, in the future it will be used to implement gateway-specific
    // functionalities.
    private AtomicReference<ModbusNetwork> network;

    // a reference to the logger factory service used for logging
    private AtomicReference<LoggerFactory> loggerFactory;

    // the registration object needed to handle the life span of this bundle in
    // the OSGi framework (it is a ServiceRegistration object for use by the
    // bundle registering the service to update the service's properties or to
    // unregister the service).
    private ServiceRegistration<?> regDriver;

    // register this driver as a gateway used by device-specific drivers
    private ServiceRegistration<?> regModbusGateway;

    // the set of currently connected gateways... indexed by their ids
    private Map<String, ModbusGatewayDriverInstance> connectedGateways;

    public ModbusGatewayDriver()
    {
        // initialize the atomic reference to the network
        this.network = new AtomicReference<ModbusNetwork>();

        // initialize the atomic reference to the logger factory
        this.loggerFactory = new AtomicReference<LoggerFactory>();

        // initialize the map of connected gateways
        this.connectedGateways = new ConcurrentHashMap<String, ModbusGatewayDriverInstance>();
    }

    /**
     * The class constructor, builds a new instance of the ModbusGatewayDriver.
     * It tracks the services needed to enable the driver in the OSGi framework.
     * 
     * @param context
     *            The context for this bundle
     */
    public void activate(BundleContext context)
    {
        // init the logger
        this.logger.info("Modbus Gateway Driver activated!");
        // store the context
        this.context = context;

        if ((this.network.get() != null) && (this.regDriver == null))
            this.registerDriver();
    }

    public void deactivate()
    {
        // log deactivation
        this.logger.info(ModbusGatewayDriver.logId + " Deactivation required");

        this.unRegister();

        // null the inner data structures
        this.context = null;
        this.network = null;

    }

    public void unRegister()
    {
        // un-registers this driver

        if (this.regDriver != null)
        {
            this.regDriver.unregister();
            this.regDriver = null;
        }

        // un-register the gateway service
        if (this.regModbusGateway != null)
        {
            this.regModbusGateway.unregister();
            this.regModbusGateway = null;
        }

    }

    /**
     * Handle binding of the {@link ModbusNetwork} service needed to operate.
     * 
     * @param networkDriver
     *            The {@link ModbusNetwork} service available in the framework.
     */
    public void addedNetworkDriver(ModbusNetwork networkDriver)
    {
        this.network.set(networkDriver);

        if ((this.context != null) && (this.regDriver == null))
            this.registerDriver();
    }

    /**
     * Handle un-binding of the {@link ModbusNetwork} service needed to operate.
     * 
     * @param networkDriver
     *            The {@link ModbusNetwork} service that became unavailable.
     */
    public void removedNetworkDriver(ModbusNetwork networkDriver)
    {
        // unregisters this driver from the OSGi framework
        if (this.network.compareAndSet(networkDriver, null))
            this.unRegister();
    }

    /**
     * Handle binding of the {@link LoggerFactory} service needed to log
     * diagnostic messages.
     * 
     * @param loggerFactory
     *            The {@link LoggerFactory} instance available in the framework.
     */
    public void addedLoggerFactory(LoggerFactory loggerFactory)
    {
        // store the logger factory
        this.loggerFactory.set(loggerFactory);
        // create the logger
        this.logger = loggerFactory.getLogger(ModbusGatewayDriver.class);
    }

    /**
     * Handle un-binding of the {@link LoggerFactory} service needed to log
     * diagnostic messages.
     * 
     * @param loggerFactory
     *            The {@link LoggerFactory} service that became unavailable.
     */
    public void removedLoggerFactory(LoggerFactory loggerFactory)
    {
        if (this.loggerFactory.compareAndSet(loggerFactory, null))
        {
            this.logger = null;
        }
    }

    /**
     * Registers this driver in the OSGi framework, making its services
     * available to all the other bundles living in the same or in connected
     * frameworks.
     */
    private void registerDriver()
    {
        Hashtable<String, Object> propDriver = new Hashtable<String, Object>();
        propDriver.put(DeviceCostants.DRIVER_ID,
                ModbusGatewayDriver.class.getName());
        propDriver.put(DeviceCostants.GATEWAY_COUNT,
                this.connectedGateways.size());
        this.regDriver = this.context.registerService(Driver.class.getName(),
                this, propDriver);
        this.regModbusGateway = this.context.registerService(
                ModbusGatewayDriver.class.getName(), this, null);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public int match(ServiceReference reference) throws Exception
    {
        int matchValue = Device.MATCH_NONE;

        if (this.regDriver != null)
        {
            // get the given device category
            String deviceCategory = (String) reference
                    .getProperty(DeviceCostants.DEVICE_CATEGORY);

            // get the given device manufacturer
            String manufacturer = (String) reference
                    .getProperty(DeviceCostants.MANUFACTURER);

            // compute the matching score between the given device and this
            // driver
            if (deviceCategory != null)
            {
                if (manufacturer != null
                        && manufacturer.equals(ModbusInfo.MANUFACTURER)
                        && (deviceCategory.equals(ModbusGateway.class.getName())

                        ))
                {
                    matchValue = ModbusGateway.MATCH_MANUFACTURER
                            + ModbusGateway.MATCH_TYPE;
                }

            }
        }
        return matchValue;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public String attach(ServiceReference reference) throws Exception
    {
        if (this.regDriver != null)
        {
            // Get the device service to extract device information
            ControllableDevice device = ((ControllableDevice) this.context
                    .getService(reference));
            // get the device id
            String deviceId = device.getDeviceId();
            // get the device configuration (gateways only)
            ModbusGatewayInfo deviceInfo = ModbusGatewayInfo
                    .fromDescriptor(device.getDeviceDescriptor());
            // release the service
            this.context.ungetService(reference);

            // check not null
            if ((deviceInfo.getProtocolVariant()
                    .equals(ModbusProtocolVariant.RTU.toString())
                    && deviceInfo.getSerialParameters() != null)
                    || (!deviceInfo.getProtocolVariant()
                            .equals(ModbusProtocolVariant.RTU.toString())
                            && deviceInfo.getIpAddress() != null
                            && !deviceInfo.getIpAddress().isEmpty()))
            {

                // create a new instance of the gateway driver
                ModbusGatewayDriverInstance driver = new ModbusGatewayDriverInstance(
                        this.network.get(), reference,
                        deviceInfo.getIpAddress(), deviceInfo.getTcpPort(),
                        deviceInfo.getProtocolVariant(),
                        deviceInfo.getSerialParameters(), this.context);

                synchronized (this.connectedGateways)
                {
                    // store a reference to the gateway driver
                    this.connectedGateways.put(deviceId, driver);
                }

                // modify the service description causing a forcing the
                // framework to send a modified service notification
                final Hashtable<String, Object> propDriver = new Hashtable<String, Object>();
                propDriver.put(DeviceCostants.DRIVER_ID,
                        "Modbus_ModbusGateway_driver");
                propDriver.put(DeviceCostants.GATEWAY_COUNT,
                        this.connectedGateways.size());

                this.regDriver.setProperties(propDriver);

            }
            else
            {
                // do not attach, log and throw exception
                this.logger.warn(ModbusGatewayDriver.logId
                        + "Unable to get the current gateway address "
                        + "(empty set), this prevents the device "
                        + "from being attached!");
                throw new Exception(ModbusGatewayDriver.logId
                        + "Unable to get the current gateway address, "
                        + "this prevents the device from being attached!");
            }

        }
        return null;

    }

    /**
     * check if the gateway identified by the given gateway id is currently
     * registered with this driver
     * 
     * @param gatewayId
     * @return true if the gateway corresponding to the given id is already
     *         registered, false otherwise.
     */
    public boolean isGatewayAvailable(String gatewayId)
    {
        return this.connectedGateways.containsKey(gatewayId);
    }

    /**
     * Returns a live reference to the specific gateway driver instance
     * associated with the Modbus gateway device having the given id.
     * 
     * @param gatewayId
     * @return
     */
    public ModbusGatewayDriverInstance getSpecificGateway(String gatewayId)
    {
        return this.connectedGateways.get(gatewayId);
    }

    /**
     * @return the network
     */
    public ModbusNetwork getNetwork()
    {
        return this.network.get();
    }
}

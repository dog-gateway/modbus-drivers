package it.polito.elite.dog.drivers.modbus.device;

import it.polito.elite.dog.core.library.model.DeviceCostants;
import it.polito.elite.dog.core.library.model.devicecategory.Controllable;
import it.polito.elite.dog.drivers.modbus.gateway.ModbusGatewayDriver;
import it.polito.elite.dog.drivers.modbus.gateway.ModbusGatewayDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusInfo;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import net.wimpi.modbus.util.SerialParameters;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.device.Device;
import org.osgi.service.device.Driver;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Super-class fo all {@link ModbusDeviceDriver}s, implements the common
 * functions of a {@link ModbusDeviceDriver} thus simplifying as much as
 * possible the implementation of specific drivers.
 * 
 * @author bonino
 *
 */
public abstract class ModbusDeviceDriver implements Driver
{
    // The OSGi framework context
    protected BundleContext context;

    // The OSGi LogService logger
    protected Logger logger;

    // a reference to the network driver
    protected AtomicReference<ModbusNetwork> network;

    // a reference to the gateway driver
    protected AtomicReference<ModbusGatewayDriver> gateway;

    // a reference to the logger factory
    protected AtomicReference<LoggerFactory> loggerFactory;

    // the list of instances controlled / spawned by this driver
    // TODO: check if it makes sense
    protected Hashtable<String, ModbusDriverInstance> managedInstances;

    // the registration object needed to handle the life span of this bundle in
    // the OSGi framework (it is a ServiceRegistration object for use by the
    // bundle registering the service to update the service's properties or to
    // unregister the service).
    private ServiceRegistration<?> regDriver;

    // what are the device categories that can match with this driver?
    protected Set<String> deviceCategories;

    // the driver instance class from which extracting the supported device
    // categories
    protected Class<?> driverInstanceClass;

    /**
     * Class constructor, initializes atomic references and other inner
     * datastructures.
     */
    public ModbusDeviceDriver()
    {
        // initialize atomic references
        this.gateway = new AtomicReference<ModbusGatewayDriver>();
        this.network = new AtomicReference<ModbusNetwork>();
        this.loggerFactory = new AtomicReference<LoggerFactory>();

        // initialize the connected drivers list
        this.managedInstances = new Hashtable<String, ModbusDriverInstance>();

        // initialize the set of implemented device categories
        this.deviceCategories = new HashSet<String>();
    }

    /**
     * Handle the bundle activation
     */
    public void activate(BundleContext bundleContext)
    {
        // store the context
        this.context = bundleContext;

        // fill the device categories
        this.properFillDeviceCategories(this.driverInstanceClass);

        // register the driver
        this.registerModbusDeviceDriver();

    }

    /**
     * Handle bundle de-activation
     */
    public void deactivate()
    {
        this.unRegisterModbusDeviceDriver();
    }

    /**
     * Handle binding of the {@link ModbusNetwork} service.
     * 
     * @param network
     *            The {@link ModbusNetwork} service to bind.
     */
    public void addedNetworkDriver(ModbusNetwork network)
    {
        // log network river addition
        if (this.logger != null)
            this.logger.debug("Added network driver");

        // store the network driver reference
        this.network.set(network);

        // register if not yet registered
        this.registerModbusDeviceDriver();
    }

    /**
     * Handle un-binding of the {@link ModbusNetwork} service.
     * 
     * @param network
     *            The {@link ModbusNetwork} reference to remove.
     */
    public void removedNetworkDriver(ModbusNetwork network)
    {
        // null the network freeing the old reference for gc
        if (this.network.compareAndSet(network, null))
        {
            // unregister the services
            this.unRegisterModbusDeviceDriver();

            // log network river removal
            if (this.logger != null)
                this.logger.debug("Removed network driver");

        }
    }

    /**
     * Handle binding of the required {@link ModbusGatewayDriver} service.
     * 
     * @param gateway
     *            The {@link ModbusGatewayDriver} service reference.
     */
    public void addedGatewayDriver(ModbusGatewayDriver gateway)
    {
        // log network driver addition
        if (this.logger != null)
            this.logger.debug("Added gateway driver");

        // store the network driver reference
        this.gateway.set(gateway);

        // register if not yet registered
        this.registerModbusDeviceDriver();

    }

    /**
     * Handle un-binding of the required {@link ModbusGatewayDriver} service.
     * 
     * @param gateway
     *            The {@link ModbusDeviceDriver} service reference to remove.
     */
    public void removedGatewayDriver(ModbusGatewayDriver gateway)
    {
        // null the gateway freeing the old reference for gc
        if (this.gateway.compareAndSet(gateway, null))
        {
            // unregister the services
            this.unRegisterModbusDeviceDriver();
            // log network driver removal
            if (this.logger != null)
                this.logger.debug("Removed gateway driver");
        }
    }

    /**
     * Handle binding of required {@link LoggerFactory} service.
     * 
     * @param loggerFactory
     *            The {@link LoggerFactory} service reference.
     */
    public void addedLoggerFactory(LoggerFactory loggerFactory)
    {
        this.loggerFactory.set(loggerFactory);
        this.logger = loggerFactory.getLogger(ModbusDeviceDriver.class);
    }

    /**
     * Handle un-binding of required {@link LoggerFactory} service.
     * 
     * @param loggerFactory
     *            The {@link LoggerFactory} service reference to unbind.
     */
    public void removedLoggerFactory(LoggerFactory loggerFactory)
    {
        this.loggerFactory.compareAndSet(loggerFactory, null);
        this.logger = null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public int match(ServiceReference reference) throws Exception
    {
        // initial match is no match
        int matchValue = Device.MATCH_NONE;

        // get the given device category
        String deviceCategory = (String) reference
                .getProperty(DeviceCostants.DEVICE_CATEGORY);

        // get the given device manufacturer
        String manufacturer = (String) reference
                .getProperty(DeviceCostants.MANUFACTURER);

        // get the gateway to which the device is connected
        String gateway = (String) reference.getProperty(DeviceCostants.GATEWAY);

        // compute the matching score between the given device and
        // this driver
        if (deviceCategory != null)
        {
            if (manufacturer != null && (gateway != null)
                    && (manufacturer.equals(ModbusInfo.MANUFACTURER))
                    && (this.deviceCategories.contains(deviceCategory))
                    && (this.gateway.get() != null)
                    && (this.gateway.get().isGatewayAvailable(gateway)))
            {
                matchValue = Controllable.MATCH_MANUFACTURER
                        + Controllable.MATCH_TYPE;
            }

        }

        return matchValue;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public String attach(ServiceReference reference) throws Exception
    {
        // get the gateway to which the device is connected
        ModbusGatewayDriverInstance gatewayInstance = this.gateway.get()
                .getSpecificGateway(
                        (String) reference.getProperty(DeviceCostants.GATEWAY));

        // store the endpoint address for the attached device
        String gwAddress = gatewayInstance.getGatewayAddress();
        // store the port associated to the gateway address
        String gwPort = gatewayInstance.getGatewayPort();
        // store the protocol type for the gateway
        String gwProtocol = gatewayInstance.getGwProtocol();
        // store the serial parameters specified for the gateway, if any are
        // available.
        SerialParameters serialParameters = gatewayInstance
                .getSerialParameters();
        // the gateway-level request timeout
        long requestTimeout = gatewayInstance.getGatewayRequestTimeout();
        // the gateway-level request gap
        long requestGap = gatewayInstance.getGatewayRequestGap();

        ModbusDriverInstance driverInstance = this.createModbusDriverInstance(
                this.network.get(), gwAddress, gwPort, gwProtocol,
                serialParameters, requestTimeout, requestGap, this.context,
                reference);

        // store a reference to the connected driver
        synchronized (this.managedInstances)
        {
            this.managedInstances.put(
                    (String) reference.getProperty(DeviceCostants.DEVICEURI),
                    driverInstance);
        }

        return null;
    }

    public abstract ModbusDriverInstance createModbusDriverInstance(
            ModbusNetwork modbusNetwork, String gwAddress, String gwPort,
            String gwProtocol, SerialParameters serialParameters,
            long requestTimeout, long requestGap, BundleContext context,
            ServiceReference<Device> device);

    /**
     * Fill a set with all the device categories whose devices can match with
     * this driver. Automatically retrieve the device categories list by reading
     * the implemented interfaces of its DeviceDriverInstance class bundle.
     */
    public void properFillDeviceCategories(Class<?> cls)
    {
        if (cls != null)
        {
            for (Class<?> devCat : cls.getInterfaces())
            {
                this.deviceCategories.add(devCat.getName());
            }
        }

    }

    /**
     * Registers this driver in the OSGi framework making its services available
     * for all the other Dog bundles
     */
    private void registerModbusDeviceDriver()
    {
        if ((this.network.get() != null) && (this.gateway.get() != null)
                && (this.context != null) && (this.regDriver == null))
        {
            // create a new property object describing this driver
            Hashtable<String, Object> propDriver = new Hashtable<String, Object>();

            // add the id of this driver to the properties
            propDriver.put(DeviceCostants.DRIVER_ID, this.getClass().getName());

            // register this driver in the OSGi framework
            this.regDriver = this.context
                    .registerService(Driver.class.getName(), this, propDriver);
        }

    }

    /**
     * Handle the bundle de-activation
     */
    protected void unRegisterModbusDeviceDriver()
    {
        // TODO DETACH allocated Drivers
        if (regDriver != null)
        {
            regDriver.unregister();
            regDriver = null;
        }
    }

}

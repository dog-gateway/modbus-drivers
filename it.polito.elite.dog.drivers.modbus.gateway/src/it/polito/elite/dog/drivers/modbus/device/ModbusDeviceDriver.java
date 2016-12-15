package it.polito.elite.dog.drivers.modbus.device;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.device.Device;
import org.osgi.service.device.Driver;
import org.osgi.service.log.LogService;

import it.polito.elite.dog.core.library.model.ControllableDevice;
import it.polito.elite.dog.core.library.model.DeviceCostants;
import it.polito.elite.dog.core.library.model.devicecategory.Controllable;
import it.polito.elite.dog.core.library.util.LogHelper;
import it.polito.elite.dog.drivers.modbus.gateway.ModbusGatewayDriver;
import it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusInfo;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;

public abstract class ModbusDeviceDriver implements Driver
{
	// The OSGi framework context
	protected BundleContext context;

	// System logger
	protected LogHelper logger;

	// a reference to the network driver
	protected AtomicReference<ModbusNetwork> network;

	// a reference to the gateway driver
	protected AtomicReference<ModbusGatewayDriver> gateway;

	// the list of instances controlled / spawned by this driver
	protected Hashtable<String, ModbusDriverInstance> managedInstances;

	// the registration object needed to handle the life span of this bundle in
	// the OSGi framework (it is a ServiceRegistration object for use by the
	// bundle registering the service to update the service's properties or to
	// unregister the service).
	private ServiceRegistration<?> regDriver;

	// the filter query for listening to framework events relative to the
	// to the modbus gateway driver
	String filterQuery = String.format("(%s=%s)", Constants.OBJECTCLASS,
			ModbusGatewayDriver.class.getName());

	// what are the device categories that can match with this driver?
	protected Set<String> deviceCategories;

	// the driver instance class from which extracting the supported device
	// categories
	protected Class<?> driverInstanceClass;

	public ModbusDeviceDriver()
	{
		// initialize atomic references
		this.gateway = new AtomicReference<ModbusGatewayDriver>();
		this.network = new AtomicReference<ModbusNetwork>();

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
		// init the logger
		this.logger = new LogHelper(bundleContext);

		// store the context
		this.context = bundleContext;

		// fill the device categories
		this.properFillDeviceCategories(this.driverInstanceClass);

		// register the driver
		this.registerModbusDeviceDriver();

	}

	public void deactivate()
	{
		this.unRegisterModbusDeviceDriver();
	}

	public void addedNetworkDriver(ModbusNetwork network)
	{
		// log network river addition
		if (this.logger != null)
			this.logger.log(LogService.LOG_DEBUG, "Added network driver");

		// store the network driver reference
		this.network.set(network);

		// register if not yet registered
		this.registerModbusDeviceDriver();
	}

	public void removedNetworkDriver(ModbusNetwork network)
	{
		// null the network freeing the old reference for gc
		if (this.network.compareAndSet(network, null))
		{
			// unregister the services
			this.unRegisterModbusDeviceDriver();

			// log network river removal
			if (this.logger != null)
				this.logger.log(LogService.LOG_DEBUG, "Removed network driver");

		}
	}

	public void addedGatewayDriver(ModbusGatewayDriver gateway)
	{
		// log network driver addition
		if (this.logger != null)
			this.logger.log(LogService.LOG_DEBUG, "Added gateway driver");

		// store the network driver reference
		this.gateway.set(gateway);

		// register if not yet registered
		this.registerModbusDeviceDriver();

	}

	public void removedGatewayDriver(ModbusGatewayDriver gateway)
	{
		// null the gateway freeing the old reference for gc
		if (this.gateway.compareAndSet(gateway, null))
		{
			// unregister the services
			this.unRegisterModbusDeviceDriver();
			// log network driver removal
			if (this.logger != null)
				this.logger.log(LogService.LOG_DEBUG, "Removed gateway driver");
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public int match(ServiceReference reference) throws Exception
	{
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
		// get the referenced device
		ControllableDevice device = ((ControllableDevice) context
				.getService(reference));

		// check if not already attached
		if (!this.managedInstances.containsKey(device.getDeviceId()))
		{
			// get the gateway to which the device is connected
			String gateway = (String) device.getDeviceDescriptor().getGateway();

			ModbusDriverInstance driverInstance = this
					.createModbusDriverInstance(this.network.get(), device,
							this.gateway.get().getSpecificGateway(gateway)
									.getGatewayAddress(),
							this.gateway.get().getSpecificGateway(gateway)
									.getGatewayPort(),
							this.gateway.get().getSpecificGateway(gateway)
									.getGwProtocol(),
							this.context);
			
			// connect this driver instance with the device
			device.setDriver(driverInstance);

			// store a reference to the connected driver
			synchronized (this.managedInstances)
			{
				this.managedInstances.put(device.getDeviceId(), driverInstance);
			}
		}
		return null;
	}

	public abstract ModbusDriverInstance createModbusDriverInstance(
			ModbusNetwork modbusNetwork, ControllableDevice device,
			String gatewayAddress, String gatewayPort, String gwProtocol,
			BundleContext context);

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
		if ((this.network != null) && (this.gateway != null)
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

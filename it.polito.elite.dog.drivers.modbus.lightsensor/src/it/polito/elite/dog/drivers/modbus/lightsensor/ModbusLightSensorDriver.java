/*
 * Dog - Device Driver
 * 
 * Copyright (c) 2013 Dario Bonino
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
package it.polito.elite.dog.drivers.modbus.lightsensor;

import org.osgi.framework.BundleContext;

import it.polito.elite.dog.core.library.model.ControllableDevice;
import it.polito.elite.dog.drivers.modbus.device.ModbusDeviceDriver;
import it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;

/**
 * 
 * A class for representing the driver of a Modbus humidity sensor.
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 */
public class ModbusLightSensorDriver extends ModbusDeviceDriver
{

	/**
	 * The class constructor, creates an instance of the
	 * {@link ModbusLightSensorDriver} given the OSGi context to which the
	 * bundle must be linked.
	 * 
	 * @param context
	 * 
	 */
	public ModbusLightSensorDriver()
	{
		// call the superclass constructor
		super();

		// set the driver instance class
		this.driverInstanceClass = ModbusLightSensorDriverInstance.class;
	}

	@Override
	public ModbusDriverInstance createModbusDriverInstance(
			ModbusNetwork modbusNetwork, ControllableDevice device,
			String gatewayAddress, String gatewayPort, String gwProtocol,
			BundleContext context)
	{
		return new ModbusLightSensorDriverInstance(modbusNetwork, device,
				gatewayAddress, gatewayPort, gwProtocol, context);
	}

}

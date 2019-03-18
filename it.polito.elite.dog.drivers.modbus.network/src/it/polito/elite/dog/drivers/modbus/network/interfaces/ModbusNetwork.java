/*
 * Dog - Network Driver
 * 
 * Copyright (c) 2012-2013 Dario Bonino
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
package it.polito.elite.dog.drivers.modbus.network.interfaces;

import it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 *
 * @since Jan 18, 2012
 */
public interface ModbusNetwork
{
    /**
     * Read the current value associated to the given register...
     * 
     * @param register
     *            the register unique identifier.
     */
    public void read(ModbusRegisterInfo register, ModbusDriverInstance driver);

    /**
     * Writes a given command to a given Modbus register
     * 
     * @param register
     *            the register unique identifier.
     * @param commandValue
     *            the command value to send.
     */
    public void write(ModbusRegisterInfo register, String commandValue);

    /**
     * Adds a new device-specific driver for the given register
     * 
     * @param register
     *            the register unique identifier.
     * @param driver
     *            the {@link ModbusDriverInstance} instance to add.
     */
    public void addDriver(ModbusRegisterInfo register,
            ModbusDriverInstance driver);

    /**
     * Removes a device-specific driver for the given register
     * 
     * @param register
     *            the register unique identifier.
     * @param driver
     *            the {@link ModbusDriverInstance} instance to remove.
     */
    public void removeDriver(ModbusRegisterInfo register,
            ModbusDriverInstance driver);

    /**
     * Removes the driver-register associations for the given driver. To be
     * called when a specific driver disconnects
     * 
     * @param datapoint
     */
    public void removeDriver(ModbusDriverInstance driver);
}

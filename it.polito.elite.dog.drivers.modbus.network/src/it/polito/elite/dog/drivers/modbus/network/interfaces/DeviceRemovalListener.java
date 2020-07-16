/*
 * Dog 2.0 - Modbus Network Driver
 * 
 * Copyright [2012-2019] 
 * [Dario Bonino (dario.bonino@gmail.com)] 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package it.polito.elite.dog.drivers.modbus.network.interfaces;

/**
 * An interface specifying the contract for classes that must be notified about
 * the occurring removal of a device (by a driver-specific instance).
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Jul 16, 2020
 */
public interface DeviceRemovalListener
{
    /**
     * Notifies the listener that the device with the given URI has been removed
     * from the framework.
     * 
     * @param deviceUid
     *            The device unique identifier
     */
    public void removedDevice(String deviceUid);
}

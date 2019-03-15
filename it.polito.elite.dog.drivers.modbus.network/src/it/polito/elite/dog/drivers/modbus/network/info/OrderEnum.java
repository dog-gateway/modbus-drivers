/*
 * Dog 2.0 - Modbus Network Driver
 * 
 * Copyright [2012] 
 * [Dario Bonino (dario.bonino@polito.it), Politecnico di Torino] 
 * [Muhammad Sanaullah (muhammad.sanaullah@polito.it), Politecnico di Torino] 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package it.polito.elite.dog.drivers.modbus.network.info;

/**
 * An enumeration representing the byte (or word) order of a modbus register.
 * 
 * @author <a href="mailto:dario.bonino@gmail">Dario Bonino</a>
 *
 * @since Mar 14, 2019
 */
public enum OrderEnum
{
    LITTLE_ENDIAN("le"), BIG_ENDIAN("be");

    private String value;

    private OrderEnum(String value)
    {
        this.value = value;
    }

    public String toString()
    {
        return this.value;
    }

    /**
     * Create a {@link OrderEnum} value from a {@link String}.
     * 
     * @param text
     *            The {@link String} to convert.
     * @return The corresponding {@link OrderEnum} value or null.
     */
    public static OrderEnum fromValue(String text)
    {
        for (OrderEnum b : OrderEnum.values())
        {
            if (String.valueOf(b.value).equals(text))
            {
                return b;
            }
        }
        return null;
    }
}

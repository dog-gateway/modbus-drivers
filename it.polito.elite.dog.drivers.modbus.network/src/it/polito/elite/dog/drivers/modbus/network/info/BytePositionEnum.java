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
package it.polito.elite.dog.drivers.modbus.network.info;

/**
 * An enumeration representing a byte position within a single modbus register.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since May 26, 2020
 */
public enum BytePositionEnum
{
    HI("hi"), LOW("lo");

    private String value;

    private BytePositionEnum(String value)
    {
        this.value = value;
    }

    @Override
    public String toString()
    {
        return this.value;
    }

    /**
     * Converts a {@link String} to a {@link BytePositionEnum} value.
     * 
     * @param text
     *            The {@link String} to convert.
     * @return The converted {@link BytePositionEnum} value or null.
     */
    public static BytePositionEnum fromValue(String text)
    {
        for (BytePositionEnum b : BytePositionEnum.values())
        {
            if (String.valueOf(b.value).equals(text))
            {
                return b;
            }
        }
        return null;
    }

}

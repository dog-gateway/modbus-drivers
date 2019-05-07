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
 * An enumeration representing the type of a modbus register, either an input
 * register, an holding register, a coil
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Mar 14, 2019
 */
public enum RegisterTypeEnum
{
    INPUT_REGISTER("ir"),

    HOLDING_REGISTER("hr"),

    COIL("c"),

    DISCRETE_INPUT("di");

    private String value;

    private RegisterTypeEnum(String value)
    {
        this.value = value;
    }

    @Override
    public String toString()
    {
        return this.value;
    }

    /**
     * Create a {@link RegisterTypeEnum} from a {@link String}.
     * 
     * @param text
     *            The {@link String} to convert.
     * @return The corresponding {@link RegisterTypeEnum} value or null.
     */
    public static RegisterTypeEnum fromValue(String text)
    {
        for (RegisterTypeEnum b : RegisterTypeEnum.values())
        {
            if (String.valueOf(b.value).equals(text))
            {
                return b;
            }
        }
        return null;
    }
}

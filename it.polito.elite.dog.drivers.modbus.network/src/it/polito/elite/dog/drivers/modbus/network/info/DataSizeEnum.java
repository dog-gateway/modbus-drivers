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
 * An enumeration representing the data size of a modbus register.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Mar 14, 2019
 */
public enum DataSizeEnum
{
    BIT("bit", 16),

    INT16("int16", 16), UINT16("uint16", 16),

    INT32("int32", 32), UINT32("uint32", 32),

    INT48("int48", 48), UINT48("uint48", 48),

    INT64("int64", 64), UINT64("uint64", 64),

    FLOAT32("float32", 32), FLOAT64("float64", 64);

    private String value;
    private int nBits;
    private int nRegisters;

    private DataSizeEnum(String value, int nBits)
    {
        this.value = value;
        this.nBits = nBits;
        this.nRegisters = this.nBits / 16;
    }

    @Override
    public String toString()
    {
        return this.value;
    }

    /**
     * Provides the length of the register in bytes.
     * 
     * @return The length of the register in bytes.
     */
    public int getNBits()
    {
        return this.nBits;
    }

    /**
     * Provides the length of the register in "base register -16bit" units
     * 
     * @return The length of the register in 16bit blocks.
     */
    public int getNRegisters()
    {
        return this.nRegisters;
    }
    
    /**
     * Provides the length of the register in bytes.
     * @return The length of the register in bytes.
     */
    public int getNBytes()
    {
        return this.nRegisters*2;
    }

    /**
     * Converts a {@link String} to a {@link DataSizeEnum} value.
     * 
     * @param text
     *            The {@link String} to convert.
     * @return The converted {@link DataSizeEnum} value or null.
     */
    public static DataSizeEnum fromValue(String text)
    {
        for (DataSizeEnum b : DataSizeEnum.values())
        {
            if (String.valueOf(b.value).equals(text))
            {
                return b;
            }
        }
        return null;
    }
}

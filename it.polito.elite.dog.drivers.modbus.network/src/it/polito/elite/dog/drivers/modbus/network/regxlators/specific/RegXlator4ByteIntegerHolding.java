/*
 * Dog 2.0 - Modbus Network Driver
 * 
 * Copyright [2012] 
 * [Dario Bonino (dario.bonino@polito.it), Politecnico di Torino] 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package it.polito.elite.dog.drivers.modbus.network.regxlators.specific;

import it.polito.elite.dog.drivers.modbus.network.info.DataSizeEnum;
import it.polito.elite.dog.drivers.modbus.network.info.OrderEnum;
import it.polito.elite.dog.drivers.modbus.network.info.RegisterTypeEnum;
import it.polito.elite.dog.drivers.modbus.network.regxlators.BaseRegXlator;

/**
 * RegXlator for LITTLE ENDIAN INT32 Holding Registers (backward compatibility)
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>, Politecnico
 *         di Torino
 * @author <a href="mailto:muhammad.sanaullah@polito.it">Muhammad Sanaullah</a>,
 *         Politecnico di Torino
 * 
 * @since Feb 27, 2012
 */
public class RegXlator4ByteIntegerHolding extends BaseRegXlator
{

    public RegXlator4ByteIntegerHolding()
    {
        super(DataSizeEnum.INT32, RegisterTypeEnum.HOLDING_REGISTER,
                OrderEnum.BIG_ENDIAN, OrderEnum.LITTLE_ENDIAN, null, 0, null);
    }
}

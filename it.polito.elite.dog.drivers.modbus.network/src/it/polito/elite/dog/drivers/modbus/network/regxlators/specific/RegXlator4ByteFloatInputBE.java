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
package it.polito.elite.dog.drivers.modbus.network.regxlators.specific;

import it.polito.elite.dog.drivers.modbus.network.info.DataSizeEnum;
import it.polito.elite.dog.drivers.modbus.network.info.OrderEnum;
import it.polito.elite.dog.drivers.modbus.network.info.RegisterTypeEnum;
import it.polito.elite.dog.drivers.modbus.network.regxlators.BaseRegXlator;

/**
 * RegXlator for BIG ENDIAN FLOAT32 Input Registers (backward compatibility)
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 */
public class RegXlator4ByteFloatInputBE extends BaseRegXlator
{

    public RegXlator4ByteFloatInputBE()
    {
        super(DataSizeEnum.FLOAT32, RegisterTypeEnum.INPUT_REGISTER,
                OrderEnum.BIG_ENDIAN, OrderEnum.BIG_ENDIAN, null, 0, null);
    }
}

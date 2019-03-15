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
package it.polito.elite.dog.drivers.modbus.network.info;

import it.polito.elite.dog.core.library.model.ConfigurationConstants;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 *
 * @since Mar 1, 2012
 */
public class ModbusInfo extends ConfigurationConstants
{
    // for serial port connection the port name used
    public static final String PORT_NAME = "portName";
    // for the serial port connection the baud rate used
    public static final String BAUD_RATE = "baudRate";
    // for the serial port connection the data bits used
    public static final Object DATA_BITS = "dataBits";
    // for the serial port connection the parity used
    public static final Object PARITY = "parity";
    // for the serial port connection the stop bits used
    public static final Object STOP_BITS = "stopBits";
    // for the serial port connection the encoding used
    public static final Object ENCODING = "encoding";
    // for the serial port connection the indication if the echo is used or not
    public static final Object ECHO = "echo";
    // the manufacturer identifier (Modbus)
    public static final String MANUFACTURER = "Modbus";
    // the gateway address
    public static final String GATEWAY_ADDRESS = "IPAddress";
    // the gateway port
    public static final String GATEWAY_PORT = "port";
    // the gateway variant
    public static final String PROTO_ID = "protocolVariant";

    // register-specific parameters

    // the register address
    public static final String REGISTER_ADDRESS = "registerAddress";
    // the register type
    public static final String REGISTER_TYPE = "registerType";
    // the register size
    public static final String DATA_SIZE = "dataSize";
    // the order at the byte level, by default in Modbus is BIG ENDIAN
    public static final String BYTE_ORDER = "byteOrder";
    // the word order
    public static final String WORD_ORDER = "wordOrder";
    // the double word order
    public static final String DOUBLE_WORD_ORDER = "doubleWordOrder";
    // the bit position for BIT-type register
    public static final String BIT = "bit";
    // the slave identifier for the register
    public static final String SLAVE_ID = "slaveId";
    // the scale factor
    public static final String SCALE_FACTOR = "scaleFactor";
    // the unit of measure associated to the register value
    public static final String REGISTER_UOM = "unitOfMeasure";
    // the timeout to apply to the modbus request for a register
    public static final String REQUEST_TIMEOUT_MILLIS = "requestTimeout";
    // the minimum gap between two subsequent requests to the same register
    public static final String REQUEST_GAP_MILLIS = "requestGap";

}

/*
 * Dog - Gateway Driver
 * 
 * Copyright (c) 2012-2019 Dario Bonino
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

package it.polito.elite.dog.drivers.modbus.gateway;

import java.util.Set;

import it.polito.elite.dog.core.library.model.DeviceDescriptor;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusInfo;
import net.wimpi.modbus.util.SerialParameters;

/**
 * A class representing the configuration information of a ModbusGateway. Used
 * to parse and extract information about the gateway, e.g. the ip address, the
 * tcp port, any serial parameters, etc.
 * 
 * @author bonino
 *
 */
public class ModbusGatewayInfo
{
    private String ipAddress;
    private String tcpPort;
    private String protocol;
    private String protocolVariant;
    private SerialParameters serialParameters;
    private long requestTimeoutMillis;
    private long requestGapMillis;

    /**
     * Empty constructor, initializes required data structures.
     */
    public ModbusGatewayInfo()
    {
        // initialize the serial parameters
        this.serialParameters = new SerialParameters();
        // FIXME: use a well defined constant
        this.protocol = "modbus";
        // set default for request timeout
        this.requestTimeoutMillis = ModbusInfo.DEFAULT_REQUEST_TIMEOUT_MILLIS;
        this.requestGapMillis = ModbusInfo.DEFAULT_REQUEST_GAP_MILLIS;
    }

    /**
     * Build a {@link ModbusGatewayInfo} instance starting from a
     * {@link DeviceDescriptor} representing the gateway.
     * 
     * @param descriptor
     *            The {@link DeviceDescriptor}
     * @return The corresponding {@link ModbusGatewayInfo}.
     */
    public static ModbusGatewayInfo fromDescriptor(DeviceDescriptor descriptor)
    {
        // Initialize the ModbusGatewayInfo instance
        ModbusGatewayInfo info = new ModbusGatewayInfo();

        // get the corresponding end point set
        Set<String> gatewayAddressSet = descriptor
                .getSimpleConfigurationParams().get(ModbusInfo.GATEWAY_ADDRESS);

        Set<String> gatewayPortSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.GATEWAY_PORT);

        Set<String> gatewayProtocolSet = descriptor
                .getSimpleConfigurationParams().get(ModbusInfo.PROTO_ID);

        // Get the values for the parameters for the serial port (valid only in
        // case of serial connections
        Set<String> portNameSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.PORT_NAME);

        Set<String> baudRateSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.BAUD_RATE);

        Set<String> dataBitsSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.DATA_BITS);

        Set<String> paritySet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.PARITY);

        Set<String> stopBitsSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.STOP_BITS);

        Set<String> encodingSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.ENCODING);

        Set<String> echoSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.ECHO);

        Set<String> serialTimeoutSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.REQUEST_TIMEOUT_MILLIS);

        Set<String> requestGapSet = descriptor.getSimpleConfigurationParams()
                .get(ModbusInfo.REQUEST_GAP_MILLIS);

        // if not null, it is a singleton
        if (gatewayAddressSet != null)
        {
            // get the endpoint address of the connecting gateway
            info.setIpAddress(gatewayAddressSet.iterator().next());

            // get the gateway port if exists
            if ((gatewayPortSet != null) && (!gatewayPortSet.isEmpty()))
                info.setTcpPort(gatewayPortSet.iterator().next());

        }

        // get the gateway protocol if exists
        if ((gatewayProtocolSet != null) && (!gatewayProtocolSet.isEmpty()))
            info.setProtocolVariant(gatewayProtocolSet.iterator().next());

        // get the request gap if exists
        String requestGap = null;
        if ((requestGapSet != null) && (!requestGapSet.isEmpty()))
        {
            requestGap = requestGapSet.iterator().next();
            // parse the timeout
            try
            {
                long gap = Long.valueOf(requestGap.trim());
                info.setRequestGapMillis(gap);
            }
            catch (NumberFormatException nfe)
            {
                // do nothing, leave the default
            }
        }

        // get the serial timeout if exists
        String serialTimeout = null;
        if ((serialTimeoutSet != null) && (!serialTimeoutSet.isEmpty()))
        {
            serialTimeout = serialTimeoutSet.iterator().next();

            // parse the timeout
            try
            {
                long timeout = Long.valueOf(serialTimeout.trim());
                info.setRequestTimeoutMillis(timeout);
            }
            catch (NumberFormatException nfe)
            {
                // do nothing, use the default.
            }
        }

        // Only if the port name is indicated (for serial port connections)
        // the other serial parameters are verified
        if ((portNameSet != null) && (!portNameSet.isEmpty()))
        {
            info.getSerialParameters()
                    .setPortName(portNameSet.iterator().next());

            // get the baud rate if exists
            String baudRate = ModbusGatewayDefault.DEFAULT_BAUD_RATE;
            if ((baudRateSet != null) && (!baudRateSet.isEmpty()))
            {
                baudRate = baudRateSet.iterator().next();
            }
            info.getSerialParameters().setBaudRate(baudRate);

            // get the data bits if exists
            String dataBits = ModbusGatewayDefault.DEFAULT_DATA_BITS;
            if ((dataBitsSet != null) && (!dataBitsSet.isEmpty()))
            {
                dataBits = dataBitsSet.iterator().next();
            }
            info.getSerialParameters().setDatabits(dataBits);

            // get the parity if exists
            String parity = ModbusGatewayDefault.DEFAULT_PARITY;
            if ((paritySet != null) && (!paritySet.isEmpty()))
            {
                parity = paritySet.iterator().next();
            }
            info.getSerialParameters().setParity(parity);

            // get the stop bits if exists
            String stopBits = ModbusGatewayDefault.DEFAULT_STOP_BITS;
            if ((stopBitsSet != null) && (!stopBitsSet.isEmpty()))
            {
                stopBits = stopBitsSet.iterator().next();
            }
            info.getSerialParameters().setStopbits(stopBits);

            // get the encoding if exists
            String encoding = ModbusGatewayDefault.DEFAULT_ENCODING;
            if ((encodingSet != null) && (!encodingSet.isEmpty()))
            {
                encoding = encodingSet.iterator().next();
            }
            info.getSerialParameters().setEncoding(encoding);

            // get the echo if exists
            String echo = ModbusGatewayDefault.DEFAULT_ECHO;
            if ((echoSet != null) && (!echoSet.isEmpty()))
            {
                echo = echoSet.iterator().next();
            }
            info.getSerialParameters().setEcho(Boolean.parseBoolean(echo));

            // set the serial timeout
            info.getSerialParameters()
                    .setReceiveTimeout((int) info.getRequestTimeoutMillis());

        }

        return info;
    }

    /**
     * Provide the ip address of the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @return the ipAddress The ip address of the gateway as a {@link String}.
     */
    public String getIpAddress()
    {
        return ipAddress;
    }

    /**
     * Set the ip address of the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @param ipAddress
     *            The ip address of the gateway as a {@link String}.
     */
    public void setIpAddress(String ipAddress)
    {
        this.ipAddress = ipAddress;
    }

    /**
     * Provide the TCP port of the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @return the tcpPort The port of the gateway as a {@link String}
     *         containing a number betwen 0 and 65535, default 502.
     */
    public String getTcpPort()
    {
        return tcpPort;
    }

    /**
     * Set the TCP port of the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @param tcpPort
     *            The port of the gateway as a {@link String} containing a
     *            number betwen 0 and 65535, default 502.
     */
    public void setTcpPort(String tcpPort)
    {
        this.tcpPort = tcpPort;
    }

    /**
     * Set the protocol used by the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @return the protocol The protocol as a {@link String}.
     */
    public String getProtocol()
    {
        return protocol;
    }

    /**
     * Get the protocol used by the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @param protocol
     *            The protocol as a {@link String}.
     */
    public void setProtocol(String protocol)
    {
        this.protocol = protocol;
    }

    /**
     * Set the protocol variant used by the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @return The protocol variant as a {@link String}.
     */
    public String getProtocolVariant()
    {
        return protocolVariant;
    }

    /**
     * Get the protocol variant used by the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @param protocolVariant
     *            The protocol variant as a {@link String}.
     */
    public void setProtocolVariant(String protocolVariant)
    {
        this.protocolVariant = protocolVariant;
    }

    /**
     * Get any serial parameter associated to the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @return the serialParameters The set of serial port parameters as a
     *         {@link SerialParameters} instance.
     */
    public SerialParameters getSerialParameters()
    {
        return serialParameters;
    }

    /**
     * Set any serial parameter associated to the gateway represented by this
     * {@link ModbusGatewayInfo} instance.
     * 
     * @param serialParameters
     *            The set of serial port parameters as a
     *            {@link SerialParameters} instance.
     */
    public void setSerialParameters(SerialParameters serialParameters)
    {
        this.serialParameters = serialParameters;
    }

    /**
     * Gets the gap between subsequent requests, defined at the gateway level.
     * 
     * @return the requestGapMillis
     */
    public long getRequestGapMillis()
    {
        return requestGapMillis;
    }

    /**
     * Sets the gap between subsequent requests, defined at the gateway level.
     * 
     * @param requestGapMillis
     *            the requestGapMillis to set
     */
    public void setRequestGapMillis(long requestGapMillis)
    {
        this.requestGapMillis = requestGapMillis;
    }

    /**
     * Get the request timeout defined at the gateway level.
     * 
     * @return the requestTimeoutMillis
     */
    public long getRequestTimeoutMillis()
    {
        return requestTimeoutMillis;
    }

    /**
     * Sets the request timeout defined at the gateway level.
     * 
     * @param requestTimeoutMillis
     *            the requestTimeoutMillis to set
     */
    public void setRequestTimeoutMillis(long requestTimeoutMillis)
    {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

}

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

import it.polito.elite.dog.drivers.modbus.network.protocol.ModbusProtocolVariant;
import it.polito.elite.dog.drivers.modbus.network.regxlators.BaseRegXlator;
import it.polito.elite.dog.drivers.modbus.network.regxlators.RegXlatorTypes;
import net.wimpi.modbus.util.SerialParameters;

import java.net.InetAddress;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 * @since Jan 13, 2012
 */
public class ModbusRegisterInfo implements Comparable<ModbusRegisterInfo>
{
    // the IP address of the gateway to which a specific device is connected
    private InetAddress gatewayIPAddress;

    // the modbus port exposed by the gateway
    private String gatewayPort;

    // the serial parameters for the RTU devices
    private SerialParameters serialParameters;

    // the protocol variant used by the gateway
    private ModbusProtocolVariant gatewayProtocol;

    // the slave ID of a specific device when connected to the given gateway
    private int slaveId;

    // the register address
    private int address;

    // the register xlator
    private BaseRegXlator xlator;

    // the request timeout defined for the register
    private long requestTimeoutMillis;
    // the minimum gap between requests to the same register
    private long requestGapMillis;

    /**
     * Empty class constructor, implements the bean pattern.
     */
    public ModbusRegisterInfo()
    {
        // set default
        this.requestGapMillis = ModbusInfo.DEFAULT_REQUEST_GAP_MILLIS;
        this.requestTimeoutMillis = ModbusInfo.DEFAULT_REQUEST_TIMEOUT_MILLIS;
    }

    /**
     * The class constructor, given a register address and type, creates a new
     * register instance.
     * 
     * @param address
     *            the register address
     */
    public ModbusRegisterInfo(int address, int typeId)
    {
        // store the address
        this.address = address;

        // create the suitable xlator to handle translations to/from this
        // register
        this.xlator = RegXlatorTypes.createRegXlator(typeId);

    }

    /**
     * Provides the address of the represented Modbus register
     * 
     * @return the address
     */
    public int getAddress()
    {
        return address;
    }

    /**
     * Sets the address of the modbus register represented by this instance
     * 
     * @param address
     *            the address to set
     */
    public void setAddress(int address)
    {
        this.address = address;
    }

    /**
     * Get the IP address of the gateway handling this register (device
     * connected to the gateway and abstracted as a register value)
     * 
     * @return the gatewayIPAddress
     */
    public InetAddress getGatewayIPAddress()
    {
        return gatewayIPAddress;
    }

    /**
     * Set the IP address of the gateway handling this register (device
     * connected to the gateway and abstracted as a register value)
     * 
     * @param gatewayIPAddress
     *            the gatewayIPAddress to set
     */
    public void setGatewayIPAddress(InetAddress gatewayIPAddress)
    {
        this.gatewayIPAddress = gatewayIPAddress;
    }

    /**
     * Get the SlaveId of the device connected to the gateway and abstracted as
     * a register value
     * 
     * @return the slaveId
     */
    public int getSlaveId()
    {
        return slaveId;
    }

    /**
     * Set the SlaveId of the device connected to the gateway and abstracted as
     * a register value
     * 
     * @param slaveId
     *            the slaveId to set
     */
    public void setSlaveId(int slaveId)
    {
        this.slaveId = slaveId;
    }

    /**
     * @return the xlator
     */
    public BaseRegXlator getXlator()
    {
        return xlator;
    }

    /**
     * @param xlator
     *            the xlator to set
     */
    public void setXlator(BaseRegXlator xlator)
    {
        this.xlator = xlator;
    }

    /**
     * 
     * @param gwPort
     */
    public void setGatewayPort(String gwPort)
    {
        this.gatewayPort = gwPort;

    }

    /**
     * @return the gatewayPort
     */
    public String getGatewayPort()
    {
        return gatewayPort;
    }

    /**
     * @return the gatewayProtocol
     */
    public ModbusProtocolVariant getGatewayProtocol()
    {
        return gatewayProtocol;
    }

    /**
     * @param gatewayProtocol
     *            the gatewayProtocol to set
     */
    public void setGatewayProtocol(String gatewayProtocol)
    {
        this.gatewayProtocol = ModbusProtocolVariant.valueOf(gatewayProtocol);
    }

    /**
     * @return the serialParameters
     */
    public SerialParameters getSerialParameters()
    {
        return serialParameters;
    }

    /**
     * @param serialParameters
     *            the serialParameters to set
     */
    public void setSerialParameters(SerialParameters serialParameters)
    {
        this.serialParameters = serialParameters;
    }

    /**
     * Provides the timeout to wait for a response for this specific register.
     * 
     * @return the requestTimeoutMillis The timeout in milliseconds.
     */
    public long getRequestTimeoutMillis()
    {
        return requestTimeoutMillis;
    }

    /**
     * Sets the timeout to wait for a response for this specific register.
     * 
     * @param requestTimeoutMillis
     *            The timeout in milliseconds.
     */
    public void setRequestTimeoutMillis(long requestTimeoutMillis)
    {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    /**
     * Provides the minimum gap that shall be preserved between two subsequent
     * requests to the register.
     * 
     * @return the minimum gap between 2 subsequent requests to the same
     *         register, in milliseconds.
     */
    public long getRequestGapMillis()
    {
        return requestGapMillis;
    }

    /**
     * Sets the minimum gap that shall be preserved between two subsequent
     * requests to the register.
     * 
     * @param requestGapMillis
     *            the minimum gap between 2 subsequent requests to the same
     *            register, in milliseconds.
     */
    public void setRequestGapMillis(long requestGapMillis)
    {
        this.requestGapMillis = requestGapMillis;
    }

    /**
     * Get a unique identifier for the gateway to which "belongs" the register
     * represented by this modbus register info.
     * 
     * @return
     */
    public String getGatewayIdentifier()
    {
        String identifier = null;

        switch (this.gatewayProtocol)
        {
            case TCP:
            {
                identifier = "tcp://" + this.gatewayIPAddress.getHostAddress()
                        + ":" + this.gatewayPort;
                break;
            }
            case RTU:
            {
                identifier = "rtu://" + this.serialParameters.getPortName();
                break;
            }
            case RTU_TCP:
            {
                identifier = "rtu_tcp://"
                        + this.gatewayIPAddress.getHostAddress() + ":"
                        + this.gatewayPort;
                break;
            }
            case RTU_UDP:
            {
                identifier = "rtu_udp://"
                        + this.gatewayIPAddress.getHostAddress() + ":"
                        + this.gatewayPort;
                break;
            }
            default:
            {
                // null
                identifier = null;
                break;
            }
        }

        return identifier;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + address;
        result = prime * result + ((gatewayIPAddress == null) ? 0
                : gatewayIPAddress.hashCode());
        result = prime * result
                + ((gatewayPort == null) ? 0 : gatewayPort.hashCode());
        result = prime * result
                + ((gatewayProtocol == null) ? 0 : gatewayProtocol.hashCode());
        result = prime * result
                + (int) (requestGapMillis ^ (requestGapMillis >>> 32));
        result = prime * result
                + (int) (requestTimeoutMillis ^ (requestTimeoutMillis >>> 32));
        result = prime * result + ((serialParameters == null) ? 0
                : serialParameters.hashCode());
        result = prime * result + slaveId;
        result = prime * result + ((xlator == null) ? 0 : xlator.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ModbusRegisterInfo other = (ModbusRegisterInfo) obj;
        if (address != other.address)
            return false;
        if (gatewayIPAddress == null)
        {
            if (other.gatewayIPAddress != null)
                return false;
        }
        else if (!gatewayIPAddress.equals(other.gatewayIPAddress))
            return false;
        if (gatewayPort == null)
        {
            if (other.gatewayPort != null)
                return false;
        }
        else if (!gatewayPort.equals(other.gatewayPort))
            return false;
        if (gatewayProtocol != other.gatewayProtocol)
            return false;
        if (requestGapMillis != other.requestGapMillis)
            return false;
        if (requestTimeoutMillis != other.requestTimeoutMillis)
            return false;
        if (serialParameters == null)
        {
            if (other.serialParameters != null)
                return false;
        }
        else if (!serialParameters.equals(other.serialParameters))
            return false;
        if (slaveId != other.slaveId)
            return false;
        if (xlator == null)
        {
            if (other.xlator != null)
                return false;
        }
        else if (!xlator.equals(other.xlator))
            return false;
        return true;
    }

    /**
     * Check if this modbus register info is empty.
     * 
     * @return True if it is empty, false otherwise.
     */
    public boolean isEmpty()
    {
        return !(((this.gatewayIPAddress != null && this.gatewayPort != null)
                || (this.serialParameters != null)) && (this.address >= 0)
                && (this.slaveId > 0) && (this.xlator != null));
    }

    @Override
    public int compareTo(ModbusRegisterInfo registerInfo)
    {
        int comparisonValue = -1;

        if (this.equals(registerInfo))
        {
            comparisonValue = 0;
        }
        else
        {
            if (this.slaveId > registerInfo.getSlaveId())
            {
                comparisonValue = +1;
            }
            else if (this.slaveId < registerInfo.getSlaveId())
            {
                comparisonValue = -1;
            }
            else if (this.slaveId == registerInfo.getSlaveId())
            {
                if (this.address > registerInfo.getAddress())
                {
                    comparisonValue = +1;
                }
                else
                {
                    comparisonValue = -1;
                }
            }
        }
        return comparisonValue;
    }

    public ModbusRegisterInfo clone()
    {
        ModbusRegisterInfo clone = new ModbusRegisterInfo();
        clone.address = this.address;
        clone.gatewayIPAddress = this.gatewayIPAddress;
        clone.gatewayPort = this.gatewayPort;
        clone.gatewayProtocol = this.gatewayProtocol;
        clone.requestGapMillis = this.requestGapMillis;
        clone.requestTimeoutMillis = this.requestTimeoutMillis;
        clone.slaveId = this.slaveId;
        clone.xlator = this.xlator.clone();
        clone.serialParameters = this.serialParameters;
        return clone;
    }

    /**
     * Checks if the current instance of register info is valid and can be used
     * or not.
     * 
     * @return
     */
    public boolean isValid()
    {
        return this.address >= 0 && this.slaveId >= 0 && this.xlator != null
                && this.gatewayProtocol != null
                && (this.serialParameters != null
                        || (this.gatewayIPAddress != null
                                && this.gatewayPort != null));
    }
}

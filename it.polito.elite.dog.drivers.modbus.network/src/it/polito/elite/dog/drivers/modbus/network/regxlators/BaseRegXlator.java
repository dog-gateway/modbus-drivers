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
package it.polito.elite.dog.drivers.modbus.network.regxlators;

import java.nio.ByteBuffer;

import org.osgi.service.log.Logger;

import it.polito.elite.dog.drivers.modbus.network.info.DataSizeEnum;
import it.polito.elite.dog.drivers.modbus.network.info.OrderEnum;
import it.polito.elite.dog.drivers.modbus.network.info.RegisterTypeEnum;
import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ModbusResponse;
import net.wimpi.modbus.msg.ReadCoilsRequest;
import net.wimpi.modbus.msg.ReadCoilsResponse;
import net.wimpi.modbus.msg.ReadInputDiscretesRequest;
import net.wimpi.modbus.msg.ReadInputDiscretesResponse;
import net.wimpi.modbus.msg.ReadInputRegistersRequest;
import net.wimpi.modbus.msg.ReadInputRegistersResponse;
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest;
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse;
import net.wimpi.modbus.msg.WriteCoilRequest;
import net.wimpi.modbus.msg.WriteMultipleRegistersRequest;
import net.wimpi.modbus.procimg.InputRegister;
import net.wimpi.modbus.procimg.Register;
import net.wimpi.modbus.procimg.SimpleRegister;

/**
 * A generic RegXlator able to handle almost all register types.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Mar 14, 2019
 */
public class BaseRegXlator
{
    // the register size as enum
    protected DataSizeEnum registerSize;
    // the register type as enum
    protected RegisterTypeEnum registerType;
    // the byte order as enum
    protected OrderEnum byteOrder;
    // the word order as enum
    protected OrderEnum wordOrder;
    // the double word order as enum
    protected OrderEnum doubleWordOrder;
    // the bit to extract (valid only for bit registers)
    protected int bit = -1; // default

    /**
     * The scale factor with which the actual modbus register value shall be
     * scaled before providing/setting the value into a
     * {@link ModbusRegisterInfo} instance. By default it is equal to 1.0.
     */
    protected double scaleFactor = 1.0;

    /**
     * The unit of Measure associated to the values translated by an instance of
     * a {@link RegXlator} subclass.
     */
    protected String unitOfMeasure;

    // the endianness translation map for 64bit values
    // le = 0, be = 1
    // dbwe we
    // 0 0 3210
    // 0 1 2301
    // 1 0 1032
    // 1 1 0123
    protected int[][] endianess64bit = { { 3, 2, 1, 0 }, { 2, 3, 0, 1 },
            { 1, 0, 3, 2 }, { 0, 1, 2, 3 } };
    // the endianness translation map for 32bit values
    // le = 0, be = 1
    // we
    // 0 10
    // 1 01
    protected int[][] endianess32bit = { { 1, 0 }, { 0, 1 } };

    // the class logger
    private Logger logger;

    /**
     * Create a new instance of regXlator given the register description
     * parameters.
     * 
     * @param registerSize
     *            The register size as a {@link DataSizeEnum} instance.
     * @param registerType
     *            The register type as a {@link RegisterTypeEnum} instance.
     * @param byteOrder
     *            The byte order within the register as a {@link OrderEnum}
     *            instance.
     * @param wordOrder
     *            The word order within the register (valid only for register
     *            sizes >= 32) as an {@link OrderEnum} instance.
     * @param doubleWordOrder
     *            The order of double words composing a 64bit register, as an
     *            {@link OrderEnum} instance.
     * @param bit
     *            The position of the BIT to read/write, if negative (-1) shall
     *            be ignored.
     */
    public BaseRegXlator(DataSizeEnum registerSize,
            RegisterTypeEnum registerType, OrderEnum byteOrder,
            OrderEnum wordOrder, OrderEnum doubleWordOrder, int bit)
    {
        super();
        this.registerSize = registerSize;
        this.registerType = registerType;
        this.byteOrder = byteOrder;
        this.wordOrder = wordOrder;
        this.doubleWordOrder = doubleWordOrder;
        this.bit = bit;
    }

    /**
     * Returns the value of the given response, interpreted according to the
     * parameters set for this regXLator instance.
     * 
     * @param response
     *            The response to "parse".
     * @return The value of the response as a number with an attached unit of
     *         measure, if specified as a property of this regXkator
     */
    public String getValue(ModbusResponse response)
    {
        // the value as a String
        String value = null;

        // check if a response is available for extracting the value
        if (response != null)
        {
            // handle the response type
            switch (this.registerType)
            {
                case DISCRETE_INPUT:
                {
                    if (response instanceof ReadInputDiscretesResponse)
                    {
                        value = this.getInputDiscreteValue(
                                (ReadInputDiscretesResponse) response);
                    }
                    break;
                }
                case COIL:
                {
                    if (response instanceof ReadCoilsResponse)
                    {
                        value = this.getCoilValue((ReadCoilsResponse) response);
                    }
                    break;
                }
                case INPUT_REGISTER:
                {
                    if (response instanceof ReadInputRegistersResponse)
                    {
                        value = this.getInputRegisterValue(
                                (ReadInputRegistersResponse) response);
                    }
                    break;
                }
                case HOLDING_REGISTER:
                {
                    if (response instanceof ReadMultipleRegistersResponse)
                    {
                        value = this.getHoldingRegisterValue(
                                (ReadMultipleRegistersResponse) response);
                    }
                    break;
                }
                default:
                {
                    // do nothing
                    break;
                }

            }
        }

        // return the extracted value as string
        return value;
    }

    public ModbusRequest getWriteRequest(int address, String value)
    {
        return this.getWriteRequest(address, value, null);
    }

    /**
     * Prepare a write request for the register having the given address. Allows
     * specifying the value to write in the register and, for BIT register only,
     * allows to specify the value of the register where the bit value shall be
     * applied.
     * 
     * @param address
     *            The address of the register
     * @param value
     *            The value to assign to the register as a {@link String}. It
     *            will be converted to the right type depending on the regxlator
     *            parameters.
     * @param oldValue
     *            The value of the register to which shall be applied the BIT
     *            value specified in the
     * 
     *            <pre>
     *            value
     * 
     *            <pre>
     *            parameter of this method. Only used in BIT registers.
     * @return The write request to use on the modbus communication stack.
     */
    public ModbusRequest getWriteRequest(int address, String value,
            String oldValue)
    {
        // the request, initially null
        ModbusRequest request = null;

        switch (this.registerType)
        {
            case COIL:
            {
                request = new WriteCoilRequest(address, Boolean.valueOf(value));
                break;
            }
            case DISCRETE_INPUT:
            case INPUT_REGISTER:
            {
                // do nothing: writing is not supported on any INPUT register
                break;
            }
            case HOLDING_REGISTER:
            {
                Register[] registers = this.toRegisters(value, oldValue);
                if (registers != null)
                {
                    request = new WriteMultipleRegistersRequest(address,
                            registers);
                }
                break;
            }
        }

        return request;
    }

    public ModbusRequest getReadRequest(int address)
    {
        // the request, initially null
        ModbusRequest request = null;

        // generate a request tailored to the register type
        switch (this.registerType)
        {
            case COIL:
            {
                request = new ReadCoilsRequest(address, 1);
                break;
            }
            case DISCRETE_INPUT:
            {
                request = new ReadInputDiscretesRequest(address, 1);
                break;
            }
            case HOLDING_REGISTER:
            {
                request = new ReadMultipleRegistersRequest(address,
                        this.registerSize.getNRegisters());
                break;
            }
            case INPUT_REGISTER:
            {
                request = new ReadInputRegistersRequest(address,
                        this.registerSize.getNRegisters());
            }

        }

        // return the request
        return request;
    }

    /**
     * Provides the size of the register as a {@link DataSizeEnum} instance.
     * 
     * @return the registerSize
     */
    public DataSizeEnum getRegisterSize()
    {
        return registerSize;
    }

    /**
     * Provides the type of register as a {@link RegisterTypeEnum} instance.
     * 
     * @return the registerType
     */
    public RegisterTypeEnum getRegisterType()
    {
        return registerType;
    }

    /**
     * @return the scaleFactor
     */
    public double getScaleFactor()
    {
        return scaleFactor;
    }

    /**
     * @return the unitOfMeasure
     */
    public String getUnitOfMeasure()
    {
        return unitOfMeasure;
    }

    /**
     * @param scaleFactor
     *            the scaleFactor to set
     */
    public void setScaleFactor(double scaleFactor)
    {
        this.scaleFactor = scaleFactor;
    }

    /**
     * @param unitOfMeasure
     *            the unitOfMeasure to set
     */
    public void setUnitOfMeasure(String unitOfMeasure)
    {
        this.unitOfMeasure = unitOfMeasure;
    }

    /**
     * Get the logger currently assigned to this regxlator.
     * 
     * @return the logger
     */
    public Logger getLogger()
    {
        return this.logger;
    }

    /**
     * Set the logger for this regxlator.
     * 
     * @param logger
     *            the logger to set
     */
    public void setLogger(Logger logger)
    {
        this.logger = logger;
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
        result = prime * result + bit;
        result = prime * result
                + ((byteOrder == null) ? 0 : byteOrder.hashCode());
        result = prime * result
                + ((doubleWordOrder == null) ? 0 : doubleWordOrder.hashCode());
        result = prime * result
                + ((registerSize == null) ? 0 : registerSize.hashCode());
        result = prime * result
                + ((registerType == null) ? 0 : registerType.hashCode());
        result = prime * result
                + ((wordOrder == null) ? 0 : wordOrder.hashCode());
        result = prime * result + super.hashCode();
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
        BaseRegXlator other = (BaseRegXlator) obj;
        if (bit != other.bit)
            return false;
        if (byteOrder != other.byteOrder)
            return false;
        if (doubleWordOrder != other.doubleWordOrder)
            return false;
        if (registerSize != other.registerSize)
            return false;
        if (registerType != other.registerType)
            return false;
        if (wordOrder != other.wordOrder)
            return false;
        if (Double.doubleToLongBits(scaleFactor) != Double
                .doubleToLongBits(other.scaleFactor))
            return false;
        if (unitOfMeasure == null)
        {
            if (other.unitOfMeasure != null)
                return false;
        }
        else if (!unitOfMeasure.equals(other.unitOfMeasure))
            return false;
        return true;
    }

    // ------- PRIVATE METHODS ------------

    /**
     * Get the value of an holding register as String representing the value and
     * the associated unit of measure, if specified.
     * 
     * @param readResponse
     *            The response to interpret.
     * @return The value of the response.
     */
    private String getHoldingRegisterValue(
            ReadMultipleRegistersResponse readResponse)
    {
        return this.getInputHoldingValue(readResponse.getRegisters());
    }

    /**
     * Get the value of an input register as a String representing the value and
     * the associated unit of measure, if specified.
     * 
     * @param readResponse
     *            The response to interpret.
     * @return The value encoded in the response
     */
    private String getInputRegisterValue(
            ReadInputRegistersResponse readResponse)
    {
        return this.getInputHoldingValue(readResponse.getRegisters());
    }

    /**
     * Get the value of a coil register as a boolean.
     * 
     * @param readResponse
     *            The response to interpret.
     * @return The value encoded in the response.
     */
    private String getCoilValue(ReadCoilsResponse readResponse)
    {
        return "" + readResponse.getCoilStatus(0);
    }

    /**
     * Get the bit of a discrete input register specified by the bit field of
     * this instance, as a boolean.
     * 
     * @param readResponse
     *            The response to interpret.
     * @return The value encoded in the response.
     */
    private String getInputDiscreteValue(
            ReadInputDiscretesResponse readResponse)
    {
        // TODO Auto-generated method stub
        return "" + readResponse.getDiscreteStatus(this.bit);
    }

    /**
     * Parse, compute the value of a set of registers, and apply a scale factor
     * (and offset, in future) if specified as parameter of this
     * {@link BaseRegXlator} instance.
     * 
     * @param registers
     *            The set of registers to interpret.
     * @return The interpreted value as a String composed by a number and a unit
     *         of measure.
     */
    private String getInputHoldingValue(InputRegister[] registers)
    {
        // the parsing result
        String value = null;

        // extract the holding register response
        Number scaledValue = this.fromRegisters(registers);

        // scale the value if needed
        // no scaling can be applied to BIT values
        // WARNING!! this implies a precision loss for 64bit INT e UINT values
        if (this.registerSize != DataSizeEnum.BIT)
        {
            // avoid conversion and precision loss on unit scale factors.
            if (this.scaleFactor != 1.0)
            {
                scaledValue = scaledValue.doubleValue() * this.scaleFactor;
            }

            value = "" + scaledValue
                    + ((this.unitOfMeasure != null) ? " " + this.unitOfMeasure
                            : "");
        }
        else
        {
            // interpret BIT registers as boolean
            value = "" + (scaledValue.shortValue() != 0 ? true : false);
        }

        return value;
    }

    /**
     * Extracts a numeric value corresponding to the given set of registers,
     * interpreted according to the specific parameters set for this regXlator.
     * 
     * @param registers
     *            The array of registers to interpret.
     * @return The extracted value.
     */
    private Number fromRegisters(InputRegister[] registers)
    {
        // the result of the register extraction, initially null.
        Number result = null;

        // check size
        if (this.registerSize.getNRegisters() == registers.length)
        {
            // extract the register payload ready to be wrapped by a byte buffer
            byte[] registerBytes = this.computeBEPayload(registers,
                    this.getEndiannessMap(this.getEndiannessKey()));

            // wrap the register bytes as a byte buffer
            ByteBuffer registerBytesValue = ByteBuffer.wrap(registerBytes);

            // convert the register value
            switch (this.registerSize)
            {
                case UINT16:
                {
                    result = (int) (registerBytesValue.getShort() & 0xffff);
                    break;
                }
                case INT16:
                {
                    result = registerBytesValue.getShort();
                    break;
                }
                case UINT32:
                {
                    result = (long) (registerBytesValue.getInt() & 0xffffffff);
                    break;
                }
                case INT32:
                {
                    result = registerBytesValue.getInt();
                    break;
                }
                case UINT64:
                case INT64:
                {
                    // WARNING: make sure that for UINT64 the number is treated
                    // correctly as unsigned.
                    result = registerBytesValue.getLong();
                    break;
                }
                case FLOAT32:
                {
                    result = registerBytesValue.getFloat();
                    break;
                }
                case FLOAT64:
                {
                    result = registerBytesValue.getDouble();
                    break;
                }
                case BIT:
                {
                    // BIT is interpreted as the index of the bit starting from
                    // the least significant bit
                    // order LSB -> MSB
                    // get the right byte
                    int byteIndex = ((registerBytes.length * 8 - 1) - this.bit)
                            / 8;

                    // protection against out-of-bound exceptions...
                    if (byteIndex >= 0 && byteIndex < registerBytes.length)
                    {
                        byte byteToMask = registerBytes[byteIndex];

                        // extract the bit of interest
                        result = (0x01 << (this.bit % 8)) & byteToMask;
                    }
                }
            }
        }

        return result;
    }

    /**
     * Computes the exact byte array corresponding to a register value given an
     * array of modbus registers (to interpret as a single register) and a map
     * defining the order in which the register bytes shall be composed.
     * 
     * @param registers
     *            The register array from which extracting the byte array.
     * @param map
     *            The map defining the byte order within registers
     * @return The BIG_ENDIAN representation of the register, as an array of
     *         bytes.
     */
    private byte[] computeBEPayload(InputRegister[] registers, int[] map)
    {
        byte[] payloadBytesBE = new byte[registers.length * 2];

        for (int i = 0; i < registers.length; i++)
        {
            // get the register bytes in the correct order
            byte[] registerBytes = registers[map[i]].toBytes();
            if (this.byteOrder == OrderEnum.BIG_ENDIAN)
            {

                payloadBytesBE[i * 2] = registerBytes[0];
                payloadBytesBE[i * 2 + 1] = registerBytes[1];
            }
            else
            {
                payloadBytesBE[i * 2] = registerBytes[1];
                payloadBytesBE[i * 2 + 1] = registerBytes[0];
            }
        }

        return payloadBytesBE;
    }

    /**
     * Convert a register value represented as a String into an array of
     * registers ready to be written over the modbus line.
     * 
     * @param value
     *            The value to convert.
     * @return The corresponding registers in the order defined by this
     *         regXlator.
     */
    private Register[] toRegisters(String value, String oldValue)
    {
        // the bytes to include in the registers
        byte[] registerBytes = new byte[this.registerSize.getNBits()];

        // the ByteBuffer to fill with the value
        ByteBuffer buffer = ByteBuffer.wrap(registerBytes);

        // handle different register types
        switch (this.registerSize)
        {
            case BIT:
            {
                // mask value an only change the bit-th bit

                // create the bitmask
                int bitmask = ~(0x00000001 << this.bit);
                // convert the value to int
                int valueInt = Integer.valueOf(oldValue.trim());

                int maskedValue = valueInt & bitmask;

                // only works for 16bit sizes, shall be extended for larger
                // sizes
                buffer.putShort((short) (maskedValue
                        + (Boolean.valueOf(value.trim()) ? Math.pow(2, this.bit)
                                : 0)));
                break;
            }
            case FLOAT32:
            {
                buffer.putFloat(Float.valueOf(value.trim()));
                break;
            }
            case FLOAT64:
            {
                buffer.putDouble(Double.valueOf(value.trim()));
                break;
            }
            case INT16:
            case UINT16:
            {
                buffer.putShort(Short.valueOf(value.trim()));
                break;
            }
            case INT32:
            case UINT32:
            {
                buffer.putInt(Integer.valueOf(value.trim()));
                break;
            }
            case INT64:
            case UINT64:
            {
                buffer.putLong(Integer.valueOf(value.trim()));
                break;
            }
        }

        // translate big endian bytes in registers
        return this.composeRegisters(registerBytes);
    }

    /**
     * Compose the set of modbus registers corresponding to a given array of
     * bytes representing a register value. The bytes are supposed to be in BIG
     * ENDIAN order.
     * 
     * @param registerBytes
     *            The bytes of the value to represent in terms of modbus
     *            registers.
     * @return The modbus registers, re-arranges in the order specified by this
     *         regXlator endianness parameters.
     */
    private Register[] composeRegisters(byte[] registerBytes)
    {
        // the resulting registers, in the right order
        Register[] registers = null;

        // the registers corresponding to the given bytes in BIG_ENDIAN order
        Register[] beRegisters = new Register[this.registerSize
                .getNRegisters()];

        // build the base registers depending on the byte order
        for (int i = 0; i < registerBytes.length; i += 2)
        {
            // registers are 2-byte long
            if (this.byteOrder == OrderEnum.BIG_ENDIAN)
            {
                beRegisters[i] = new SimpleRegister(registerBytes[i],
                        registerBytes[i + 1]);
            }
            else
            {
                beRegisters[i] = new SimpleRegister(registerBytes[i + 1],
                        registerBytes[i]);
            }
        }

        // re-order depending on word and double word order
        if (this.registerSize.getNRegisters() > 1)
        {
            registers = this.reArrangeRegisters(beRegisters);
        }
        else
        {
            registers = beRegisters;
        }

        return registers;
    }

    /**
     * Given a set of registers in BIG ENDIAN order, re-arrange the register
     * order depending on the endianness parameters associated to this
     * regxlator.
     * 
     * @param beRegisters
     *            The registers in BIG ENDIAN order.
     * @return The registers in the order corresponding to the current regxlator
     *         endianness parameters.
     */
    private Register[] reArrangeRegisters(Register[] beRegisters)
    {
        Register[] reOrderedRegisters = new Register[beRegisters.length];
        // get the order map
        int[] orderingMap = this.getEndiannessMap(this.getEndiannessKey());

        for (int i = 0; i < beRegisters.length; i++)
        {
            reOrderedRegisters[orderingMap[i]] = beRegisters[i];
        }
        return reOrderedRegisters;
    }

    /**
     * Compute the key (int) to use for extracting the register map from the
     * endianness lookup table.
     * 
     * @return the position in the lookup table to use for encoding/decoding the
     *         modbus registers contained in requests/responses that are handled
     *         by this regXlator.
     */
    private int getEndiannessKey()
    {
        int endiannessKey = -1;
        if (this.registerSize.getNBits() == 64)
        {
            endiannessKey = ((this.doubleWordOrder == OrderEnum.LITTLE_ENDIAN)
                    ? 0
                    : 2)
                    + ((this.wordOrder == OrderEnum.LITTLE_ENDIAN) ? 0 : 1);
        }
        else if (this.registerSize.getNBits() == 32)
        {
            endiannessKey = (this.wordOrder == OrderEnum.LITTLE_ENDIAN) ? 0 : 1;
        }

        return endiannessKey;
    }

    /**
     * Get the register mapping array (array of register indexes in the order
     * required to convert the array of registers into an BIG_ENDIAN array of
     * registers)
     * 
     * @param endiannessKey
     *            The position of the array in the endianness map.
     * @return The mapping array.
     */
    private int[] getEndiannessMap(int endiannessKey)
    {
        int[] endiannessMap = new int[] { 0 };
        if (this.registerSize.getNBits() == 64)
        {
            endiannessMap = endianess64bit[endiannessKey];
        }
        else if (this.registerSize.getNBits() == 32)
        {
            endiannessMap = endianess32bit[endiannessKey];
        }
        return endiannessMap;
    }
}

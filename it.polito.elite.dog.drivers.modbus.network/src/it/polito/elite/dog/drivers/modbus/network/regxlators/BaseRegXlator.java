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

import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ModbusResponse;
import net.wimpi.modbus.msg.ReadCoilsResponse;
import net.wimpi.modbus.msg.ReadInputDiscretesResponse;
import net.wimpi.modbus.msg.ReadInputRegistersResponse;
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse;
import net.wimpi.modbus.procimg.InputRegister;

/**
 * A generic RegXlator able to handle almost all register types.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Mar 14, 2019
 */
public class BaseRegXlator extends RegXlator
{
    // the register size as enum
    private DataSizeEnum registerSize;
    // the register type as enum
    private RegisterTypeEnum registerType;
    // the byte order as enum
    private OrderEnum byteOrder;
    // the word order as enum
    private OrderEnum wordOrder;
    // the double word order as enum
    private OrderEnum doubleWordOrder;
    // the bit to extract (valid only for bit registers)
    private int bit = -1; // default

    // the endianness translation map for 64bit values
    // le = 0, be = 1
    // dbwe we
    // 0 0 3210
    // 0 1 2301
    // 1 0 1032
    // 1 1 0123
    private int[][] endianess64bit = { { 3, 2, 1, 0 }, { 2, 3, 0, 1 },
            { 1, 0, 3, 2 }, { 0, 1, 2, 3 } };
    // the endianness translation map for 32bit values
    // le = 0, be = 1
    // we
    // 0 10
    // 1 01
    private int[][] endianess32bit = { { 1, 0 }, { 0, 1 } };

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

        // fill the type size of the superclass
        // backward compatibility, may be removed in future
        this.typeSize = 2 * this.registerSize.getNRegisters();
    }

    @Override
    public String getValue()
    {
        return this.getValue(this.readResponse);
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

    @Override
    public ModbusRequest getWriteRequest(int address, String value)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ModbusRequest getReadRequest(int address)
    {
        // TODO Auto-generated method stub
        return null;
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
        // WARNING!! this implies a precision loss for 64bit INT e UINT values
        if (this.registerSize != DataSizeEnum.BIT && this.scaleFactor != 1.0)
        {
            scaledValue = scaledValue.doubleValue() * this.scaleFactor;
        }
        
        // TODO: define if BIT values shall be boolean.

        // no scaling can be applied to BIT values
        value = "" + scaledValue
                + ((this.unitOfMeasure != null) ? " " + this.unitOfMeasure
                        : "");

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
            byte[] registerBytes = this.extractBEPayload(registers);

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
                    // get the right byte
                    byte byteToMask = registerBytes[this.bit / 8];

                    // extract the bit of interest
                    result = (byteToMask >> (7 - this.bit % 8)) & 0x01;
                }
            }
        }

        return result;
    }

    /**
     * Extracts the "payload" of the single register (possibly spanning more
     * than 16bit) handled by this {@link RegXlator}. The payload is extracted
     * in a BIG ENDIAN order, ready to be wrapped by a {@link ByteBuffer}.
     * 
     * @param registers
     *            The registers composing the "single register".
     * @return The extracted payload as a BigEndian byte array.
     */
    private byte[] extractBEPayload(InputRegister[] registers)
    {
        // the re-ordere byte-level payload.
        // must be big endian for subsequent wrapping in a byte buffer
        byte[] payloadBytesBE = null;

        // change behavior by lenght
        if (this.registerSize.getNBytes() == 64)
        {
            // compute the index of the endianness map
            int endiannessKey = ((this.doubleWordOrder == OrderEnum.LITTLE_ENDIAN)
                    ? 0
                    : 2)
                    + ((this.wordOrder == OrderEnum.LITTLE_ENDIAN) ? 0 : 1);

            payloadBytesBE = this.computeBEPayload(registers,
                    this.endianess64bit[endiannessKey]);
        }
        else if (this.registerSize.getNBytes() == 32)
        {
            int endiannessKey = (this.wordOrder == OrderEnum.LITTLE_ENDIAN) ? 0
                    : 1;

            payloadBytesBE = this.computeBEPayload(registers,
                    this.endianess32bit[endiannessKey]);
        }
        else if (this.registerSize.getNBytes() == 16)
        {
            payloadBytesBE = this.computeBEPayload(registers, new int[] { 0 });
        }

        return payloadBytesBE;
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
}

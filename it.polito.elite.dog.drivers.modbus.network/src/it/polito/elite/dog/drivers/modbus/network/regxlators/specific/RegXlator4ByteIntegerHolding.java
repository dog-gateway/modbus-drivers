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

import java.nio.ByteBuffer;
import java.util.Locale;

import it.polito.elite.dog.drivers.modbus.network.regxlators.RegXlator;
import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ReadMultipleRegistersRequest;
import net.wimpi.modbus.msg.ReadMultipleRegistersResponse;
import net.wimpi.modbus.msg.WriteMultipleRegistersRequest;
import net.wimpi.modbus.procimg.Register;
import net.wimpi.modbus.procimg.SimpleRegister;

/**
 * Handles translation of 4 bytes integer values...
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>, Politecnico
 *         di Torino
 * @author <a href="mailto:muhammad.sanaullah@polito.it">Muhammad Sanaullah</a>,
 *         Politecnico di Torino
 * 
 * @since Feb 27, 2012
 */
public class RegXlator4ByteIntegerHolding extends RegXlator
{
	
	public RegXlator4ByteIntegerHolding()
	{
		super();
		this.typeSize = 4;
	}
	
	/**
	 * Translates a couple of 2-byte registers into a 4-byte register and
	 * extracts the corresponding integer value
	 * 
	 * TODO: check if it works...
	 * 
	 * @param registers
	 * @return
	 */
	public int fromRegister(Register[] registers)
	{
		byte bytes[] = new byte[this.typeSize];
		/*
		for (int j = 0; j < registers.length; j++)
		{
			Register cRegister = registers[j];
			byte registerBytes[] = cRegister.toBytes();
			for (int k = 0; k < registerBytes.length; k++)
			{
				bytes[j * registerBytes.length + k] = registerBytes[k];
			}
		}
		*/
		
		//swap bytes
		byte highWord[] = registers[1].toBytes();
		byte lowWord[] = registers[0].toBytes();
		
		//copy the two byte in the response byte array
		bytes[1] = highWord[1];
		bytes[0] = highWord[0];
		bytes[3] = lowWord[1];
		bytes[2] = lowWord[0];
		
		//wrap the byte array
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		
		return buffer.getInt();
	}
	
	/**
	 * Translates an integer value into a couple of 2-byte registers
	 * 
	 * @param value
	 * @return
	 */
	public Register[] toRegister(int value)
	{
		byte bytes[] = new byte[this.typeSize];
		
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		
		buffer.putInt(value);
		
		Register registers[] = new SimpleRegister[2];
		registers[1] = new SimpleRegister(bytes[0], bytes[1]);
		registers[0] = new SimpleRegister(bytes[2], bytes[3]);
		return registers;
	}
	
	@Override
	public String getValue()
	{
		String value = null;
		if ((this.readResponse != null)&&(this.readResponse instanceof ReadMultipleRegistersResponse))
		{
			//get the integer value contained in the received readResponse
			int valueAsInt = this.fromRegister(((ReadMultipleRegistersResponse) this.readResponse).getRegisters());
			
			//format and scale the value according to the inner scaling parameter
			value = String.format(Locale.US,"%f", valueAsInt*this.scaleFactor);
			
			//add the unit of measure if needed
			if ((this.unitOfMeasure != null) && (!this.unitOfMeasure.isEmpty()))
				value += " " + this.unitOfMeasure;
		}
		return value;
	}

	@Override
	public ModbusRequest getWriteRequest(int address, String value)
	{
		// Set the given value as writeRequest value
		Register registers[] = this.toRegister((int)(Double.valueOf(value)*(1/this.scaleFactor)));
		this.writeRequest = new WriteMultipleRegistersRequest(address, registers);
		((WriteMultipleRegistersRequest)this.writeRequest).setReference(address);
		return this.writeRequest;
	}

	@Override
	public ModbusRequest getReadRequest(int address)
	{
		this.readRequest = new ReadMultipleRegistersRequest(address, this.typeSize/2);
		return this.readRequest;
	}
}

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
package it.polito.elite.dog.drivers.modbus.network.regxlators;

import java.util.Locale;

import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ReadInputRegistersRequest;
import net.wimpi.modbus.msg.ReadInputRegistersResponse;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>, Politecnico
 *         di Torino
 * @author <a href="mailto:muhammad.sanaullah@polito.it">Muhammad Sanaullah</a>,
 *         Politecnico di Torino
 *
 * @since Dec 28, 2017
 */
public class RegXlator2ByteUnsignedIntegerInput extends RegXlator
{

	public RegXlator2ByteUnsignedIntegerInput()
	{
		super();
		this.typeSize = 2;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * it.polito.elite.dog.drivers.modbus.network.regxlators.RegXlator#getValue
	 * ()
	 */
	@Override
	public String getValue()
	{
		String value = null;
		if ((this.readResponse != null)
				&& (this.readResponse instanceof ReadInputRegistersResponse))
		{
			// get the integer value contained in the received readResponse
			int valueAsInt = ((ReadInputRegistersResponse) this.readResponse)
					.getRegisterValue(0);

			// format and scale the value according to the inner scaling
			// parameter
			value = String.format(Locale.US, "%f",
					valueAsInt * this.scaleFactor);

			// add the unit of measure if needed
			if ((this.unitOfMeasure != null) && (!this.unitOfMeasure.isEmpty()))
				value += " " + this.unitOfMeasure;
		}
		return value;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see it.polito.elite.dog.drivers.modbus.network.regxlators.RegXlator#
	 * getWriteRequest(int, java.lang.String)
	 */
	@Override
	public ModbusRequest getWriteRequest(int address, String value)
	{
		// / not supported for input registers
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see it.polito.elite.dog.drivers.modbus.network.regxlators.RegXlator#
	 * getReadRequest(int)
	 */
	@Override
	public ModbusRequest getReadRequest(int address)
	{
		this.readRequest = new ReadInputRegistersRequest(address,
				this.typeSize / 2);
		return this.readRequest;
	}

}

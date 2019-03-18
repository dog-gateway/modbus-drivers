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
package it.polito.elite.dog.drivers.modbus.network.regxlators;

import org.osgi.service.log.Logger;

import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ModbusResponse;

/**
 * The abstract class from which all the register translator implementations (<
 * ? extends {@link RegXlator}>) shall inherit.
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 * @since Feb 27, 2012
 */
public abstract class RegXlator
{
	protected int typeSize;
	
	// the read readResponse to translate
	protected ModbusResponse readResponse;
	
	// the read request to compose
	protected ModbusRequest readRequest;
	
	// the write request to translate
	protected ModbusRequest writeRequest;
	
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
	
	public static Logger logger;
	
	/**
	 * Returns the value associated to the given register (with the associated
	 * unit of measure, if exiting)
	 * 
	 * @return
	 */
	public abstract String getValue();
	
	/**
	 * @return the scaleFactor
	 */
	public double getScaleFactor()
	{
		return scaleFactor;
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
	 * @return the unitOfMeasure
	 */
	public String getUnitOfMeasure()
	{
		return unitOfMeasure;
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
	 * @return the typeSize
	 */
	public int getTypeSize()
	{
		return typeSize;
	}
	
	/**
	 * @return the readResponse
	 */
	public ModbusResponse getReadResponse()
	{
		return readResponse;
	}
	
	/**
	 * @param readResponse
	 *            the readResponse to set
	 */
	public void setReadResponse(ModbusResponse response)
	{
		this.readResponse = response;
	}
	
	/**
	 * 
	 * To be called after a call to setValue(), provides back the request need
	 * to write the current register value on the real modbus device described
	 * by means of this {@link RegXlator}.
	 * 
	 * @return the writeRequest
	 */
	public abstract ModbusRequest getWriteRequest(int address, String value);
	
	/**
	 * @param writeRequest
	 *            the writeRequest to set
	 */
	public void setWriteRequest(ModbusRequest request)
	{
		this.writeRequest = request;
	}
	
	/**
	 * @return the readRequest
	 */
	public abstract ModbusRequest getReadRequest(int address);

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        long temp;
        temp = Double.doubleToLongBits(scaleFactor);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        result = prime * result + typeSize;
        result = prime * result
                + ((unitOfMeasure == null) ? 0 : unitOfMeasure.hashCode());
        return result;
    }

    /* (non-Javadoc)
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
        RegXlator other = (RegXlator) obj;
        if (Double.doubleToLongBits(scaleFactor) != Double
                .doubleToLongBits(other.scaleFactor))
            return false;
        if (typeSize != other.typeSize)
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
	
	
	
}

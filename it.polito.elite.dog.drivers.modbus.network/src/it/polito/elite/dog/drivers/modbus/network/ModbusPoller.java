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
package it.polito.elite.dog.drivers.modbus.network;

import it.polito.elite.dog.core.library.util.LogHelper;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;
import it.polito.elite.dog.drivers.modbus.network.protocol.ModbusProtocolVariant;
import net.wimpi.modbus.Modbus;
import net.wimpi.modbus.ModbusException;
import net.wimpi.modbus.ModbusIOException;
import net.wimpi.modbus.ModbusSlaveException;
import net.wimpi.modbus.io.ModbusTransaction;
import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ModbusResponse;
import net.wimpi.modbus.net.MasterConnection;

import java.net.InetAddress;
import java.util.Set;

import org.osgi.service.log.LogService;

/**
 * Merged changes suggested by Claudio Degioanni for efficient handling of
 * multiple gateways (some of which might be failing)
 * 
 * Last updated on 04/11/2016.
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>, Politecnico
 *         di Torino<br/>
 *         <a href="claudiodegio@gmail.com">Claudio Degioanni</a>
 *  
 * @since Feb 24, 2012
 */
public class ModbusPoller extends Thread
{
	// the minimum time between subsequent register reads
	public static int MINIMUM_TIME_BETWEEN_REGISTER_READS = 2;

	// reference to the EchelonIlon100DriverImpl currently using this poller
	private ModbusDriverImpl driver;

	// reference to the address of the gateway to poll
	private InetAddress gatewayAddress;

	// the runnable flag
	private boolean runnable = true;

	// the poller logger
	private LogHelper logger;

	// the log identifier, unique for the class
	public static String logId = "[ModbusPoller]: ";

	public ModbusPoller(ModbusDriverImpl modbusDriverImpl,
			InetAddress gwAddress)
	{
		// store a reference to the poller driver
		this.driver = modbusDriverImpl;

		// init the logger
		this.logger = this.driver.getLogger();

		// store the gateway address
		this.gatewayAddress = gwAddress;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run()
	{
		// run until the thread is runnable
		while (this.runnable)
		{
			// log
			this.logger.log(LogService.LOG_DEBUG,
					ModbusPoller.logId + "Starting a new polling cycle...");

			// get the set of datapoints and read
			Set<ModbusRegisterInfo> registers = this.driver
					.getGatewayRegisters(this.gatewayAddress);

			// check not null
			if (registers != null)
			{
				this.readAll(registers);
			}

			// ok now the polling cycle has ended and the poller can sleep for
			// the given polling time
			try
			{
				Thread.sleep(this.driver.getPollingTimeMillis());
			}
			catch (InterruptedException e)
			{
				// log the error
				this.logger.log(LogService.LOG_WARNING,
						ModbusPoller.logId + "Interrupted exception: " + e);
			}

		}

		// auto-reset the state at runnable...
		this.runnable = true;
	}

	/**
	 * Sets the thread state at runnable (true) or not runnable(false)
	 * 
	 * @param runnable
	 */
	public void setRunnable(boolean runnable)
	{
		this.runnable = runnable;
	}

	/**
	 * This is actually a duplicate of the readAll method in the driver
	 * implementation. It is still needed a verification to check whether
	 * calling the driver method will generate queues of waiting threads or not.
	 * If not using the driver method is fine, provided that is completely equal
	 * to this. This modification was kindly contributed by Claudio Degioanni.
	 * 
	 * @param registers
	 */
	private void readAll(final Set<ModbusRegisterInfo> registers)
	{
		if ((registers != null) && (!registers.isEmpty()))
		{
			// get the address of the modbus gateway, which is supposed to be
			// the same for all registers...
			ModbusRegisterInfo mInfo = registers.iterator().next();
			InetAddress gwAddress = mInfo.getGatewayIPAddress();

			// get the gateway port
			String gwPortAsString = mInfo.getGatewayPort();

			// handle the port using defaults
			int gwPort = Modbus.DEFAULT_PORT;

			try
			{
				gwPort = Integer.valueOf(gwPortAsString);
			}
			catch (NumberFormatException e)
			{
				// reset to the default
				gwPort = Modbus.DEFAULT_PORT;
			}

			// parse the protocol variant
			ModbusProtocolVariant variant = ModbusProtocolVariant
					.valueOf(mInfo.getGatewayProtocol());

			// prepare the TCP connection to the gateway offering access to the
			// given register
			MasterConnection modbusConnection = this.driver.getConnectionPool()
					.get(gwAddress);

			if ((modbusConnection != null) && (modbusConnection.isConnected()))
			{
				// successfully connected
				this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId
						+ "Successfully connected to the Modbus TCP Slave");
				synchronized (registers)
				{
					for (ModbusRegisterInfo register : registers)
					{
						// prepare the read request using the register
						// translator
						// for composing the right Modbus request...
						ModbusRequest readRequest = register.getXlator()
								.getReadRequest(register.getAddress());

						// set the slave id associated to the given register
						readRequest.setUnitID(register.getSlaveId());

						// create a modbus tcp transaction for the just created
						// readRequest
						ModbusTransaction transaction = this.driver
								.getTransaction(readRequest, modbusConnection,
										variant);

						// try to execute the transaction and manage possible
						// errors...
						try
						{
							transaction.execute();

							// get the readResponse
							ModbusResponse response = transaction.getResponse();

							// debug
							String responseAsString = response.getHexMessage();
							this.logger.log(LogService.LOG_DEBUG,
									ModbusDriverImpl.logId + "Received -> "
											+ responseAsString);

							// translate the readResponse
							register.getXlator().setReadResponse(response);

							this.logger.log(LogService.LOG_DEBUG,
									ModbusDriverImpl.logId
											+ "Translated into -> "
											+ register.getXlator().getValue());

							// dispatch the new message...
							ModbusDriverInstance driver = this.driver
									.getRegister2Driver().get(register);
							driver.newMessageFromHouse(register,
									register.getXlator().getValue());
						}
						catch (ModbusIOException e)
						{
							// debug
							this.logger.log(LogService.LOG_ERROR,
									ModbusDriverImpl.logId
											+ "Error on Modbus I/O communication for register "
											+ register + "\nException: " + e);

							// close the connection
							modbusConnection.close();
						}
						catch (ModbusSlaveException e)
						{
							// debug
							this.logger.log(LogService.LOG_ERROR,
									ModbusDriverImpl.logId
											+ "Error on Modbus Slave, for register "
											+ register + "\nException: " + e);
							// close the connection
							modbusConnection.close();
						}
						catch (ModbusException e)
						{
							// debug
							this.logger.log(LogService.LOG_ERROR,
									ModbusDriverImpl.logId
											+ "Error on Modbus while reading register "
											+ register + "\nException: " + e);
							// close the connection
							modbusConnection.close();
						}

						// stop this polling cycle if the connection is closed
						if (!modbusConnection.isConnected())
							break;

						try
						{
							// minimum time between subsequent register read
							Thread.sleep(
									ModbusPoller.MINIMUM_TIME_BETWEEN_REGISTER_READS);
						}
						catch (InterruptedException e)
						{
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
			else
			{
				// info on port usage
				this.logger.log(LogService.LOG_INFO,
						ModbusDriverImpl.logId + "Using port: " + gwPort);

				// close and re-open
				this.driver.closeAndReOpen(gwAddress, gwPort, variant);
			}
		}

	}
}

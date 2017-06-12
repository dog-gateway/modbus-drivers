/*
 * Dog - Network Driver
 * 
 * Copyright (c) 2012-2013 Dario Bonino, Claudio Degioanni
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
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import it.polito.elite.dog.drivers.modbus.network.protocol.ModbusProtocolVariant;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import net.wimpi.modbus.Modbus;
import net.wimpi.modbus.ModbusException;
import net.wimpi.modbus.ModbusIOException;
import net.wimpi.modbus.ModbusSlaveException;
import net.wimpi.modbus.io.ModbusRTUTCPTransaction;
import net.wimpi.modbus.io.ModbusSerialTransaction;
import net.wimpi.modbus.io.ModbusTCPTransaction;
import net.wimpi.modbus.io.ModbusTransaction;
import net.wimpi.modbus.io.ModbusUDPTransaction;
import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ModbusResponse;
import net.wimpi.modbus.net.MasterConnection;
import net.wimpi.modbus.net.RTUTCPMasterConnection;
import net.wimpi.modbus.net.SerialMasterConnection;
import net.wimpi.modbus.net.TCPMasterConnection;
import net.wimpi.modbus.net.UDPMasterConnection;
import net.wimpi.modbus.util.SerialParameters;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedService;
import org.osgi.service.log.LogService;

/**
 * The network driver for devices based on the Modbus TCP protocol
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>, Politecnico
 *         di Torino<br/>
 *         <a href="claudiodegio@gmail.com">Claudio Degioanni</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 * @since Jan 18, 2012
 */
public class ModbusDriverImpl implements ModbusNetwork, ManagedService
{
	// the bundle context
	private BundleContext bundleContext;
	
	// the service registration handle
	private ServiceRegistration<?> regServiceModbusDriverImpl;
	
	// the driver logger
	private LogHelper logger;
	
	// the log identifier, unique for the class
	public static String logId = "[ModbusDriverImpl]: ";
	
	// the register to driver map
	private Map<ModbusRegisterInfo, ModbusDriverInstance> register2Driver;
	
	// the inverse map
	private Map<ModbusDriverInstance, Set<ModbusRegisterInfo>> driver2Register;
	
	// the modbus server-to-register association for polling
	private Map<InetAddress, Set<ModbusRegisterInfo>> gatewayAddress2Registers;
	
	// the baseline pollingTime adopted if no server-specific setting is given
	private int pollingTimeMillis = 5000; // default value
	
	// the number of connection trials
	private int nConnectionTrials = 0;
	private int trialsDone;
	
	// the time that must occur between two subsequent trials
	private int betweenTrialTimeMillis = 3000;
	
	// a reference to the connection trials timer
	private Timer connectionTrialsTimer;
	
	// number of cycles that a broken register will be in the blacklist
	private int maxBlacklistPollingCycles = 80;
	
	// the modbus poller
	private Map<InetAddress, ModbusPoller> pollerPool;
	
	// the modbus connection pool
	private Map<InetAddress, MasterConnection> connectionPool;
	
	public ModbusDriverImpl()
	{
		// empty wait for a call to the activate method
	}
	
	/**
	 * Called when the bundle is activated
	 * 
	 * @param bundleContext
	 *            the OSGi context associated to the bundle
	 */
	public void activate(BundleContext bundleContext)
	{
		// create a logger
		this.logger = new LogHelper(bundleContext);
		
		// set the number of done trials to 0
		this.trialsDone = 0;
		
		// store the bundle context
		this.bundleContext = bundleContext;
		
		// create the register to driver map
		this.register2Driver = new ConcurrentHashMap<ModbusRegisterInfo, ModbusDriverInstance>();
		
		// create the driver to register map
		this.driver2Register = new ConcurrentHashMap<ModbusDriverInstance, Set<ModbusRegisterInfo>>();
		
		// create the gateway address to register map
		this.gatewayAddress2Registers = new ConcurrentHashMap<InetAddress, Set<ModbusRegisterInfo>>();
		
		// create the connection pool (one per gateway address)
		this.connectionPool = new ConcurrentHashMap<InetAddress, MasterConnection>();
		
		// empty wait for a call to the activate method (concurrent set
		// implementation)
		this.pollerPool = new ConcurrentHashMap<InetAddress, ModbusPoller>();
		
		// log the activation
		this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId + "Activated...");
	}
	
	/**
	 * Called whenever the bundle is deactivated by the framework
	 */
	public void deactivate()
	{
		// log
		this.logger.log(LogService.LOG_INFO, ModbusDriverImpl.logId + "Deactivated...");
		this.unRegister();
		
		// store the bundle context
		this.bundleContext = null;
		
		// delete the register to driver map
		this.register2Driver = null;
		
		// delete the driver to register map
		this.driver2Register = null;
		
		// delete the gateway address to register map
		this.gatewayAddress2Registers = null;
		
		// delete the poller pool (unRegister already stops poller threads)
		this.pollerPool = null;
		
		// close connections
		Collection<MasterConnection> connections = this.connectionPool.values();
		
		if (connections != null)
		{
			for (MasterConnection connection : connections)
			{
				if (connection.isConnected())
					connection.close();
			}
		}
		
		// delete the connection pool (one per gateway address)
		this.connectionPool = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.service.cm.ManagedService#updated(java.util.Dictionary)
	 */
	@Override
	public void updated(Dictionary<String, ?> properties) throws ConfigurationException
	{
		// get the bundle configuration parameters
		if (properties != null)
		{
			// try to get the baseline polling time
			String pollingTimeAsString = (String) properties.get("pollingTimeMillis");
			
			// check not null
			if (pollingTimeAsString != null)
			{
				// trim leading and trailing spaces
				pollingTimeAsString = pollingTimeAsString.trim();
				// parse the string
				this.pollingTimeMillis = Integer.valueOf(pollingTimeAsString);
			}
			
			// try to get the baseline polling time
			String betweenTrialTimeMillisAsString = (String) properties.get("betweenTrialTimeMillis");
			
			// check not null
			if (betweenTrialTimeMillisAsString != null)
			{
				// trim leading and trailing spaces
				betweenTrialTimeMillisAsString = betweenTrialTimeMillisAsString.trim();
				
				// parse the string
				this.betweenTrialTimeMillis = Integer.valueOf(betweenTrialTimeMillisAsString);
			}
			// try to get the baseline polling time
			String numTryAsString = (String) properties.get("numTry");
			
			// check not null
			if (numTryAsString != null)
			{
				// trim leading and trailing spaces
				numTryAsString = numTryAsString.trim();
				
				// parse the string
				this.nConnectionTrials = Integer.valueOf(numTryAsString);
			}

			// try to get the maxBlacklistPollingCycles
			String maxBlacklistPollingCyclesAsString = (String) properties.get("maxBlacklistPollingCycles");
			
			// check not null
			if (maxBlacklistPollingCyclesAsString != null)
			{
				// trim maxBlacklistPollingCycles
				maxBlacklistPollingCyclesAsString = maxBlacklistPollingCyclesAsString.trim();
				// parse the string
				this.maxBlacklistPollingCycles = Integer.valueOf(maxBlacklistPollingCyclesAsString);
			}
			
			// register the driver service if not already registered
			if (this.regServiceModbusDriverImpl == null)
				this.regServiceModbusDriverImpl = this.bundleContext.registerService(ModbusNetwork.class.getName(),
						this, null);
		}
		
	}
	
	/**
	 * Unregisters the driver from the OSGi framework
	 */
	public void unRegister()
	{
		// stop all the poller threads
		for (ModbusPoller poller : this.pollerPool.values())
		{
			poller.setRunnable(false);
		}
		
		// unregister
		if (this.regServiceModbusDriverImpl != null)
		{
			this.regServiceModbusDriverImpl.unregister();
			this.regServiceModbusDriverImpl = null;
		}
		
	}
	
	/**
	 * Provides a reference to the {@link LogService} instance used by this
	 * class to log messages...
	 * 
	 * @return
	 */
	public LogHelper getLogger()
	{
		return this.logger;
	}
	
	/**
	 * Provides back the set of connected gateways, identified by their IP
	 * address
	 * 
	 * @return
	 */
	public Set<InetAddress> getConnectedGateways()
	{
		return this.gatewayAddress2Registers.keySet();
	}
	
	/**
	 * Provides back the {@link Set} of registers ({@link ModbusRegisterInfo})
	 * associated to the given server ip
	 * 
	 * @param gwAddress
	 *            The IP address of the server on which registers are available.
	 * @return
	 */
	public synchronized Set<ModbusRegisterInfo> getGatewayRegisters(InetAddress gwAddress)
	{
		Set<ModbusRegisterInfo> currentSnapshot = Collections.synchronizedSet(new HashSet<ModbusRegisterInfo>());
		
		// return a snapshot of the registers set to avoid concurrent
		// modification exceptions in the start-up and polling phase...
		synchronized (this)
		{
			Set<ModbusRegisterInfo> allRegisterInfos = this.gatewayAddress2Registers.get(gwAddress);
			for (ModbusRegisterInfo r : allRegisterInfos)
			{
				currentSnapshot.add(r);
			}
		}
		return currentSnapshot;
	}
	
	/**
	 * Provides the polling time to be used by Poller threads connect to this
	 * driver instance...
	 * 
	 * @return
	 */
	public long getPollingTimeMillis()
	{
		return this.pollingTimeMillis;
	}
	
	/**
	 * @return the register2Driver
	 */
	public Map<ModbusRegisterInfo, ModbusDriverInstance> getRegister2Driver()
	{
		return register2Driver;
	}
	
	/**
	 * @return the driver2Register
	 */
	public Map<ModbusDriverInstance, Set<ModbusRegisterInfo>> getDriver2Register()
	{
		return driver2Register;
	}
	
	/**
	 * @return the gatewayAddress2Registers
	 */
	public Map<InetAddress, Set<ModbusRegisterInfo>> getGatewayAddress2Registers()
	{
		return gatewayAddress2Registers;
	}
	
	/**
	 * @return the connectionPool
	 */
	public Map<InetAddress, MasterConnection> getConnectionPool()
	{
		return connectionPool;
	}
	
	/**
	 * 
	 * @return the maxBlacklistPollingCycles
	 */
	public int getMaxBlacklistPollingCycles()
	{	
		return this.maxBlacklistPollingCycles;
	}
	
	/***************************************************************************************
	 * 
	 * Modbus Network Implementation
	 * 
	 **************************************************************************************/
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork
	 * #read(it.polito.elite.dog.drivers.modbus.network.info.
	 * ModbusRegisterInfo)
	 */
	@Override
	public synchronized void read(ModbusRegisterInfo register)
	{
		// prepare the TCP connection to the gateway offering access to the
		// given register
		MasterConnection modbusConnection = this.connectionPool.get(register.getGatewayIPAddress());
		
		// get the gateway port
		String gwPortAsString = register.getGatewayPort();
		
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
		ModbusProtocolVariant variant = ModbusProtocolVariant.valueOf(register.getGatewayProtocol());
		
		if (modbusConnection.isConnected())
		{
			// successfully connected
			this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId
					+ "Successfully connected to the Modbus TCP Slave");
			
			// prepare the read request using the register translator for
			// composing the right Modbus request...
			ModbusRequest readRequest = register.getXlator().getReadRequest(register.getAddress());
			
			// set the slave id associated to the given register
			readRequest.setUnitID(register.getSlaveId());
			
			// create a modbus transaction for the just created readRequest
			ModbusTransaction transaction = this.getTransaction(readRequest, modbusConnection, variant);
			
			// try to execute the transaction and manage possible errors...
			try
			{
				transaction.execute();
			}
			catch (ModbusIOException e)
			{
				// debug
				this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
						+ "Error on Modbus I/O communication for register " + register + "\nException: " + e);
			}
			catch (ModbusSlaveException e)
			{
				// debug
				this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId + "Error on Modbus Slave, for register "
						+ register + "\nException: " + e);
			}
			catch (ModbusException e)
			{
				// debug
				this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
						+ "Error on Modbus while reading register " + register + "\nException: " + e);
			}
			
			// get the readResponse
			ModbusResponse response = transaction.getResponse();
			
			// debug
			String responseAsString = response.getHexMessage();
			this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId + "Received -> " + responseAsString);
			
			// translate the readResponse
			register.getXlator().setReadResponse(response);
			
			this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId + "Translated into -> "
					+ register.getXlator().getValue());
			
			// dispatch the new message...
			ModbusDriverInstance driver = this.register2Driver.get(register);
			driver.newMessageFromHouse(register, register.getXlator().getValue());
		}
		else
		{
			// info on port usage
			this.logger.log(LogService.LOG_INFO, ModbusDriverImpl.logId + "Using port: " + gwPort);
			
			// close and re-open
			this.closeAndReOpen(register.getGatewayIPAddress(), gwPort, variant, register.getSerialParameters());
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork
	 * # readAll(java.util.Set<it.polito.elite.dog.drivers.modbus.network. info
	 * . ModbusRegisterInfo>)
	 */
	@Override
	public void readAll(final Set<ModbusRegisterInfo> registers)
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
			ModbusProtocolVariant variant = ModbusProtocolVariant.valueOf(mInfo.getGatewayProtocol());
			
			// prepare the TCP connection to the gateway offering access to the
			// given register
			MasterConnection modbusConnection = this.connectionPool.get(gwAddress);
			
			if (modbusConnection.isConnected())
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
						ModbusRequest readRequest = register.getXlator().getReadRequest(register.getAddress());
						
						// set the slave id associated to the given register
						readRequest.setUnitID(register.getSlaveId());
						
						// create a modbus tcp transaction for the just created
						// readRequest
						ModbusTransaction transaction = this.getTransaction(readRequest, modbusConnection, variant);
						
						// try to execute the transaction and manage possible
						// errors...
						try
						{
							transaction.execute();
							
							// get the readResponse
							ModbusResponse response = transaction.getResponse();
							
							// debug
							String responseAsString = response.getHexMessage();
							this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId + "Received -> "
									+ responseAsString);
							
							// translate the readResponse
							register.getXlator().setReadResponse(response);
							
							this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId + "Translated into -> "
									+ register.getXlator().getValue());
							
							// dispatch the new message...
							ModbusDriverInstance driver = this.register2Driver.get(register);
							driver.newMessageFromHouse(register, register.getXlator().getValue());
						}
						catch (ModbusIOException e)
						{
							// debug
							this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
									+ "Error on Modbus I/O communication for register " + register + "\nException: "
									+ e);
							
							// close the connection
							modbusConnection.close();
						}
						catch (ModbusSlaveException e)
						{
							// debug
							this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
									+ "Error on Modbus Slave, for register " + register + "\nException: " + e);
							// close the connection
							modbusConnection.close();
						}
						catch (ModbusException e)
						{
							// debug
							this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
									+ "Error on Modbus while reading register " + register + "\nException: " + e);
							// close the connection
							modbusConnection.close();
						}
						
						// stop this polling cycle if the connection is closed
						if (!modbusConnection.isConnected())
							break;
					}
				}
			}
			else
			{
				// info on port usage
				this.logger.log(LogService.LOG_INFO, ModbusDriverImpl.logId + "Using port: " + gwPort);
				
				// close and re-open
				this.closeAndReOpen(gwAddress, gwPort, variant, mInfo.getSerialParameters());
			}
		}
		
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork
	 * #write(it.polito.elite.dog.drivers.modbus.network.info.
	 * ModbusRegisterInfo, java.lang.String)
	 */
	@Override
	public void write(ModbusRegisterInfo register, String commandValue)
	{
		// prepare the TCP connection to the gateway offering access to the
		// given register
		MasterConnection modbusConnection = this.connectionPool.get(register.getGatewayIPAddress());
		
		// get the gateway port
		String gwPortAsString = register.getGatewayPort();
		
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
		ModbusProtocolVariant variant = ModbusProtocolVariant.valueOf(register.getGatewayProtocol());
		
		if (modbusConnection.isConnected())
		{
			// successfully connected
			this.logger.log(LogService.LOG_DEBUG, ModbusDriverImpl.logId
					+ "Successfully connected to the Modbus TCP Slave");
			
			ModbusRequest writeRequest = register.getXlator().getWriteRequest(register.getAddress(), commandValue);
			writeRequest.setUnitID(register.getSlaveId());
			writeRequest.setTransactionID(1);
			
			// create a modbus tcp transaction for the just created writeRequest
			ModbusTransaction transaction = this.getTransaction(writeRequest, modbusConnection, variant);
			
			// try to execute the transaction and manage possible errors...
			try
			{
				transaction.execute();
			}
			catch (ModbusIOException e)
			{
				// debug
				this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
						+ "Error on Modbus I/O communication for register " + register.getAddress() + "\nException: " + e);
			}
			catch (ModbusSlaveException e)
			{
				// debug
				this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId + "Error on Modbus Slave, for register "
						+ register.getAddress() + "\nException: " + e);
			}
			catch (ModbusException e)
			{
				// debug
				this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
						+ "Error on Modbus while writing register " + register.getAddress() + "\nException: " + e);
			}
		}
		else
		{
			
			// info on port usage
			this.logger.log(LogService.LOG_INFO, ModbusDriverImpl.logId + "Using port: " + gwPort);
			
			// close and re-open
			this.closeAndReOpen(register.getGatewayIPAddress(), gwPort, variant, register.getSerialParameters());
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork
	 * #addDriver(it.polito.elite.dog.drivers.modbus.network.info.
	 * ModbusRegisterInfo,
	 * it.polito.elite.dog.drivers.modbus.network.ModbusDriverInstance)
	 */
	@Override
	public void addDriver(ModbusRegisterInfo register, ModbusDriverInstance driver)
	{
		// get the register gateway address
		InetAddress gwAddress = register.getGatewayIPAddress();
		String gwPortAsString = register.getGatewayPort();
		SerialParameters serialParameters = register.getSerialParameters();
		ModbusProtocolVariant gwProtocolVariant = ModbusProtocolVariant.valueOf(register.getGatewayProtocol());

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

		// info on port usage
		this.logger.log(LogService.LOG_INFO, ModbusDriverImpl.logId + "Using port: " + gwPort);

		// adds a given register-driver association
		// TODO: check if any register can be associated to more than one driver
		this.register2Driver.put(register, driver);

		// fills the reverse map
		Set<ModbusRegisterInfo> driverRegisters = this.driver2Register.get(driver);
		if (driverRegisters == null)
		{
			// create the new set of registers associated to the given driver
			driverRegisters = new HashSet<ModbusRegisterInfo>();
			this.driver2Register.put(driver, driverRegisters);

		}
		driverRegisters.add(register);

		synchronized (this){

			// fill the server to register map
			Set<ModbusRegisterInfo> registers = this.gatewayAddress2Registers.get(gwAddress);
			if (registers == null)
			{
				// create the new entry
				registers = new HashSet<ModbusRegisterInfo>();
				this.gatewayAddress2Registers.put(register.getGatewayIPAddress(), registers);
			}

			// handle the modbus connection
			if (!this.connectionPool.containsKey(gwAddress))
			{
				// open the modbus connection
				switch(gwProtocolVariant) {
				case TCP:
				case RTU_TCP:
				case RTU_UDP:
					this.openConnection(gwAddress, gwPort, gwProtocolVariant);
					break;
				case RTU:
					this.openConnection(gwAddress, gwPort, gwProtocolVariant, serialParameters);
					break;
				}

			}

			// check if a poller is already available or not
			ModbusPoller poller = this.pollerPool.get(gwAddress);

			// if no poller is currently handling the gateway, then create a new one
			if (poller == null)
			{
				//create a new poller
				poller = new ModbusPoller(this, gwAddress);

				// add the thread to the poller pool
				this.pollerPool.put(gwAddress,poller);

				// start polling
				poller.start();
			}

			// add the register entry
			registers.add(register);
		}
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork
	 * #removeDriver(it.polito.elite.dog.drivers.modbus.network.info.
	 * ModbusRegisterInfo)
	 */
	@Override
	public void removeDriver(ModbusRegisterInfo register)
	{
		// removes a given register-driver association
		ModbusDriverInstance drv = this.register2Driver.remove(register);
		
		if (drv != null)
		{
			// removes the register from the corresponding set
			Set<ModbusRegisterInfo> driverRegisters = this.driver2Register.get(drv);
			driverRegisters.remove(register);
			
			// if after removal the set is empty, removes the reverse map entry
			if (driverRegisters.isEmpty())
				this.driver2Register.remove(drv);
		}
		
		// remove the register entry from the server to register map
		Set<ModbusRegisterInfo> serverRegisters = this.gatewayAddress2Registers.get(register.getGatewayIPAddress());
		if (serverRegisters != null)
		{
			// create the new entry
			serverRegisters.remove(register);
			
			// if it is the last entry in the set remove the map entry
			if (serverRegisters.isEmpty())
				this.gatewayAddress2Registers.remove(register.getGatewayIPAddress());
		}
		
	}
	
	@Override
	public void removeDriver(ModbusDriverInstance driver)
	{
		// removes a given driver-register association
		Set<ModbusRegisterInfo> driverRegisters = this.driver2Register.remove(driver);
		
		// remove the register-to-driver and the server-to-register
		// associations
		if (driverRegisters != null)
		{
			for (ModbusRegisterInfo register : driverRegisters)
			{
				// remove the datapoint-to-driver associations
				this.register2Driver.remove(register);
				
				// remove the datapoints from the endpoint/datapoint association
				Set<ModbusRegisterInfo> serverRegisters = this.gatewayAddress2Registers.get(register
						.getGatewayIPAddress());
				if (serverRegisters != null)
				{
					// create the new entry
					serverRegisters.remove(register);
					
					// if it is the last entry in the set remove the map entry
					if (serverRegisters.isEmpty())
						this.gatewayAddress2Registers.remove(register.getGatewayIPAddress());
				}
			}
		}
	}
	
	/**
	 * Opens a TCP master connection towards a given gateway
	 * 
	 * @param gwAddress
	 * @return
	 */
	private void openConnection(final InetAddress gwAddress, final int gwPort, final ModbusProtocolVariant gwProtocol, final SerialParameters serialParameters)
	{
		if (!this.connectionPool.containsKey(gwAddress))
		{
			// handle the connection type
			MasterConnection connection = null;
			
			switch (gwProtocol)
			{
				case TCP:
				{
					// create the ModbusTCP connection
					connection = new TCPMasterConnection(gwAddress);
					
					// set the port
					((TCPMasterConnection) connection).setPort(gwPort);
					
					break;
				}
				case RTU_TCP:
				{
					// create the ModbusRTUoverTCP connection
					connection = new RTUTCPMasterConnection(gwAddress, gwPort);
					
					break;
				}
				case RTU_UDP:
				{
					// create the ModbusRTUoverUDP connection
					connection = new UDPMasterConnection(gwAddress);
					((UDPMasterConnection) connection).setPort(gwPort);
					
					break;
				}
				case RTU:
				{
					// create the ModbusRTU connection
					connection = new SerialMasterConnection(serialParameters);
					break;
				}
			}
			
			// if not null, otherwise the protocol is not supported
			if (connection != null)
			{
				// connect to the gateway
				try
				{
					connection.connect();
					
				}
				catch (Exception e)
				{
					// log the connection error
					this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
							+ "Unable to connect to the Modbus TCP Slave with Address: " + gwAddress + "\nException: "
							+ e);
					
					if ((this.trialsDone < this.nConnectionTrials) || (this.nConnectionTrials == 0))
					{
						// log a warning
						this.logger.log(LogService.LOG_WARNING, ModbusDriverImpl.logId
								+ "Unable to connect to the given Modbus gateway, retrying in "
								+ this.betweenTrialTimeMillis + " ms");
						// schedule a new timer to re-call the open function
						// after
						// the
						// given trial timeout...
						connectionTrialsTimer = new Timer();
						connectionTrialsTimer.schedule(new TimerTask() {
							
							@Override
							public void run()
							{
								openConnection(gwAddress, gwPort, gwProtocol, serialParameters);
							}
						}, this.betweenTrialTimeMillis);
						
						// avoid incrementing the number of trials if the
						// nConnectionTrials is equal to 0 (i.e. infinite
						// re-trial)
						if (this.nConnectionTrials != 0)
							this.trialsDone++;
					}
					else
					{
						// log a fatal error
						this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId
								+ "Unable to connect to the given Modbus gateway");
					}
				}
				
				this.connectionPool.put(gwAddress, connection);
			}
		}
		else
		{
			// log a fatal error
			this.logger.log(LogService.LOG_ERROR, ModbusDriverImpl.logId + "Protocol variant not supported: "
					+ gwProtocol.toString());
		}
	}
	
	/**
	 * Utility method for these types of connections TCP, RTU_TCP, RTU_UDP
	 * 
	 *  
	 */
	private void openConnection(final InetAddress gwAddress, final int gwPort, final ModbusProtocolVariant gwProtocol)
	{
		openConnection(gwAddress, gwPort, gwProtocol, null);
	}
	
	
	/**
	 * Closes a TCPMaster connection towards a given gateway and tries to
	 * re-open it
	 * 
	 * @param gwAddress
	 * @return
	 */
	protected void closeAndReOpen(final InetAddress gwAddress, final int gwPort, final ModbusProtocolVariant gwProtocol, final SerialParameters serialParameters)
	{
		MasterConnection connection = this.connectionPool.get(gwAddress);
		
		if ((connection != null) && (!connection.isConnected()))
			connection.close();
		
		// remove the connection from the pool
		this.connectionPool.remove(gwAddress);
		
		// schedule a new timer to re-call the open function after the
		// given trial timeout...
		connectionTrialsTimer = new Timer();
		connectionTrialsTimer.schedule(new TimerTask() {
			
			@Override
			public void run()
			{
				openConnection(gwAddress,gwPort, gwProtocol, serialParameters);
			}
		}, this.betweenTrialTimeMillis);
	}
	
	protected ModbusTransaction getTransaction(ModbusRequest request, MasterConnection connection,
			ModbusProtocolVariant protocol)
	{
		ModbusTransaction transaction = null;
		// handle protocol variants
		switch (protocol)
		{
			case TCP:
			{
				// create a modbus tcp transaction for the given request
				transaction = new ModbusTCPTransaction(request);
				
				// attach the transaction to the given connection
				((ModbusTCPTransaction) transaction).setConnection((TCPMasterConnection) connection);
				
				break;
			}
			case RTU:
			{
				// create a modbus tcp transaction for the given request
				transaction = new ModbusSerialTransaction(request);
				
				// attach the transaction to the given connection
				((ModbusSerialTransaction) transaction).setSerialConnection(((SerialMasterConnection) connection).getConnection());
				
				break;
			}
			case RTU_TCP:
			{
				// create a modbus RTU over TCP transaction for the given
				// request
				transaction = new ModbusRTUTCPTransaction(request);
				
				// attach the transaction to the given connection
				((ModbusRTUTCPTransaction) transaction).setConnection((RTUTCPMasterConnection) connection);
				
				break;
			}
			case RTU_UDP:
			{
				// create a modbus RTU over TCP transaction for the given
				// request
				transaction = new ModbusUDPTransaction((UDPMasterConnection) connection);
				
				// attach the transaction to the given connection
				((ModbusUDPTransaction) transaction).setRequest(request);
			}
		}
		
		// return the created transaction
		return transaction;
	}
}


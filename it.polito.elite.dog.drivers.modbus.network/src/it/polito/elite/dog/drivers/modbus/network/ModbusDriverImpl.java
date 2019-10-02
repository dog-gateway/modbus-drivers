/*
 * Dog - Network Driver
 * 
 * Copyright (c) 2012-2019 Dario Bonino, Claudio Degioanni, Claudio Ventrella
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

import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import it.polito.elite.dog.drivers.modbus.network.protocol.ModbusProtocolVariant;
import it.polito.elite.dog.drivers.modbus.network.regxlators.BaseRegXlator;
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
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The network driver for devices based on the Modbus TCP protocol
 * 
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>, Politecnico
 *         di Torino<br/>
 *         <a href="claudiodegio@gmail.com">Claudio Degioanni</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 * @since Jan 18, 2012
 * @version 1.2
 */
public class ModbusDriverImpl implements ModbusNetwork, ManagedService
{
    // the default polling time, to be moved to the configuration class
    public static final int DEFAULT_POLLING_TIME_MILLIS = 5000;
    // the default number of reconnection trials
    public static final int DEFAULT_N_RECONNECTION_ATTEMPTS = 0;
    // the default interval between two subsequent re-connection attempts
    public static final int DEFAULT_RECONNECTION_INTERVAL_MILLIS = 3000;
    // the default blacklist duration (in polling cycles)
    public static final int DEFAULT_BLACKLIST_DURATION = 80;

    // the bundle context
    private BundleContext bundleContext;
    // the service registration handle
    private ServiceRegistration<?> regServiceModbusDriverImpl;
    // the reference to the LoggerFactory
    private AtomicReference<LoggerFactory> loggerFactory;
    // the driver logger
    private Logger logger;
    // the register to driver map
    // TODO: extend to allow multiple driver per register
    private Map<ModbusRegisterInfo, Set<ModbusDriverInstance>> register2Driver;
    // the inverse map
    private Map<ModbusDriverInstance, Set<ModbusRegisterInfo>> driver2Register;
    // the modbus network-level-gateway-to-register association for polling
    private Map<String, Set<ModbusRegisterInfo>> gatewayAddress2Registers;
    // the baseline pollingTime adopted if no server-specific setting is given
    private int pollingTimeMillis;
    // the number of connection trials
    private int nConnectionTrials;
    private int trialsDone;
    // the time that must occur between two subsequent trials
    private int betweenTrialTimeMillis;

    // handle multiple reconnection attempts
    private ScheduledExecutorService reconnectionService;
    private Map<String, Future<?>> activeReconnectionTimers;

    // number of cycles that a broken register will be in the blacklist
    private int maxBlacklistPollingCycles;
    // the set of modbus poller, one per each gateway.
    private Map<String, ModbusPoller> pollerPool;
    // the modbus connection pool
    private Map<String, MasterConnection> connectionPool;

    /**
     * Class constructor, initializes base data structures.
     */
    public ModbusDriverImpl()
    {
        // -- initialize atomic references
        this.loggerFactory = new AtomicReference<LoggerFactory>();

        // create the scheduled executor service
        // the number of threads in the pool defines the maximum number that can
        // be handled in paralell.
        this.reconnectionService = Executors.newScheduledThreadPool(1);
        this.activeReconnectionTimers = new HashMap<String, Future<?>>();

        // -- initialize defaults
        // the polling time
        this.pollingTimeMillis = ModbusDriverImpl.DEFAULT_POLLING_TIME_MILLIS;
        // the number of connection trials
        this.nConnectionTrials = ModbusDriverImpl.DEFAULT_N_RECONNECTION_ATTEMPTS;
        // the time that must occur between two subsequent trials
        this.betweenTrialTimeMillis = ModbusDriverImpl.DEFAULT_RECONNECTION_INTERVAL_MILLIS;
        // number of cycles that a broken register will be in the blacklist
        this.maxBlacklistPollingCycles = ModbusDriverImpl.DEFAULT_BLACKLIST_DURATION;
    }

    /**
     * Sets the reference to a {@link LoggerFactory} service available in the
     * OSGi framework.
     * 
     * @param loggerFactory
     *            The available {@link LoggerFactory} service.
     */
    public void setLoggerFactory(LoggerFactory loggerFactory)
    {
        // store the reference
        this.loggerFactory.set(loggerFactory);
        // create the class logger
        this.logger = this.loggerFactory.get()
                .getLogger(ModbusDriverImpl.class);
    }

    /**
     * Removes a reference to a {@link LoggerFactory} service, which is no more
     * available in the framework.
     * 
     * @param loggerFactory
     *            The {@link LoggerFactory} service that became unavailable.
     */
    public void unsetLoggerFactory(LoggerFactory loggerFactory)
    {
        // remove the reference
        if (this.loggerFactory.compareAndSet(loggerFactory, null))
        {
            // remove the logger
            this.logger = null;
        }
    }

    /**
     * Called when the bundle is activated
     * 
     * @param bundleContext
     *            the OSGi context associated to the bundle
     */
    public void activate(BundleContext bundleContext)
    {
        // store the bundle context
        this.bundleContext = bundleContext;

        // set the number of done trials to 0
        this.trialsDone = 0;

        // create the register to driver map
        this.register2Driver = new ConcurrentHashMap<ModbusRegisterInfo, Set<ModbusDriverInstance>>();

        // create the driver to register map
        this.driver2Register = new ConcurrentHashMap<ModbusDriverInstance, Set<ModbusRegisterInfo>>();

        // create the gateway address to register map
        this.gatewayAddress2Registers = new ConcurrentHashMap<String, Set<ModbusRegisterInfo>>();

        // create the connection pool (one per gateway address)
        this.connectionPool = new ConcurrentHashMap<String, MasterConnection>();

        // create the pool of modbus pollers.
        this.pollerPool = new ConcurrentHashMap<String, ModbusPoller>();

        // log the activation
        this.logger.info("Activated...");
    }

    /**
     * Called whenever the bundle is deactivated by the framework
     */
    public void deactivate()
    {
        // log
        this.logger.info("Deactivating...");

        // stop the modbus pollers
        this.stopPollers();

        // delete the poller pool (unRegister already stops poller threads)
        this.pollerPool = null;

        // unregister the driver services
        this.unRegister();

        // store the bundle context
        this.bundleContext = null;

        // delete the register to driver map
        this.register2Driver = null;

        // delete the driver to register map
        this.driver2Register = null;

        // delete the gateway address to register map
        this.gatewayAddress2Registers = null;

        // close connections
        if (this.connectionPool != null)
        {
            // close all connections
            this.closeConnections();
            // delete the connection pool (one per gateway address)
            this.connectionPool = null;
        }
    }

    public void modified(BundleContext context)
    {
        // Intentionally left empty, used just to avoid bundle deactivation on
        // update.
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.osgi.service.cm.ManagedService#updated(java.util.Dictionary)
     */
    @Override
    public void updated(Dictionary<String, ?> properties)
            throws ConfigurationException
    {
        // get the bundle configuration parameters
        if (properties != null)
        {
            // try to get the baseline polling time
            String pollingTimeAsString = (String) properties
                    .get(ModbusNetworkConstants.POLLING_TIME_MILLIS);

            // check not null
            if (pollingTimeAsString != null)
            {
                // trim leading and trailing spaces
                pollingTimeAsString = pollingTimeAsString.trim();
                // parse the string
                this.pollingTimeMillis = Integer.valueOf(pollingTimeAsString);
            }

            // try to get the baseline polling time
            String betweenTrialTimeMillisAsString = (String) properties
                    .get(ModbusNetworkConstants.RETRY_PERIOD_MILLIS);

            // check not null
            if (betweenTrialTimeMillisAsString != null)
            {
                // trim leading and trailing spaces
                betweenTrialTimeMillisAsString = betweenTrialTimeMillisAsString
                        .trim();

                // parse the string
                this.betweenTrialTimeMillis = Integer
                        .valueOf(betweenTrialTimeMillisAsString);
            }
            // try to get the baseline polling time
            String numTryAsString = (String) properties
                    .get(ModbusNetworkConstants.N_RETRIES);

            // check not null
            if (numTryAsString != null)
            {
                // trim leading and trailing spaces
                numTryAsString = numTryAsString.trim();

                // parse the string
                this.nConnectionTrials = Integer.valueOf(numTryAsString);
            }

            // try to get the maxBlacklistPollingCycles
            String maxBlacklistPollingCyclesAsString = (String) properties
                    .get(ModbusNetworkConstants.BLACKLIST_CYCLE);

            // check not null
            if (maxBlacklistPollingCyclesAsString != null)
            {
                // trim maxBlacklistPollingCycles
                maxBlacklistPollingCyclesAsString = maxBlacklistPollingCyclesAsString
                        .trim();
                // parse the string
                this.maxBlacklistPollingCycles = Integer
                        .valueOf(maxBlacklistPollingCyclesAsString);
            }

            this.register();
        }

    }

    private void register()
    {
        // register the driver service if not already registered
        if ((this.bundleContext != null)
                && (this.regServiceModbusDriverImpl == null))
            this.regServiceModbusDriverImpl = this.bundleContext
                    .registerService(ModbusNetwork.class.getName(), this, null);
    }

    /**
     * Unregisters the driver from the OSGi framework
     */
    private void unRegister()
    {
        // unregister
        if (this.regServiceModbusDriverImpl != null)
        {
            this.regServiceModbusDriverImpl.unregister();
            this.regServiceModbusDriverImpl = null;
        }

    }

    /**
     * Stop all modbus pollers.
     */
    private void stopPollers()
    {
        // stop all the poller threads
        for (ModbusPoller poller : this.pollerPool.values())
        {
            poller.setRunnable(false);
        }
    }

    /**
     * Close all connections
     */
    private void closeConnections()
    {
        Collection<MasterConnection> connections = this.connectionPool.values();

        if (connections != null)
        {
            for (MasterConnection connection : connections)
            {
                if (connection.isConnected())
                    connection.close();
            }
        }

    }

    /**
     * Get a reference to the LoggerFactory.
     * 
     * @return the {@link LoggerFactory} currently bound to this service.
     */
    public LoggerFactory getLoggerFactory()
    {
        return this.loggerFactory.get();
    }

    /**
     * Provides back the set of connected gateways, identified by their IP
     * address
     * 
     * @return
     */
    public Set<String> getConnectedGateways()
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
    public synchronized Set<ModbusRegisterInfo> getGatewayRegisters(
            String gwIdentifier)
    {
        Set<ModbusRegisterInfo> currentSnapshot = Collections
                .synchronizedSet(new HashSet<ModbusRegisterInfo>());

        // return a snapshot of the registers set to avoid concurrent
        // modification exceptions in the start-up and polling phase...
        synchronized (this)
        {
            Set<ModbusRegisterInfo> allRegisterInfos = this.gatewayAddress2Registers
                    .get(gwIdentifier);
            if (allRegisterInfos != null)
            {
                for (ModbusRegisterInfo r : allRegisterInfos)
                {
                    currentSnapshot.add(r);
                }
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
    public Map<ModbusRegisterInfo, Set<ModbusDriverInstance>> getRegister2Driver()
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
    public Map<String, Set<ModbusRegisterInfo>> getGatewayAddress2Registers()
    {
        return gatewayAddress2Registers;
    }

    /**
     * @return the connectionPool
     */
    public Map<String, MasterConnection> getConnectionPool()
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
    public synchronized Object read(ModbusRegisterInfo register,
            ModbusDriverInstance driver)
    {
        Object result = null;

        // prepare the TCP connection to the gateway offering access to the
        // given register
        MasterConnection modbusConnection = this.connectionPool
                .get(register.getGatewayIdentifier());

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
        ModbusProtocolVariant variant = register.getGatewayProtocol();

        if (modbusConnection.isConnected())
        {
            // successfully connected
            this.logger.debug("Successfully connected to the Modbus TCP Slave");

            // prepare the read request using the register translator for
            // composing the right Modbus request...
            ModbusRequest readRequest = register.getXlator()
                    .getReadRequest(register.getAddress());

            // set the slave id associated to the given register
            readRequest.setUnitID(register.getSlaveId());

            // create a modbus transaction for the just created readRequest
            ModbusTransaction transaction = this.getTransaction(readRequest,
                    modbusConnection, variant);

            // try to execute the transaction and manage possible errors...
            try
            {
                transaction.execute();
            }
            catch (ModbusIOException e)
            {
                // debug
                this.logger
                        .error("Error on Modbus I/O communication for register "
                                + register + "\nException: " + e);
            }
            catch (ModbusSlaveException e)
            {
                // debug
                this.logger.error("Error on Modbus Slave, for register "
                        + register + "\nException: " + e);
            }
            catch (ModbusException e)
            {
                // debug
                this.logger.error("Error on Modbus while reading register "
                        + register + "\nException: " + e);
            }

            // get the readResponse
            ModbusResponse response = transaction.getResponse();

            // debug
            String responseAsString = response.getHexMessage();
            this.logger.debug("Received -> " + responseAsString);

            Object responseValue = register.getXlator().getValue(response);

            if (responseValue != null)
            {

                this.logger.debug("Translated into -> " + responseValue);

                // dispatch the new message...
                // retained for backward compatibility
                if (driver != null)
                {
                    driver.newMessageFromHouse(register, responseValue);
                }

                // return the result
                result = responseValue;
            }
        }
        else
        {
            // info on port usage
            this.logger.info("Using port: " + gwPort);

            // close and re-open
            this.closeAndReOpen(register.getGatewayIdentifier(),
                    register.getGatewayIPAddress(), gwPort, variant,
                    register.getSerialParameters());
        }

        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork
     * #write(it.polito.elite.dog.drivers.modbus.network.info.
     * ModbusRegisterInfo, java.lang.String)
     */
    @Override
    public boolean write(ModbusRegisterInfo register, Object commandValue)
    {
        return this.writeValue(register, commandValue, null);
    }

    @Override
    public boolean writeBit(ModbusRegisterInfo register, Object commandValue,
            Object registerValue)
    {
        return this.writeValue(register, commandValue, registerValue);
    }

    /**
     * Write a given value to the given register. If a register value is
     * specified and the register data size is BIT, write the bit value inside
     * the register value and update the "actual" value on the device using the
     * "modified" register value.
     * 
     * @param register
     *            The register to write
     * @param commandValue
     *            The command value to write
     * @param registerValue
     *            The value of the register to "modify" for BIT registers.
     */
    private boolean writeValue(ModbusRegisterInfo register, Object commandValue,
            Object registerValue)
    {
        boolean written = false;

        // prepare the TCP connection to the gateway offering access to the
        // given register
        MasterConnection modbusConnection = this.connectionPool
                .get(register.getGatewayIdentifier());

        // check not null
        if (modbusConnection != null)
        {

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
            ModbusProtocolVariant variant = register.getGatewayProtocol();

            if (modbusConnection.isConnected())
            {
                // successfully connected
                this.logger.debug(
                        "Successfully connected to the Modbus TCP Slave");

                ModbusRequest writeRequest = null;

                if (registerValue != null
                        && register.getXlator() instanceof BaseRegXlator)
                {
                    writeRequest = ((BaseRegXlator) register.getXlator())
                            .getWriteRequest(register.getAddress(),
                                    commandValue, registerValue);

                }
                else
                {
                    writeRequest = register.getXlator().getWriteRequest(
                            register.getAddress(), commandValue);
                }

                // if the write request is null, than the register is not
                // writable
                if (writeRequest != null)
                {
                    writeRequest.setUnitID(register.getSlaveId());
                    writeRequest.setTransactionID(1);

                    // create a modbus tcp transaction for the just created
                    // writeRequest
                    ModbusTransaction transaction = this.getTransaction(
                            writeRequest, modbusConnection, variant);

                    // try to execute the transaction and manage possible
                    // errors...
                    try
                    {
                        transaction.execute();
                        written = true;
                    }
                    catch (ModbusIOException e)
                    {
                        // debug
                        this.logger.error(
                                "Error on Modbus I/O communication for register "
                                        + register.getAddress()
                                        + "\nException: " + e);
                    }
                    catch (ModbusSlaveException e)
                    {
                        // debug
                        this.logger.error("Error on Modbus Slave, for register "
                                + register.getAddress() + "\nException: " + e);
                    }
                    catch (ModbusException e)
                    {
                        // debug
                        this.logger
                                .error("Error on Modbus while writing register "
                                        + register.getAddress()
                                        + "\nException: " + e);
                    }
                }
            }
            else
            {

                // info on port usage
                this.logger.info(
                        "The gateway {} is currently not connected, attempting re-connection",
                        register.getGatewayIdentifier());

                // close and re-open
                this.closeAndReOpen(register.getGatewayIdentifier(),
                        register.getGatewayIPAddress(), gwPort, variant,
                        register.getSerialParameters());
            }
        }

        return written;
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
    public synchronized void addDriver(ModbusRegisterInfo register,
            ModbusDriverInstance driver)
    {
        // get the register gateway address
        InetAddress gwAddress = register.getGatewayIPAddress();

        // get the register serial parameters
        SerialParameters serialParameters = register.getSerialParameters();
        // get the register protocol variant
        ModbusProtocolVariant gwProtocolVariant = register.getGatewayProtocol();

        // get the register gateway port
        String gwPortAsString = register.getGatewayPort();
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
        this.logger.info("Adding register {} on slave {} using gateway {}.",
                register.getAddress(), register.getSlaveId(),
                register.getGatewayIdentifier());

        // adds a given register-driver association
        Set<ModbusDriverInstance> drivers = this.register2Driver.get(register);

        // create the set if not yet existing
        if (drivers == null)
        {
            drivers = new HashSet<ModbusDriverInstance>();
            this.register2Driver.put(register, drivers);
        }
        // add the driver
        drivers.add(driver);

        // fills the reverse map
        Set<ModbusRegisterInfo> driverRegisters = this.driver2Register
                .get(driver);
        if (driverRegisters == null)
        {
            // create the new set of registers associated to the given driver
            driverRegisters = new HashSet<ModbusRegisterInfo>();
            this.driver2Register.put(driver, driverRegisters);
        }
        driverRegisters.add(register);

        // fill the server to register map
        Set<ModbusRegisterInfo> registers = this.gatewayAddress2Registers
                .get(register.getGatewayIdentifier());
        if (registers == null)
        {
            // create the new entry
            registers = new TreeSet<ModbusRegisterInfo>();
            this.gatewayAddress2Registers.put(register.getGatewayIdentifier(),
                    registers);
        }

        synchronized (this.connectionPool)
        {
            // handle the modbus connection
            if (!this.connectionPool
                    .containsKey(register.getGatewayIdentifier()))
            {
                // open the modbus connection
                switch (gwProtocolVariant)
                {
                    case TCP:
                    case RTU_TCP:
                    case RTU_UDP:
                        this.openConnection(register.getGatewayIdentifier(),
                                gwAddress, gwPort, gwProtocolVariant, false);
                        break;
                    case RTU:
                        this.openConnection(register.getGatewayIdentifier(),
                                gwAddress, gwPort, gwProtocolVariant,
                                serialParameters, false);
                        break;
                }

            }
        }

        // check if a poller is already available or not
        ModbusPoller poller = this.pollerPool
                .get(register.getGatewayIdentifier());

        // if no poller is currently handling the gateway, then create a new
        // one
        if (poller == null)
        {
            // create a new poller
            poller = new ModbusPoller(this, register.getGatewayIdentifier());

            // add the thread to the poller pool
            this.pollerPool.put(register.getGatewayIdentifier(), poller);

            // start polling
            poller.start();
        }

        // add the register entry
        if (registers != null)
        {
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
    public void removeDriver(ModbusRegisterInfo register,
            ModbusDriverInstance driver)
    {
        // get the driver set for the register
        Set<ModbusDriverInstance> drivers = this.register2Driver.get(register);

        if (drivers != null)
        {
            // removes a given register-driver association
            if (drivers.remove(driver))
            {
                // if the driver was the last, remove the entry
                if (drivers.size() == 0)
                {
                    this.register2Driver.remove(register);
                }

                // removes the register from the corresponding set
                Set<ModbusRegisterInfo> driverRegisters = this.driver2Register
                        .get(driver);
                driverRegisters.remove(register);

                // if after removal the set is empty, removes the reverse map
                // entry
                if (driverRegisters.isEmpty())
                    this.driver2Register.remove(driver);
            }

            // remove the register entry from the server to register map
            Set<ModbusRegisterInfo> serverRegisters = this.gatewayAddress2Registers
                    .get(register.getGatewayIdentifier());
            if (serverRegisters != null)
            {
                // create the new entry
                serverRegisters.remove(register);

                // if it is the last entry in the set remove the map entry
                if (serverRegisters.isEmpty())
                {
                    this.gatewayAddress2Registers
                            .remove(register.getGatewayIdentifier());

                    // log
                    this.logger.debug("Stopping the poller thread for: {}",
                            register.getGatewayIdentifier());
                    ModbusPoller pollerToStop = this.pollerPool
                            .get(register.getGatewayIdentifier());
                    pollerToStop.setRunnable(false);

                    // log
                    this.logger.debug("Removing poller for: {}",
                            register.getGatewayIdentifier());
                    this.pollerPool.remove(register.getGatewayIdentifier());
                }
            }
        }

    }

    @Override
    public void removeDriver(ModbusDriverInstance driver)
    {
        // removes a given driver-register association
        Set<ModbusRegisterInfo> driverRegisters = this.driver2Register
                .remove(driver);

        // remove the register-to-driver and the server-to-register
        // associations
        if (driverRegisters != null)
        {
            for (ModbusRegisterInfo register : driverRegisters)
            {
                // remove the datapoint-to-driver associations
                this.register2Driver.remove(register);

                // remove the datapoints from the endpoint/datapoint association
                Set<ModbusRegisterInfo> serverRegisters = this.gatewayAddress2Registers
                        .get(register.getGatewayIdentifier());
                if (serverRegisters != null)
                {
                    // remove the entry
                    serverRegisters.remove(register);

                    // if it is the last entry in the set remove the map entry
                    if (serverRegisters.isEmpty())
                    {

                        // remove the entry
                        this.gatewayAddress2Registers
                                .remove(register.getGatewayIdentifier());

                        // stop any pending reconnection attempt
                        this.logger.debug(
                                "Removing pending reconnection attempts");
                        Future<?> pendingTimer = this.activeReconnectionTimers
                                .remove(register.getGatewayIdentifier());

                        // avoid null pointer exception
                        if (pendingTimer != null)
                        {
                            // cancel the timer
                            pendingTimer.cancel(true);
                        }

                        // stop / delete the poller as no register shall be read
                        // from the given gateway address.
                        // log
                        this.logger.debug("Stopping the poller thread for: {}",
                                register.getGatewayIdentifier());
                        ModbusPoller pollerToStop = this.pollerPool
                                .get(register.getGatewayIdentifier());

                        // check not null
                        if (pollerToStop != null)
                        {
                            pollerToStop.setRunnable(false);
                        }

                        // log
                        this.logger.debug("Removing poller for: {}",
                                register.getGatewayIdentifier());
                        this.pollerPool.remove(register.getGatewayIdentifier());

                    }
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
    private void openConnection(final String gwIdentifier,
            final InetAddress gwAddress, final int gwPort,
            final ModbusProtocolVariant gwProtocol,
            final SerialParameters serialParameters, final boolean isRetrial)
    {
        if (!this.connectionPool.containsKey(gwIdentifier))
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
                    // try connecting if it is a re-trial or if it is not a
                    // re-trial and there are no pending reconnection timers for
                    // the gateway.
                    if (isRetrial
                            || (!isRetrial && !this.activeReconnectionTimers
                                    .containsKey(gwIdentifier)))
                    {
                        connection.connect();

                        this.connectionPool.put(gwIdentifier, connection);
                    }

                }
                catch (Exception e)
                {
                    // log the connection error
                    this.logger.error(
                            "Unable to connect to the Modbus TCP Slave with Address: "
                                    + gwAddress + "\nException: " + e);

                    if ((this.trialsDone < this.nConnectionTrials)
                            || (this.nConnectionTrials == 0))
                    {
                        // log a warning
                        this.logger.warn(
                                "Unable to connect to the given Modbus gateway, retrying in "
                                        + this.betweenTrialTimeMillis + " ms");

                        // schedule a new timer to re-call the open function
                        // after
                        // the
                        // given trial timeout...
                        Runnable openConnectionTask = new Runnable()
                        {

                            @Override
                            public void run()
                            {
                                // TODO Auto-generated method stub
                                openConnection(gwIdentifier, gwAddress, gwPort,
                                        gwProtocol, serialParameters, true);
                            }

                        };
                        // schedule
                        Future<?> reconnectionTaskResult = this.reconnectionService
                                .schedule(openConnectionTask,
                                        this.betweenTrialTimeMillis,
                                        TimeUnit.MILLISECONDS);

                        // stop any pending timer for the same gateway
                        Future<?> pendingTimer = this.activeReconnectionTimers
                                .remove(gwIdentifier);

                        if (pendingTimer != null)
                        {
                            pendingTimer.cancel(true);
                        }

                        // store the future
                        this.activeReconnectionTimers.put(gwIdentifier,
                                reconnectionTaskResult);

                        // avoid incrementing the number of trials if the
                        // nConnectionTrials is equal to 0 (i.e. infinite
                        // re-trial)
                        if (this.nConnectionTrials != 0)
                            this.trialsDone++;
                    }
                    else
                    {
                        // log a fatal error
                        this.logger.error(
                                "Unable to connect to the given Modbus gateway");
                    }
                }

            }
        }
        else
        {
            // log a fatal error
            this.logger.error(
                    "Requested to open a connection towards a Gateway {} which "
                            + "is already in the pool of currently open connections",
                    gwIdentifier);
        }
    }

    /**
     * Utility method for these types of connections TCP, RTU_TCP, RTU_UDP
     * 
     * 
     */
    private void openConnection(final String gwIdentifier,
            final InetAddress gwAddress, final int gwPort,
            final ModbusProtocolVariant gwProtocol, boolean isRetrial)
    {
        openConnection(gwIdentifier, gwAddress, gwPort, gwProtocol, null,
                isRetrial);
    }

    /**
     * Closes a TCPMaster connection towards a given gateway and tries to
     * re-open it
     * 
     * @param gwAddress
     * @return
     */
    protected void closeAndReOpen(final String gwIdentifier,
            final InetAddress gwAddress, final int gwPort,
            final ModbusProtocolVariant gwProtocol,
            final SerialParameters serialParameters)
    {
        MasterConnection connection = this.connectionPool.get(gwIdentifier);

        if ((connection != null) && (!connection.isConnected()))
            connection.close();

        // remove the connection from the pool
        this.connectionPool.remove(gwIdentifier);

        // schedule a new timer to re-call the open function after the
        // given trial timeout...
        Runnable openConnectionTask = new Runnable()
        {

            @Override
            public void run()
            {
                // TODO Auto-generated method stub
                openConnection(gwIdentifier, gwAddress, gwPort, gwProtocol,
                        serialParameters, true);
            }

        };

        // check if a pending connection request already exists for the given
        // gateway
        if (this.activeReconnectionTimers.containsKey(gwIdentifier))
        {
            // check if completed
            Future<?> reconnectionTaskResult = this.activeReconnectionTimers
                    .get(gwIdentifier);

            if (reconnectionTaskResult.isDone()
                    || reconnectionTaskResult.isCancelled())
            {
                // re schedule
                reconnectionTaskResult = this.reconnectionService.schedule(
                        openConnectionTask, this.betweenTrialTimeMillis,
                        TimeUnit.MILLISECONDS);

                // store the future
                this.activeReconnectionTimers.put(gwIdentifier,
                        reconnectionTaskResult);
            }
            else
            {
                // log
                this.logger.info(
                        "A re-connection attempt is already ongoing for the gateway {}.",
                        gwIdentifier);
            }
        }
        else
        {
            // schedule
            Future<?> reconnectionTaskResult = this.reconnectionService
                    .schedule(openConnectionTask, this.betweenTrialTimeMillis,
                            TimeUnit.MILLISECONDS);

            // store the future
            this.activeReconnectionTimers.put(gwIdentifier,
                    reconnectionTaskResult);
        }
    }

    protected ModbusTransaction getTransaction(ModbusRequest request,
            MasterConnection connection, ModbusProtocolVariant protocol)
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
                ((ModbusTCPTransaction) transaction)
                        .setConnection((TCPMasterConnection) connection);

                break;
            }
            case RTU:
            {
                // create a modbus tcp transaction for the given request
                transaction = new ModbusSerialTransaction(request);

                // attach the transaction to the given connection
                ((ModbusSerialTransaction) transaction).setSerialConnection(
                        ((SerialMasterConnection) connection).getConnection());

                break;
            }
            case RTU_TCP:
            {
                // create a modbus RTU over TCP transaction for the given
                // request
                transaction = new ModbusRTUTCPTransaction(request);

                // attach the transaction to the given connection
                ((ModbusRTUTCPTransaction) transaction)
                        .setConnection((RTUTCPMasterConnection) connection);

                break;
            }
            case RTU_UDP:
            {
                // create a modbus RTU over TCP transaction for the given
                // request
                transaction = new ModbusUDPTransaction(
                        (UDPMasterConnection) connection);

                // attach the transaction to the given connection
                ((ModbusUDPTransaction) transaction).setRequest(request);
            }
        }

        // return the created transaction
        return transaction;
    }
}

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

import it.polito.elite.dog.core.library.model.diagnostics.NetworkError;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusInfo;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;
import it.polito.elite.dog.drivers.modbus.network.protocol.ModbusProtocolVariant;
import it.polito.elite.dog.drivers.modbus.network.regxlators.BaseRegXlator;
import net.wimpi.modbus.Modbus;
import net.wimpi.modbus.ModbusException;
import net.wimpi.modbus.ModbusIOException;
import net.wimpi.modbus.ModbusSlaveException;
import net.wimpi.modbus.io.ModbusTransaction;
import net.wimpi.modbus.msg.ModbusRequest;
import net.wimpi.modbus.msg.ModbusResponse;
import net.wimpi.modbus.net.MasterConnection;

import org.osgi.service.log.Logger;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A poller thread dedicated to a single Modbus gateway, be it a serial port, or
 * a remote network-level gateway.
 * 
 * Last updated on 13/03/2019.
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
    private String gatewayIdentifier;

    // the runnable flag
    private boolean runnable = true;

    // the poller logger
    private Logger logger;

    // the log identifier, unique for the class
    public static String logId = "[ModbusPoller]: ";

    // maximum time for which a register is blacklisted in terms of polling
    // cycles
    public int max_time_in_blacklist;

    // the register blacklist
    public Map<ModbusRegisterInfo, Integer> blacklist;

    public ModbusPoller(ModbusDriverImpl modbusDriverImpl,
            String gatewayIdentifier)
    {
        // store a reference to the poller driver
        this.driver = modbusDriverImpl;

        // init the logger
        this.logger = this.driver.getLoggerFactory()
                .getLogger(ModbusPoller.class);

        // store the gateway address
        this.gatewayIdentifier = gatewayIdentifier;

        // init the blacklist
        this.blacklist = new HashMap<ModbusRegisterInfo, Integer>();

        // get the maximum time for which a register shall be in the blacklist
        // after such a number of polling cycles the register will be
        // white-listed and re-inserted in the "polling" set.
        max_time_in_blacklist = modbusDriverImpl.getMaxBlacklistPollingCycles();
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
            this.logger.debug("Starting a new polling cycle...");

            // get the set of datapoints and read
            Set<ModbusRegisterInfo> registers = this.driver
                    .getGatewayRegisters(this.gatewayIdentifier);

            // check not null
            if (registers != null)
            {
                // debug information (can be removed for optimization purposes)
                Integer allRegisters = registers.size();

                // remove broken registers
                this.filterRegisters(registers);

                // debug the number of read registers
                this.logger.debug("Trying to read " + registers.size() + "/"
                        + allRegisters + " registers");

                // read the registers and retrieve any "broken" or "unreadable"
                // register
                Set<ModbusRegisterInfo> brokenRegisters = this
                        .readAll(registers);

                // add broken registers to the blacklist and remove
                // registers in the blacklist that have reached the
                // max_time_in_blacklist.
                this.handleBrokenRegisters(brokenRegisters);

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
                this.logger.warn("Interrupted exception: " + e);
            }

        }

        // auto-reset the state at runnable...
        this.runnable = true;
    }

    /**
     * Handles broken registers, inserting them in the blacklist, while removing
     * items that have reached the max_time_in_blacklist value.
     * 
     * @param brokenRegisters
     */
    private void handleBrokenRegisters(Set<ModbusRegisterInfo> brokenRegisters)
    {

        // get the blacklist iterator (it allows to remove items while
        // iterating)
        Iterator<ModbusRegisterInfo> it = this.blacklist.keySet().iterator();

        // iterate over the list items
        while (it.hasNext())
        {
            // get the current item
            ModbusRegisterInfo register = it.next();
            // get the number of polling cycles for which the register is still
            // blacklisted
            int cycles_passed = this.blacklist.get(register);

            // if the blacklist time has ended, restore the register
            if (cycles_passed == 0)
            {
                // debug
                this.logger.debug(max_time_in_blacklist
                        + " cycles are passed, removing from blacklist reg: "
                        + register.getAddress() + " (slaveId "
                        + register.getSlaveId() + ")");
                // restore the register
                it.remove();

            }
            else
            {
                // update the polling cycles count for the register
                this.blacklist.put(register, cycles_passed - 1);
            }

        }

        // debug
        this.logger.debug(brokenRegisters.size() + " new broken registers");

        // debug
        if (brokenRegisters.size() == 0)
        {
            this.logger.debug(" no broken registers");
        }
        else
        {
            // add broken registers
            for (ModbusRegisterInfo register2 : brokenRegisters)
            {
                // debug
                this.logger.debug(" broken reg: " + register2.getAddress()
                        + " (slaveId " + register2.getSlaveId() + ")");
                // add the current broken register to the blacklist
                this.blacklist.put(register2, max_time_in_blacklist);
            }
        }

    }

    /**
     * Filters the given list of registers by removing the ones inserted in the
     * blacklist.
     * 
     * @param registers
     *            The list of registers to be filtered.
     */
    private void filterRegisters(Set<ModbusRegisterInfo> registers)
    {
        // get an iterator over the set of registers to filter
        Iterator<ModbusRegisterInfo> it = registers.iterator();

        // iterate over the set
        while (it.hasNext())
        {
            // get the current register
            ModbusRegisterInfo register = it.next();

            // remove the register if contained in the blacklist
            if (this.blacklist.containsKey(register))
                it.remove();
        }

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
    private Set<ModbusRegisterInfo> readAll(
            final Set<ModbusRegisterInfo> registers)
    {

        // create the set of unreadable or broken registers
        Set<ModbusRegisterInfo> brokenRegisters = new HashSet<ModbusRegisterInfo>();

        // read only if some register has to be read
        if ((registers != null) && (!registers.isEmpty()))
        {
            // the read success flag
            boolean read = false;
            NetworkError error = null;

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
            ModbusProtocolVariant variant = mInfo.getGatewayProtocol();

            // get the gateway identifier
            String gwIdentifier = mInfo.getGatewayIdentifier();

            // prepare the connection to the gateway offering access to the
            // given register
            MasterConnection modbusConnection = this.driver.getConnectionPool()
                    .get(gwIdentifier);

            if ((modbusConnection != null) && (modbusConnection.isConnected()))
            {
                // successfully connected
                this.logger.debug(
                        "Successfully connected to the Modbus TCP Slave");

                synchronized (registers)
                {
                    // iterate in order
                    Iterator<ModbusRegisterInfo> regIterator = registers
                            .iterator();
                    while (regIterator.hasNext())
                    {
                        // set the read flag at false
                        read = false;
                        // set the detected error at null
                        error = null;

                        // get the current register
                        ModbusRegisterInfo register = regIterator.next();

                        // debug for read register
                        this.logger.debug("Querying register: "
                                + register.getAddress() + " on slave "
                                + register.getSlaveId() + " of type "
                                + ((BaseRegXlator) register.getXlator())
                                        .getRegisterType()
                                + " and size "
                                + ((BaseRegXlator) register.getXlator())
                                        .getRegisterSize());

                        // prepare the read request using the register
                        // translator
                        // for composing the right Modbus request...
                        ModbusRequest readRequest = register.getXlator()
                                .getReadRequest(register.getAddress());

                        // set the slave id associated to the given register
                        readRequest.setUnitID(register.getSlaveId());

                        // debug
                        this.logger
                                .debug("Sent: " + readRequest.getHexMessage());

                        // create a modbus tcp transaction for the just created
                        // readRequest
                        ModbusTransaction transaction = this.driver
                                .getTransaction(readRequest, modbusConnection,
                                        variant);

                        // get the initial transaction id
                        int firstTransactionId = transaction.getTransactionID();

                        // try to execute the transaction and manage possible
                        // errors...
                        try
                        {
                            transaction.execute();

                            // get the readResponse
                            ModbusResponse response = transaction.getResponse();

                            // response might be null if the transaction
                            // fails
                            if (response != null)
                            {
                                // -- check the transaction id --
                                int responseTransactionId = response
                                        .getTransactionID();
                                // if the response transaction id is the
                                // default, then the transport might not support
                                // transaction IDs (available on modbus TCP
                                // only).
                                // FIXME: according to Jamod implementation, the
                                // first part of the check on the transaction id
                                // should use a <= instead of a <, as in theory
                                // < allows for reading values belonging to a
                                // previous transaction. Nevertheless there is
                                // evidence of devices providing transaction IDs
                                // misaligned by 1 with respect to requests.
                                // leaving the < in the check may lead to wrong
                                // data assignment in particular cases.
                                if (responseTransactionId != Modbus.DEFAULT_TRANSACTION_ID
                                        && (responseTransactionId < firstTransactionId
                                                || responseTransactionId > transaction
                                                        .getTransactionID())
                                        && this.driver
                                                .isTransactionCheckEnabled())
                                {
                                    this.logger.error(
                                            "Received response with wrong transaction ID, ignoring it. Expected: "
                                                    + transaction
                                                            .getTransactionID()
                                                    + " Received: "
                                                    + responseTransactionId);
                                    // cause a generic IO error
                                    // TODO: add new NetworkError for this.
                                    throw new ModbusIOException();
                                }
                                else
                                {
                                    this.logger.debug("Transaction Id: "
                                            + responseTransactionId
                                            + " - sent: "
                                            + transaction.getTransactionID());
                                    // handle possible number format exceptions
                                    // generated on translation of register
                                    // values. Errors might still occur for non-
                                    // numeric values
                                    try
                                    {
                                        // debug
                                        String responseAsString = response
                                                .getHexMessage();
                                        this.logger.debug("Received -> "
                                                + responseAsString);

                                        // get the response value
                                        Object value = register.getXlator()
                                                .getValue(response);

                                        this.logger.debug(
                                                "Translated into -> " + value);

                                        // check not null
                                        if (value != null)
                                        {

                                            // dispatch the new message...
                                            // TODO: check if this shall be done
                                            // asynchronously
                                            Set<ModbusDriverInstance> drivers = this.driver
                                                    .getRegister2Driver()
                                                    .get(register);

                                            if (drivers != null)
                                            {
                                                for (ModbusDriverInstance driver : drivers)
                                                {
                                                    // notify the value
                                                    driver.newMessageFromHouse(
                                                            register, value);
                                                    // set the device as
                                                    // reachable
                                                    driver.setReachable(
                                                            register, true,
                                                            error);
                                                }
                                            }

                                            // successful read operation!
                                            read = true;
                                        }
                                    }
                                    catch (NumberFormatException nfe)
                                    {
                                        this.logger.warn(
                                                "Unable to translate modbus register value. Received value: {}",
                                                response.getHexMessage());
                                        // set the error
                                        error = NetworkError.VALUE_TRANSLATION;
                                    }
                                }
                            }
                        }
                        catch (ModbusIOException mioe)
                        {
                            // error
                            this.logger.warn(
                                    "Error on Modbus I/O communication for register "
                                            + register.getAddress()
                                            + " on slave "
                                            + register.getSlaveId()
                                            + " gateway "
                                            + register.getGatewayIdentifier()
                                            + "\nException: " + mioe);

                            // set the error status
                            if (mioe.isEOF())
                            {
                                error = NetworkError.EOF;
                            }
                            else
                            {
                                error = NetworkError.GENERIC_IO;
                            }
                            // ignore the error and do not trigger reconnection.
                        }
                        catch (ModbusSlaveException mse)
                        {
                            // debug
                            this.logger
                                    .warn("Error on Modbus Slave, for register "
                                            + register.getAddress()
                                            + " on slave "
                                            + register.getSlaveId()
                                            + " gateway "
                                            + register.getGatewayIdentifier()
                                            + "\nException: " + mse);
                            // set the error status
                            switch (mse.getType())
                            {
                                case Modbus.ILLEGAL_FUNCTION_EXCEPTION:
                                {
                                    error = NetworkError.ILLEGAL_FUNCTION;
                                    break;
                                }
                                case Modbus.ILLEGAL_ADDRESS_EXCEPTION:
                                {
                                    error = NetworkError.ILLEGAL_ADDRESS;
                                    break;
                                }
                                case Modbus.ILLEGAL_VALUE_EXCEPTION:
                                {
                                    error = NetworkError.ILLEGAL_VALUE;
                                    break;
                                }
                                default:
                                {
                                    error = NetworkError.GENERIC_IO;
                                    break;
                                }
                            }
                            // ignore the error and do not trigger reconnection.
                            // close the connection
                            // modbusConnection.close();

                        }
                        catch (ModbusException e)
                        {
                            // debug
                            this.logger.error(
                                    "Error on Modbus while reading register "
                                            + register.getAddress()
                                            + " on slave "
                                            + register.getSlaveId()
                                            + " gateway "
                                            + register.getGatewayIdentifier()
                                            + "\nException: " + e);
                            // set the error status
                            error = NetworkError.GENERIC_IO;
                            // close the connection
                            modbusConnection.close();

                        }
                        finally
                        {
                            // if the read operation was not successful, mark
                            // the current register as "broken"
                            if (read == false)
                            {
                                // add the current register o the set of broken
                                // registers
                                brokenRegisters.add(register);

                                // check if all registers on this line are
                                // broken
                                if (registers.size() == brokenRegisters.size())
                                {
                                    // trigger reconnection by
                                    // closing the connection
                                    modbusConnection.close();
                                }

                                // notify device unreachable
                                this.notifyUnreachableRegister(register, error);
                            }

                        }

                        // re-connect if the connection is closed
                        if (!modbusConnection.isConnected())
                        {
                            // info on port usage
                            this.logger.info("Trigger reconnection to: {}",
                                    gwIdentifier);

                            // close and re-open
                            this.driver.closeAndReOpen(gwIdentifier, gwAddress,
                                    gwPort, variant,
                                    mInfo.getSerialParameters());
                            break;
                        }

                        try
                        {
                            // minimum time between subsequent register read
                            Thread.sleep((register.getRequestGapMillis() > 0)
                                    ? register.getRequestGapMillis()
                                    : ModbusInfo.DEFAULT_REQUEST_GAP_MILLIS);
                        }
                        catch (InterruptedException e)
                        {
                            // log the exception
                            this.logger.warn(
                                    "ModbusPoller was interrupted:\n {}", e);
                        }
                    }
                }
            }
            else
            {
                this.logger.debug("The gateway {} is currently unreachable.",
                        gwIdentifier);

                // notify devices unreachable
                // iterate in order
                Iterator<ModbusRegisterInfo> regIterator = registers.iterator();
                while (regIterator.hasNext())
                {
                    this.notifyUnreachableRegister(regIterator.next(),
                            NetworkError.UNREACHABLE);
                }
            }
        }

        // return the list of registers marked as "broken"
        return brokenRegisters;

    }

    private void notifyUnreachableRegister(ModbusRegisterInfo register,
            NetworkError error)
    {
        // get the set of drivers to notify
        Set<ModbusDriverInstance> drivers = this.driver.getRegister2Driver()
                .get(register);

        // if at least a driver is registered for the current
        // register,
        // iterate over the driver list
        if (drivers != null)
        {
            // log the number of drivers to notify
            logger.info("Number of drivers to notify: " + drivers.size());
            
            for (ModbusDriverInstance driver : drivers)
            {
                // log the notified device
                logger.info("Notified driver: " + driver + " for device: "
                        + driver.device.getDeviceId());
                // notify device unreachable
                driver.setReachable(register, false, error);
            }
        }
    }
}
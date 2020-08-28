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
package it.polito.elite.dog.drivers.modbus.network;

import java.util.Dictionary;

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.log.Logger;

/**
 * A class that stores the current configuration of a Modbus network driver and
 * handles parsing of configuration properties.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Aug 28, 2020
 */
public class ModbusDriverConfiguration
{
    // the baseline pollingTime adopted if no server-specific setting is given
    private int pollingTimeMillis;
    // the number of connection trials
    private int nConnectionTrials;
    // the time that must occur between two subsequent trials
    private int betweenTrialTimeMillis;
    // number of cycles that a broken register will be in the blacklist
    private int maxBlacklistPollingCycles;
    // the number of retries to perform within a modbus transaction
    private int nRetriesWithinTransaction;
    // the delay between subsequent retries within a transaction
    private int delayBetweenRetriesWithinTransactionMillis;
    // the flag for activating / de activating the transaction check on modbus
    // tcp
    private boolean transactionCheckEnabled;
    // the maximum difference between transaction IDs, only checked if
    // transactionCheckEnabled is true
    // if set to 0 then stricti (Modbus-compliant) transaction check will be
    // performed
    private int maxTransactionDelta;
    // a flag to trigger disconnection from the modbus slave on transaction id
    // errors
    private boolean disconnectOnTransactionErrors;

    // the class logger
    private Logger logger;

    /**
     * Create a new instance of {@link ModbusDriverConfiguration} using the
     * given logger to display diagnostic messages.
     * 
     * @param logger
     *            The {@link Logger} instance to use for displaying diagnostic
     *            messages
     */
    public ModbusDriverConfiguration(Logger logger)
    {
        this.logger = logger;

        // initialize defaults
        this.pollingTimeMillis = ModbusDriverImpl.DEFAULT_POLLING_TIME_MILLIS;
        this.nConnectionTrials = ModbusDriverImpl.DEFAULT_N_RECONNECTION_ATTEMPTS;
        this.betweenTrialTimeMillis = ModbusDriverImpl.DEFAULT_RECONNECTION_INTERVAL_MILLIS;
        this.maxBlacklistPollingCycles = ModbusDriverImpl.DEFAULT_BLACKLIST_DURATION;
        this.nRetriesWithinTransaction = ModbusDriverImpl.DEFAULT_RETRIES_WITHIN_TRANSACTION;
        this.delayBetweenRetriesWithinTransactionMillis = ModbusDriverImpl.DEFAULT_RETRY_DELAY_WITHIN_TRANSACTION_MILLIS;
        this.transactionCheckEnabled = ModbusDriverImpl.DEFAULT_TRANSACTION_CHECK_ENABLED;
        this.maxTransactionDelta = ModbusDriverImpl.DEFAULT_MAX_TRANSACTION_ID_DELTA;
        this.disconnectOnTransactionErrors = ModbusDriverImpl.DEFAULT_DISCONNECT_ON_TRANSACTION_CHECK_FAILURE;
    }

    /**
     * Provide the polling time (i.e., the interval between subsequent polling
     * cycles) in milliseconds.
     * 
     * @return The interval between subsequent polling cycles in milliseconds.
     */
    public int getPollingTimeMillis()
    {
        return pollingTimeMillis;
    }

    /**
     * Provide the number of reconnection attempts should be performed on a
     * failing modbus connection.
     * 
     * @return The number of reconnection attempts to perform, 0 means infinite.
     */
    public int getNumberOfConnectionTrials()
    {
        return nConnectionTrials;
    }

    /**
     * Provide the period to wait before attempting a re-connection, in
     * milliseconds.
     * 
     * @return The period to wait before attempting a re-connection, in
     *         milliseconds.
     */
    public int getBetweenTrialTimeMillis()
    {
        return betweenTrialTimeMillis;
    }

    /**
     * Provide the number of communication attempts that should be performed
     * during a single modbus transaction.
     * 
     * @return The number of communication attempts within a single modbus
     *         transaction.
     */
    public int getNumberOfRetriesWithinTransaction()
    {
        return nRetriesWithinTransaction;
    }

    /**
     * Provide the delay between two subsequent communication attempts within
     * the same modbus transaction, in milliseconds.
     * 
     * @return The delay between two subsequent communication attempts
     */
    public int getDelayBetweenRetriesWithinTransactionMillis()
    {
        return delayBetweenRetriesWithinTransactionMillis;
    }

    /**
     * Provide the number of polling cycles in which a failing register should
     * not be polled, e.g., to avoid long delays due to unresponsive registers.
     * 
     * @return Tthe number of polling cycles in which a failing register should
     *         not be polled.
     */
    public int getMaxBlacklistPollingCycles()
    {
        return maxBlacklistPollingCycles;
    }

    /**
     * Provide the flag enabling (or not) the transaction id check for Modbus
     * TCP connections, only.
     * 
     * @return True if transaction ids should be checked, false otherwise.
     */
    public boolean isTransactionCheckEnabled()
    {
        return transactionCheckEnabled;
    }

    /**
     * Provide the maximum delta between sent and received transaction IDs that
     * can be considered acceptable. Please notice that in general the only
     * acceptable value is 0, however some devices might exhibit special
     * behaviors, e.g., always responding with a transaction id lower (by 1)
     * than the one provided i the request.
     * 
     * @return The maximum delta between sent and received transaction IDs.
     */
    public int getMaxTransactionDelta()
    {
        return maxTransactionDelta;
    }

    /**
     * Provides a flag signalling if a transaction ID check failure should
     * trigger a re-connection or not.
     * 
     * @return True if the driver should drop the connection and re-connect
     *         after a transaction ID check failure.
     */
    public boolean isDisconnectOnTransactionErrors()
    {
        return disconnectOnTransactionErrors;
    }

    /**
     * Parses the dictionary provided by the OSGi config admin service to fill
     * the driver configuration with provided values.
     * 
     * @param properties
     *            The updated configuration.
     * @throws ConfigurationException
     *             If something goes wrong during the configuration.
     */
    public void updated(Dictionary<String, ?> properties)
            throws ConfigurationException
    {
        // get the bundle configuration parameters
        if (properties != null)
        {
            // get the baseline polling time
            this.pollingTimeMillis = this.parseIntValueOrDefault(
                    (String) properties
                            .get(ModbusNetworkConstants.POLLING_TIME_MILLIS),
                    ModbusDriverImpl.DEFAULT_POLLING_TIME_MILLIS);
            // get the time to wait between re-connection attempts
            this.betweenTrialTimeMillis = this.parseIntValueOrDefault(
                    (String) properties
                            .get(ModbusNetworkConstants.RETRY_PERIOD_MILLIS),
                    ModbusDriverImpl.DEFAULT_RECONNECTION_INTERVAL_MILLIS);
            // get the number of attempt to perform before marking a connection
            // as dead
            this.nConnectionTrials = this.parseIntValueOrDefault(
                    (String) properties.get(ModbusNetworkConstants.N_RETRIES),
                    ModbusDriverImpl.DEFAULT_N_RECONNECTION_ATTEMPTS);
            // get the number of polling cycles in which a failing register
            // should not be polled
            this.maxBlacklistPollingCycles = this.parseIntValueOrDefault(
                    (String) properties
                            .get(ModbusNetworkConstants.BLACKLIST_CYCLE),
                    ModbusDriverImpl.DEFAULT_BLACKLIST_DURATION);
            // get the number of communication attempts performed within a
            // single modbus transaction
            this.nRetriesWithinTransaction = this.parseIntValueOrDefault(
                    (String) properties.get(
                            ModbusNetworkConstants.N_RETRIES_PER_TRANSACTION),
                    ModbusDriverImpl.DEFAULT_RETRIES_WITHIN_TRANSACTION);
            // get the delay between communication attempts within a single
            // modbus transaction
            this.delayBetweenRetriesWithinTransactionMillis = this
                    .parseIntValueOrDefault((String) properties.get(
                            ModbusNetworkConstants.RETRY_DELAY_WITHIN_TRANSACTION),
                            ModbusDriverImpl.DEFAULT_RETRY_DELAY_WITHIN_TRANSACTION_MILLIS);
            // get the transaction check flag, if true received transaction ids
            // are checked against sent transaction ids. Otherwise they are
            // ignored. This setting is only used for modbus tcp connections.
            this.transactionCheckEnabled = this.parseBooleanValueOrDefault(
                    (String) properties.get(
                            ModbusNetworkConstants.ENABLE_TRANSACTION_CHECK),
                    ModbusDriverImpl.DEFAULT_TRANSACTION_CHECK_ENABLED);
            // get the allowed delta between the request and response
            // transaction IDs
            this.maxTransactionDelta = this.parseIntValueOrDefault(
                    (String) properties.get(
                            ModbusNetworkConstants.MAX_TRANSACTION_ID_DELTA),
                    ModbusDriverImpl.DEFAULT_MAX_TRANSACTION_ID_DELTA);
            // get the connection close flag on transaction check failure
            this.disconnectOnTransactionErrors = this
                    .parseBooleanValueOrDefault((String) properties.get(
                            ModbusNetworkConstants.DISCONNECT_ON_TRANSACTION_CHECK_FAILURE),
                            ModbusDriverImpl.DEFAULT_DISCONNECT_ON_TRANSACTION_CHECK_FAILURE);

            // debug
            this.logger.debug("Loaded configuration: \n{}", this.toString());

        }
    }

    private int parseIntValueOrDefault(String valueToParse, int defaultValue)
    {
        int value = defaultValue;

        if (valueToParse != null)
        {
            try
            {
                value = Integer.parseInt(valueToParse.trim());
            }
            catch (NumberFormatException nfe)
            {
                // log the error and use the default
                this.logger.warn("Unable to parse value: {}, using default: {}",
                        valueToParse, defaultValue);
            }
        }

        return value;
    }

    private boolean parseBooleanValueOrDefault(String valueToParse,
            boolean defaultValue)
    {
        boolean value = defaultValue;

        if (valueToParse != null)
        {
            value = Boolean.parseBoolean(valueToParse.trim());
        }

        return value;
    }

    public String toString()
    {
        return String.format("=========== Modbus Driver "
                + "Configuration ===========\n%s\t\t\t: %s"
                + "\n%s\t\t\t: %s\n%s\t\t\t\t: %s\n%s\t\t\t\t: %s"
                + "\n%s\t\t: %s\n%s\t: %s\n%s\t\t\t: %s\n%s\t: %s\n%s\t: %s"
                + "\n===============================" + "====================",
                ModbusNetworkConstants.POLLING_TIME_MILLIS,
                this.pollingTimeMillis,
                ModbusNetworkConstants.RETRY_PERIOD_MILLIS,
                this.betweenTrialTimeMillis, ModbusNetworkConstants.N_RETRIES,
                this.nConnectionTrials, ModbusNetworkConstants.BLACKLIST_CYCLE,
                this.maxBlacklistPollingCycles,
                ModbusNetworkConstants.N_RETRIES_PER_TRANSACTION,
                this.nRetriesWithinTransaction,
                ModbusNetworkConstants.RETRY_DELAY_WITHIN_TRANSACTION,
                this.delayBetweenRetriesWithinTransactionMillis,
                ModbusNetworkConstants.ENABLE_TRANSACTION_CHECK,
                this.transactionCheckEnabled,
                ModbusNetworkConstants.MAX_TRANSACTION_ID_DELTA,
                this.maxTransactionDelta,
                ModbusNetworkConstants.DISCONNECT_ON_TRANSACTION_CHECK_FAILURE,
                this.disconnectOnTransactionErrors);
    }
}

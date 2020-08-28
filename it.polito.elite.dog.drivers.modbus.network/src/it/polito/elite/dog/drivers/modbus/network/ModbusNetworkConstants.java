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
package it.polito.elite.dog.drivers.modbus.network;

/**
 * A class defining the configuration options for the Modbus Network Driver.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Jan 31, 2019
 */
public class ModbusNetworkConstants
{
    // The modbus polling time
    public static final String POLLING_TIME_MILLIS = "pollingTimeMillis";
    // The polling gap, i.e. the minimum time between subsequant queries to the
    // same slave.
    public static final String POLLING_GAP_MILLIS = "pollingGapMillis";
    // The time between subsequent connection trials
    public static final String RETRY_PERIOD_MILLIS = "retryPeriodMillis";
    // The number of retries to attempt
    public static final String N_RETRIES = "nRetries";
    // The number of polling cycles for which a failing register is blacklisted
    public static final String BLACKLIST_CYCLE = "blacklistCycle";
    // The number of retries per transaction
    public static final String N_RETRIES_PER_TRANSACTION = "nRetriesWithinTransaction";
    // The delay between retries within a single transaction
    public static final String RETRY_DELAY_WITHIN_TRANSACTION = "retryDelayWithinTransactionMillis";
    // the transaction check enable
    public static final String ENABLE_TRANSACTION_CHECK = "enableTransactionCheck";
    // the maximum delta between request and response transaction IDs
    public static final String MAX_TRANSACTION_ID_DELTA = "maxDeltaBetweenRequestResponseIDs";
    // the flag to force the driver to disconnect on transaction check failures
    public static final Object DISCONNECT_ON_TRANSACTION_CHECK_FAILURE = "disconnectOnTransactionCheckFailure";

}

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

import it.polito.elite.dog.core.library.model.AbstractDevice;
import it.polito.elite.dog.core.library.model.CNParameters;
import it.polito.elite.dog.core.library.model.ControllableDevice;
import it.polito.elite.dog.core.library.model.DeviceStatus;
import it.polito.elite.dog.core.library.model.StatefulDevice;
import it.polito.elite.dog.core.library.model.diagnostics.DeviceDiagnostics;
import it.polito.elite.dog.core.library.model.diagnostics.NetworkError;
import it.polito.elite.dog.core.library.util.ElementDescription;
import it.polito.elite.dog.drivers.modbus.network.info.BytePositionEnum;
import it.polito.elite.dog.drivers.modbus.network.info.DataSizeEnum;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusInfo;
import it.polito.elite.dog.drivers.modbus.network.info.ModbusRegisterInfo;
import it.polito.elite.dog.drivers.modbus.network.info.OrderEnum;
import it.polito.elite.dog.drivers.modbus.network.info.RegisterTypeEnum;
import it.polito.elite.dog.drivers.modbus.network.interfaces.ModbusNetwork;
import it.polito.elite.dog.drivers.modbus.network.protocol.ModbusProtocolVariant;
import it.polito.elite.dog.drivers.modbus.network.regxlators.BaseRegXlator;
import it.polito.elite.dog.drivers.modbus.network.regxlators.RegXlatorTypes;
import net.wimpi.modbus.util.SerialParameters;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.device.Device;
import org.osgi.service.log.Logger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.util.tracker.ServiceTracker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:dario.bonino@polito.it">Dario Bonino</a>
 * @see <a href="http://elite.polito.it">http://elite.polito.it</a>
 * 
 * @since Jan 18, 2012
 */
public abstract class ModbusDriverInstance extends
        ServiceTracker<Device, ControllableDevice> implements StatefulDevice
{
    // a reference to the network driver interface to allow network-level access
    // for sub-classes
    protected ModbusNetwork network;

    // the state of the device associated to this driver
    protected DeviceStatus currentState;

    // the device associated to the driver
    protected ControllableDevice device;

    // the endpoint address associated to this device by means of the gateway
    // attribute
    protected String gwAddress;

    // the port of the endpoint address
    protected String gwPort;

    // the protocol variant (can be null)
    protected String gwProtocol;

    // Serial parameters for the serial connections (null for the other modbus
    // variants)
    protected SerialParameters serialParameters;

    // the gateway-level requestTimeout
    protected long gatewayRequestTimeout;
    // the gateway-level requestGap
    protected long gatewayRequestGap;

    // the set of currently failed registers associated wth the last error code.
    protected Map<ModbusRegisterInfo, NetworkError> failedRegisters;

    // the datapoint to notifications map
    protected Map<ModbusRegisterInfo, CNParameters> register2Notification;

    // the command2datapoint map
    protected Map<CNParameters, ModbusRegisterInfo> command2Register;

    // the instance logger
    protected Logger logger;

    /**
     * The constructor with serial parameters, useful for the serial
     * connections, provides common initialization for all the needed data
     * structures, must be called by sub-class constructors
     * 
     * @param network
     *            the network driver to use (as described by the
     *            {@link ModbusNetwork} interface.
     * @param device
     *            the device to which this driver is attached/associated
     */
    public ModbusDriverInstance(ModbusNetwork network, String gwAddress,
            String gwPort, String gwProtocol, SerialParameters serialParams,
            long gatewayRequestTimeout, long gatewayRequestGap,
            BundleContext ctx, ServiceReference<Device> deviceReference)
    {
        super(ctx, deviceReference, null);

        // create the class logger
        this.logger = ctx
                .getService(context.getServiceReference(LoggerFactory.class))
                .getLogger(ModbusDriverInstance.class);

        // store a reference to the network driver
        this.network = network;
        // store the endpoint address for the attached device
        this.gwAddress = gwAddress;
        // store the port associated to the gateway address
        this.gwPort = gwPort;
        // store the protocol type for the gateway
        this.gwProtocol = (gwProtocol != null) ? gwProtocol
                : ModbusProtocolVariant.TCP.toString();
        this.serialParameters = serialParams;
        // store the gateway-level request timeout and gap.
        this.gatewayRequestTimeout = gatewayRequestTimeout;
        this.gatewayRequestGap = gatewayRequestGap;

        // create the map needed to associate data points to notifications
        this.register2Notification = new ConcurrentHashMap<ModbusRegisterInfo, CNParameters>();

        // create the map to associate commands and data points
        this.command2Register = new ConcurrentHashMap<CNParameters, ModbusRegisterInfo>();

        // create the table of currently failed registers
        this.failedRegisters = new ConcurrentHashMap<ModbusRegisterInfo, NetworkError>();

        // open the tracker
        this.open();
    }

    /**
     * Notifies a device-specific driver of a new register value coming from the
     * underlying modbus network connection (either through polling or through
     * direct read). The updated value is contained in the given
     * {@link ModbusRegisterInfo} instance.
     * 
     * @param dataPointInfo
     *            The {@link DataPointInfo} instance representing the data point
     *            whose value has changed.
     * @param value
     *            The value to notify.
     */
    public abstract void newMessageFromHouse(ModbusRegisterInfo dataPointInfo,
            Object value);

    /*
     * (non-Javadoc)
     * 
     * @see it.polito.elite.dog.core.library.model.StatefulDevice#getState()
     */
    @Override
    public DeviceStatus getState()
    {
        // TODO Auto-generated method stub
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.osgi.util.tracker.ServiceTracker#addingService(org.osgi.framework.
     * ServiceReference)
     */
    @Override
    public ControllableDevice addingService(ServiceReference<Device> reference)
    {
        // get a reference to the device
        Device device = this.context.getService(reference);

        // check if the device is a controllable device
        if (device instanceof ControllableDevice)
        {
            // store a reference to the device
            this.device = (ControllableDevice) device;

            // register the driver on the device
            this.device.setDriver(this);

            // fill the data structures depending on the specific device
            // configuration parameters
            this.fillConfiguration();

            // call the specific configuration method, if needed
            this.specificConfiguration();

            // let subclasses initialize the state
            this.setUpDevice(this.device);

            // perform operations needed to add the device to the network driver
            // associate the device-specific driver to the network driver
            if (this.register2Notification.keySet() != null)
            {
                for (ModbusRegisterInfo register : this.register2Notification.keySet())
                    this.addToNetworkDriver(register);
            }

            return (ControllableDevice) device;
        }
        else
        {
            // unget the service
            this.context.ungetService(reference);
            // return null
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.osgi.util.tracker.ServiceTracker#removedService(org.osgi.framework.
     * ServiceReference, java.lang.Object)
     */
    @Override
    public void removedService(ServiceReference<Device> reference,
            ControllableDevice service)
    {
        // perform de-registration from the network driver
        this.network.removeDriver(this);

        // release the device
        this.context.ungetService(reference);
    }

    /**
     * Tries to retrieve the initial state of the device handled by this driver
     */
    public void getInitialState()
    {
        // for each datapoint registered with this driver, call the read command
        for (ModbusRegisterInfo register : this.register2Notification.keySet())
            this.network.read(register, this);
    }

    /**
     * @return the gwAddress
     */
    public String getGatewayAddress()
    {
        return gwAddress;
    }

    /**
     * 
     * @return the gwPort
     */
    public String getGatewayPort()
    {
        return gwPort;
    }

    /**
     * @return the gwProtocol
     */
    public String getGwProtocol()
    {
        return gwProtocol;
    }

    /**
     * @return the serialParameters
     */
    public SerialParameters getSerialParameters()
    {
        return serialParameters;
    }

    /**
     * Returns the request timeout defined at the gateway level
     * 
     * @return
     */
    public long getGatewayRequestTimeout()
    {
        return this.gatewayRequestTimeout;
    }

    /**
     * Returns the request gap defined at the gateway level
     * 
     * @return
     */
    public long getGatewayRequestGap()
    {
        return this.gatewayRequestGap;
    }

    /**
     * Get a unique identifier for the gateway to which this driver instance is
     * connected
     * 
     * @return the gateway identifier, or null.
     */
    public String getGatewayIdentifier()
    {
        String identifier = null;
        try
        {

            switch (ModbusProtocolVariant.valueOf(this.gwProtocol))
            {
                case TCP:
                {
                    identifier = "tcp://" + InetAddress
                            .getByName(this.gwAddress).getHostAddress() + ":"
                            + this.gwPort;
                    break;
                }
                case RTU:
                {
                    identifier = "rtu://" + this.serialParameters.getPortName();
                    break;
                }
                case RTU_TCP:
                {
                    identifier = "rtu_tcp://" + InetAddress
                            .getByName(this.gwAddress).getHostAddress() + ":"
                            + this.gwPort;
                    break;
                }
                case RTU_UDP:
                {
                    identifier = "rtu_udp://" + InetAddress
                            .getByName(this.gwAddress).getHostAddress() + ":"
                            + this.gwPort;
                    break;
                }
                default:
                {
                    // null
                    identifier = null;
                    break;
                }
            }
        }
        catch (UnknownHostException uhe)
        {
            // do nothing but logging
            this.logger.warn("Unable to provide gateway identifier as the host "
                    + "address is unknown.");
        }

        return identifier;
    }

    protected abstract void setUpDevice(ControllableDevice device);

    /**
     * Extending classes might implement this method to provide driver-specific
     * configurations to be done during the driver creation process, before
     * associating the device-specific driver to the network driver
     */
    protected abstract void specificConfiguration();

    /**
     * Adds a device managed by a device-specific driver instance to the
     * {@link ModbusNetwork} driver. It must be implemented by extending classes
     * and it must take care of identifying any additional information needed to
     * correctly specify the given register and to associate the corresponding
     * {@link ModbusRegisterInfo} with the proper {@link ModbusDriverImpl}
     * instance.
     * 
     * @param register
     *            the register to add as a {@link ModbusRegisterInfo} instance.
     */
    protected abstract void addToNetworkDriver(ModbusRegisterInfo register);

    /**
     * Notifies a device-specific driver of a currently unreachable register.
     * This information may be used by the driver, e.g., to trigger a device
     * registration update.
     * 
     * Must be overridden by Multiple devices and/or by devices needing to
     * handle network errors.
     * 
     * @param dataPointInfo
     *            The register whose reachability changed.
     * @param reachable
     *            The reachability status, as a boolean value (true for
     *            reachable, false otherwise).
     * @param error
     *            The error which caused a register to be marked as not
     *            reachble, null if reachable = true.
     */
    protected void setReachable(ModbusRegisterInfo dataPointInfo,
            boolean reachable, NetworkError error)
    {
        if (!reachable)
        {
            // to handle the error both the datapointInfo and the error should
            // be not null
            if (error != null && dataPointInfo != null)
            {
                // store the failed registers
                NetworkError previousError = this.failedRegisters
                        .put(dataPointInfo, error);

                // if the previous error was null, than a new error occurred and
                // should be notified
                if (previousError == null)
                {
                    this.handleNewError(dataPointInfo, error);
                }
            }
            else
            {
                this.logger.warn(
                        "Received reachability status without info or error: info:"
                                + dataPointInfo + " error: " + error);
            }

            // check whether the number of registers is equal to the number of
            // managed registers, in such a case set the device as offline.
            if (this.register2Notification.keySet().size() == this.failedRegisters.size())
            {
                this.setDeviceOnline(false);
            }
        }
        else
        {
            // remove the register from the list of failed registers for this
            // device
            NetworkError previousError = this.failedRegisters
                    .remove(dataPointInfo);

            // notify the the register is no more failing
            if (previousError != null)
            {
                this.handleErrorCeased(dataPointInfo);
            }

            // set the device as online
            if (this.register2Notification.keySet().size() > this.failedRegisters.size())
            {
                this.setDeviceOnline(true);
            }
        }
    }

    /**
     * Handle the detection of an unknown error on a register. Must be
     * overridden by subclasses to provide specific handling for such events.
     * 
     * @param register
     *            The failing register.
     * @param error
     *            The failure cause.
     */
    protected void handleNewError(ModbusRegisterInfo register,
            NetworkError error)
    {
        // log the error
        this.logger.info("Unreachable register: " + register.getAddress()
                + " on slave " + register.getSlaveId()
                + " connected to gateway " + register.getGatewayIdentifier()
                + ": " + (error != null ? error.getCause() : "unknown error"));
    }

    /**
     * Handle the detection of a failure end on a specific register. Must be
     * overridden by subclasses to provide specific handling of such events.
     * 
     * @param register
     *            The register that is no more failing.
     */
    protected void handleErrorCeased(ModbusRegisterInfo register)
    {
        // log that the error ceased
        this.logger.info("Register " + register.getAddress() + " on slave "
                + register.getSlaveId() + " connected to gateway "
                + register.getGatewayIdentifier() + " is now reachable.");
    }

    /**
     * Handle the detection of a device being completely offline. Must be
     * overridden by subclasses to provide specific handling of such events.
     * Called only if the device connection status changes.
     * 
     * @param online
     *            True if the device is online, false otherwise.
     */
    protected void handleConnectionStatus(boolean online)
    {
        // log that the error ceased
        this.logger.info(
                "Device " + this.device.getDeviceDescriptor().getDeviceURI()
                        + " is " + (online ? "online." : "offline."));
    }

    /**
     * Sets the online flag of the device handled by this driver.
     * 
     * @param online
     *            True if the device is connected and can be read, false
     *            otherwise.
     */
    protected void setDeviceOnline(boolean online)
    {
        if (this.device instanceof AbstractDevice)
        {
            AbstractDevice aDevice = ((AbstractDevice) this.device);
            if (aDevice.isOnline() != online)
            {
                aDevice.setOnline(online);
                this.handleConnectionStatus(online);
            }
        }
    }

    /**
     * Fills a base {@link DeviceDiagnostics} object which can then be detailed
     * by classes descending from {@link ModbusDriverInstance}.
     * 
     * @return The base {@link DeviceDiagnostics} instance filled with features
     *         independent from the device type.
     */
    protected void buildBaseDiagnostics(DeviceDiagnostics<?> deviceDiagnostics)
    {
        deviceDiagnostics
                .setDeviceId(this.device.getDeviceDescriptor().getDeviceURI());
        deviceDiagnostics
                .setDeviceClass(this.device.getClass().getSimpleName());
        deviceDiagnostics.setDeviceDriver(this.getClass().getSimpleName());
        deviceDiagnostics.setDeviceGateway(
                this.device.getDeviceDescriptor().getGateway());
        deviceDiagnostics.setOnline(((AbstractDevice) this.device).isOnline());
        deviceDiagnostics.setProtocol(this.device.getDeviceDescriptor()
                .getTechnology().toLowerCase());
    }

    // -------- PRIVATE METHODS ----------

    /***
     * Fills the inner data structures depending on the specific device
     * configuration parameters, extracted from the device instance associated
     * to this driver instance
     */
    private void fillConfiguration()
    {
        // Handle shared properties, typically not specified.

        // Handle command-specific parameters
        this.fillCommandConfiguration();
        // Handle notification-specific parameters
        this.fillNotificationConfiguration();

    }

    /**
     * Fill the relevant parameters that are defined in the specific command
     * sections. It must be noticed that only one register may be associated to
     * a single command.
     */
    private void fillCommandConfiguration()
    {
        // get parameters associated to each device command (if any)
        Set<ElementDescription> commandsSpecificParameters = this.device
                .getDeviceDescriptor().getCommandSpecificParams();

        // --------------- Handle command specific parameters ----------------
        for (ElementDescription parameter : commandsSpecificParameters)
        {
            // the parameter map
            Map<String, String> params = parameter.getElementParams();

            if (params != null && !params.isEmpty())
            {

                // get the real command name
                String realCommandName = params.get(ModbusInfo.COMMAND_NAME);
                // get the command id
                String commandId = parameter.getElementName();
                // get the command class
                String commandClass = parameter.getElementType();

                // build a command representation
                CNParameters commandInfo = new CNParameters(realCommandName,
                        commandId, commandClass, params);

                try
                {

                    ModbusRegisterInfo registerInfo = this
                            .extractRegisterSpecificParameters(params);

                    // check that a register info has been extracted
                    if (registerInfo != null && !registerInfo.isEmpty())
                    {
                        // add the command to data point entry
                        this.command2Register.put(commandInfo, registerInfo);
                    }
                }
                catch (UnknownHostException uhe)
                {
                    // log the error
                    this.logger.error(
                            "Error while parsing register-specific information: \n{}",
                            uhe);

                }
                catch (NumberFormatException nfe)
                {
                    // log the error
                    this.logger.error(
                            "Error while parsing register-specific information: \n{}",
                            nfe);

                }

            }

        }
    }

    /**
     * Fill the relevant parameters that are defined in the specific
     * notification sections. It must be noticed that a single register may be
     * associated to more than one notification, as such, the
     * register2Notification datastructure might in principle contain more than
     * one notification per each register.
     */
    private void fillNotificationConfiguration()
    {
        // get parameters associated to each device notification (if any)
        Set<ElementDescription> notificationsSpecificParameters = this.device
                .getDeviceDescriptor().getNotificationSpecificParams();

        for (ElementDescription parameter : notificationsSpecificParameters)
        {

            // the parameter map
            Map<String, String> params = parameter.getElementParams();
            if ((params != null) && (!params.isEmpty()))
            {
                // the notification class
                String notificationClass = parameter.getElementType();
                // the unique id of the notification
                String notificationId = parameter.getElementName();
                // get the notification name
                String notificationName = params
                        .get(ModbusInfo.NOTIFICATION_NAME);

                // build a descriptor for these notification parameters
                CNParameters notificationInfo = new CNParameters(
                        notificationName, notificationId, notificationClass,
                        params);

                try
                {
                    // extract the register info given the notification
                    // parameters
                    ModbusRegisterInfo registerInfo = this
                            .extractRegisterSpecificParameters(params);

                    // check that a register info has been extracted
                    if (registerInfo != null && !registerInfo.isEmpty())
                    {

                        // fill the data point to notification map,
                        this.register2Notification.put(registerInfo,
                                notificationInfo);
                    }
                }

                catch (UnknownHostException uhe)
                {
                    // log the error
                    this.logger.error(
                            "Error while parsing register-specific information: \n{}",
                            uhe);

                }
                catch (NumberFormatException nfe)
                {
                    // log the error
                    this.logger.error("Error while parsing register-specific"
                            + " information: \n{}", nfe);

                }
            }

        }
    }

    private ModbusRegisterInfo extractRegisterSpecificParameters(
            Map<String, String> params) throws UnknownHostException
    {
        // the register info to return
        ModbusRegisterInfo registerInfo = null;

        // get the Modbus register address
        String regAddress = params.get(ModbusInfo.REGISTER_ADDRESS);

        // no address no party
        if (regAddress != null && !regAddress.isEmpty())
        {
            // if the given address is not a number than the address will be set
            // at Integer.MIN_VALUE (a negative number which cannot be a
            // register address)
            int registerAddress = (int) this.getNumberOrDefault(regAddress,
                    Integer.class, ModbusInfo.DEFAULT_REGISTER_ADDRESS);
            // get the Modbus register unit of measure
            String unitOfMeasure = params.get(ModbusInfo.REGISTER_UOM);
            // get the Modbus register type as a string
            String registerType = params.get(ModbusInfo.REGISTER_TYPE);
            // get the Modbus register slave id
            // if the given slave id is not a number than the slave id will be
            // set
            // at Integer.MIN_VALUE (a negative number that cannot be a slave
            // id)
            int registerSlaveId = (int) this.getNumberOrDefault(
                    params.get(ModbusInfo.SLAVE_ID), Integer.class,
                    ModbusInfo.DEFAULT_SLAVE_ID);

            // get the Modbus register scale factor if needed
            double scaleFactor = (double) this.getNumberOrDefault(
                    params.get(ModbusInfo.SCALE_FACTOR), Double.class,
                    ModbusInfo.DEFAULT_SCALE_FACTOR);

            // try parsing the register type as enum, if the result is null
            // than the register type is likely to be specified using the
            // former numeric-based specification.
            RegisterTypeEnum regTypeNew = RegisterTypeEnum
                    .fromValue(registerType);

            // create the register info to store the register-specific
            // parameters
            registerInfo = new ModbusRegisterInfo();

            // set the register info parameters
            registerInfo.setAddress(registerAddress);

            // fill the register gateway address
            registerInfo
                    .setGatewayIPAddress(InetAddress.getByName(this.gwAddress));

            // fill the register gateway port
            registerInfo.setGatewayPort(this.gwPort);

            // fill the protocol variant associated to the gateway
            registerInfo.setGatewayProtocol(this.gwProtocol);

            // for the serial connections, it adds the serial parameters
            if (this.gwProtocol.equals(ModbusProtocolVariant.RTU.toString()))
            {
                registerInfo.setSerialParameters(serialParameters);
            }

            // fill the slave id
            registerInfo.setSlaveId(registerSlaveId);

            // support to v1.2 version of driver
            if (regTypeNew != null)
            {

                // get the request timeout
                long requestTimeout = (long) this.getNumberOrDefault(
                        ModbusInfo.REQUEST_TIMEOUT_MILLIS, Long.class,
                        this.gatewayRequestTimeout);

                long requestGap = (long) this.getNumberOrDefault(
                        ModbusInfo.REQUEST_GAP_MILLIS, Long.class,
                        this.gatewayRequestGap);

                // fill the request timeout
                registerInfo.setRequestTimeoutMillis(requestTimeout);

                // fill the request gap
                registerInfo.setRequestGapMillis(requestGap);

                // the register data size
                DataSizeEnum dataSize = this.getDataSizeEnumOrDefault(
                        params.get(ModbusInfo.DATA_SIZE), DataSizeEnum.INT16);
                // the register byte order
                OrderEnum byteOrder = this.getOrderEnumOrDefault(
                        params.get(ModbusInfo.BYTE_ORDER),
                        OrderEnum.BIG_ENDIAN);
                // the word order
                OrderEnum wordOrder = this.getOrderEnumOrDefault(
                        params.get(ModbusInfo.WORD_ORDER),
                        OrderEnum.BIG_ENDIAN);
                // the double word order
                OrderEnum doubleWordOrder = this.getOrderEnumOrDefault(
                        params.get(ModbusInfo.DOUBLE_WORD_ORDER),
                        OrderEnum.BIG_ENDIAN);
                // the bit value
                int bit = (int) this.getNumberOrDefault(
                        params.get(ModbusInfo.BIT), Integer.class,
                        ModbusInfo.DEFAULT_BIT);

                BytePositionEnum bytePosition = this
                        .getBytePositionEnumOrDefault(
                                params.get(ModbusInfo.BYTE_POSITION), null);

                // build a BaseXlator
                BaseRegXlator baseXlator = new BaseRegXlator(dataSize,
                        regTypeNew, byteOrder, wordOrder, doubleWordOrder, bit,
                        bytePosition);

                // set the register info xlator
                registerInfo.setXlator(baseXlator);
            }
            else
            {

                // fill the translator properties
                BaseRegXlator xlator = RegXlatorTypes
                        .createRegXlator(Integer.valueOf(registerType));

                // set the register info xlator
                registerInfo.setXlator(xlator);
            }

            // set the logger
            registerInfo.getXlator().setLogger(this.logger);
            // set the scale factor
            registerInfo.getXlator().setScaleFactor(scaleFactor);
            // set the unit of measure
            registerInfo.getXlator().setUnitOfMeasure(unitOfMeasure);
        }

        return (registerInfo != null && registerInfo.isValid()) ? registerInfo
                : null;
    }

    private Number getNumberOrDefault(String value,
            Class<? extends Number> numberClass, Number defaultValue)
    {
        Number actualValue = defaultValue;
        if (value != null && !value.isEmpty())
        {
            try
            {
                Method valueOf = numberClass.getMethod("valueOf", String.class);
                actualValue = (Number) valueOf.invoke(null, value.trim());
            }
            catch (NoSuchMethodException | SecurityException
                    | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException ex)
            {

                // do nothing
            }
        }

        return actualValue;
    }

    private OrderEnum getOrderEnumOrDefault(String value,
            OrderEnum defaultValue)
    {
        OrderEnum actualValue = defaultValue;

        if (value != null && !value.isEmpty())
        {
            OrderEnum candidateValue = OrderEnum.fromValue(value.trim());

            if (candidateValue != null)
            {
                actualValue = candidateValue;
            }
        }

        return actualValue;
    }

    private DataSizeEnum getDataSizeEnumOrDefault(String value,
            DataSizeEnum defaultValue)
    {
        DataSizeEnum actualValue = defaultValue;

        if (value != null && !value.isEmpty())
        {
            DataSizeEnum candidateValue = DataSizeEnum.fromValue(value.trim());

            if (candidateValue != null)
            {
                actualValue = candidateValue;
            }
        }

        return actualValue;
    }

    private BytePositionEnum getBytePositionEnumOrDefault(String value,
            BytePositionEnum defaultValue)
    {
        BytePositionEnum actualValue = defaultValue;

        if (value != null && !value.isEmpty())
        {
            BytePositionEnum candidateValue = BytePositionEnum
                    .fromValue(value.trim());

            if (candidateValue != null)
            {
                actualValue = candidateValue;
            }
        }

        return actualValue;
    }
}

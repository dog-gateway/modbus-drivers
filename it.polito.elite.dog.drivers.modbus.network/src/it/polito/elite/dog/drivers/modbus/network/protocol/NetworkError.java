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
package it.polito.elite.dog.drivers.modbus.network.protocol;

/**
 * An enumeration that defined the possible errors occurring while the network
 * driver is operating.
 * 
 * @author <a href="mailto:dario.bonino@gmail.com">Dario Bonino</a>
 *
 * @since Feb 28, 2020
 */
public enum NetworkError
{
    EOF(1, "Unexpected end of file while reading from modbus interface."),

    ILLEGAL_FUNCTION(2, "Illegal function error from modbus device."),

    ILLEGAL_ADDRESS(3, "Illegal register from modbus device."),

    ILLEGAL_VALUE(4, "Illegal register value from modbus device."),

    GENERIC_IO(5, "Generic I/O error while reading from modbus interface."),

    UNREACHABLE(6, "Gateway or device is unreachable"),

    VALUE_TRANSLATION(7, "Unable to translate received register value");

    // the numeric code associated to the network-level error
    private int code;
    // the human-readable cause for the error
    private String cause;

    /**
     * Build a new instance of {@link NetworkError}.
     * 
     * @param code
     *            The error code, as an integer.
     * @param cause
     *            The human-readable error cause.
     */
    private NetworkError(int code, String cause)
    {
        this.code = code;
        this.cause = cause;
    }

    /**
     * Provides the error code associated to a {@link NetworkError} instance.
     * 
     * @return The error code, as an integer.
     */
    public int getCode()
    {
        return code;
    }

    /**
     * Provides the human-readable description of the error.
     * 
     * @return The cause as a {@link String}.
     */
    public String getCause()
    {
        return cause;
    }

}

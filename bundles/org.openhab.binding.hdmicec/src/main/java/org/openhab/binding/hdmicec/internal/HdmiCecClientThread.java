/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.hdmicec.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.openhab.binding.hdmicec.handler.HdmiCecBridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdmiCecClientThread} class is used to create a background thread
 * that communicates with the cec-client process.
 *
 * @author Arjen Korevaar - Initial contribution
 */
public class HdmiCecClientThread implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(HdmiCecClientThread.class);

    private HdmiCecBridgeHandler bridge;
    private String cecClientPath;
    private String comPort;

    private boolean isRunning;
    private boolean connected;

    private Process process;
    private PrintWriter writer;

    public HdmiCecClientThread(HdmiCecBridgeHandler bridge, String cecClientPath, String comPort) {
        this.bridge = bridge;
        this.cecClientPath = cecClientPath;
        this.comPort = comPort;
    }

    @Override
    public void run() {
        logger.debug("Connecting to cec-client: {}, com port: {}", cecClientPath, comPort);

        isRunning = true;

        String line;

        try {
            ProcessBuilder builder = new ProcessBuilder();
            builder.command(cecClientPath, comPort);
            builder.redirectErrorStream(true); // This is the important part

            process = builder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    PrintWriter wrt = new PrintWriter(process.getOutputStream(), true)) {

                writer = wrt;

                logger.debug("Connected to cec-client");
                bridge.connected();

                connected = true;

                while (isRunning && (line = reader.readLine()) != null) {
                    logger.trace("Line read from cec-client: {}", line);
                    bridge.messageReceived(line);
                }
            } finally {
                logger.debug("Disconnected from cec-client");
                connected = false;
                bridge.disconnected();
            }
        } catch (IOException e) {
            logger.error("Error reading from cec-client", e);
        }
    }

    public void sendMessage(String message) {
        try {
            writer.printf("%s\r\n", message);
            writer.flush();
        } catch (Exception e) {
            logger.debug("Error sending message to cec-client: {}", message, e);
        }
    }

    public boolean getConnected() {
        return connected;
    }

    public void stop() {
        sendMessage("q");

        isRunning = false;

        if (process != null)
            process.destroy();
    }
}
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
package org.openhab.binding.hdmicec.handler;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.hdmicec.internal.HdmiCecClientThread;
import org.openhab.binding.hdmicec.internal.HdmiCecBindingConstants;
import org.openhab.binding.hdmicec.internal.HdmiCecBridgeConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdmiCecHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Arjen Korevaar - Initial contribution
 */
public class HdmiCecBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(HdmiCecBridgeHandler.class);

    private @Nullable HdmiCecBridgeConfiguration config;

    // we're betting on the fact that the first value in () is the device ID. Seems
    // valid from what I've seen!
    private Pattern deviceStatement = Pattern.compile("DEBUG.* \\((.)\\).*");

    private HdmiCecClientThread client;

    public HdmiCecBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_SEND)) {
            if (command instanceof StringType) {
                // think about this, do we want to have a controlled vocabulary or just transmit
                // something raw, or both?

                sendMessage(command.toString());
            }
        }
    }

    @Override
    public void initialize() {
        logger.debug("Initialize HDMI CEC Bridge");

        config = getConfigAs(HdmiCecBridgeConfiguration.class);

        File cecClientFile = new File(config.cecClientPath);

        if (!cecClientFile.exists()) {
            logger.debug("File {} does not exist.", config.cecClientPath);

            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "cec-client executable not found.");
            return;
        }

        connect();
    }

    private void connect() {        
        updateStatus(ThingStatus.INITIALIZING);

        logger.debug("Starting cec-client connection thread.");

        client = new HdmiCecClientThread(this, config.cecClientPath, config.comPort);

        Thread thread = new Thread(client, "HDMI CEC Binding - cec-client thread");
        thread.setDaemon(true);
        thread.start();

        logger.debug("Connection thread started.");
    }

    public void connected() {
        updateStatus(ThingStatus.ONLINE);
    }

    public void disconnect() {
        if (client != null)
            client.stop();
    }

    public void disconnected() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "cec-client disconnected.");

        if (config.connectionRetryInterval > 0) {
            scheduler.schedule(() -> {
                connect();
            }, config.connectionRetryInterval, TimeUnit.SECONDS);
        }
    }

    public void sendMessage(String message) {
        if (client != null)
            client.sendMessage(message);
    }

    public void messageReceived(String line) {
        if (line.contains("communication thread ended")) {
            logger.debug("Message found: communication thread ended");
            disconnect();
        } else if (line.contains("could not start CEC communications")) {
            logger.debug("Message found: could not start CEC communications");
            disconnect();
        } else {
            Matcher matcher = deviceStatement.matcher(line);

            if (matcher.matches()) {
                for (Thing thing : getThing().getThings()) {
                    HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler) thing.getHandler();
                    if (equipment != null && equipment.getDevice().equalsIgnoreCase(matcher.group(1))) {
                        equipment.cecMatchLine(line);
                        logger.debug("Line handled by equipment: {} ({})", line, equipment.getAddress());
                    }
                }
            }
        }
    }

    // public void updateStatus(boolean online, String status) {
    // logger.debug("updateStatus: online = {}, status = {}.", online, status);

    // if (!online) {
    // updateStatus(ThingStatus.OFFLINE);
    // stopCecClient();
    // }

    // for (Thing thing : getThing().getThings()) {
    // HdmiCecEquipmentHandler equipment = (HdmiCecEquipmentHandler)
    // thing.getHandler();
    // if (equipment != null) {
    // // actually, do we want to do this?
    // equipment.cecClientStatus(online, status);
    // }
    // }
    // }

    @Override
    public void dispose() {
        disconnect();
        super.dispose();
    }
}

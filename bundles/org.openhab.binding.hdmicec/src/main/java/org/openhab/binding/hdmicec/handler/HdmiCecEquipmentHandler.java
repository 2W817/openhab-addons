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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.hdmicec.internal.HdmiCecBindingConstants;
import org.openhab.binding.hdmicec.internal.HdmiCecEquipmentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HdmiCecHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Arjen Korevaar - Initial contribution
 */
public class HdmiCecEquipmentHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(HdmiCecEquipmentHandler.class);

    private @Nullable HdmiCecEquipmentConfiguration config;

    private HdmiCecBridgeHandler bridgeHandler;

    // we're betting on the fact that the first value in () is the device ID. Seems
    // valid from what I've seen!
    private Pattern powerOn = Pattern.compile(".*: power status changed from '(.*)' to 'on'");
    private Pattern powerOff = Pattern.compile(".*: power status changed from '(.*)' to 'standby'");
    private Pattern activeSourceOn = Pattern.compile(".*making .* \\((.)\\) the active source");
    private Pattern activeSourceOff = Pattern.compile(".*marking .* \\((.)\\) as inactive source");
    private Pattern eventPattern = Pattern.compile("^(?!.*(<<|>>)).*: (.*)$"); // the 2nd group is the event

    public String getDevice() {
        return config.device;
    }

    public String getAddress() {
        return config.address;
    }

    public HdmiCecEquipmentHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_POWER)) {
            if (command.equals(OnOffType.ON)) {
                bridgeHandler.sendCommand("on " + config.device);
            } else if (command.equals(OnOffType.OFF)) {
                bridgeHandler.sendCommand("standby " + config.device);
            }
        } else if (channelUID.getId().equals(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE)) {
            String addressAsFrame = config.address.replace(".", "").substring(0, 2) + ":"
                    + config.address.replace(".", "").substring(2);

            if (command.equals(OnOffType.ON)) {
                bridgeHandler.sendCommand("tx " + config.device + "F:82:" + addressAsFrame);
            } else if (command.equals(OnOffType.OFF)) {
                bridgeHandler.sendCommand("tx " + config.device + "F:9D:" + addressAsFrame);
            }
        }
    }

    @Override
    public void initialize() {
        logger.debug("Initializing the HdmiCec Equipment handler");

        config = getConfigAs(HdmiCecEquipmentConfiguration.class);

        try {
            getThing().setLabel(getThing().getLabel().replace("Equipment", getThing().getUID().getId()));

            logger.debug("Initializing thing {}", getThing().getUID());
            bridgeHandler = (HdmiCecBridgeHandler) getBridge().getHandler();

            if (getBridge().getStatus() == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } catch (Exception e) {
            logger.error("Error in initialize: {}", e.toString());
        }
    }

    public void cecClientStatus(boolean online, String status) {
        if (online) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, status);
        }

        logger.debug("Cec client status: online = {} status = {}", online, status);
    }

    public void cecMatchLine(String line) {
        Matcher matcher = powerOn.matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.ON);
            return;
        }
        matcher = powerOff.matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_POWER, OnOffType.OFF);
            return;
        }
        matcher = activeSourceOn.matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE, OnOffType.ON);
            return;
        }
        matcher = activeSourceOff.matcher(line);
        if (matcher.matches()) {
            updateState(HdmiCecBindingConstants.CHANNEL_ACTIVE_SOURCE, OnOffType.OFF);
            return;
        }
        matcher = eventPattern.matcher(line);
        if (matcher.matches()) {
            triggerChannel(HdmiCecBindingConstants.CHANNEL_EVENT, matcher.group(2));
            return;
        }
    }
}

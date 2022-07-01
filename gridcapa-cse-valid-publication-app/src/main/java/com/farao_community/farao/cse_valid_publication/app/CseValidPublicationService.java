/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.configuration.AmqpMessagesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class CseValidPublicationService {
    private static final String APPLICATION_ID = "process-publication-server";
    private static final String CONTENT_ENCODING = "UTF-8";
    private static final String CONTENT_TYPE = "application/vnd.api+json";
    private static final int PRIORITY = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationService.class);

    private final AmqpMessagesConfiguration amqpMessagesConfiguration;
    private final AmqpTemplate amqpTemplate;
    private final JsonConverter jsonConverter;

    public CseValidPublicationService(AmqpMessagesConfiguration amqpMessagesConfiguration, AmqpTemplate amqpTemplate, JsonConverter jsonConverter) {
        this.amqpMessagesConfiguration = amqpMessagesConfiguration;
        this.amqpTemplate = amqpTemplate;
        this.jsonConverter = jsonConverter;
    }

    public ProcessStartRequest publishProcess(String nullableId, String region, String process, String targetDate, int targetDateOffset) {
        System.out.println("Publish process " + process + "for region " + region + "with target date " + targetDate);
        //todo iport TTC adjustment and run asynchronous cse valid request
        // write TTC validtion file for business date
        return null;
    }

}

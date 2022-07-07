/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Configuration
public class AmqpMessagesConfiguration { //todo delete ou add other elements?

    @Value("${cse-valid-publication.amqp.queue-name}")
    private String queueName;

    @Value("${cse-valid-publication.amqp.expiration}")
    private String expiration; // encoded string of TTL period in milliseconds

    public String getQueueName() {
        return queueName;
    }

    public String getExpiration() {
        return expiration;
    }
}

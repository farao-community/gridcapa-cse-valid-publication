/*
 * Copyright (c) 2021, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties
@ConfigurationProperties("cse-valid-publication.url")
public class UrlConfiguration {
    private final String taskManagerBusinessDateUrl;

    public UrlConfiguration(String taskManagerBusinessDateUrl) {
        this.taskManagerBusinessDateUrl = taskManagerBusinessDateUrl;
    }

    public String getTaskManagerBusinessDateUrl() {
        return taskManagerBusinessDateUrl;
    }
}

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.configuration.FilenamesConfiguration;
import com.farao_community.farao.cse_valid_publication.app.configuration.UrlConfiguration;
import com.farao_community.farao.cse_valid_publication.app.configuration.UrlWhitelistConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@SuppressWarnings("hideutilityclassconstructor")
@SpringBootApplication
@EnableConfigurationProperties({FilenamesConfiguration.class, UrlConfiguration.class, UrlWhitelistConfiguration.class})
@EnableRetry
public class CseValidPublicationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CseValidPublicationApplication.class, args);
    }
}

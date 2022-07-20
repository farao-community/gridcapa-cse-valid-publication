/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@ConfigurationProperties(prefix = "cse-valid-publication.filenames")
@EnableConfigurationProperties
@ConstructorBinding
public class FilenamesConfiguration {
    private final String ttcAdjustment;
    private final String ttcValidation;
    private final String crac;

    public FilenamesConfiguration(String ttcAdjustment, String ttcValidation, String crac) {
        this.ttcAdjustment = ttcAdjustment;
        this.ttcValidation = ttcValidation;
        this.crac = crac;
    }

    public String getTtcAdjustment() {
        return ttcAdjustment;
    }

    public String getTtcValidation() {
        return ttcValidation;
    }

    public String getCrac() {
        return crac;
    }
}

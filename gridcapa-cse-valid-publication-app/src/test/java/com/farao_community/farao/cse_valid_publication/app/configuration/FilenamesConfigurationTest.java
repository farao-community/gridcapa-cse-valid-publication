/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class FilenamesConfigurationTest {
    @Autowired
    private FilenamesConfiguration filenamesConfiguration;

    @Test
    void getTtcAdjustment() {
        assertEquals("TTC_Adjustment_(?<year>[0-9]{4})(?<month>[0-9]{2})(?<day>[0-9]{2})_(ID|2D)[1-7](_Final)?_CSE(?<version>[0-9]{1,2}).xml", filenamesConfiguration.getTtcAdjustment());
    }

    @Test
    void getTtcValidation() {
        assertEquals("'TTC_RTEValidation_'yyyyMMdd'_%s'e'_(?<version>[0-9]{1,2}).xml'", filenamesConfiguration.getTtcValidation());
    }

    @Test
    void getCrac() {
        assertEquals("(?<year>[0-9]{4})(?<month>[0-9]{2})(?<day>[0-9]{2})_(?<hour>[0-9]{2})(?<minute>[0-9]{2})_([0-9]{2}|2D)[1-7]_CRAC_FR(?<version>[0-9]{1,2}).xml", filenamesConfiguration.getCrac());
    }
}

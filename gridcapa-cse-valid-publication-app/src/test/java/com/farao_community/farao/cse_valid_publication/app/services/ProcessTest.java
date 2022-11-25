/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class ProcessTest {

    @Test
    void getProcessCodeIdcc() {
        assertEquals("ID", ProcessUtils.getProcessCode("IDCC"));
    }

    @Test
    void getProcessCodeD2cc() {
        assertEquals("2D", ProcessUtils.getProcessCode("D2CC"));
    }

    @Test
    void getProcessCodeError() {
        assertThrows(CseValidPublicationInvalidDataException.class, () -> ProcessUtils.getProcessCode("Unknown"));
    }
}

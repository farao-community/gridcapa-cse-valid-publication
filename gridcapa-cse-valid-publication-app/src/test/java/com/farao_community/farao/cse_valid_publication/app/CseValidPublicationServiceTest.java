/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.gridcapa_cse_valid.starter.CseValidClient;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;


import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class CseValidPublicationServiceTest {

    @Autowired
    private CseValidPublicationService cseValidPublicationService;

    @MockBean
    private MinioAdapter minioAdapter;

    @MockBean
    private CseValidClient cseValidClient;

    @Test
    void publishProcessTest() {
        assertTrue(cseValidPublicationService.publishProcess(null, "D2CC", "2020-08-12", 1));
    }

    @Test
    void publishProcessWithErrorProcess() {
        String message = String.format("Unknown target process for CSE: %s", "INCORRECT_PROCESS");
        assertThrows(CseValidPublicationInvalidDataException.class, () -> cseValidPublicationService.publishProcess(null, "INCORRECT_PROCESS", "2020-01-01", 0), message);
    }

    @Test
    void publishProcessWithErrorDate() {
        String message = String.format("Incorrect format for target date : '%s' is invalid, please use ISO-8601 format", "INCORRECT_DATE");
        assertThrows(CseValidPublicationInvalidDataException.class, () -> cseValidPublicationService.publishProcess(null, "D2CC", "INCORRECT_DATE", 0), message);
    }
}

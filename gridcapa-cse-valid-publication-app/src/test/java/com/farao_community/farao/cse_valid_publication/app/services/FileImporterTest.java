/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.xsd.TcDocumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@SpringBootTest
class FileImporterTest {

    @Autowired
    private FileImporter fileImporter;

    @MockBean
    private UrlValidationService urlValidationService;

    private final String testResourcePath = "/services/";

    @Test
    void importTtcFileTest() throws IOException {
        String ttcValidationUrl = "TTC_RTEValidation_20200813_2D4_1.xml";

        InputStream inputStream = getClass().getResourceAsStream(testResourcePath + ttcValidationUrl);
        when(urlValidationService.openUrlStream(ttcValidationUrl)).thenReturn(inputStream);
        TcDocumentType tcDocument = fileImporter.importTtcFile(ttcValidationUrl);
        assertEquals("TTC_RTEValidation_20200813_2D4", tcDocument.getDocumentIdentification().getV());
    }

    @Test
    void importTtcFileErrorTest() throws IOException {
        String ttcValidationUrl = "TTC_RTEValidation_20200813_doesNotExist.xml";

        InputStream inputStream = getClass().getResourceAsStream(testResourcePath + ttcValidationUrl);
        when(urlValidationService.openUrlStream(ttcValidationUrl)).thenReturn(inputStream);
        TcDocumentType tcDocument = fileImporter.importTtcFile(ttcValidationUrl);
        assertNull(tcDocument);
    }
}

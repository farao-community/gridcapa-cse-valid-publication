/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TcDocumentType;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class FileImporterTest {

    @Autowired
    private FileImporter fileImporter;

    @MockBean
    private MinioAdapter minioAdapter;

    @MockBean
    private UrlValidationService urlValidationService;

    private String testResourcePath = "/services/";

    @Test
    void importExistingTtcAdjustment() {
        String ttcFileName = "TTC_Adjustment_20200813_2D4_Final_CSE1.xml";
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream(testResourcePath + ttcFileName));
        when(minioAdapter.fileExists("D2CC/TTC_ADJUSTMENT/" + ttcFileName)).thenReturn(true);
        TcDocumentType document = fileImporter.importTtcAdjustment("D2CC/TTC_ADJUSTMENT/" + ttcFileName);

        assertEquals("TTC_Adjustment_20200813_2D4_CSE", document.getDocumentIdentification().getV());
        assertEquals(1, document.getDocumentVersion().getV());
        assertEquals(1, document.getAdjustmentResults().size());
        assertEquals("2020-08-12T22:30Z", document.getAdjustmentResults().get(0).getTimestamp().get(0).getReferenceCalculationTime().getV());
        assertEquals("Critical Branch", document.getAdjustmentResults().get(0).getTimestamp().get(0).getTTCLimitedBy().getV());
    }

    @Test
    void importExistingTtcAdjustmentVersion2() {
        String ttcFileName2 = "TTC_Adjustment_20200813_2D4_Final_CSE2.xml";
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream(testResourcePath + ttcFileName2));
        when(minioAdapter.fileExists("D2CC/TTC_ADJUSTMENT/" + ttcFileName2)).thenReturn(true);
        TcDocumentType document = fileImporter.importTtcAdjustment("D2CC/TTC_ADJUSTMENT/" + ttcFileName2);

        assertEquals("TTC_Adjustment_20200813_2D4_CSE", document.getDocumentIdentification().getV());
        assertEquals(2, document.getDocumentVersion().getV());
        assertEquals(1, document.getAdjustmentResults().size());
    }

    @Test
    void importMissingTtcAdjustment() {
        String ttcFileName = "TTC_Adjustment_20200813_2D4_CSE1_doesNotExist.xml";
        when(minioAdapter.getFile(any())).thenReturn(null);
        when(minioAdapter.fileExists(any())).thenReturn(false);
        assertNull(fileImporter.importTtcAdjustment("D2CC/TTC_ADJUSTMENT/" + ttcFileName));
    }

    @Test
    void importTtcAdjustmentWithError() {
        String ttcFileName = "TTC_Adjustment_20200813_2D4_CSE1_error.xml";
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream(testResourcePath + ttcFileName));
        when(minioAdapter.fileExists("D2CC/TTC_ADJUSTMENT/" + ttcFileName)).thenReturn(true);
        assertThrows(CseValidPublicationInvalidDataException.class, () -> fileImporter.importTtcAdjustment("D2CC/TTC_ADJUSTMENT/" + ttcFileName));
    }

    @Test
    void importTtcValidationTest() throws IOException {
        String ttcValidationUrl = "TTC_RTEValidation_20200813_2D4_1.xml";

        InputStream inputStream = getClass().getResourceAsStream(testResourcePath + ttcValidationUrl);
        when(urlValidationService.openUrlStream(ttcValidationUrl)).thenReturn(inputStream);
        TcDocumentType tcDocument = fileImporter.importTtcValidation(ttcValidationUrl);
        assertEquals("TTC_RTEValidation_20200813_2D4", tcDocument.getDocumentIdentification().getV());
    }

    @Test
    void importTtcValidationErrorTest() throws IOException {
        String ttcValidationUrl = "TTC_RTEValidation_20200813_doesNotExist.xml";

        InputStream inputStream = getClass().getResourceAsStream(testResourcePath + ttcValidationUrl);
        when(urlValidationService.openUrlStream(ttcValidationUrl)).thenReturn(inputStream);
        TcDocumentType tcDocument = fileImporter.importTtcValidation(ttcValidationUrl);
        assertNull(tcDocument);
    }
}

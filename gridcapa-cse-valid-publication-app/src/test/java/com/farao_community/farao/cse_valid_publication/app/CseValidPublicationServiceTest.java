/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TTimestamp;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TcDocumentType;
import com.farao_community.farao.gridcapa_cse_valid.starter.CseValidClient;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;


import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class CseValidPublicationServiceTest {

    @Autowired
    private CseValidPublicationService cseValidPublicationService;

    @MockBean
    private MinioAdapter minioAdapter;

    private String testResourcePath = "/services/";

    @Test
    void publishProcessWithoutTtcAdjustmentTest() {
        assertTrue(cseValidPublicationService.publishProcess(null, "D2CC", "2020-08-12", 1));
        TcDocumentType tcDocumentType = cseValidPublicationService.getTcDocumentTypeWriter().getTcDocumentType();
        assertNotNull(tcDocumentType);
        assertEquals(24, tcDocumentType.getValidationResults().get(0).getTimestamp().size());
        assertEquals(24, tcDocumentType.getValidationResults().get(0).getTimestamp().size());
        TTimestamp timestampResult = tcDocumentType.getValidationResults().get(0).getTimestamp().get(0);
        assertEquals("2020-08-12T22:30Z", timestampResult.getReferenceCalculationTime().getV());
        assertEquals("Process fail during TSO validation phase: Missing TTC_adjustment file.", timestampResult.getRedFlagReason().getV());
    }

    @Test
    void publishProcessWithCseValidErrorTest() {
        String ttcFileName = "TTC_Adjustment_20200813_2D4_Final_CSE1.xml";
        when(minioAdapter.listFiles(any())).thenReturn(List.of(ttcFileName));
        when(minioAdapter.fileExists(any())).thenReturn(true);
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream(testResourcePath + ttcFileName));
        when(minioAdapter.generatePreSignedUrl(any())).thenReturn("url");
        assertTrue(cseValidPublicationService.publishProcess(null, "D2CC", "2020-08-12", 1));
        TcDocumentType tcDocumentType = cseValidPublicationService.getTcDocumentTypeWriter().getTcDocumentType();
        assertNotNull(tcDocumentType);
        assertEquals(24, tcDocumentType.getValidationResults().get(0).getTimestamp().size());
        assertEquals(24, tcDocumentType.getValidationResults().get(0).getTimestamp().size());
        TTimestamp timestampResult = tcDocumentType.getValidationResults().get(0).getTimestamp().get(0);
        assertEquals("2020-08-12T22:30Z", timestampResult.getReferenceCalculationTime().getV());
        assertEquals("Process fail during TSO validation phase.", timestampResult.getRedFlagReason().getV());
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

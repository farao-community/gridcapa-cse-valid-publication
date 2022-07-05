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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

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
    MinioAdapter minioAdapter;

    @Test
    void importExistingTtcAdjustment() {
        String ttcFileName = "com.farao_community.farao.cse_valid_publication.app/services/TTC_Adjustment_20200813_2D4_CSE1.xml";
        when(minioAdapter.listFiles(any())).thenReturn(Collections.singletonList(ttcFileName));
        when(minioAdapter.fileExists(any())).thenReturn(true);
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream("/" + ttcFileName));
        TcDocumentType document = fileImporter.importTtcAdjustment("D2CC", LocalDate.of(2020, 8, 13));

        assertEquals("TTC_Adjustment_20200813_2D4_CSE", document.getDocumentIdentification().getV());
        assertEquals(1, document.getDocumentVersion().getV());
        assertEquals(1, document.getAdjustmentResults().size());
        assertEquals("2020-08-12T22:30Z", document.getAdjustmentResults().get(0).getTimestamp().get(0).getReferenceCalculationTime().getV());
        assertEquals("Critical Branch", document.getAdjustmentResults().get(0).getTimestamp().get(0).getTTCLimitedBy().getV());
    }

    @Test
    void importExistingTtcAdjustmentVersion2() {
        String ttcFileName1 = "com.farao_community.farao.cse_valid_publication.app/services/TTC_Adjustment_20200813_2D4_CSE1.xml";
        String ttcFileName2 = "com.farao_community.farao.cse_valid_publication.app/services/TTC_Adjustment_20200813_2D4_CSE2.xml";
        when(minioAdapter.listFiles(any())).thenReturn(Arrays.asList(ttcFileName1, ttcFileName2));
        when(minioAdapter.fileExists(ttcFileName2)).thenReturn(true);
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream("/" + ttcFileName2));
        TcDocumentType document = fileImporter.importTtcAdjustment("D2CC", LocalDate.of(2020, 8, 13));

        assertEquals("TTC_Adjustment_20200813_2D4_CSE", document.getDocumentIdentification().getV());
        assertEquals(2, document.getDocumentVersion().getV());
        assertEquals(1, document.getAdjustmentResults().size());
    }

    @Test
    void importMissingTtcAdjustment() {
        String ttcFileName = "TTC_Adjustment_20200813_2D4_CSE1_doesNotExist.xml";
        when(minioAdapter.listFiles(any())).thenReturn(Collections.singletonList(ttcFileName));
        when(minioAdapter.fileExists(any())).thenReturn(false);
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream("/" + ttcFileName));
        assertNull(fileImporter.importTtcAdjustment("D2CC", LocalDate.of(2020, 8, 13)));
    }

    @Test
    void importTtcAdjustmentError() {
        String ttcFileName = "TTC_Adjustment_20200813_2D4_CSE1_doesNotExist.xml";
        when(minioAdapter.listFiles(any())).thenReturn(Collections.singletonList(ttcFileName));
        when(minioAdapter.fileExists(any())).thenReturn(true);
        when(minioAdapter.getFile(any())).thenReturn(getClass().getResourceAsStream("/" + ttcFileName));
        assertThrows(CseValidPublicationInvalidDataException.class, () -> fileImporter.importTtcAdjustment("D2CC", LocalDate.of(2020, 8, 13)));
    }
}

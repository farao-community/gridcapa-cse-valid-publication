/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class FileExporterTest {

    @Autowired
    private FileExporter fileExporter;

    @MockBean
    private MinioAdapter minioAdapter;

    private LocalDate localDate = LocalDate.of(2020, 8, 13);

    @Test
    void testSaveTtcValidation() {
        TcDocumentTypeWriter tcDocumentTypeWriter = new TcDocumentTypeWriter("2D", localDate);
        when(minioAdapter.listFiles(any())).thenReturn(Collections.emptyList());
        fileExporter.saveTtcValidation(tcDocumentTypeWriter, "D2CC", localDate);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadOutput(Mockito.any(), Mockito.any(InputStream.class));
        //assertTrue(minioAdapter.fileExists("D2CC/TTC_VALIDATION/TTC_RTEValidation_20200813_2D4_1.xml")); only without mock minioAdapter
    }

    @Test
    void testSaveTtcValidationError() {
        assertThrows(CseValidPublicationInternalException.class, () -> fileExporter.saveTtcValidation(null, "D2CC", localDate));

    }
}

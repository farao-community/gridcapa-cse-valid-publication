/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid.api.resource.ProcessType;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Oualid Aloui {@literal <oualid.aloui at rte-france.com>}
 */
@SpringBootTest
class FileExporterTest {

    @Autowired
    private FileExporter fileExporter;

    @MockitoBean
    private MinioAdapter minioAdapter;

    private final LocalDate localDate = LocalDate.of(2020, 8, 13);

    @Test
    void testSaveTtcValidation() {
        TcDocumentTypeWriter tcDocumentTypeWriter = new TcDocumentTypeWriter(ProcessType.D2CC.getCode(), localDate);
        when(minioAdapter.listFiles(any())).thenReturn(Collections.emptyList());
        fileExporter.saveTtcValidation(tcDocumentTypeWriter, ProcessType.D2CC, localDate);
        Mockito.verify(minioAdapter, Mockito.times(1)).uploadOutput(Mockito.any(), Mockito.any(InputStream.class));
        //assertTrue(minioAdapter.fileExists("D2CC/TTC_VALIDATION/TTC_RTEValidation_20200813_2D4_1.xml")); only without mock minioAdapter
    }

    @Test
    void testSaveTtcValidationError() {
        assertThrows(CseValidPublicationInternalException.class, () -> fileExporter.saveTtcValidation(null, ProcessType.D2CC, localDate));

    }
}

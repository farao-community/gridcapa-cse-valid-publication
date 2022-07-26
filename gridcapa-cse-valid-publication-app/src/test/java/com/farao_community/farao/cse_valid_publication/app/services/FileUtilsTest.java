/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class FileUtilsTest {

    @Autowired
    private FileUtils fileUtils;

    @MockBean
    MinioAdapter minioAdapter;

    @Test
    void testTtcAdjustmentVersion() {
        String ttcFileName1 = "TTC_Adjustment_20200813_2D4_Final_CSE1.xml";
        String ttcFileName2 = "TTC_Adjustment_20200813_2D4_Final_CSE2.xml";
        when(minioAdapter.listFiles(any())).thenReturn(Arrays.asList(ttcFileName1, ttcFileName2));
        assertEquals(ttcFileName2, fileUtils.getTtcAdjustmentFileName("D2CC", LocalDate.of(2020, 8, 13)));
    }

    @Test
    void testMissingTtcAdjustment() {
        String ttcFileName1 = "TTC_Adjustment_20200813_2D4.xml";
        String ttcFileName2 = "TTC_Adjustment_20200813_2D4.xml";
        when(minioAdapter.listFiles(any())).thenReturn(Arrays.asList(ttcFileName1, ttcFileName2));
        assertNull(fileUtils.getTtcAdjustmentFileName("D2CC", LocalDate.of(2020, 8, 13)));
    }

    @Test
    void getFrCracFilePathTest() {
        String cracFileName1 = "20200813_0230_112_CRAC_FR1.xml";
        String cracFileName2 = "20200813_0230_112_CRAC_FR2.xml";
        when(minioAdapter.listFiles(any())).thenReturn(Arrays.asList(cracFileName1, cracFileName2));
        assertEquals("20200813_0230_112_CRAC_FR2.xml", fileUtils.getFrCracFilePath("IDCC", "2020-08-13T00:30Z"));
    }
}

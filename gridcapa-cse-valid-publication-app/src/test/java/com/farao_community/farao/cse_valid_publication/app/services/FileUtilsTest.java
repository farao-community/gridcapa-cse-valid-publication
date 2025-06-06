/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid.api.resource.CseValidFileResource;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@SpringBootTest
class FileUtilsTest {

    @Autowired
    private FileUtils fileUtils;

    @MockitoBean
    MinioAdapter minioAdapter;

    @Test
    void getVersionNumberRegexDoesNotMatch() {
        assertEquals(-1, FileUtils.getVersionNumber("notFoundRegex", "filename.ext"));
    }

    @Test
    void getVersionNumber() {
        assertEquals(42, FileUtils.getVersionNumber("Test_(?<version>[0-9]{1,2}).xml", "Test_42.xml"));
    }

    @Test
    void createFileResource() {
        CseValidFileResource fileResource = fileUtils.createFileResource("file.ext", "http://path/to/file.ext");

        assertNotNull(fileResource);
        assertEquals("file.ext", fileResource.getFilename());
        assertEquals("http://path/to/file.ext", fileResource.getUrl());
    }

    @Test
    void createFileResourceForNotPresentFile() {
        when(minioAdapter.generatePreSignedUrl(null)).thenReturn("http://preSignedUrl");

        CseValidFileResource fileResource = fileUtils.createFileResource("file.ext", null);

        assertNotNull(fileResource);
        assertEquals("file.ext", fileResource.getFilename());
        assertEquals("http://preSignedUrl", fileResource.getUrl());
    }
}

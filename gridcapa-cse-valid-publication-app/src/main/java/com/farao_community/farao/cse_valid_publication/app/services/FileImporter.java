/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.xsd.ObjectFactory;
import com.farao_community.farao.cse_valid_publication.app.xsd.TcDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBIntrospector;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class FileImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileImporter.class);
    private final UrlValidationService urlValidationService;

    public FileImporter(UrlValidationService urlValidationService) {
        this.urlValidationService = urlValidationService;
    }

    public TcDocumentType importTtcFile(String ttcFileUrl, String ttcFileType) {
        try (InputStream inputStream = urlValidationService.openUrlStream(ttcFileUrl)) {
            JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
            return (TcDocumentType) JAXBIntrospector.getValue(jaxbContext.createUnmarshaller().unmarshal(inputStream));
        } catch (IOException e) {
            LOGGER.warn("Cannot open TTC {} url {} with error {}", ttcFileType, ttcFileUrl, e.getMessage());
            return null;
        } catch (Exception e) {
            LOGGER.warn("Cannot retrieve TTC {} file {} with error {}", ttcFileType, ttcFileUrl, e.getMessage());
            return null;
        }
    }
}

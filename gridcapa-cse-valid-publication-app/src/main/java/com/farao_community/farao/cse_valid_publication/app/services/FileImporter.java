/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.configuration.CseValidPublicationProperties;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.ObjectFactory;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TcDocumentType;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBIntrospector;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class FileImporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileImporter.class);
    private final MinioAdapter minioAdapter;
    private final CseValidPublicationProperties properties;

    public FileImporter(MinioAdapter minioAdapter, CseValidPublicationProperties properties) {
        this.minioAdapter = minioAdapter;
        this.properties = properties;
    }

    public TcDocumentType importTtcAdjustment(String process, LocalDate targetDate) {
        try {
            String folder = String.format("%s/TTC_ADJUSTMENT/", process);
            String filenameRegex = properties.getFilenames().getTtcAdjustment().replace("[0-9]{8}", targetDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
            String ttcAdjustmentFileName = getMostRecentFile(folder, filenameRegex);
            if (minioAdapter.fileExists(ttcAdjustmentFileName)) {
                InputStream inputStream = minioAdapter.getFile(ttcAdjustmentFileName);
                JAXBContext jaxbContext = JAXBContext.newInstance(ObjectFactory.class);
                LOGGER.info("Importing TTC adjustment file '{}'", ttcAdjustmentFileName);
                return (TcDocumentType) JAXBIntrospector.getValue(jaxbContext.createUnmarshaller().unmarshal(inputStream));
            } else {
                LOGGER.warn("TTC adjustment file does not exist {} ", ttcAdjustmentFileName);
                return null;
            }
        } catch (Exception e) {
            String message = String.format("Cannot retrieve TTC adjustment file for CSE, process '%s' and date '%s'", process, targetDate.format(DateTimeFormatter.ISO_DATE));
            throw new CseValidPublicationInvalidDataException(message, e);
        }
    }

    private String getMostRecentFile(String prefixDate, String regex) {
        List<String> files = minioAdapter.listFiles(prefixDate);
        int mostRecentVersion = -1;
        String mostRecentFilename = null;

        for (String filename : files) {
            int version = Utils.getVersionNumber(regex, filename);

            if (version > mostRecentVersion) {
                mostRecentFilename = filename;
                mostRecentVersion = version;
            }
        }
        return mostRecentFilename;
    }

}

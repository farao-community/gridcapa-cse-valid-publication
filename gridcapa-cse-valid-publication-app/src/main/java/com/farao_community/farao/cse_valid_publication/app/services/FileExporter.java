/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.configuration.FilenamesConfiguration;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class FileExporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileExporter.class);
    private final MinioAdapter minioAdapter;
    private final FilenamesConfiguration filenamesConfiguration;

    public FileExporter(MinioAdapter minioAdapter, FilenamesConfiguration properties) {
        this.minioAdapter = minioAdapter;
        this.filenamesConfiguration = properties;
    }

    public void saveTtcValidation(TcDocumentTypeWriter tcDocumentTypeWriter, String process, LocalDate localDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(filenamesConfiguration.getTtcValidation());
        String processCode = ProcessUtils.getProcessCode(process);
        String filename = putMostRecentFile(localDate, process, String.format(localDate.format(formatter), processCode), tcDocumentTypeWriter);
        InputStream ttcValidationIs = tcDocumentTypeWriter.buildTcDocumentType();
        String filenameForLog = filename.replaceAll("[\n\r\t]", "_");
        LOGGER.info("Save TTC validation file '{}'", filenameForLog);
        minioAdapter.uploadOutput(filename, ttcValidationIs);
    }

    private String putMostRecentFile(LocalDate localDate, String process, String regex, TcDocumentTypeWriter tcDocumentTypeWriter) {
        String folder = String.format("%s/TTC_VALIDATION/", process);
        List<String> files = minioAdapter.listFiles(folder);

        try {
            int mostRecentVersion = 0;

            for (String filename : files) {
                int version = FileUtils.getVersionNumber(regex, filename);

                if (version > mostRecentVersion) {
                    mostRecentVersion = version;
                }
            }

            String processCode = ProcessUtils.getProcessCode(process);
            String filenameFormatted = String.format(localDate.format(DateTimeFormatter.ofPattern(filenamesConfiguration.getTtcValidation(), Locale.FRANCE)), processCode);
            filenameFormatted = filenameFormatted.replace("(?<version>[0-9]{1,2})", String.valueOf(mostRecentVersion + 1));
            tcDocumentTypeWriter.setVersionNumber(mostRecentVersion + 1);
            return String.format("%s%s", folder, filenameFormatted);
        } catch (Exception e) {
            String message = String.format("Cannot upload TTC validation file for process '%s', target date '%s'", process, localDate);
            throw new CseValidPublicationInternalException(message, e);
        }
    }
}

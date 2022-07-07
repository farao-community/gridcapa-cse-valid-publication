/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid.api.resource.CseValidFileResource;
import com.farao_community.farao.cse_valid_publication.app.configuration.FilenamesConfiguration;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Component
public class FileUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtils.class);
    private final MinioAdapter minioAdapter;
    private final FilenamesConfiguration filenamesConfiguration;

    public FileUtils(MinioAdapter minioAdapter, FilenamesConfiguration filenamesConfiguration) {
        this.minioAdapter = minioAdapter;
        this.filenamesConfiguration = filenamesConfiguration;
    }

    public String getTtcAdjustmentFileName(String process, LocalDate targetDate) {
        String folder = String.format("%s/TTC_ADJUSTMENT/", process);
        String filenameRegex = filenamesConfiguration.getTtcAdjustment().replace("[0-9]{8}", targetDate.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        return getMostRecentFile(folder, filenameRegex);
    }

    public String getFrCracFilePath(String process, String timestamp) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp).atZoneSameInstant(ZoneId.of("Europe/Brussels")).toOffsetDateTime();
        String parentDirectory = String.format("%s/CRACs/", process);
        String regexWithDateTime = replaceDateTimeInCracFilename(filenamesConfiguration.getCrac(), offsetDateTime);
        try {
            LOGGER.info(String.format("Trying to find the CRAC file %s%s", parentDirectory, replaceDateTimeInCracFilename(filenamesConfiguration.getCrac(), offsetDateTime)));
            return getMostRecentFile(parentDirectory, regexWithDateTime);
        } catch (CseValidPublicationInvalidDataException e) {
            return null;
        }
    }

    private String replaceDateTimeInCracFilename(String cracFileRegex, OffsetDateTime offsetDateTime) {
        String newCracRegex = cracFileRegex.replace("(?<year>[0-9]{4})", String.format("%04d", offsetDateTime.getYear()))
                .replace("(?<month>[0-9]{2})", String.format("%02d", offsetDateTime.getMonthValue()))
                .replace("(?<day>[0-9]{2})", String.format("%02d", offsetDateTime.getDayOfMonth()))
                .replace("(?<hour>[0-9]{2})", String.format("%02d", offsetDateTime.getHour()))
                .replace("(?<minute>[0-9]{2})", String.format("%02d", offsetDateTime.getMinute()));
        return newCracRegex;
    }

    private String getMostRecentFile(String prefixPath, String regex) {
        List<String> files = minioAdapter.listFiles(prefixPath); // files contains prefixFolder
        int mostRecentVersion = -1;
        String mostRecentFilename = null;

        for (String filename : files) {
            int version = getVersionNumber(regex, filename);

            if (version > mostRecentVersion) {
                mostRecentFilename = filename;
                mostRecentVersion = version;
            }
        }
        return mostRecentFilename;
    }

    int getVersionNumber(String regex, String filename) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(FilenameUtils.getName(filename));
        if (matcher.matches()) {
            String s = matcher.group("version");
            return Integer.parseInt(s);
        } else {
            return -1;
        }
    }

    public CseValidFileResource createFileResource(String filename, String filePath) {
        return new CseValidFileResource(filename, minioAdapter.generatePreSignedUrl(filePath));
    }

    public CseValidFileResource createFileResource(String filePath) {
        String filename = FilenameUtils.getName(filePath); //todo a tester
        return createFileResource(filename, filePath);
    }
}

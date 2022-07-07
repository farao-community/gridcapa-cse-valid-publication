/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid.api.resource.CseValidFileResource;
import com.farao_community.farao.cse_valid.api.resource.CseValidRequest;
import com.farao_community.farao.cse_valid.api.resource.CseValidResponse;
import com.farao_community.farao.cse_valid_publication.app.configuration.AmqpMessagesConfiguration;
import com.farao_community.farao.cse_valid_publication.app.configuration.FilenamesConfiguration;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.services.FileExporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileImporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileUtils;
import com.farao_community.farao.cse_valid_publication.app.services.TcDocumentTypeWriter;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TTime;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TTimestamp;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TcDocumentType;
import com.farao_community.farao.gridcapa_cse_valid.starter.CseValidClient;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class CseValidPublicationService {
    private static final String APPLICATION_ID = "process-publication-server";
    private static final String CONTENT_ENCODING = "UTF-8";
    private static final String CONTENT_TYPE = "application/vnd.api+json";
    private static final int PRIORITY = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationService.class);

    private final AmqpMessagesConfiguration amqpMessagesConfiguration;
    private final AmqpTemplate amqpTemplate;
    private final JsonConverter jsonConverter;
    private final FileImporter fileImporter;
    private final FileExporter fileExporter;
    private final FilenamesConfiguration filenamesConfiguration;
    private final MinioAdapter minioAdapter;
    private TcDocumentTypeWriter tcDocumentTypeWriter;
    private final FileUtils fileUtils;
    private final CseValidClient cseValidClient;

    public CseValidPublicationService(AmqpMessagesConfiguration amqpMessagesConfiguration, AmqpTemplate amqpTemplate, JsonConverter jsonConverter, FileImporter fileImporter, FileExporter fileExporter, FilenamesConfiguration filenamesConfiguration, MinioAdapter minioAdapter, FileUtils fileUtils, CseValidClient cseValidClient) {
        this.amqpMessagesConfiguration = amqpMessagesConfiguration;
        this.filenamesConfiguration = filenamesConfiguration;
        this.amqpTemplate = amqpTemplate;
        this.jsonConverter = jsonConverter;
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.minioAdapter = minioAdapter;
        this.fileUtils = fileUtils;
        this.cseValidClient = cseValidClient;
    }

    public ProcessStartRequest publishProcess(String nullableId, String process, String targetDate, int targetDateOffset) {
        String id = nullableId;
        if (id == null) {
            id = UUID.randomUUID().toString();
            LOGGER.warn("No ID provided for process request, auto generated to {}", id);
        }
        LocalDate localTargetDate;
        try {
            localTargetDate = LocalDate.parse(targetDate);
        } catch (DateTimeException e) {
            throw new CseValidPublicationInvalidDataException(String.format("Incorrect format for target date : '%s' is invalid, please use ISO-8601 format", targetDate), e);
        }
        localTargetDate = localTargetDate.plusDays(targetDateOffset);

        tcDocumentTypeWriter = new TcDocumentTypeWriter(process, localTargetDate);
        String ttcAdjustmentFilePath = fileUtils.getTtcAdjustmentFileName(process, localTargetDate);
        TcDocumentType tcDocument = fileImporter.importTtcAdjustment(ttcAdjustmentFilePath);
        if (tcDocument != null) {
            List<CompletableFuture<Void>> timestamps = new ArrayList<>();
            List<TTimestamp> timestampsToBeValidated = tcDocument.getAdjustmentResults().get(0).getTimestamp();
            LOGGER.info("TTC adjustment file contains {} timestamps to be validated", timestampsToBeValidated.size());
            for (TTimestamp ts : timestampsToBeValidated) {
                LOGGER.info("Running validation for timestamp : {}", ts.getTime().getV());
                if (checkIfComputationIsNeeded(process, ts, tcDocumentTypeWriter)) {
                    OffsetDateTime targetTimestamp = OffsetDateTime.parse(ts.getReferenceCalculationTime().getV()).atZoneSameInstant(ZoneId.of("Europe/Brussels")).toOffsetDateTime();
                    CseValidFileResource ttcAdjustmentFile = fileUtils.createFileResource(ttcAdjustmentFilePath);
                    CseValidFileResource cracFile = fileUtils.createFileResource(fileUtils.getFrCracFilePath(process,  ts.getReferenceCalculationTime().getV()));
                    CseValidFileResource cgmFile = fileUtils.createFileResource(ts.getCGMfile().getV(), process + "/CGMs/" + ts.getCGMfile().getV());
                    CseValidFileResource glskFile = fileUtils.createFileResource(ts.getGSKfile().getV(), process + "/GLSKs/" + ts.getGSKfile().getV());

                    CseValidRequest cseValidRequest = buildCseValidRequest(process, id, targetTimestamp, ttcAdjustmentFile, cracFile, cgmFile, glskFile);
                    CseValidResponse cseValidResponse = cseValidClient.run(cseValidRequest);
                    LOGGER.info("Cse valid response received: {}", cseValidResponse);
                    fillWithCseValidResponse(ts, cseValidResponse);
                    /*
                    timestamps.add(addAliasesToInputNetwork(request, ts, timestampId)
                            .thenCompose(aliasesResponse -> callDichotomyService(request, ts, timestampId, aliasesResponse))
                            .thenAccept(dichotomyResponse -> fillResultsWithDichotomyResponse(ts, dichotomyResponse, timestampId, tcDocumentTypeWriter))
                            .exceptionally(ex -> {
                                LOGGER.error(String.format("Exception occurred during results creation for timestamp %s", ts.getTimeInterval().getV()), ex);
                                tcDocumentTypeWriter.fillWithError(ts);
                                return null;
                            })
                    );*/
                }
            }
            CompletableFuture.allOf(timestamps.toArray(new CompletableFuture[0])).join();

        } else {
            tcDocumentTypeWriter.fillWithNoTtcAdjustmentError();
        }
        fileExporter.saveTtcValidation(tcDocumentTypeWriter, process, localTargetDate);
        return null; //todo response json
    }

    private void fillWithCseValidResponse(TTimestamp ts, CseValidResponse cseValidResponse) {
        if (cseValidResponse.getResultFileUrl() != null) {
            TcDocumentType tcDocumentType = fileImporter.importTtcValidation(cseValidResponse.getResultFileUrl());
            TTimestamp timestampResult = getTimestampResult(tcDocumentType, ts.getTime());
            if (timestampResult != null) {
                tcDocumentTypeWriter.fillWithTimestampResult(timestampResult);
            } else {
                LOGGER.warn("No timestamp result found for {}", ts.getTime());
                tcDocumentTypeWriter.fillWithError(ts);
            }
        } else {
            LOGGER.warn("No TTC validation url found for {}", ts.getTime());
            tcDocumentTypeWriter.fillWithError(ts);
        }
    }

    private TTimestamp getTimestampResult(TcDocumentType tcDocumentType, TTime time) {
        if (tcDocumentType != null) {
            return tcDocumentType.getValidationResults().get(0).getTimestamp().stream()
                    .filter(t -> t.getTime().getV().equals(time.getV()))
                    .findFirst()
                    .orElse(null);
        } else {
            return null;
        }
    }

    CseValidRequest buildCseValidRequest(String process, String id, OffsetDateTime timestamp, CseValidFileResource ttcAdjustmentFile, CseValidFileResource cracFile, CseValidFileResource cgmFile, CseValidFileResource glskFile) {
        switch (process) {
            case "IDCC":
                return CseValidRequest.buildIdccValidRequest(id,
                        timestamp,
                        ttcAdjustmentFile,
                        cracFile,
                        cgmFile,
                        glskFile);
            case "D2CC":
                return CseValidRequest.buildD2ccValidRequest(id,
                        timestamp,
                        ttcAdjustmentFile,
                        cracFile,
                        cgmFile,
                        glskFile);
            default:
                throw new NotImplementedException(String.format("Unknown target process for CSE: %s", process));

        }
    }

    private boolean checkIfComputationIsNeeded(String process, TTimestamp ts, TcDocumentTypeWriter tcDocumentTypeWriter) {
        // This TTC_Adjustment file must contains important non null values (Mnii, MiBnii, Antc)
        if (!datasPresentInTTCAdjustmentFile(ts)) {
            tcDocumentTypeWriter.fillTimestampWithMissingInputFiles(ts, "Process fail during TSO validation phase: Missing datas.");
            return false;
        }

        // The timestamp must need to be validated (MiBnii - Antc <= Mnii)
        if (!actualMaxImportAugmented(ts)) {
            tcDocumentTypeWriter.fillTimestampWithNoComputationNeeded(ts);
            return false;
        }

        // The files (CRAC, CGM, GLSK) specified inside the timestamp must be present on the MinIO server
        return areFilesPresent(process, tcDocumentTypeWriter, ts);

    }

    private boolean datasPresentInTTCAdjustmentFile(TTimestamp ts) {
        if (ts.getMNII() == null || ts.getMiBNII() == null || ts.getANTCFinal() == null
                || (ts.getMiBNII().getV().intValue() == 0 && ts.getANTCFinal().getV().intValue() == 0)) {
            LOGGER.info("Missing datas in TTC Adjustment");
            return false;
        }
        return true;
    }

    private boolean actualMaxImportAugmented(TTimestamp ts) {
        // In theory , test should be ==, but >= ensure that no calculation is done if MNII > MiBNII, which should not happen
        // But who knows...
        int mibniiMinusAntc = ts.getMiBNII().getV().intValue() - ts.getANTCFinal().getV().intValue();
        int mnii = ts.getMNII().getV().intValue();
        if (mibniiMinusAntc >= mnii) {
            LOGGER.info("Timestamp '{}' NTC has not been augmented by adjustment process, no computation needed.", ts.getTime().getV());
            return false;
        }
        LOGGER.info("Timestamp '{}' augmented NTC must be validated.", ts.getTime().getV());
        return true;
    }

    private boolean areFilesPresent(String process, TcDocumentTypeWriter tcDocumentTypeWriter, TTimestamp ts) {
        boolean isCgmFileAvailable = ts.getCGMfile() != null && minioAdapter.fileExists(process + "/CGMs/" + ts.getCGMfile().getV());
        boolean isGlskFileAvailable = ts.getGSKfile() != null && minioAdapter.fileExists(process + "/GLSKs/" + ts.getGSKfile().getV());
        boolean isCracFileAvailable = ts.getReferenceCalculationTime() != null && minioAdapter.fileExists(fileUtils.getFrCracFilePath(process, ts.getReferenceCalculationTime().getV()));

        if (!isCgmFileAvailable || !isCracFileAvailable || !isGlskFileAvailable) {
            LOGGER.error("Missing some input files for timestamp '{}'", ts.getTime().getV());
            tcDocumentTypeWriter.fillTimestampWithMissingInputFiles(ts, redFlagReasonError(isCgmFileAvailable, isCracFileAvailable, isGlskFileAvailable));
            return false;
        }
        return true;
    }

    private String redFlagReasonError(boolean cgmFileMissing, boolean cracFileMissing, boolean glskFileMissing) {
        StringJoiner stringJoiner = new StringJoiner(", ", "Process fail during TSO validation phase: Missing ", ".");

        if (!cgmFileMissing) {
            stringJoiner.add("CGM file");
        }
        if (!cracFileMissing) {
            stringJoiner.add("CRAC file");
        }
        if (!glskFileMissing) {
            stringJoiner.add("GLSK file");
        }

        return stringJoiner.toString();
    }
}

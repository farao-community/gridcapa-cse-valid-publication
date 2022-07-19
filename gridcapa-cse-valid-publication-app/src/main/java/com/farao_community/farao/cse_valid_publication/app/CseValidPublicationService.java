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
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationService.class);

    private final AmqpMessagesConfiguration amqpMessagesConfiguration;
    private final AmqpTemplate amqpTemplate;
    private final FileImporter fileImporter;
    private final FileExporter fileExporter;
    private final FilenamesConfiguration filenamesConfiguration;
    private final MinioAdapter minioAdapter;
    private TcDocumentTypeWriter tcDocumentTypeWriter;
    private final FileUtils fileUtils;
    private final CseValidClient cseValidClient;

    public CseValidPublicationService(AmqpMessagesConfiguration amqpMessagesConfiguration, AmqpTemplate amqpTemplate, FileImporter fileImporter, FileExporter fileExporter, FilenamesConfiguration filenamesConfiguration, MinioAdapter minioAdapter, FileUtils fileUtils, CseValidClient cseValidClient) {
        this.amqpMessagesConfiguration = amqpMessagesConfiguration;
        this.filenamesConfiguration = filenamesConfiguration;
        this.amqpTemplate = amqpTemplate;
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.minioAdapter = minioAdapter;
        this.fileUtils = fileUtils;
        this.cseValidClient = cseValidClient;
    }

    public boolean publishProcess(String nullableId, String process, String targetDate, int targetDateOffset) {
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
        String processCode = getProcessCode(process);
        tcDocumentTypeWriter = new TcDocumentTypeWriter(processCode, localTargetDate);
        String ttcAdjustmentFilePath = fileUtils.getTtcAdjustmentFileName(process, localTargetDate);
        TcDocumentType tcDocument = fileImporter.importTtcAdjustment(ttcAdjustmentFilePath);
        if (tcDocument != null) {
            List<CompletableFuture<Void>> timestamps = new ArrayList<>();
            List<TTimestamp> timestampsToBeValidated = tcDocument.getAdjustmentResults().get(0).getTimestamp();
            LOGGER.info("TTC adjustment file contains {} timestamps to be validated", timestampsToBeValidated.size());
            for (TTimestamp ts : timestampsToBeValidated) {
                LOGGER.info("Running validation for timestamp : {}", ts.getTime().getV());

                timestamps.add(addCseValidRequest(process, id, ttcAdjustmentFilePath, ts)
                        .thenCompose(this::runCseValidRequest)
                        .thenAccept(cseValidResponse -> fillWithCseValidResponse(ts, cseValidResponse))
                        .exceptionally(ex -> {
                            LOGGER.error(String.format("Exception occurred during results creation for timestamp %s", ts.getTime().getV()), ex);
                            tcDocumentTypeWriter.fillWithError(ts);
                            return null;
                        })
                );
            }
            CompletableFuture.allOf(timestamps.toArray(new CompletableFuture[0])).join();
        } else {
            tcDocumentTypeWriter.fillWithNoTtcAdjustmentError();
        }
        fileExporter.saveTtcValidation(tcDocumentTypeWriter, process, localTargetDate);
        return true;
    }

    private String getProcessCode(String process) {
        switch (process) {
            case "IDCC":
                return "ID";
            case "D2CC":
                return "2D";
            default:
                throw new CseValidPublicationInvalidDataException(String.format("Unknown target process for CSE: %s", process));
        }
    }

    private CompletableFuture<CseValidResponse> runCseValidRequest(CseValidRequest cseValidRequest) {
        if (cseValidRequest == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> cseValidClient.run(cseValidRequest)) //todo cse publication send requests asynchronously but cse valid runner does not allow yet asynchronous run
                .exceptionally(ex -> {
                    LOGGER.error(String.format("Exception during running Cse Valid request for timestamp '%s'", cseValidRequest.getTimestamp()), ex);
                    return null;
                });
    }

    private  CompletableFuture<CseValidRequest> addCseValidRequest(String process, String id, String ttcAdjustmentFilePath, TTimestamp ts) {
        return CompletableFuture.supplyAsync(() -> buildCseValidRequest(process, id, ttcAdjustmentFilePath, ts))
                .exceptionally(ex -> {
                    LOGGER.error(String.format("Exception during cse valid request creation for timestamp '%s'", ts.getTime().getV()), ex);
                    return null;
                });
    }

    private void fillWithCseValidResponse(TTimestamp ts, CseValidResponse cseValidResponse) {
        LOGGER.info("Cse valid response received {}", cseValidResponse);
        if (cseValidResponse != null && cseValidResponse.getResultFileUrl() != null) {
            TcDocumentType tcDocumentType = fileImporter.importTtcValidation(cseValidResponse.getResultFileUrl());
            TTimestamp timestampResult = getTimestampResult(tcDocumentType, ts.getTime());
            if (timestampResult != null) {
                LOGGER.info("Filling timestamp result for {}", ts.getTime().getV());
                tcDocumentTypeWriter.fillWithTimestampResult(timestampResult);
            } else {
                LOGGER.warn("No timestamp result found for {}", ts.getTime().getV());
                tcDocumentTypeWriter.fillWithError(ts);
            }
        } else {
            LOGGER.warn("No TTC validation url found for {}", ts.getTime().getV());
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

    CseValidRequest buildCseValidRequest(String process, String id, String ttcAdjustmentFilePath, TTimestamp ts) {
        OffsetDateTime targetTimestamp = OffsetDateTime.parse(ts.getReferenceCalculationTime().getV()).atZoneSameInstant(ZoneId.of("Europe/Brussels")).toOffsetDateTime();
        CseValidFileResource ttcAdjustmentFile = fileUtils.createFileResource(ttcAdjustmentFilePath);
        CseValidFileResource cgmFile = fileUtils.createFileResource(ts.getCGMfile().getV(), process + "/CGMs/" + ts.getCGMfile().getV());
        CseValidFileResource glskFile = fileUtils.createFileResource(ts.getGSKfile().getV(), process + "/GLSKs/" + ts.getGSKfile().getV());
        String cracFilePath = fileUtils.getFrCracFilePath(process,  ts.getReferenceCalculationTime().getV());
        CseValidFileResource cracFile = cracFilePath != null ? fileUtils.createFileResource(cracFilePath) : fileUtils.createFileResource("NOT_PRESENT", null);
        switch (process) {
            case "IDCC":
                return CseValidRequest.buildIdccValidRequest(id,
                        targetTimestamp,
                        ttcAdjustmentFile,
                        cracFile,
                        cgmFile,
                        glskFile);
            case "D2CC":
                return CseValidRequest.buildD2ccValidRequest(id,
                        targetTimestamp,
                        ttcAdjustmentFile,
                        cracFile,
                        cgmFile,
                        glskFile);
            default:
                throw new NotImplementedException(String.format("Unknown target process for CSE: %s", process));

        }
    }

}

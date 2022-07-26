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
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.services.FileExporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileImporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileUtils;
import com.farao_community.farao.cse_valid_publication.app.services.TcDocumentTypeWriter;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTime;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTimestamp;
import com.farao_community.farao.cse_valid_publication.app.xsd.TcDocumentType;
import com.farao_community.farao.gridcapa_cse_valid.starter.CseValidClient;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class CseValidPublicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationService.class);

    private final FileImporter fileImporter;
    private final FileExporter fileExporter;
    private TcDocumentTypeWriter tcDocumentTypeWriter;
    private final FileUtils fileUtils;
    private final CseValidClient cseValidClient;

    public CseValidPublicationService(FileImporter fileImporter, FileExporter fileExporter, FileUtils fileUtils, CseValidClient cseValidClient) {
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
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
            HashMap<TTimestamp, CseValidRequest> timestampCseValidRequests = new HashMap<>();
            List<TTimestamp> timestampsToBeValidated = tcDocument.getAdjustmentResults().get(0).getTimestamp();
            LOGGER.info("TTC adjustment file contains {} timestamps to be validated", timestampsToBeValidated.size());
            String cseValidRequestId = UUID.randomUUID().toString();
            timestampsToBeValidated.forEach(ts -> timestampCseValidRequests.put(ts, buildCseValidRequest(process, cseValidRequestId, ttcAdjustmentFilePath, ts)));
            Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCseValidResponses = new HashMap<>();
            try {
                runCseValidRequests(timestampCseValidRequests, timestampCseValidResponses);
            } catch (InterruptedException e) {
                LOGGER.error("Error during Cse valid running for date {}", targetDate, e);
                Thread.currentThread().interrupt();
                throw new CseValidPublicationInternalException(String.format("Error during Cse valid running for date %s ", targetDate), e);
            } catch (ExecutionException e) {
                LOGGER.error("Error during Cse valid running for date {}", targetDate, e);
                throw new CseValidPublicationInternalException(String.format("Error during Cse valid running for date %s ", targetDate), e);
            }
            fillResultForAllTimestamps(timestampCseValidResponses);
        } else {
            tcDocumentTypeWriter.fillWithNoTtcAdjustmentError();
        }
        fileExporter.saveTtcValidation(tcDocumentTypeWriter, process, localTargetDate);
        return true;
    }

    private void fillResultForAllTimestamps(Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCseValidResponses) {
        timestampCseValidResponses.forEach((ts, cseValidResponseCompletableFuture) -> {
            try {
                fillWithCseValidResponse(ts, cseValidResponseCompletableFuture.get());
            } catch (ExecutionException e) {
                LOGGER.error(String.format("Exception occurred during results creation for timestamp %s", ts.getTime().getV()), e);
                tcDocumentTypeWriter.fillWithError(ts);
            } catch (InterruptedException e) {
                LOGGER.error(String.format("Exception occurred during results creation for timestamp %s", ts.getTime().getV()), e);
                Thread.currentThread().interrupt();
                tcDocumentTypeWriter.fillWithError(ts);
            }
        });
    }

    private void runCseValidRequests(HashMap<TTimestamp, CseValidRequest> timestampCseValidRequests, Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCompletableFutures) throws ExecutionException, InterruptedException {
        timestampCseValidRequests.forEach((ts, request) -> {
            CompletableFuture<CseValidResponse> cseValidResponseCompletable = runCseValidRequest(request);
            timestampCompletableFutures.put(ts, cseValidResponseCompletable);
            cseValidResponseCompletable.thenAccept(cseValidResponse1 -> LOGGER.info("Cse valid response received {}", cseValidResponse1))
                    .exceptionally(ex -> {
                        LOGGER.error(String.format("Exception occurred during running Cse valid request for time %s", ts.getTime().getV()), ex);
                        return null;
                    });
        });
        CompletableFuture.allOf(timestampCompletableFutures.values().toArray(new CompletableFuture[0])).get();
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
        //cse publication send requests asynchronously but cse valid runner does not allow yet asynchronous run
        if (cseValidRequest == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.supplyAsync(() -> cseValidClient.run(cseValidRequest))
                .exceptionally(ex -> {
                    LOGGER.error(String.format("Exception during running Cse Valid request for timestamp '%s'", cseValidRequest.getTimestamp()), ex);
                    return null;
                });
    }

    private void fillWithCseValidResponse(TTimestamp ts, CseValidResponse cseValidResponse) {
        if (cseValidResponse != null && cseValidResponse.getResultFileUrl() != null) {
            TcDocumentType tcDocumentType = fileImporter.importTtcValidation(cseValidResponse.getResultFileUrl());
            TTimestamp timestampResult = getTimestampResult(tcDocumentType, ts.getTime());
            if (timestampResult != null) {
                LOGGER.info("Filling timestamp result for time {}", ts.getTime().getV());
                tcDocumentTypeWriter.fillWithTimestampResult(timestampResult);
            } else {
                LOGGER.warn("No timestamp result found for time {}", ts.getTime().getV());
                tcDocumentTypeWriter.fillWithError(ts);
            }
        } else {
            LOGGER.warn("No TTC validation url found for time {}", ts.getTime().getV());
            tcDocumentTypeWriter.fillWithError(ts);
        }
    }

    private TTimestamp getTimestampResult(TcDocumentType tcDocumentType, TTime time) {
        if (tcDocumentType != null && tcDocumentType.getValidationResults().get(0) != null) {
            return tcDocumentType.getValidationResults().get(0).getTimestamp().stream()
                    .filter(t -> t.getTime().getV().equals(time.getV()))
                    .findFirst()
                    .orElse(null);
        } else {
            return null;
        }
    }

    private CseValidRequest buildCseValidRequest(String process, String id, String ttcAdjustmentFilePath, TTimestamp ts) {
        OffsetDateTime time = OffsetDateTime.parse(ts.getTime().getV());
        OffsetDateTime targetTimestamp = OffsetDateTime.parse(ts.getReferenceCalculationTime().getV());
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
                        glskFile,
                        time);
            case "D2CC":
                return CseValidRequest.buildD2ccValidRequest(id,
                        targetTimestamp,
                        ttcAdjustmentFile,
                        cracFile,
                        cgmFile,
                        glskFile,
                        time);
            default:
                throw new NotImplementedException(String.format("Unknown target process for CSE: %s", process));

        }
    }

    public TcDocumentTypeWriter getTcDocumentTypeWriter() {
        return tcDocumentTypeWriter;
    }

}

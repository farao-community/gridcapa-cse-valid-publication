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
import com.farao_community.farao.cse_valid_publication.app.services.ProcessUtils;
import com.farao_community.farao.cse_valid_publication.app.services.TaskManagerService;
import com.farao_community.farao.cse_valid_publication.app.services.TcDocumentTypeWriter;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTime;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTimestamp;
import com.farao_community.farao.cse_valid_publication.app.xsd.TcDocumentType;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessRunDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa_cse_valid.starter.CseValidClient;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Service
public class CseValidPublicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");

    private final CseValidClient cseValidClient;
    private final FileExporter fileExporter;
    private final FileImporter fileImporter;
    private final FileUtils fileUtils;
    private final MinioAdapter minioAdapter;
    private final TaskManagerService taskManagerService;

    public CseValidPublicationService(CseValidClient cseValidClient, FileExporter fileExporter, FileImporter fileImporter, FileUtils fileUtils, MinioAdapter minioAdapter, final TaskManagerService taskManagerService) {
        this.cseValidClient = cseValidClient;
        this.fileExporter = fileExporter;
        this.fileImporter = fileImporter;
        this.fileUtils = fileUtils;
        this.minioAdapter = minioAdapter;
        this.taskManagerService = taskManagerService;
    }

    public void publishProcess(String process, String initialTargetDate, int targetDateOffset) {
        LocalDate targetDateWithOffset;
        try {
            targetDateWithOffset = LocalDate.parse(initialTargetDate).plusDays(targetDateOffset);
        } catch (DateTimeException e) {
            throw new CseValidPublicationInvalidDataException(String.format("Incorrect format for target date: '%s' is invalid, please use ISO-8601 format", initialTargetDate), e);
        }
        LOGGER.info("Target date with offset: {}", targetDateWithOffset);

        final String processCode = ProcessUtils.getProcessCode(process);
        final TcDocumentTypeWriter tcDocumentTypeWriter = new TcDocumentTypeWriter(processCode, targetDateWithOffset);

        final TaskDto[] taskDtoArray = taskManagerService.getTasksFromBusinessDate(targetDateWithOffset.toString())
                .filter(taskDtos -> taskDtos.length > 0)
                .orElseThrow(() -> new CseValidPublicationInternalException("Failed to retrieve task DTOs on business date"));

        getProcessFile(taskDtoArray[0], "TTC_ADJUSTMENT")
            .filter(this::isProcessFileDtoConsistent)
            .map(processFileDto -> fileImporter.importTtcFile(minioAdapter.generatePreSignedUrlFromFullMinioPath(processFileDto.getFilePath(), 1)))
            .ifPresentOrElse(
                tcDocumentType -> validateTtc(process, initialTargetDate, taskDtoArray, tcDocumentType, tcDocumentTypeWriter),
                tcDocumentTypeWriter::fillWithNoTtcAdjustmentError);

        fileExporter.saveTtcValidation(tcDocumentTypeWriter, process, targetDateWithOffset);
    }

    private void validateTtc(String process, String initialTargetDate, TaskDto[] taskDtoArray, TcDocumentType tcDocument, TcDocumentTypeWriter tcDocumentTypeWriter) {
        Map<TTimestamp, CseValidRequest> timestampCseValidRequests = new HashMap<>();
        List<TTimestamp> timestampsToBeValidated = tcDocument.getAdjustmentResults().get(0).getTimestamp();
        LOGGER.info("TTC adjustment file contains {} timestamps to be validated", timestampsToBeValidated.size());

        Map<String, TaskDto> taskDtoMap = Arrays.stream(taskDtoArray)
                .map(dto -> taskManagerService.addNewRunInTaskHistory(dto.getTimestamp().toString(), dto.getInputs()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(td -> td.getTimestamp().format(TIMESTAMP_FORMATTER), Function.identity()));
        timestampsToBeValidated.forEach(ts -> timestampCseValidRequests.put(ts, buildCseValidRequest(process, ts, taskDtoMap.get(ts.getReferenceCalculationTime().getV()))));

        Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCseValidResponses = new HashMap<>();
        try {
            runCseValidRequests(timestampCseValidRequests, timestampCseValidResponses);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CseValidPublicationInternalException(String.format("Error during Cse valid running for date %s", initialTargetDate), e);
        } catch (ExecutionException e) {
            throw new CseValidPublicationInternalException(String.format("Error during Cse valid running for date %s", initialTargetDate), e);
        }
        fillResultForAllTimestamps(timestampCseValidResponses, tcDocumentTypeWriter);
    }

    private CseValidRequest buildCseValidRequest(String process, TTimestamp ts, TaskDto taskDto) {
        String referenceCalculationTimeValue = ts.getReferenceCalculationTime().getV();

        if (taskDto != null) {
            OffsetDateTime time = OffsetDateTime.parse(ts.getTime().getV());
            OffsetDateTime targetTimestamp = OffsetDateTime.parse(referenceCalculationTimeValue);

            CseValidFileResource ttcAdjustmentFile = getFileResourceOrThrow(taskDto, "TTC_ADJUSTMENT", referenceCalculationTimeValue);
            CseValidFileResource cgmFile = getFileResource(taskDto, "CGM");
            CseValidFileResource glskFile = getFileResource(taskDto, "GLSK");
            CseValidFileResource importCracFile = getFileResource(taskDto, "IMPORT_CRAC");
            CseValidFileResource exportCracFile = getFileResource(taskDto, "EXPORT_CRAC");
            String runId = getCurrentRunId(taskDto);
            switch (process) {
                case "IDCC":
                    return CseValidRequest.buildIdccValidRequest(taskDto.getId().toString(),
                        runId,
                        targetTimestamp,
                        ttcAdjustmentFile,
                        importCracFile,
                        exportCracFile,
                        cgmFile,
                        glskFile,
                        time);
                case "D2CC":
                    return CseValidRequest.buildD2ccValidRequest(taskDto.getId().toString(),
                        runId,
                        targetTimestamp,
                        ttcAdjustmentFile,
                        importCracFile,
                        exportCracFile,
                        cgmFile,
                        glskFile,
                        time);
                default:
                    throw new NotImplementedException(String.format("Unknown target process for CSE: %s", process));
            }
        }
        throw new CseValidPublicationInvalidDataException(String.format("No task associated with the calculation time: %s", referenceCalculationTimeValue));
    }

    private void runCseValidRequests(Map<TTimestamp, CseValidRequest> timestampCseValidRequests, Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCompletableFutures) throws ExecutionException, InterruptedException {
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

    private void fillResultForAllTimestamps(Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCseValidResponses, TcDocumentTypeWriter tcDocumentTypeWriter) {
        timestampCseValidResponses.forEach((ts, cseValidResponseCompletableFuture) -> {
            try {
                fillWithCseValidResponse(ts, cseValidResponseCompletableFuture.get(), tcDocumentTypeWriter);
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

    private void fillWithCseValidResponse(TTimestamp ts, CseValidResponse cseValidResponse, TcDocumentTypeWriter tcDocumentTypeWriter) {
        if (cseValidResponse != null && cseValidResponse.getResultFileUrl() != null) {
            TcDocumentType tcDocumentType = fileImporter.importTtcFile(cseValidResponse.getResultFileUrl());
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

    private static Optional<ProcessFileDto> getProcessFile(TaskDto taskDto, String fileType) {
        return taskDto.getInputs().stream()
            .filter(f -> f.getFileType().equals(fileType))
            .findFirst();
    }

    private CseValidFileResource getFileResource(TaskDto taskDto, String fileType) {
        return getProcessFile(taskDto, fileType)
            .filter(this::isProcessFileDtoConsistent)
            .map(pfd -> fileUtils.createFileResource(pfd.getFilename(), minioAdapter.generatePreSignedUrlFromFullMinioPath(pfd.getFilePath(), 1)))
            .orElse(null);
    }

    private CseValidFileResource getFileResourceOrThrow(TaskDto taskDto, String fileType, String referenceCalculationTimeValue) {
        return getProcessFile(taskDto, fileType)
            .filter(this::isProcessFileDtoConsistent)
            .map(pfd -> fileUtils.createFileResource(pfd.getFilename(), minioAdapter.generatePreSignedUrlFromFullMinioPath(pfd.getFilePath(), 1)))
            .orElseThrow(() -> new CseValidPublicationInvalidDataException(String.format("No %s file found in task for timestamp: %s", fileType, referenceCalculationTimeValue)));
    }

    private boolean isProcessFileDtoConsistent(ProcessFileDto processFileDto) {
        return processFileDto.getFilename() != null && processFileDto.getFilePath() != null;
    }

    String getCurrentRunId(TaskDto taskDto) {
        List<ProcessRunDto> runHistory = taskDto.getRunHistory();
        if (runHistory == null || runHistory.isEmpty()) {
            LOGGER.warn("Failed to handle run request on timestamp {} because it has no run history", taskDto.getTimestamp());
            throw new CseValidPublicationInternalException("Failed to handle run request on timestamp because it has no run history");
        }
        runHistory.sort((o1, o2) -> o2.getExecutionDate().compareTo(o1.getExecutionDate()));
        return runHistory.get(0).getId().toString();
    }
}

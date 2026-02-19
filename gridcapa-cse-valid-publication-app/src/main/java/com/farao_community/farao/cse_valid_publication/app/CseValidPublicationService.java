/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid.api.resource.CseValidFileResource;
import com.farao_community.farao.cse_valid.api.resource.CseValidRequest;
import com.farao_community.farao.cse_valid.api.resource.CseValidResponse;
import com.farao_community.farao.cse_valid.api.resource.ProcessType;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.services.FileExporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileImporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileUtils;
import com.farao_community.farao.cse_valid_publication.app.services.TaskManagerService;
import com.farao_community.farao.cse_valid_publication.app.services.TcDocumentTypeWriter;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTimestamp;
import com.farao_community.farao.cse_valid_publication.app.xsd.TcDocumentType;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessRunDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa_cse_valid.starter.CseValidClient;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
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

import static com.farao_community.farao.cse_valid.api.resource.CseValidRequest.buildD2ccValidRequest;
import static com.farao_community.farao.cse_valid.api.resource.CseValidRequest.buildIdccValidRequest;
import static java.util.Comparator.comparing;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Oualid Aloui {@literal <oualid.aloui at rte-france.com>}
 */
@Service
public class CseValidPublicationService {
    private static final String TTC_ADJUSTMENT_FILE = "TTC_ADJUSTMENT";
    private static final String CGM_FILE = "CGM";
    private static final String GLSK_FILE = "GLSK";
    private static final String IMPORT_CRAC_FILE = "IMPORT_CRAC";
    private static final String EXPORT_CRAC_FILE = "EXPORT_CRAC";
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");

    private final CseValidClient cseValidClient;
    private final FileExporter fileExporter;
    private final FileImporter fileImporter;
    private final FileUtils fileUtils;
    private final MinioAdapter minioAdapter;
    private final TaskManagerService taskManagerService;

    public CseValidPublicationService(final CseValidClient cseValidClient,
                                      final FileExporter fileExporter,
                                      final FileImporter fileImporter,
                                      final FileUtils fileUtils,
                                      final MinioAdapter minioAdapter,
                                      final TaskManagerService taskManagerService) {
        this.cseValidClient = cseValidClient;
        this.fileExporter = fileExporter;
        this.fileImporter = fileImporter;
        this.fileUtils = fileUtils;
        this.minioAdapter = minioAdapter;
        this.taskManagerService = taskManagerService;
    }

    public void publishProcess(final ProcessType processType,
                               final String initialTargetDate,
                               final int targetDateOffset) {
        final LocalDate targetDateWithOffset;
        try {
            targetDateWithOffset = LocalDate.parse(initialTargetDate).plusDays(targetDateOffset);
        } catch (final DateTimeException e) {
            throw new CseValidPublicationInvalidDataException(String.format("Incorrect format for target date: '%s' is invalid, please use ISO-8601 format", initialTargetDate), e);
        }
        LOGGER.info("Target date with offset: {}", targetDateWithOffset);

        final String processCode = processType.getCode();
        final TcDocumentTypeWriter tcDocumentTypeWriter = new TcDocumentTypeWriter(processCode, targetDateWithOffset);

        final TaskDto[] taskDtoArray = taskManagerService.getTasksFromBusinessDate(targetDateWithOffset.toString())
            .filter(taskDtos -> taskDtos.length > 0)
            .orElseThrow(() -> new CseValidPublicationInternalException("Failed to retrieve task DTOs on business date"));

        getProcessFile(taskDtoArray[0], TTC_ADJUSTMENT_FILE)
            .filter(this::isProcessFileDtoConsistent)
            .map(processFileDto -> fileImporter.importTtcFile(minioAdapter.generatePreSignedUrlFromFullMinioPath(processFileDto.getFilePath(), 1)))
            .ifPresentOrElse(
                tcDocumentType -> validateTtc(processType, initialTargetDate, taskDtoArray, tcDocumentType, tcDocumentTypeWriter),
                tcDocumentTypeWriter::fillWithNoTtcAdjustmentError);

        fileExporter.saveTtcValidation(tcDocumentTypeWriter, processType, targetDateWithOffset);
    }

    private void validateTtc(final ProcessType processType,
                             final String initialTargetDate,
                             final TaskDto[] taskDtoArray,
                             final TcDocumentType tcDocument,
                             final TcDocumentTypeWriter tcDocumentTypeWriter) {
        final Map<TTimestamp, CseValidRequest> timestampCseValidRequests = new HashMap<>();
        final List<TTimestamp> timestampsToBeValidated = tcDocument.getAdjustmentResults().getFirst().getTimestamp();

        if (timestampsToBeValidated == null) {
            throw new CseValidPublicationInvalidDataException("TTC adjustment file has no timestamp");
        }

        LOGGER.info("TTC adjustment file contains {} timestamps to be validated", timestampsToBeValidated.size());

        final Map<String, TaskDto> taskDtoMap = Arrays.stream(taskDtoArray)
            .map(dto -> taskManagerService.addNewRunInTaskHistory(dto.getTimestamp().toString(), dto.getInputs()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(toMap(td -> td.getTimestamp().format(TIMESTAMP_FORMATTER), identity()));
        timestampsToBeValidated.forEach(ts -> timestampCseValidRequests.put(ts, buildCseValidRequest(processType, ts, taskDtoMap.get(ts.getReferenceCalculationTime().getV()))));

        final Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCseValidResponses = new HashMap<>();

        try {
            runCseValidRequests(timestampCseValidRequests, timestampCseValidResponses);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CseValidPublicationInternalException(String.format("Error during Cse valid running for date %s", initialTargetDate), e);
        } catch (final ExecutionException e) {
            throw new CseValidPublicationInternalException(String.format("Error during Cse valid running for date %s", initialTargetDate), e);
        }

        fillResultForAllTimestamps(timestampCseValidResponses, tcDocumentTypeWriter);
    }

    private CseValidRequest buildCseValidRequest(final ProcessType processType,
                                                 final TTimestamp ts,
                                                 final TaskDto taskDto) {
        final String referenceCalculationTimeValue = ts.getReferenceCalculationTime().getV();

        if (taskDto == null) {
            throw new CseValidPublicationInvalidDataException(String.format("No task associated with the calculation time: %s", referenceCalculationTimeValue));
        }

        final OffsetDateTime time = OffsetDateTime.parse(ts.getTime().getV());
        final OffsetDateTime targetTimestamp = OffsetDateTime.parse(referenceCalculationTimeValue);
        final CseValidFileResource ttcAdjustmentFile = getFileResourceOrThrow(taskDto, TTC_ADJUSTMENT_FILE, referenceCalculationTimeValue);
        final CseValidFileResource cgmFile = getFileResource(taskDto, CGM_FILE);
        final CseValidFileResource glskFile = getFileResource(taskDto, GLSK_FILE);
        final CseValidFileResource importCracFile = getFileResource(taskDto, IMPORT_CRAC_FILE);
        final CseValidFileResource exportCracFile = getFileResource(taskDto, EXPORT_CRAC_FILE);
        final String runId = getCurrentRunId(taskDto);

        return switch (processType) {
            case IDCC -> buildIdccValidRequest(taskDto.getId().toString(),
                                               runId,
                                               targetTimestamp,
                                               ttcAdjustmentFile,
                                               importCracFile,
                                               exportCracFile,
                                               cgmFile,
                                               glskFile,
                                               time);
            case D2CC -> buildD2ccValidRequest(taskDto.getId().toString(),
                                               runId,
                                               targetTimestamp,
                                               ttcAdjustmentFile,
                                               importCracFile,
                                               exportCracFile,
                                               cgmFile,
                                               glskFile,
                                               time);
        };
    }

    private void runCseValidRequests(final Map<TTimestamp, CseValidRequest> timestampCseValidRequests,
                                     final Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCompletableFutures) throws ExecutionException, InterruptedException {
        timestampCseValidRequests.forEach((ts, request) -> {
            final CompletableFuture<CseValidResponse> cseValidResponseCompletable = runCseValidRequest(request);
            timestampCompletableFutures.put(ts, cseValidResponseCompletable);

            cseValidResponseCompletable
                .thenAccept(cseValidResponse1 -> LOGGER.info("Cse valid response received {}", cseValidResponse1))
                .exceptionally(ex -> {
                    LOGGER.error(String.format("Exception occurred during running Cse valid request for time %s", ts.getTime().getV()), ex);
                    return null;
                });
        });

        CompletableFuture.allOf(timestampCompletableFutures.values().toArray(new CompletableFuture[0])).get();
    }

    private CompletableFuture<CseValidResponse> runCseValidRequest(final CseValidRequest cseValidRequest) {
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

    private void fillResultForAllTimestamps(final Map<TTimestamp, CompletableFuture<CseValidResponse>> timestampCseValidResponses,
                                            final TcDocumentTypeWriter tcDocumentTypeWriter) {
        timestampCseValidResponses.forEach((timestamp, cseValidResponseCompletableFuture) -> {
            try {
                fillWithCseValidResponse(timestamp, cseValidResponseCompletableFuture.get(), tcDocumentTypeWriter);
            } catch (final ExecutionException e) {
                LOGGER.error(String.format("Exception occurred during results creation for timestamp %s", timestamp.getTime().getV()), e);
                tcDocumentTypeWriter.fillWithError(timestamp, "Process failed: internal error during execution");
            } catch (final InterruptedException e) {
                LOGGER.error(String.format("Exception occurred during results creation for timestamp %s", timestamp.getTime().getV()), e);
                Thread.currentThread().interrupt();
                tcDocumentTypeWriter.fillWithError(timestamp, "Process failed: execution has been interrupted");
            }
        });
    }

    private void fillWithCseValidResponse(final TTimestamp timestamp,
                                          final CseValidResponse cseValidResponse,
                                          final TcDocumentTypeWriter tcDocumentTypeWriter) {
        final String time = timestamp.getTime().getV();

        if (cseValidResponse == null || cseValidResponse.getResultFileUrl() == null) {
            LOGGER.warn("No TTC validation url found for time {}", time);
            tcDocumentTypeWriter.fillWithError(timestamp, "Process failed: no response");
        } else {
            final TcDocumentType tcDocumentType = fileImporter.importTtcFile(cseValidResponse.getResultFileUrl());
            final TTimestamp timestampResult = getTimestampResult(tcDocumentType, time);

            if (timestampResult == null) {
                LOGGER.warn("No timestamp result found for time {}", time);
                tcDocumentTypeWriter.fillWithError(timestamp, "Process failed: Unsafe calculated values");
            } else {
                LOGGER.info("Filling timestamp result for time {}", time);
                tcDocumentTypeWriter.fillWithTimestampResult(timestampResult);
            }
        }
    }

    private TTimestamp getTimestampResult(final TcDocumentType tcDocumentType,
                                          final String time) {
        if (tcDocumentType == null || tcDocumentType.getValidationResults().getFirst() == null) {
            return null;
        }
        return tcDocumentType.getValidationResults().getFirst().getTimestamp().stream()
            .filter(t -> t.getTime().getV().equals(time))
            .findFirst()
            .orElse(null);
    }

    private static Optional<ProcessFileDto> getProcessFile(final TaskDto taskDto,
                                                           final String fileType) {
        return taskDto.getInputs().stream()
            .filter(f -> f.getFileType().equals(fileType))
            .findFirst();
    }

    private CseValidFileResource getFileResource(final TaskDto taskDto,
                                                 final String fileType) {
        return getProcessFile(taskDto, fileType)
            .filter(this::isProcessFileDtoConsistent)
            .map(file -> fileUtils.createFileResource(file.getFilename(), minioAdapter.generatePreSignedUrlFromFullMinioPath(file.getFilePath(), 1)))
            .orElse(null);
    }

    private CseValidFileResource getFileResourceOrThrow(final TaskDto taskDto,
                                                        final String fileType,
                                                        final String referenceCalculationTimeValue) {
        return getProcessFile(taskDto, fileType)
            .filter(this::isProcessFileDtoConsistent)
            .map(file -> fileUtils.createFileResource(file.getFilename(), minioAdapter.generatePreSignedUrlFromFullMinioPath(file.getFilePath(), 1)))
            .orElseThrow(() -> new CseValidPublicationInvalidDataException(String.format("No %s file found in task for timestamp: %s", fileType, referenceCalculationTimeValue)));
    }

    private boolean isProcessFileDtoConsistent(final ProcessFileDto processFileDto) {
        return processFileDto.getFilename() != null && processFileDto.getFilePath() != null;
    }

    String getCurrentRunId(final TaskDto taskDto) {
        final List<ProcessRunDto> runHistory = taskDto.getRunHistory();
        if (runHistory == null || runHistory.isEmpty()) {
            LOGGER.warn("Failed to handle run request on timestamp {} because it has no run history", taskDto.getTimestamp());
            throw new CseValidPublicationInternalException("Failed to handle run request on timestamp because it has no run history");
        }
        runHistory.sort(comparing(ProcessRunDto::getExecutionDate).reversed());
        return runHistory.getFirst().getId().toString();
    }
}

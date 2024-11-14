/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid.api.resource.CseValidFileResource;
import com.farao_community.farao.cse_valid.api.resource.CseValidResponse;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.services.FileExporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileImporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileUtils;
import com.farao_community.farao.cse_valid_publication.app.services.TaskManagerService;
import com.farao_community.farao.cse_valid_publication.app.xsd.TResultTimeseries;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTime;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTimestamp;
import com.farao_community.farao.cse_valid_publication.app.xsd.TcDocumentType;
import com.farao_community.farao.cse_valid_publication.app.xsd.TimeIntervalType;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessRunDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskStatus;
import com.farao_community.farao.gridcapa_cse_valid.starter.CseValidClient;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
class CseValidPublicationServiceTest {

    @Autowired
    private CseValidPublicationService cseValidPublicationService;

    @MockBean
    private CseValidClient cseValidClient;
    @MockBean
    private FileImporter fileImporter;
    @MockBean
    private FileExporter fileExporter;
    @MockBean
    private FileUtils fileUtils;
    @MockBean
    private MinioAdapter minioAdapter;
    @MockBean
    private TaskManagerService taskManagerService;

    @Test
    void publishProcessWithErrorDate() {
        String message = String.format("Incorrect format for target date : '%s' is invalid, please use ISO-8601 format", "INCORRECT_DATE");
        assertThrows(CseValidPublicationInvalidDataException.class, () -> cseValidPublicationService.publishProcess("D2CC", "INCORRECT_DATE", 0), message);
    }

    @Test
    void publishProcessTaskManagerError() {
        Mockito.when(taskManagerService.getTasksFromBusinessDate("2022-11-22"))
                        .thenReturn(Optional.empty());

        assertThrows(CseValidPublicationInternalException.class, () -> cseValidPublicationService.publishProcess("D2CC", "2022-11-22", 0));
    }

    @Test
    void publishProcess() {
        Mockito.when(minioAdapter.generatePreSignedUrlFromFullMinioPath("TTC_ADJUSTMENT_FILE_PATH", 1)).thenReturn("TTC_ADJUSTMENT_FILE_URL");
        Mockito.when(minioAdapter.generatePreSignedUrlFromFullMinioPath("CGM_FILE_PATH", 1)).thenReturn("CGM_FILE_URL");
        Mockito.when(minioAdapter.generatePreSignedUrlFromFullMinioPath("GLSK_FILE_PATH", 1)).thenReturn("GLSK_FILE_URL");
        Mockito.when(minioAdapter.generatePreSignedUrlFromFullMinioPath("CRAC_FILE_PATH", 1)).thenReturn("CRAC_FILE_URL");

        ProcessFileDto ttcAdjFile = new ProcessFileDto("TTC_ADJUSTMENT_FILE_PATH", "TTC_ADJUSTMENT", null, "TTC_ADJUSTMENT_FILENAME", "docId1", null);
        ProcessFileDto cgmFile = new ProcessFileDto("CGM_FILE_PATH", "CGM", null, "CGM_FILENAME", "docId2", null);
        ProcessFileDto glskFile = new ProcessFileDto("GLSK_FILE_PATH", "GLSK", null, "GLSK_FILENAME", "docId3", null);
        ProcessFileDto cracFile = new ProcessFileDto("CRAC_FILE_PATH", "IMPORT_CRAC", null, "CRAC_FILENAME", "docId4", null);
        OffsetDateTime offsetDateTime = OffsetDateTime.of(2022, 11, 22, 13, 30, 0, 0, ZoneOffset.UTC);
        List<ProcessFileDto> processFileDtoList = List.of(ttcAdjFile, cgmFile, glskFile, cracFile);
        List<ProcessRunDto> runHistory = new ArrayList<>();
        runHistory.add(new ProcessRunDto(UUID.randomUUID(), OffsetDateTime.now(), Collections.emptyList()));
        final TaskDto taskDto = new TaskDto(UUID.randomUUID(), offsetDateTime, null, processFileDtoList, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), runHistory, Collections.emptyList());
        TaskDto[] taskDtoArray = {taskDto};
        Mockito.when(taskManagerService.getTasksFromBusinessDate("2022-11-22")).thenReturn(Optional.of(taskDtoArray));
        TcDocumentType tcDocumentType = Mockito.mock(TcDocumentType.class);
        Mockito.when(fileImporter.importTtcFile("TTC_ADJUSTMENT_FILE_URL")).thenReturn(tcDocumentType);
        TResultTimeseries resultTimeseries = Mockito.mock(TResultTimeseries.class);
        Mockito.when(tcDocumentType.getAdjustmentResults()).thenReturn(List.of(resultTimeseries));
        Mockito.when(tcDocumentType.getValidationResults()).thenReturn(List.of(resultTimeseries));
        TTimestamp tTimestamp = new TTimestamp();
        Mockito.when(resultTimeseries.getTimestamp()).thenReturn(List.of(tTimestamp));
        TTime tTime = new TTime();
        tTime.setV("2022-11-22T13:30Z");
        tTimestamp.setTime(tTime);
        tTimestamp.setReferenceCalculationTime(tTime);
        TimeIntervalType timeInterval = new TimeIntervalType();
        tTimestamp.setTimeInterval(timeInterval);
        timeInterval.setV("42");
        CseValidFileResource cseValidFileResource = new CseValidFileResource("name", "url");
        Mockito.when(fileUtils.createFileResource(Mockito.any(), Mockito.any())).thenReturn(cseValidFileResource);
        Mockito.when(cseValidClient.run(Mockito.any())).thenReturn(new CseValidResponse("id", "fileUrl", null, null));
        Mockito.doNothing().when(fileExporter).saveTtcValidation(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.when(taskManagerService.addNewRunInTaskHistory(offsetDateTime.toString(), processFileDtoList)).thenReturn(Optional.of(taskDto));

        assertDoesNotThrow(() -> cseValidPublicationService.publishProcess("D2CC", "2022-11-22", 0));

        Mockito.verify(taskManagerService).addNewRunInTaskHistory(offsetDateTime.toString(), processFileDtoList);
    }

    @Test
    void testgGeCurrentRunIdThrowsException() {
        final TaskDto taskDto = new TaskDto(UUID.randomUUID(), OffsetDateTime.now(), TaskStatus.READY, List.of(), null, List.of(), List.of(), List.of(), List.of());
        Assertions.assertThrows(
                CseValidPublicationInternalException.class,
                () -> cseValidPublicationService.getCurrentRunId(taskDto),
                "Failed to handle run request on timestamp because it has no run history");
    }
}

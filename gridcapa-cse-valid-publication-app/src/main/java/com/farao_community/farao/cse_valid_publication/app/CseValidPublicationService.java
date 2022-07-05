/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.configuration.AmqpMessagesConfiguration;
import com.farao_community.farao.cse_valid_publication.app.configuration.CseValidPublicationProperties;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.services.FileExporter;
import com.farao_community.farao.cse_valid_publication.app.services.FileImporter;
import com.farao_community.farao.cse_valid_publication.app.services.TcDocumentTypeWriter;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.TcDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.UUID;

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
    private final CseValidPublicationProperties cseValidPublicationProperties;
    private TcDocumentTypeWriter tcDocumentTypeWriter;

    public CseValidPublicationService(AmqpMessagesConfiguration amqpMessagesConfiguration, AmqpTemplate amqpTemplate, JsonConverter jsonConverter, FileImporter fileImporter, FileExporter fileExporter, CseValidPublicationProperties cseValidPublicationProperties) {
        this.amqpMessagesConfiguration = amqpMessagesConfiguration;
        this.amqpTemplate = amqpTemplate;
        this.jsonConverter = jsonConverter;
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.cseValidPublicationProperties = cseValidPublicationProperties;
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
        TcDocumentType tcDocument = fileImporter.importTtcAdjustment(process, localTargetDate);
        if (tcDocument != null) {
            // todo run asynchronous cse valid request

        } else {
            tcDocumentTypeWriter.fillWithNoTtcAdjustmentError();
        }
        fileExporter.saveTtcValidation(tcDocumentTypeWriter, process, localTargetDate);
        return null; //todo response json
    }

}

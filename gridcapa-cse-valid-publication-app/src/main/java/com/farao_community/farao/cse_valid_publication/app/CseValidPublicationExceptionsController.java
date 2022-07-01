/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.exception.AbstractCseValidPublicationException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@ControllerAdvice
public class CseValidPublicationExceptionsController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationExceptionsController.class);
    private final JsonConverter jsonConverter;

    public CseValidPublicationExceptionsController(JsonConverter jsonConverter) {
        this.jsonConverter = jsonConverter;
    }

    @ExceptionHandler(value = AbstractCseValidPublicationException.class)
    public ResponseEntity<byte[]> internalException(AbstractCseValidPublicationException exception) {
        LOGGER.error("Exception occurred", exception);
        try {
            return ResponseEntity.status(exception.getStatus()).body(jsonConverter.toJsonMessage(exception));
        } catch (AbstractCseValidPublicationException e) {
            LOGGER.error("Cyclic CseValidPublicationException occurred. Cannot generate JSON-API typed response. Return loop detected error.");
            return ResponseEntity.status(508).build();
        }
    }

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<byte[]> anyException(Exception exception) {
        CseValidPublicationInternalException wrappingException = new CseValidPublicationInternalException("Unexpected exception", exception);
        return internalException(wrappingException);
    }
}

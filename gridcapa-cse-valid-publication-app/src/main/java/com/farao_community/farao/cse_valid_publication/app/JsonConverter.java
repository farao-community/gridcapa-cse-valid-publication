/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.exception.AbstractCseValidPublicationException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import com.github.jasminb.jsonapi.SerializationFeature;
import com.github.jasminb.jsonapi.exceptions.DocumentSerializationException;
import com.github.jasminb.jsonapi.models.errors.Error;
import org.springframework.stereotype.Component;

/**
 * JSON API conversion component
 * Allows automatic conversion from resources or exceptions towards JSON API formatted bytes.
 *
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Component
public class JsonConverter {
    private final ObjectMapper objectMapper;

    public JsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] toJsonMessage(ProcessStartRequest processStartRequest) {
        ResourceConverter converter = createConverter();
        JSONAPIDocument<?> jsonapiDocument = new JSONAPIDocument<>(processStartRequest);
        try {
            return converter.writeDocument(jsonapiDocument);
        } catch (DocumentSerializationException e) {
            throw new CseValidPublicationInternalException("Exception occurred during process start request message conversion", e);
        }
    }

    public byte[] toJsonMessage(AbstractCseValidPublicationException exception) {
        ResourceConverter converter = createConverter();
        JSONAPIDocument<?> jsonapiDocument = new JSONAPIDocument<>(convertExceptionToJsonError(exception));
        try {
            return converter.writeDocument(jsonapiDocument);
        } catch (DocumentSerializationException e) {
            throw new CseValidPublicationInternalException("Exception occurred during process start request message conversion", e);
        }
    }

    private ResourceConverter createConverter() {
        ResourceConverter converter = new ResourceConverter(objectMapper, ProcessStartRequest.class);
        converter.disableSerializationOption(SerializationFeature.INCLUDE_META);
        return converter;
    }

    private Error convertExceptionToJsonError(AbstractCseValidPublicationException exception) {
        Error error = new Error();
        error.setStatus(Integer.toString(exception.getStatus()));
        error.setCode(exception.getCode());
        error.setTitle(exception.getTitle());
        error.setDetail(exception.getDetails());
        return error;
    }
}

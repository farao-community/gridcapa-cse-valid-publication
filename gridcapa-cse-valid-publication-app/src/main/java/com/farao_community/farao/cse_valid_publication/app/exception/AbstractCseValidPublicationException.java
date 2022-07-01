/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.exception;

import org.springframework.core.NestedExceptionUtils;

/**
 * Custom abstract exception to be extended by all application exceptions.
 * Any subclass may be automatically wrapped to a JSON API error message if needed
 *
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public abstract class AbstractCseValidPublicationException extends RuntimeException {

    public AbstractCseValidPublicationException(String message) {
        super(message);
    }

    public AbstractCseValidPublicationException(String message, Throwable throwable) {
        super(message, throwable);
    }

    public abstract int getStatus();

    public abstract String getCode();

    public final String getTitle() {
        return getMessage();
    }

    public final String getDetails() {
        return NestedExceptionUtils.buildMessage(getMessage(), getCause());
    }
}

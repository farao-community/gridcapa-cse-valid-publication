/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.exception;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public class CseValidPublicationInvalidDataException extends AbstractCseValidPublicationException {
    private static final int STATUS = 400;
    private static final String CODE = "400-InvalidDataException";

    public CseValidPublicationInvalidDataException(String message) {
        super(message);
    }

    public CseValidPublicationInvalidDataException(String message, Throwable throwable) {
        super(message, throwable);
    }

    @Override
    public int getStatus() {
        return STATUS;
    }

    @Override
    public String getCode() {
        return CODE;
    }
}

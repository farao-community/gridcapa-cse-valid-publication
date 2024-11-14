/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;

public final class ProcessUtils {

    private ProcessUtils() {
        // Utils class: private constructor
    }

    public static String getProcessCode(String process) {
        return switch (process) {
            case "IDCC" -> "ID";
            case "D2CC" -> "2D";
            default -> throw new CseValidPublicationInvalidDataException(String.format("Unknown target process for CSE: %s", process));
        };
    }
}

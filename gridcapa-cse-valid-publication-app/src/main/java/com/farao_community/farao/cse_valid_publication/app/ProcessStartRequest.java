/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Type;

import java.time.LocalDate;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@Type("process-start-request")
public class ProcessStartRequest {
    @Id
    private final String id;
    private final String region;
    private final String process;
    private final LocalDate targetDate;

    public ProcessStartRequest(String id, String region, String process, LocalDate targetDate) {
        this.id = id;
        this.region = region;
        this.process = process;
        this.targetDate = targetDate;
    }

    public String getId() {
        return id;
    }

    public String getRegion() {
        return region;
    }

    public String getProcess() {
        return process;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }
}

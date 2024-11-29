/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid.api.resource.ProcessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Oualid Aloui {@literal <oualid.aloui at rte-france.com>}
 */
@RestController
public class CseValidPublicationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationController.class);
    private final CseValidPublicationService cseValidPublicationService;

    public CseValidPublicationController(CseValidPublicationService processPublicationService) {
        this.cseValidPublicationService = processPublicationService;
    }

    @PostMapping(value = "/publish")
    public ResponseEntity<Void> publishProcess(@RequestParam ProcessType processType, @RequestParam String targetDate, @RequestParam(required = false, defaultValue = "0") int targetDateOffset) {
        final String formattedTargetDate = targetDate.replaceAll("[\n\r\t]", "_");
        final String formattedProcess = processType.name().replaceAll("[\n\r\t]", "_");
        LOGGER.info("Process publication request received with following attributes: process={} targetDate={}", formattedProcess, formattedTargetDate);

        try {
            cseValidPublicationService.publishProcess(processType, targetDate, targetDateOffset);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            LOGGER.error("Failed to run computation for process {} and target date {} with error {}", formattedProcess, formattedTargetDate, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@RestController
public class CseValidPublicationController {
    private static final Logger LOGGER = LoggerFactory.getLogger(CseValidPublicationController.class);
    private final CseValidPublicationService cseValidPublicationService;
    private final JsonConverter jsonConverter;

    public CseValidPublicationController(CseValidPublicationService processPublicationService, JsonConverter jsonConverter) {
        this.cseValidPublicationService = processPublicationService;
        this.jsonConverter = jsonConverter;
    }

    @PostMapping(value = "/publish")
    public ResponseEntity<byte[]> publishProcess(@RequestParam(required = false) String id, @RequestParam String region, @RequestParam String process, @RequestParam String targetDate, @RequestParam(required = false, defaultValue = "0") int targetDateOffset) {
        LOGGER.info("Process publication request received with following attributes: id={} region={} process={} targetDate={}", id, region, process, targetDate);
        ProcessStartRequest request = cseValidPublicationService.publishProcess(id, region, process, targetDate, targetDateOffset);
        return ResponseEntity.ok().body(jsonConverter.toJsonMessage(request));
    }
}

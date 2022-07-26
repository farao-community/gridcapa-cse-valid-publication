/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid_publication.app.exception.AbstractCseValidPublicationException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
@SpringBootTest
@AutoConfigureMockMvc
class CseValidPublicationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CseValidPublicationService cseValidPublicationService;

    @Test
    void checkCorrectResponseWhenPublicationServiceSucceeds() throws Exception {
        Mockito.when(cseValidPublicationService.publishProcess("id", "D2CC", "2020-11-24", 0)).thenReturn(true);

        mockMvc.perform(post("/publish")
                        .param("id", "id")
                        .param("process", "D2CC")
                        .param("targetDate", "2020-11-24"))
                .andExpect(status().isOk());
    }

    @Test
    void checkCorrectResponseWhenPublicationServiceFailedWithD2ccOffset() throws Exception {
        Mockito.when(cseValidPublicationService.publishProcess("id", "D2CC", "2020-11-24", 2)).thenReturn(false);

        mockMvc.perform(post("/publish")
                        .param("id", "id")
                        .param("region", "CSE")
                        .param("process", "D2CC")
                        .param("targetDate", "2020-11-24")
                        .param("targetDateOffset", "2"))
                .andExpect(status().isNotFound());
    }

    @Test
    void checkErrorWhenPublicationServiceFailsWithException() throws Exception {
        AbstractCseValidPublicationException exception = new CseValidPublicationInternalException("Something really bad happened");
        Mockito.when(cseValidPublicationService.publishProcess("id", "D2CC", "2020-11-24", 0)).thenThrow(exception);

        mockMvc.perform(post("/publish")
                        .param("id", "id")
                        .param("process", "D2CC")
                        .param("targetDate", "2020-11-24"))
                .andExpect(status().isInternalServerError());
    }
}

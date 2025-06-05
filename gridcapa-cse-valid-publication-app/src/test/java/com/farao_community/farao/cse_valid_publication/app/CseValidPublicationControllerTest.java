/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app;

import com.farao_community.farao.cse_valid.api.resource.ProcessType;
import com.farao_community.farao.cse_valid_publication.app.exception.AbstractCseValidPublicationException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 * @author Oualid Aloui {@literal <oualid.aloui at rte-france.com>}
 */
@SpringBootTest
@AutoConfigureMockMvc
class CseValidPublicationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CseValidPublicationService cseValidPublicationService;

    @Test
    void checkCorrectResponseWhenPublicationServiceSucceeds() throws Exception {
        Mockito.doNothing().when(cseValidPublicationService).publishProcess(ProcessType.IDCC, "2020-11-24", 0);

        mockMvc.perform(post("/publish")
                        .param("processType", "IDCC")
                        .param("targetDate", "2020-11-24"))
                .andExpect(status().isOk());

        Mockito.verify(cseValidPublicationService, Mockito.times(1)).publishProcess(ProcessType.IDCC, "2020-11-24", 0);
    }

    @Test
    void checkErrorWhenPublicationServiceFailsWithException() throws Exception {
        AbstractCseValidPublicationException exception = new CseValidPublicationInternalException("Something really bad happened");
        Mockito.doThrow(exception).when(cseValidPublicationService).publishProcess(ProcessType.D2CC, "2020-11-24", 2);

        mockMvc.perform(post("/publish")
                        .param("processType", "D2CC")
                        .param("targetDate", "2020-11-24")
                        .param("targetDateOffset", "2"))
                .andExpect(status().isInternalServerError());
    }
}

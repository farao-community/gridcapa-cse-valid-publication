/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.configuration.UrlConfiguration;
import com.farao_community.farao.cse_valid_publication.app.exception.RetryException;
import com.farao_community.farao.cse_valid_publication.app.util.LoggingUtil;
import com.farao_community.farao.gridcapa.task_manager.api.ProcessFileDto;
import com.farao_community.farao.gridcapa.task_manager.api.TaskDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * @author Vincent Bochet {@literal <vincent.bochet at rte-france.com>}
 */
@Service
public class TaskManagerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManagerService.class);
    private static final String EXCEPTION_OCCURRED_DURING_REQUEST_TO_TASK_MANAGER = "Exception occurred during request to task-manager";
    private static final String REQUESTING_URL_ATTEMPT = "Requesting URL: {} (#{} attempt)";

    private final UrlConfiguration urlConfiguration;
    private final RestTemplate restTemplate;

    public TaskManagerService(UrlConfiguration urlConfiguration, RestTemplate restTemplate) {
        this.urlConfiguration = urlConfiguration;
        this.restTemplate = restTemplate;
    }

    @Retryable(retryFor = RetryException.class,
            backoff = @Backoff(delayExpression = "${retry.backoff.delay}", multiplierExpression = "${retry.backoff.multiplier}"),
            maxAttemptsExpression = "${retry.max-attempts}",
            recover = "fallbackGetTasksFromBusinessDate")
    public Optional<TaskDto[]> getTasksFromBusinessDate(final String startingDate) {
        try {
            final int retryCount = getRetryCount();
            final String requestUrl = getTaskManagerBusinessDateUrl(startingDate);
            LOGGER.info(REQUESTING_URL_ATTEMPT, requestUrl, retryCount);
            final ResponseEntity<TaskDto[]> responseEntity = restTemplate.getForEntity(requestUrl, TaskDto[].class);
            return getOptionalFromResponseEntity(responseEntity);
        } catch (RestClientException e) {
            throw new RetryException(EXCEPTION_OCCURRED_DURING_REQUEST_TO_TASK_MANAGER, e);
        }
    }

    @Recover
    public Optional<TaskDto[]> fallbackGetTasksFromBusinessDate(final Exception e, final String startingDate) {
        LOGGER.error("Problem occurred while querying task-manager for business date {}", startingDate, e);
        return Optional.empty();
    }

    @Retryable(retryFor = RetryException.class,
            backoff = @Backoff(delayExpression = "${retry.backoff.delay}", multiplierExpression = "${retry.backoff.multiplier}"),
            maxAttemptsExpression = "${retry.max-attempts}",
            recover = "fallbackAddNewRunInTaskHistory")
    public Optional<TaskDto> addNewRunInTaskHistory(final String timestamp, final List<ProcessFileDto> inputs) {
        try {
            final int retryCount = getRetryCount();
            final HttpEntity<List<ProcessFileDto>> requestEntity = new HttpEntity<>(inputs);
            final String requestUrl = getTaskManagerTimestampUrl(timestamp) + "/runHistory";
            final String sanifiedUrl = LoggingUtil.sanifyString(requestUrl);
            LOGGER.info("Requesting URL: {} with parameters: {} (#{} attempt)", sanifiedUrl, inputs, retryCount);
            final ResponseEntity<TaskDto> responseEntity = restTemplate.exchange(requestUrl, HttpMethod.PUT, requestEntity, TaskDto.class);
            return getOptionalFromResponseEntity(responseEntity);
        } catch (RestClientException e) {
            throw new RetryException(EXCEPTION_OCCURRED_DURING_REQUEST_TO_TASK_MANAGER, e);
        }
    }

    @Recover
    public Optional<TaskDto> fallbackAddNewRunInTaskHistory(final Exception e, final String timestamp, final List<ProcessFileDto> inputs) {
        LOGGER.error("Problem occurred while requesting task-manager to add a new run for timestamp {}", timestamp, e);
        return Optional.empty();
    }

    private static int getRetryCount() {
        final RetryContext retryContext = RetrySynchronizationManager.getContext();
        return retryContext != null ? retryContext.getRetryCount() : -1;
    }

    private static <T> Optional<T> getOptionalFromResponseEntity(final ResponseEntity<T> responseEntity) {
        if (responseEntity != null
                && responseEntity.getBody() != null
                && responseEntity.getStatusCode() == HttpStatus.OK) {
            return Optional.of(responseEntity.getBody());
        } else if (responseEntity != null
                && responseEntity.getBody() != null
                && responseEntity.getStatusCode().is2xxSuccessful()) {
            return Optional.empty();
        } else {
            throw new RetryException("Unexpected response from the task-manager");
        }
    }

    private String getTaskManagerTimestampUrl(final String timestamp) {
        return urlConfiguration.taskManagerTimestampUrl() + timestamp;
    }

    private String getTaskManagerBusinessDateUrl(final String startingDate) {
        return urlConfiguration.taskManagerBusinessDateUrl() + startingDate;
    }
}

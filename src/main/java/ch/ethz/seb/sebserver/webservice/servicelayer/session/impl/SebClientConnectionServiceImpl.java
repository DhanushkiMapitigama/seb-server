/*
 * Copyright (c) 2019 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.session.impl;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import ch.ethz.seb.sebserver.gbl.model.exam.Exam.ExamType;
import ch.ethz.seb.sebserver.gbl.model.session.ClientConnection;
import ch.ethz.seb.sebserver.gbl.model.session.ClientConnection.ConnectionStatus;
import ch.ethz.seb.sebserver.gbl.model.session.ClientEvent;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.ClientConnectionDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.session.EventHandlingStrategy;
import ch.ethz.seb.sebserver.webservice.servicelayer.session.ExamSessionService;
import ch.ethz.seb.sebserver.webservice.servicelayer.session.SebClientConnectionService;
import io.micrometer.core.instrument.util.StringUtils;

@Lazy
@Service
@WebServiceProfile
public class SebClientConnectionServiceImpl implements SebClientConnectionService {

    private static final Logger log = LoggerFactory.getLogger(SebClientConnectionServiceImpl.class);

    private final ExamSessionService examSessionService;
    private final ExamSessionCacheService examSessionCacheService;
    private final EventHandlingStrategy eventHandlingStrategy;
    private final ClientConnectionDAO clientConnectionDAO;

    protected SebClientConnectionServiceImpl(
            final ExamSessionService examSessionService,
            final ExamSessionCacheService examSessionCacheService,
            final ClientConnectionDAO clientConnectionDAO,
            final Environment environment,
            final ApplicationContext applicationContext) {

        this.examSessionService = examSessionService;
        this.examSessionCacheService = examSessionCacheService;
        this.clientConnectionDAO = clientConnectionDAO;

        String eventHandlingStrategyProperty =
                environment.getProperty(EventHandlingStrategy.EVENT_CONSUMER_STRATEGY_CONFIG_PROPERTY_KEY);

        if (StringUtils.isBlank(eventHandlingStrategyProperty)) {
            eventHandlingStrategyProperty = EventHandlingStrategy.EVENT_CONSUMER_STRATEGY_SINGLE_EVENT_STORE;
        }

        this.eventHandlingStrategy = applicationContext.getBean(
                eventHandlingStrategyProperty,
                EventHandlingStrategy.class);
    }

    @Override
    public Result<ClientConnection> createClientConnection(
            final Long institutionId,
            final String clientAddress,
            final Long examId) {

        return Result.tryCatch(() -> {

            if (log.isDebugEnabled()) {
                log.debug("SEB client connection attempt, create ClientConnection for "
                        + "instituion {} "
                        + "exam: {} "
                        + "client address: {}",
                        institutionId,
                        examId,
                        clientAddress);
            }

            checkExamRunning(examId);

            // Create ClientConnection in status CONNECTION_REQUESTED for further processing
            final String connectionToken = createToken();
            final ClientConnection clientConnection = this.clientConnectionDAO.createNew(new ClientConnection(
                    null,
                    institutionId,
                    examId,
                    ClientConnection.ConnectionStatus.CONNECTION_REQUESTED,
                    connectionToken,
                    null,
                    clientAddress,
                    null))
                    .getOrThrow();

            if (log.isDebugEnabled()) {
                log.debug("New ClientConnection created: {}", clientConnection);
            }

            return clientConnection;
        });
    }

    @Override
    public Result<ClientConnection> updateClientConnection(
            final String connectionToken,
            final Long institutionId,
            final String clientAddress,
            final Long examId,
            final String userSessionId) {

        return Result.tryCatch(() -> {
            if (log.isDebugEnabled()) {
                log.debug(
                        "SEB client connection, update ClientConnection for "
                                + "connectionToken {} "
                                + "institutionId"
                                + "exam: {} "
                                + "client address: {} "
                                + "userSessionId: {}",
                        connectionToken,
                        institutionId,
                        examId,
                        clientAddress,
                        userSessionId);
            }

            checkExamRunning(examId);

            final ClientConnection clientConnection = getClientConnection(
                    connectionToken,
                    institutionId);

            checkInstitutionalIntegrity(
                    institutionId,
                    clientConnection);

            final String virtualClientAddress = getVirtualClientAddress(
                    (examId != null) ? examId : clientConnection.examId,
                    clientAddress,
                    clientConnection.clientAddress);

            final ClientConnection updatedClientConnection = this.clientConnectionDAO
                    .save(new ClientConnection(
                            clientConnection.id,
                            null,
                            examId,
                            null,
                            null,
                            userSessionId,
                            null,
                            virtualClientAddress))
                    .getOrThrow();

            if (log.isDebugEnabled()) {
                log.debug("SEB client connection, successfully updated ClientConnection: {}",
                        updatedClientConnection);
            }

            return updatedClientConnection;
        });
    }

    @Override
    public Result<ClientConnection> establishClientConnection(
            final String connectionToken,
            final Long institutionId,
            final Long examId,
            final String clientAddress,
            final String userSessionId) {

        return Result.tryCatch(() -> {

            if (log.isDebugEnabled()) {
                log.debug(
                        "SEB client connection, establish ClientConnection for "
                                + "connectionToken {} "
                                + "institutionId"
                                + "exam: {} "
                                + "client address: {} "
                                + "userSessionId: {}",
                        connectionToken,
                        institutionId,
                        examId,
                        clientAddress,
                        userSessionId);
            }

            checkExamRunning(examId);

            final ClientConnection clientConnection = getClientConnection(
                    connectionToken,
                    institutionId);

            checkInstitutionalIntegrity(
                    institutionId,
                    clientConnection);

            // Exam integrity
            if (clientConnection.examId != null && !examId.equals(clientConnection.examId)) {
                log.error("Exam integrity violation with examId: {} on clientConnection: {}",
                        examId,
                        clientConnection);
                throw new IllegalAccessError("Exam integrity violation");
            }

            final String virtualClientAddress = getVirtualClientAddress(
                    (examId != null) ? examId : clientConnection.examId,
                    clientAddress,
                    clientConnection.clientAddress);

            final ClientConnection establishedClientConnection = new ClientConnection(
                    clientConnection.id,
                    null,
                    examId,
                    ClientConnection.ConnectionStatus.ESTABLISHED,
                    null,
                    userSessionId,
                    null,
                    virtualClientAddress);

            // ClientConnection integrity
            if (establishedClientConnection.institutionId == null ||
                    establishedClientConnection.examId == null ||
                    establishedClientConnection.clientAddress == null ||
                    establishedClientConnection.connectionToken == null) {

                log.error("ClientConnection integrity violation: {}", establishedClientConnection);
                throw new IllegalStateException("ClientConnection integrity violation: " + establishedClientConnection);
            }

            final ClientConnection updatedClientConnection = this.clientConnectionDAO
                    .save(establishedClientConnection)
                    .getOrThrow();

            if (updatedClientConnection.status == ConnectionStatus.ESTABLISHED) {
                // load into cache...
                final ClientConnectionDataInternal activeClientConnection = this.examSessionCacheService
                        .getActiveClientConnection(updatedClientConnection.connectionToken);

                if (activeClientConnection == null) {
                    log.warn("Unable to access and cache ClientConnection");
                }

                if (log.isDebugEnabled()) {
                    log.debug("ClientConnection: {} successfully established", clientConnection);
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("ClientConnection: {} updated", clientConnection);
                }
            }

            return updatedClientConnection;
        });
    }

    @Override
    public Result<ClientConnection> closeConnection(
            final String connectionToken,
            final Long institutionId,
            final String clientAddress) {

        return Result.tryCatch(() -> {

            if (log.isDebugEnabled()) {
                log.debug("SEB client connection: regular close attempt for "
                        + "instituion {} "
                        + "client address: {} "
                        + "connectionToken {} ",
                        institutionId,
                        clientAddress,
                        connectionToken);
            }

            final ClientConnection clientConnection = this.clientConnectionDAO
                    .byConnectionToken(institutionId, connectionToken)
                    .getOrThrow();

            // evict ClientConnection from cache
            this.examSessionCacheService
                    .evictClientConnection(clientConnection.connectionToken);

            final ClientConnection updatedClientConnection = this.clientConnectionDAO.save(new ClientConnection(
                    clientConnection.id,
                    null,
                    null,
                    ClientConnection.ConnectionStatus.CLOSED,
                    null,
                    null,
                    null,
                    null)).getOrThrow();

            if (log.isDebugEnabled()) {
                log.debug("SEB client connection: successfully closed ClientConnection: {}",
                        clientConnection);
            }

            return updatedClientConnection;
        });

    }

    @Override
    public void notifyPing(
            final String connectionToken,
            final long timestamp,
            final int pingNumber) {

        final ClientConnectionDataInternal activeClientConnection =
                this.examSessionCacheService.getActiveClientConnection(connectionToken);

        if (activeClientConnection != null) {
            activeClientConnection.pingMappings
                    .stream()
                    .forEach(pingIndicator -> pingIndicator.notifyPing(timestamp, pingNumber));
        }
    }

    @Override
    public void notifyClientEvent(
            final String connectionToken,
            final ClientEvent event) {

        this.eventHandlingStrategy.accept(event);

        final ClientConnectionDataInternal activeClientConnection =
                this.examSessionCacheService.getActiveClientConnection(connectionToken);

        if (activeClientConnection != null) {
            activeClientConnection.getindicatorMapping(event.eventType)
                    .stream()
                    .forEach(indicator -> indicator.notifyValueChange(event));
        }
    }

    private void checkExamRunning(final Long examId) {
        if (examId != null && !this.examSessionService.isExamRunning(examId)) {
            examNotRunningException(examId);
        }
    }

    private ClientConnection getClientConnection(final String connectionToken, final Long institutionId) {
        final ClientConnection clientConnection = this.clientConnectionDAO
                .byConnectionToken(institutionId, connectionToken)
                .getOrThrow();
        return clientConnection;
    }

    private void checkInstitutionalIntegrity(final Long institutionId, final ClientConnection clientConnection)
            throws IllegalAccessError {
        // Institutional integrity
        if (!institutionId.equals(clientConnection.institutionId)) {
            log.error("Instituion integrity violation with institution: {} on clientConnection: {}",
                    institutionId,
                    clientConnection);
            throw new IllegalAccessError("Instituion integrity violation");
        }
    }

    // TODO maybe we need a stronger connectionToken but for now a simple UUID is used
    private String createToken() {
        return UUID.randomUUID().toString();
    }

    private void examNotRunningException(final Long examId) {
        log.error("The exam {} is not running", examId);
        throw new IllegalStateException("The exam " + examId + " is not running");
    }

    private String getVirtualClientAddress(
            final Long examId,
            final String requestClientAddress,
            final String existingClientAddress) {

        if (examId == null) {
            return null;
        }

        if (requestClientAddress.equals(existingClientAddress)) {
            return null;
        }

        if (!isVDI(examId)) {
            return null;
        }

        return requestClientAddress;
    }

    private boolean isVDI(final Long examId) {
        return this.examSessionService.getRunningExam(examId)
                .getOrThrow()
                .getType() == ExamType.VDI;
    }

}
/*
 * Copyright (c) 2020 ETH Zürich, Educational Development and Technology (LET)
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package ch.ethz.seb.sebserver.webservice.servicelayer.exam.impl;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;

import ch.ethz.seb.sebserver.gbl.Constants;
import ch.ethz.seb.sebserver.gbl.api.EntityType;
import ch.ethz.seb.sebserver.gbl.api.JSONMapper;
import ch.ethz.seb.sebserver.gbl.model.exam.Exam;
import ch.ethz.seb.sebserver.gbl.model.exam.ProctoringSettings;
import ch.ethz.seb.sebserver.gbl.model.exam.ProctoringSettings.ServerType;
import ch.ethz.seb.sebserver.gbl.model.exam.Indicator;
import ch.ethz.seb.sebserver.gbl.model.exam.Indicator.IndicatorType;
import ch.ethz.seb.sebserver.gbl.model.exam.OpenEdxSEBRestriction;
import ch.ethz.seb.sebserver.gbl.model.institution.LmsSetup;
import ch.ethz.seb.sebserver.gbl.model.institution.LmsSetup.LmsType;
import ch.ethz.seb.sebserver.gbl.profile.WebServiceProfile;
import ch.ethz.seb.sebserver.gbl.util.Cryptor;
import ch.ethz.seb.sebserver.gbl.util.Result;
import ch.ethz.seb.sebserver.webservice.datalayer.batis.model.AdditionalAttributeRecord;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.AdditionalAttributesDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.ExamDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.dao.IndicatorDAO;
import ch.ethz.seb.sebserver.webservice.servicelayer.exam.ExamAdminService;
import ch.ethz.seb.sebserver.webservice.servicelayer.exam.ExamProctoringService;
import ch.ethz.seb.sebserver.webservice.servicelayer.lms.LmsAPIService;
import ch.ethz.seb.sebserver.webservice.servicelayer.lms.SEBRestrictionService;

@Lazy
@Service
@WebServiceProfile
public class ExamAdminServiceImpl implements ExamAdminService {

    private final ExamDAO examDAO;
    private final IndicatorDAO indicatorDAO;
    private final AdditionalAttributesDAO additionalAttributesDAO;
    private final LmsAPIService lmsAPIService;
    private final JSONMapper jsonMapper;
    private final Cryptor cryptor;
    private final ExamProctoringServiceFactory examProctoringServiceFactory;

    private final String defaultIndicatorName;
    private final String defaultIndicatorType;
    private final String defaultIndicatorColor;
    private final String defaultIndicatorThresholds;

    protected ExamAdminServiceImpl(
            final ExamDAO examDAO,
            final IndicatorDAO indicatorDAO,
            final AdditionalAttributesDAO additionalAttributesDAO,
            final LmsAPIService lmsAPIService,
            final JSONMapper jsonMapper,
            final Cryptor cryptor,
            final ExamProctoringServiceFactory examProctoringServiceFactory,
            @Value("${sebserver.webservice.api.exam.indicator.name:Ping}") final String defaultIndicatorName,
            @Value("${sebserver.webservice.api.exam.indicator.type:LAST_PING}") final String defaultIndicatorType,
            @Value("${sebserver.webservice.api.exam.indicator.color:b4b4b4}") final String defaultIndicatorColor,
            @Value("${sebserver.webservice.api.exam.indicator.thresholds:[{\"value\":2000.0,\"color\":\"22b14c\"},{\"value\":5000.0,\"color\":\"ff7e00\"},{\"value\":10000.0,\"color\":\"ed1c24\"}]}") final String defaultIndicatorThresholds) {

        this.examDAO = examDAO;
        this.indicatorDAO = indicatorDAO;
        this.additionalAttributesDAO = additionalAttributesDAO;
        this.lmsAPIService = lmsAPIService;
        this.jsonMapper = jsonMapper;
        this.cryptor = cryptor;
        this.examProctoringServiceFactory = examProctoringServiceFactory;

        this.defaultIndicatorName = defaultIndicatorName;
        this.defaultIndicatorType = defaultIndicatorType;
        this.defaultIndicatorColor = defaultIndicatorColor;
        this.defaultIndicatorThresholds = defaultIndicatorThresholds;
    }

    @Override
    public Result<Exam> addDefaultIndicator(final Exam exam) {
        return Result.tryCatch(() -> {

            final Collection<Indicator.Threshold> thresholds = this.jsonMapper.readValue(
                    this.defaultIndicatorThresholds,
                    new TypeReference<Collection<Indicator.Threshold>>() {
                    });

            final Indicator indicator = new Indicator(
                    null,
                    exam.id,
                    this.defaultIndicatorName,
                    IndicatorType.valueOf(this.defaultIndicatorType),
                    this.defaultIndicatorColor,
                    thresholds);

            this.indicatorDAO.createNew(indicator)
                    .getOrThrow();

            return this.examDAO
                    .byPK(exam.id)
                    .getOrThrow();
        });
    }

    @Override
    public Result<Exam> applyAdditionalSEBRestrictions(final Exam exam) {
        return Result.tryCatch(() -> {
            final LmsSetup lmsSetup = this.lmsAPIService.getLmsSetup(exam.lmsSetupId)
                    .getOrThrow();

            if (lmsSetup.lmsType == LmsType.OPEN_EDX) {
                final List<String> permissions = Arrays.asList(
                        OpenEdxSEBRestriction.PermissionComponent.ALWAYS_ALLOW_STAFF.key,
                        OpenEdxSEBRestriction.PermissionComponent.CHECK_CONFIG_KEY.key);

                this.additionalAttributesDAO.saveAdditionalAttribute(
                        EntityType.EXAM,
                        exam.id,
                        SEBRestrictionService.SEB_RESTRICTION_ADDITIONAL_PROPERTY_NAME_PREFIX +
                                OpenEdxSEBRestriction.ATTR_PERMISSION_COMPONENTS,
                        StringUtils.join(permissions, Constants.LIST_SEPARATOR_CHAR))
                        .getOrThrow();
            }

            return this.examDAO
                    .byPK(exam.id)
                    .getOrThrow();
        });
    }

    @Override
    public Result<Boolean> isRestricted(final Exam exam) {
        if (exam == null) {
            return Result.of(false);
        }

        return this.lmsAPIService
                .getLmsAPITemplate(exam.lmsSetupId)
                .map(lmsAPI -> !lmsAPI.getSEBClientRestriction(exam).hasError());
    }

    @Override
    public Result<ProctoringSettings> getExamProctoring(final Long examId) {
        return this.additionalAttributesDAO.getAdditionalAttributes(EntityType.EXAM, examId)
                .map(attrs -> attrs.stream()
                        .collect(Collectors.toMap(
                                attr -> attr.getName(),
                                Function.identity())))
                .map(mapping -> {
                    return new ProctoringSettings(
                            examId,
                            getEnabled(mapping),
                            getServerType(mapping),
                            getString(mapping, ProctoringSettings.ATTR_SERVER_URL),
                            getString(mapping, ProctoringSettings.ATTR_APP_KEY),
                            getString(mapping, ProctoringSettings.ATTR_APP_SECRET));
                });
    }

    @Override
    @Transactional
    public Result<ProctoringSettings> saveExamProctoring(final Long examId, final ProctoringSettings examProctoring) {
        return Result.tryCatch(() -> {

            this.additionalAttributesDAO.saveAdditionalAttribute(
                    EntityType.EXAM,
                    examId,
                    ProctoringSettings.ATTR_ENABLE_PROCTORING,
                    String.valueOf(examProctoring.enableProctoring));

            this.additionalAttributesDAO.saveAdditionalAttribute(
                    EntityType.EXAM,
                    examId,
                    ProctoringSettings.ATTR_SERVER_TYPE,
                    examProctoring.serverType.name());

            this.additionalAttributesDAO.saveAdditionalAttribute(
                    EntityType.EXAM,
                    examId,
                    ProctoringSettings.ATTR_SERVER_URL,
                    examProctoring.serverURL);

            this.additionalAttributesDAO.saveAdditionalAttribute(
                    EntityType.EXAM,
                    examId,
                    ProctoringSettings.ATTR_APP_KEY,
                    examProctoring.appKey);

            this.additionalAttributesDAO.saveAdditionalAttribute(
                    EntityType.EXAM,
                    examId,
                    ProctoringSettings.ATTR_APP_SECRET,
                    this.cryptor.encrypt(examProctoring.appSecret).toString());

            return examProctoring;
        });
    }

    @Override
    public Result<Boolean> isExamProctoringEnabled(final Long examId) {
        return this.additionalAttributesDAO.getAdditionalAttribute(
                EntityType.EXAM,
                examId,
                ProctoringSettings.ATTR_ENABLE_PROCTORING)
                .map(rec -> rec != null && BooleanUtils.toBoolean(rec.getValue()));
    }

    @Override
    public Result<ExamProctoringService> getExamProctoringService(final ServerType type) {
        return this.examProctoringServiceFactory.getExamProctoringService(type);
    }

    private Boolean getEnabled(final Map<String, AdditionalAttributeRecord> mapping) {
        if (mapping.containsKey(ProctoringSettings.ATTR_ENABLE_PROCTORING)) {
            return BooleanUtils.toBoolean(mapping.get(ProctoringSettings.ATTR_ENABLE_PROCTORING).getValue());
        } else {
            return false;
        }
    }

    private ServerType getServerType(final Map<String, AdditionalAttributeRecord> mapping) {
        if (mapping.containsKey(ProctoringSettings.ATTR_SERVER_TYPE)) {
            return ServerType.valueOf(mapping.get(ProctoringSettings.ATTR_SERVER_TYPE).getValue());
        } else {
            return ServerType.JITSI_MEET;
        }
    }

    private String getString(final Map<String, AdditionalAttributeRecord> mapping, final String name) {
        if (mapping.containsKey(name)) {
            return mapping.get(name).getValue();
        } else {
            return null;
        }
    }

}

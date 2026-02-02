/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.xsd.AreaType;
import com.farao_community.farao.cse_valid_publication.app.xsd.BusinessType;
import com.farao_community.farao.cse_valid_publication.app.xsd.EnergyProductType;
import com.farao_community.farao.cse_valid_publication.app.xsd.IdentificationType;
import com.farao_community.farao.cse_valid_publication.app.xsd.LongIdentificationType;
import com.farao_community.farao.cse_valid_publication.app.xsd.MessageDateTimeType;
import com.farao_community.farao.cse_valid_publication.app.xsd.MessageType;
import com.farao_community.farao.cse_valid_publication.app.xsd.PartyType;
import com.farao_community.farao.cse_valid_publication.app.xsd.ProcessType;
import com.farao_community.farao.cse_valid_publication.app.xsd.RoleType;
import com.farao_community.farao.cse_valid_publication.app.xsd.TNumber;
import com.farao_community.farao.cse_valid_publication.app.xsd.TResultTimeseries;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTime;
import com.farao_community.farao.cse_valid_publication.app.xsd.TTimestamp;
import com.farao_community.farao.cse_valid_publication.app.xsd.TcDocumentType;
import com.farao_community.farao.cse_valid_publication.app.xsd.TextType;
import com.farao_community.farao.cse_valid_publication.app.xsd.TimeIntervalType;
import com.farao_community.farao.cse_valid_publication.app.xsd.UnitOfMeasureType;
import com.farao_community.farao.cse_valid_publication.app.xsd.VersionType;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;

import static com.farao_community.farao.cse_valid_publication.app.services.Constants.DOMAIN;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.EUROPE_BRUSSELS_ZONE_ID;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.IN_AREA;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.OUT_AREA;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.PRODUCT;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.RECEIVER_IDENTIFICATION;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.SENDER_IDENTIFICATION;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.STATUS_ERROR_MESSAGE;
import static com.farao_community.farao.cse_valid_publication.app.services.Constants.TIMESERIES_IDENTIFICATION_PATTERN;
import static com.farao_community.farao.cse_valid_publication.app.xsd.BusinessTypeList.A_81;
import static com.farao_community.farao.cse_valid_publication.app.xsd.CodingSchemeType.A_01;
import static com.farao_community.farao.cse_valid_publication.app.xsd.MessageTypeList.A_32;
import static com.farao_community.farao.cse_valid_publication.app.xsd.ProcessTypeList.A_15;
import static com.farao_community.farao.cse_valid_publication.app.xsd.RoleTypeList.A_04;
import static com.farao_community.farao.cse_valid_publication.app.xsd.UnitOfMeasureTypeList.MAW;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Comparator.comparing;
import static java.util.Locale.FRANCE;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
public class TcDocumentTypeWriter {
    private static final String V1 = "1";
    private static final DateTimeFormatter UTC_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'").withZone(ZoneOffset.UTC);
    private final TcDocumentType tcDocumentType;
    private final LongIdentificationType documentIdentification;
    private final VersionType versionType;
    private final MessageType messageTypedocumentType;
    private final ProcessType processType;
    private final PartyType senderIdentificationPartyType;
    private final PartyType receiverIdentificationPartyType;
    private final RoleType senderRoleRoleType;
    private final RoleType receiverRoleRoleType;
    private final MessageDateTimeType creationTime;
    private final AreaType domainAreaType;
    private final TimeIntervalType timeIntervalType;
    private final String processCode;
    private final LocalDate localTargetDate;

    public TcDocumentTypeWriter(final String processCode, final LocalDate localDate) {
        this.tcDocumentType = new TcDocumentType();
        this.processCode = processCode;
        this.localTargetDate = localDate;
        this.documentIdentification = new LongIdentificationType();
        this.versionType = new VersionType();
        this.messageTypedocumentType = new MessageType();
        this.processType = new ProcessType();
        this.senderIdentificationPartyType = new PartyType();
        this.receiverIdentificationPartyType = new PartyType();
        this.senderRoleRoleType = new RoleType();
        this.receiverRoleRoleType = new RoleType();
        this.creationTime = new MessageDateTimeType();
        this.domainAreaType = new AreaType();
        this.timeIntervalType = new TimeIntervalType();
        fillHeaders();
        fillValidationResults();
    }

    private static OffsetDateTime toBrusselsStartOfDay(final LocalDate date) {
        return date.atStartOfDay().atZone(EUROPE_BRUSSELS_ZONE_ID).toOffsetDateTime();
    }

    public synchronized void fillWithNoTtcAdjustmentError() {
        final List<TTimestamp> timestamps = tcDocumentType.getValidationResults().getFirst().getTimestamp();

        OffsetDateTime startDateTimeBrussels = toBrusselsStartOfDay(localTargetDate).withMinute(30);
        OffsetDateTime endDateTimeBrussels;

        if (processCode.equals("ID")) {
            startDateTimeBrussels = startDateTimeBrussels.plusHours(12);
            endDateTimeBrussels = startDateTimeBrussels.plusHours(12);
        } else {
            endDateTimeBrussels = startDateTimeBrussels.plusHours(24);
        }

        while (startDateTimeBrussels.isBefore(endDateTimeBrussels)) {
            final TTimestamp ts = initializeTimestampResult(startDateTimeBrussels);

            final TextType textTypeRedFlagReason = new TextType();
            textTypeRedFlagReason.setV(STATUS_ERROR_MESSAGE);

            final TNumber statusNumber = new TNumber();
            statusNumber.setV(ZERO);

            ts.setSTATUS(statusNumber);
            ts.setRedFlagReason(textTypeRedFlagReason);

            timestamps.add(ts);

            startDateTimeBrussels = startDateTimeBrussels.plusHours(1);
        }

    }

    public synchronized void fillWithError(final TTimestamp timestampData, final String errorMessage) {
        final List<TTimestamp> listTimestamps = tcDocumentType.getValidationResults().getFirst().getTimestamp();

        final TTimestamp ts = initializeTimestampResult(timestampData);

        final TNumber status = new TNumber();
        // calculation failure
        status.setV(ONE);
        ts.setSTATUS(status);

        final TextType redFlagReason = new TextType();
        redFlagReason.setV(errorMessage);
        ts.setRedFlagReason(redFlagReason);

        listTimestamps.add(ts);

        listTimestamps.sort(comparingOffsetDateTime());

    }

    public synchronized void fillWithTimestampResult(final TTimestamp timestampResult) {
        List<TTimestamp> timestamps = tcDocumentType.getValidationResults().getFirst().getTimestamp();
        timestamps.add(timestampResult);
        timestamps.sort(comparingOffsetDateTime());
    }

    private Comparator<TTimestamp> comparingOffsetDateTime() {
        return comparing(tst -> OffsetDateTime.parse(tst.getTime().getV()));
    }

    private void fillHeaders() {
        initializeHeadersData();
        tcDocumentType.setDocumentIdentification(documentIdentification);
        tcDocumentType.setDocumentVersion(versionType);
        tcDocumentType.setDocumentType(messageTypedocumentType);
        tcDocumentType.setProcessType(processType);
        tcDocumentType.setSenderIdentification(senderIdentificationPartyType);
        tcDocumentType.setSenderRole(senderRoleRoleType);
        tcDocumentType.setReceiverIdentification(receiverIdentificationPartyType);
        tcDocumentType.setReceiverRole(receiverRoleRoleType);
        tcDocumentType.setCreationDateTime(creationTime);
        tcDocumentType.setResultTimeInterval(timeIntervalType);
        tcDocumentType.setDomain(domainAreaType);
    }

    private void initializeHeadersData() {
        tcDocumentType.setDtdVersion(V1);
        tcDocumentType.setDtdRelease(V1);

        documentIdentification.setV(getDocumentIdentification());

        versionType.setV(1);

        messageTypedocumentType.setV(A_32);

        processType.setV(A_15);

        senderIdentificationPartyType.setV(SENDER_IDENTIFICATION);
        senderIdentificationPartyType.setCodingScheme(A_01);

        receiverIdentificationPartyType.setV(RECEIVER_IDENTIFICATION);
        receiverIdentificationPartyType.setCodingScheme(A_01);

        senderRoleRoleType.setV(A_04);
        receiverRoleRoleType.setV(A_04);

        domainAreaType.setCodingScheme(A_01);
        domainAreaType.setV(DOMAIN);

        timeIntervalType.setV(getTimeInterval());
        creationTime.setV(calendarFromDateTime(OffsetDateTime.now()));
    }

    private void fillValidationResults() {
        final List<TResultTimeseries> resultTimeSeries = tcDocumentType.getValidationResults();
        TResultTimeseries tResultTimeseries = new TResultTimeseries();

        final IdentificationType timeSeriesIdentification = new IdentificationType();
        timeSeriesIdentification.setV(localTargetDate.format(DateTimeFormatter.ofPattern(TIMESERIES_IDENTIFICATION_PATTERN,
                                                                                         FRANCE)));

        final BusinessType businessType = new BusinessType();
        businessType.setV(A_81);

        final EnergyProductType energyProductType = new EnergyProductType();
        energyProductType.setV(PRODUCT);

        final AreaType inArea = new AreaType();
        inArea.setV(IN_AREA);
        inArea.setCodingScheme(A_01);

        final AreaType outArea = new AreaType();
        outArea.setV(OUT_AREA);
        outArea.setCodingScheme(A_01);

        final UnitOfMeasureType unitOfMeasureType = new UnitOfMeasureType();
        unitOfMeasureType.setV(MAW);

        tResultTimeseries.setTimeSeriesIdentification(timeSeriesIdentification);
        tResultTimeseries.setBusinessType(businessType);
        tResultTimeseries.setProduct(energyProductType);
        tResultTimeseries.setInArea(inArea);
        tResultTimeseries.setOutArea(outArea);
        tResultTimeseries.setMeasureUnit(unitOfMeasureType);

        resultTimeSeries.add(tResultTimeseries);
    }

    private TTimestamp initializeTimestampResult(final OffsetDateTime currentDateTime) {
        final TTimestamp timestamp = new TTimestamp();

        final TTime time = new TTime();
        time.setV(currentDateTime.format(UTC_FORMAT));

        final TimeIntervalType timeInterval = new TimeIntervalType();
        timeInterval.setV(currentDateTime.withMinute(0).format(UTC_FORMAT)
                          + "/"
                          + currentDateTime.withMinute(0).plusHours(1).format(UTC_FORMAT));

        timestamp.setReferenceCalculationTime(time);

        timestamp.setTimeInterval(timeInterval);
        timestamp.setTime(time);

        return timestamp;
    }

    private TTimestamp initializeTimestampResult(final TTimestamp timestampData) {
        final TTimestamp timestamp = new TTimestamp();

        final TTime time = new TTime();
        time.setV(timestampData.getTime().getV());

        final TimeIntervalType timeInterval = new TimeIntervalType();
        timeInterval.setV(timestampData.getTimeInterval().getV());

        timestamp.setReferenceCalculationTime(timestampData.getReferenceCalculationTime());

        timestamp.setTimeInterval(timeInterval);
        timestamp.setTime(time);

        return timestamp;
    }

    public InputStream buildTcDocumentType() {
        final StringWriter stringWriter = new StringWriter();
        try {
            final JAXBContext jaxbContext = JAXBContext.newInstance(TcDocumentType.class);
            final Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            final JAXBElement<TcDocumentType> root = new JAXBElement<>(new QName("TTC_rtevalidation_document"), TcDocumentType.class, tcDocumentType);
            jaxbMarshaller.marshal(root, stringWriter);
        } catch (final JAXBException e) {
            throw new CseValidPublicationInvalidDataException("Error while writing TTC validation result document ", e);
        }
        return new ByteArrayInputStream(stringWriter.toString().getBytes());
    }

    private String getTimeInterval() {
        final OffsetDateTime startDateTimeBrussels = toBrusselsStartOfDay(localTargetDate);
        final OffsetDateTime endDateTimeBrussels = localTargetDate.atStartOfDay().plusDays(1).atZone(EUROPE_BRUSSELS_ZONE_ID).toOffsetDateTime();
        return String.format("%s/%s", startDateTimeBrussels.format(ISO_INSTANT), endDateTimeBrussels.format(ISO_INSTANT));
    }

    private String getDocumentIdentification() {
        final String pattern = String.format("'TTC_RTEValidation_'yyyyMMdd'_%s'e", processCode);
        return localTargetDate.format(DateTimeFormatter.ofPattern(pattern, FRANCE));
    }

    private XMLGregorianCalendar calendarFromDateTime(final OffsetDateTime offsetDateTime) {
        try {
            final GregorianCalendar calendar = GregorianCalendar.from(offsetDateTime.toZonedDateTime());
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
        } catch (final DatatypeConfigurationException e) {
            throw new CseValidPublicationInternalException("Internal date-time conversion error", e);
        }
    }

    public void setVersionNumber(final int versionNumber) {
        final VersionType version = new VersionType();
        version.setV(versionNumber);
        tcDocumentType.setDocumentVersion(version);
    }

    public TcDocumentType getTcDocumentType() {
        return tcDocumentType;
    }

}

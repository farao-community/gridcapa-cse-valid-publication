/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInternalException;
import com.farao_community.farao.cse_valid_publication.app.exception.CseValidPublicationInvalidDataException;
import com.farao_community.farao.cse_valid_publication.app.ttc_adjustment.*;
import xsd.etso_code_lists.*;
import xsd.etso_core_cmpts.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.farao_community.farao.cse_valid_publication.app.services.Constants.*;

/**
 * @author Theo Pascoli {@literal <theo.pascoli at rte-france.com>}
 */
public class TcDocumentTypeWriter {
    private final DateTimeFormatter isoInstantFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'");
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

    public TcDocumentTypeWriter(String processCode, LocalDate localDate) {
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

    public synchronized void fillWithNoTtcAdjustmentError() {
        List<TTimestamp> listTimestamps = tcDocumentType.getValidationResults().get(0).getTimestamp();

        OffsetDateTime startDateTime = localTargetDate.atStartOfDay().atZone(EUROPE_BRUSSELS_ZONE_ID).toOffsetDateTime().withMinute(30);
        OffsetDateTime endDateTime;

        if (processCode.equals("ID")) {
            startDateTime = startDateTime.plusHours(12);
            endDateTime = startDateTime.plusHours(12);
        } else {
            endDateTime = startDateTime.plusHours(24);
        }

        while (startDateTime.isBefore(endDateTime)) {
            TTimestamp ts = initializeTimestampResult(startDateTime);

            TextType textTypeRedFlagReason = new TextType();
            textTypeRedFlagReason.setV(STATUS_ERROR_MESSAGE);

            TNumber statusNumber = new TNumber();
            statusNumber.setV(BigInteger.ZERO);

            ts.setSTATUS(statusNumber);
            ts.setRedFlagReason(textTypeRedFlagReason);

            listTimestamps.add(ts);

            startDateTime = startDateTime.plusHours(1);
        }

    }

    public synchronized void fillWithError(TTimestamp timestampData) {
        List<TTimestamp> listTimestamps = tcDocumentType.getValidationResults().get(0).getTimestamp();

        TTimestamp ts = initializeTimestampResult(timestampData);

        TNumber status = new TNumber();
        // calcul failure
        status.setV(BigInteger.ONE);
        ts.setSTATUS(status);

        TextType errorMessage = new TextType();
        errorMessage.setV("Process fail during TSO validation phase.");
        ts.setRedFlagReason(errorMessage);

        listTimestamps.add(ts);

        listTimestamps.sort(Comparator.comparing(c -> OffsetDateTime.parse(c.getTime().getV())));

    }

    public synchronized void fillTimestampWithMissingInputFiles(TTimestamp timestampData, String redFlagError) {
        List<TTimestamp> listTimestamps = tcDocumentType.getValidationResults().get(0).getTimestamp();

        TTimestamp ts = initializeTimestampResult(timestampData);

        TNumber status = new TNumber();
        status.setV(BigInteger.ZERO);
        ts.setSTATUS(status);

        TextType errorMessage = new TextType();
        errorMessage.setV(redFlagError);
        ts.setRedFlagReason(errorMessage);

        listTimestamps.add(ts);

        listTimestamps.sort(Comparator.comparing(c -> OffsetDateTime.parse(c.getTime().getV())));
    }

    public synchronized void fillWithTimestampResult(TTimestamp timestampResult) {
        List<TTimestamp> listTimestamps = tcDocumentType.getValidationResults().get(0).getTimestamp();
        listTimestamps.add(timestampResult);
        listTimestamps.sort(Comparator.comparing(c -> OffsetDateTime.parse(c.getTime().getV())));
    }

    public synchronized void fillTimestampWithNoComputationNeeded(TTimestamp initialTs) {
        List<TTimestamp> listTimestamps = tcDocumentType.getValidationResults().get(0).getTimestamp();

        TTimestamp ts = new TTimestamp();
        ts.setReferenceCalculationTime(initialTs.getReferenceCalculationTime());

        QuantityType mnii = new QuantityType();
        mnii.setV(initialTs.getMiBNII().getV().subtract(initialTs.getANTCFinal().getV()));

        ts.setTimeInterval(initialTs.getTimeInterval());
        ts.setTime(initialTs.getTime());

        TNumber status = new TNumber();
        status.setV(BigInteger.TWO);
        ts.setSTATUS(status);
        ts.setMNII(mnii);
        ts.setTTCLimitedBy(initialTs.getTTCLimitedBy());
        ts.setCRACfile(initialTs.getCRACfile());
        ts.setCGMfile(initialTs.getCGMfile());
        ts.setGSKfile(initialTs.getGSKfile());
        ts.setLimitingElement(initialTs.getLimitingElement());

        listTimestamps.add(ts);
        listTimestamps.sort(Comparator.comparing(c -> OffsetDateTime.parse(c.getTime().getV())));
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
        tcDocumentType.setDtdVersion("1");
        tcDocumentType.setDtdRelease("1");

        documentIdentification.setV(getDocumentIdentification());

        versionType.setV(1);

        messageTypedocumentType.setV(MessageTypeList.A_32);

        processType.setV(ProcessTypeList.A_15);

        senderIdentificationPartyType.setV(SENDER_IDENTIFICATION);
        senderIdentificationPartyType.setCodingScheme(CodingSchemeType.A_01);

        receiverIdentificationPartyType.setV(RECEIVER_IDENTIFICATION);
        receiverIdentificationPartyType.setCodingScheme(CodingSchemeType.A_01);

        senderRoleRoleType.setV(RoleTypeList.A_04);
        receiverRoleRoleType.setV(RoleTypeList.A_04);

        domainAreaType.setCodingScheme(CodingSchemeType.A_01);
        domainAreaType.setV(DOMAIN);

        timeIntervalType.setV(getTimeInterval());
        creationTime.setV(calendarFromDateTime(OffsetDateTime.now()));
    }

    private void fillValidationResults() {
        List<TResultTimeseries> listResultTimeseries = tcDocumentType.getValidationResults();
        TResultTimeseries tResultTimeseries = new TResultTimeseries();

        IdentificationType timeSeriesIdentification = new IdentificationType();
        timeSeriesIdentification.setV(localTargetDate.format(DateTimeFormatter.ofPattern(TIMESERIES_IDENTIFICATION_PATTERN, Locale.FRANCE)));

        BusinessType businessType = new BusinessType();
        businessType.setV(BusinessTypeList.A_81);

        EnergyProductType energyProductType = new EnergyProductType();
        energyProductType.setV(PRODUCT);

        AreaType inArea = new AreaType();
        inArea.setV(IN_AREA);
        inArea.setCodingScheme(CodingSchemeType.A_01);

        AreaType outArea = new AreaType();
        outArea.setV(OUT_AREA);
        outArea.setCodingScheme(CodingSchemeType.A_01);

        UnitOfMeasureType unitOfMeasureType = new UnitOfMeasureType();
        unitOfMeasureType.setV(UnitOfMeasureTypeList.MAW);

        tResultTimeseries.setTimeSeriesIdentification(timeSeriesIdentification);
        tResultTimeseries.setBusinessType(businessType);
        tResultTimeseries.setProduct(energyProductType);
        tResultTimeseries.setInArea(inArea);
        tResultTimeseries.setOutArea(outArea);
        tResultTimeseries.setMeasureUnit(unitOfMeasureType);

        listResultTimeseries.add(tResultTimeseries);
    }

    private TTimestamp initializeTimestampResult(OffsetDateTime currentDateTime) {
        TTimestamp ts = new TTimestamp();

        TTime time = new TTime();
        time.setV(currentDateTime.format(isoInstantFormatter.withZone(ZoneOffset.UTC)));

        TimeIntervalType timeInterval = new TimeIntervalType();
        timeInterval.setV(currentDateTime.withMinute(0).format(isoInstantFormatter.withZone(ZoneOffset.UTC)) + "/" + currentDateTime.withMinute(0).plusHours(1).format(isoInstantFormatter.withZone(ZoneOffset.UTC)));

        ts.setReferenceCalculationTime(time);

        ts.setTimeInterval(timeInterval);
        ts.setTime(time);

        return ts;
    }

    private TTimestamp initializeTimestampResult(TTimestamp timestampData) {
        TTimestamp ts = new TTimestamp();

        TTime time = new TTime();
        time.setV(timestampData.getTime().getV());

        TimeIntervalType timeInterval = new TimeIntervalType();
        timeInterval.setV(timestampData.getTimeInterval().getV());

        ts.setReferenceCalculationTime(time);

        ts.setTimeInterval(timeInterval);
        ts.setTime(time);

        return ts;
    }

    public InputStream buildTcDocumentType() {
        StringWriter sw = new StringWriter();
        try {
            JAXBContext jaxbContext = JAXBContext.newInstance(TcDocumentType.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            JAXBElement<TcDocumentType> root = new JAXBElement<>(new QName("TTC_rtevalidation_document"), TcDocumentType.class, tcDocumentType);
            jaxbMarshaller.marshal(root, sw);
        } catch (JAXBException e) {
            throw new CseValidPublicationInvalidDataException("Error while writing TTC validation result document ", e);
        }
        return new ByteArrayInputStream(sw.toString().getBytes());
    }

    private String getTimeInterval() {
        OffsetDateTime startTime = localTargetDate.atStartOfDay().atZone(EUROPE_BRUSSELS_ZONE_ID).toOffsetDateTime();
        OffsetDateTime endTime = localTargetDate.atStartOfDay().plusDays(1).atZone(EUROPE_BRUSSELS_ZONE_ID).toOffsetDateTime();
        return String.format("%s/%s", startTime.format(DateTimeFormatter.ISO_INSTANT), endTime.format(DateTimeFormatter.ISO_INSTANT));
    }

    private String getDocumentIdentification() {
        String pattern = String.format("'TTC_RTEValidation_'yyyyMMdd'_%s'e", processCode);
        return localTargetDate.format(DateTimeFormatter.ofPattern(pattern, Locale.FRANCE));
    }

    private XMLGregorianCalendar calendarFromDateTime(OffsetDateTime offsetDateTime) {
        try {
            GregorianCalendar calendar = GregorianCalendar.from(offsetDateTime.toZonedDateTime());
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
        } catch (DatatypeConfigurationException e) {
            throw new CseValidPublicationInternalException("Internal date-time conversion error", e);
        }
    }

    TcDocumentType getTcDocumentType() {
        return tcDocumentType;
    }

    public void setVersionNumber(int versionNumber) {
        VersionType version = new VersionType();
        version.setV(versionNumber);
        tcDocumentType.setDocumentVersion(version);
    }
}

package org.codice.ddf.catalog.ui.metacard.validation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.federation.FederationException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.catalog.validation.AttributeValidator;
import ddf.catalog.validation.AttributeValidatorRegistry;
import ddf.catalog.validation.ReportingMetacardValidator;
import ddf.catalog.validation.report.AttributeValidationReport;
import ddf.catalog.validation.report.MetacardValidationReport;
import ddf.catalog.validation.violation.ValidationViolation;

public class Validator {
    private final List<ReportingMetacardValidator> validators;

    private final List<AttributeValidatorRegistry> attributeValidatorRegistry;

    public Validator(List<ReportingMetacardValidator> validators,
            List<AttributeValidatorRegistry> attributeValidatorRegistry) {
        this.validators = validators;
        this.attributeValidatorRegistry = attributeValidatorRegistry;
    }

    public List<ViolationResult> getValidation(Metacard metacard)
            throws SourceUnavailableException, UnsupportedQueryException,
            FederationException {
        Set<ValidationViolation> attributeValidationViolations = validators.stream()
                .map(v -> v.validateMetacard(metacard))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(MetacardValidationReport::getAttributeValidationViolations)
                .reduce((left, right) -> {
                    left.addAll(right);
                    return left;
                })
                .orElse(new HashSet<>());
        Map<String, ViolationResult> violationsResult = getViolationsResult(
                attributeValidationViolations);

        return violationsResult.entrySet()
                .stream()
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

    }

    public AttributeValidationResponse validateAttribute(String attribute, String value) {
        Set<AttributeValidator> validators = attributeValidatorRegistry.stream()
                .map(avr -> avr.getValidators(attribute))
                .reduce((left, right) -> {
                    left.addAll(right);
                    return left;
                })
                .orElse(new HashSet<>());

        Set<String> suggestedValues = new HashSet<>();
        Set<ValidationViolation> violations = new HashSet<>();
        for (AttributeValidator validator : validators) {
            Optional<AttributeValidationReport> validationReport =
                    validator.validate(new AttributeImpl(attribute, value));
            if (validationReport.isPresent()) {
                AttributeValidationReport report = validationReport.get();
                if (!report.getSuggestedValues()
                        .isEmpty()) {
                    suggestedValues = report.getSuggestedValues();
                }
                violations.addAll(report.getAttributeValidationViolations());
            }
        }

        return new AttributeValidationResponse(violations, suggestedValues);
    }

    private Map<String, ViolationResult> getViolationsResult(
            Set<ValidationViolation> attributeValidationViolations) {
        Map<String, ViolationResult> violationsResult = new HashMap<>();
        for (ValidationViolation violation : attributeValidationViolations) {
            for (String attribute : violation.getAttributes()) {
                if (!violationsResult.containsKey(attribute)) {
                    violationsResult.put(attribute, new ViolationResult());
                }
                ViolationResult violationResponse = violationsResult.get(attribute);
                violationResponse.setAttribute(attribute);

                if (ValidationViolation.Severity.ERROR.equals(violation.getSeverity())) {
                    violationResponse.getErrors()
                            .add(violation.getMessage());
                } else if (ValidationViolation.Severity.WARNING.equals(violation.getSeverity())) {
                    violationResponse.getWarnings()
                            .add(violation.getMessage());
                } else {
                    throw new RuntimeException("Unexpected Severity Level");
                }
            }
        }
        return violationsResult;
    }
}
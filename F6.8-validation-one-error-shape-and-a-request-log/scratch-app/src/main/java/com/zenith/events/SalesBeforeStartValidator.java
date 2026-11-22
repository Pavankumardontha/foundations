package com.zenith.events;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class SalesBeforeStartValidator
        implements ConstraintValidator<SalesBeforeStart, CreateEventRequest> {

    @Override
    public boolean isValid(CreateEventRequest request, ConstraintValidatorContext context) {
        // Absence is @NotNull's job. A cross-field rule only judges values that exist.
        if (request.salesOpenAt() == null || request.startsAt() == null) {
            return true;
        }
        if (request.salesOpenAt().isBefore(request.startsAt())) {
            return true;
        }
        // Report the violation on a field, so it lands in fieldErrors like every other rule.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("salesOpenAt")
                .addConstraintViolation();
        return false;
    }
}

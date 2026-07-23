package com.example.store;

import com.atlassian.oai.validator.OpenApiInteractionValidator;

/**
 * Shared OpenAPI validator for controller tests, so response assertions can also verify the running service actually
 * matches OpenAPI.yaml, not just the hand-written expectations in each test. Loaded once and reused - the validator is
 * stateless and expensive to build repeatedly.
 */
public final class OpenApiContractSupport {

    private static final OpenApiInteractionValidator VALIDATOR = OpenApiInteractionValidator.createForSpecificationUrl(
                    "OpenAPI.yaml")
            .build();

    private OpenApiContractSupport() {}

    public static OpenApiInteractionValidator validator() {
        return VALIDATOR;
    }
}

package com.github.jsonldjava.impl;

import static com.github.jsonldjava.core.RDFDatasetUtils.parseNQuads;

import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.RDFDataset;
import com.github.jsonldjava.core.RDFParser;

public class NQuadRDFParser implements RDFParser {
    @Override
    public RDFDataset parse(Object input) throws JsonLdError {
        if (input instanceof String) {
            return parseNQuads((String) input);
        } else {
            throw new JsonLdError("NQuad Parser expected string input.").setType(
                    JsonLdError.Error.INVALID_INPUT).setDetail("input", input);
        }
    }

}

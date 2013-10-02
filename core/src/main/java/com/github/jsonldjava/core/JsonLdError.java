package com.github.jsonldjava.core;

import java.util.HashMap;
import java.util.Map;

public class JsonLdError extends Exception {

    Map<String, Object> details;
    private Error type;

    public JsonLdError(String string, Map<String, Object> details) {
        super(string);
        this.details = details;
    }
    
    public JsonLdError(Error type, Object detail) {
    	// TODO: pretty toString (e.g. print whole json objects)
    	super(detail == null ? "" : detail.toString());
    	this.type = type;
    }

    public JsonLdError(String string) {
        super(string);
        details = new HashMap();
    }

    public JsonLdError setDetail(String string, Object val) {
        details.put(string, val);
        // System.out.println("ERROR DETAIL: " + string + ": " +
        // val.toString());
        return this;
    }

    public enum Error {
        SYNTAX_ERROR, PARSE_ERROR, RDF_ERROR, CONTEXT_URL_ERROR, INVALID_URL, COMPACT_ERROR, CYCLICAL_CONTEXT, FLATTEN_ERROR, FRAME_ERROR, NORMALIZE_ERROR, UNKNOWN_FORMAT, INVALID_INPUT,
        // TODO: remove unused error types (should be the ones above this line)
        RERCURSIVE_CONTEXT_INCLUSION, LOADING_REMOTE_CONTEXT_FAILED, INVALID_REMOTE_CONTEXT, INVALID_LOCAL_CONTEXT, INVALID_BASE_IRI, INVALID_VOCAB_MAPPING, INVALID_DEFAULT_LANGUAGE, 
        CYCLIC_IRI_MAPPING, KEYWORD_REDEFINITION, INVALID_TERM_DEFINITION, INVALID_TYPE_MAPPING, INVALID_IRI_MAPPING, INVALID_REVERSE_PROPERTY, INVALID_KEYWORD_ALIAS, 
        INVALID_CONTAINER_MAPPING, INVALID_LANGUAGE_MAPPING
    }

    public JsonLdError setType(Error error) {
        this.type = error;
        return this;
    };

    public Error getType() {
        return type;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    @Override
    public String getMessage() {
        String msg = super.getMessage();
        for (final String key : details.keySet()) {
            msg += " {" + key + ":" + details.get(key) + "}";
        }
        return msg;
    }
}

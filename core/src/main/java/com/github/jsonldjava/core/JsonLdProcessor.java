package com.github.jsonldjava.core;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.jsonldjava.core.JsonLdError.Error;
import com.github.jsonldjava.utils.JSONUtils;

/** 
 * http://json-ld.org/spec/latest/json-ld-api/#the-jsonldprocessor-interface
 * 
 * @author tristan
 *
 */
public class JsonLdProcessor {

    public static List<Object> expand(Object input, Object context, JsonLdOptions opts) throws JsonLdError {
    	// 1) 
    	// TODO: look into java futures/promises
    	
    	// 2) TODO: better verification of DOMString IRI
    	if (input instanceof String && ((String)input).contains(":")) {
    		try {
    			JSONUtils.fromURL(new URL((String)input));
    		} catch (Exception e) {
    			throw new JsonLdError(Error.LOADING_DOCUMENT_FAILED, e.getMessage());
    		}
    		// if set the base in options should override the base iri in the active context
    		// thus only set this as the base iri if it's not already set in options
    		if (opts.getBase() == null) { 
    			opts.setBase((String)input); 
    		}
    	}
    	
    	// 3)
    	Context activeCtx = new Context(opts);
    	// 4)
    	if (context != null) {
    		if (context instanceof Map && ((Map<String,Object>) context).containsKey("@context")) {
    			context = ((Map<String,Object>) context).get("@context");
    		}
    		activeCtx = activeCtx.parse(context);
    	}
    	
    	// 5)
    	// TODO: add support for getting a context from HTTP when content-type is set to a jsonld compatable format
    	
    	// 6)
    	Object expanded = new JsonLdApi(opts).expand(activeCtx, input);
    	
    	// final step of Expansion Algorithm
        if (expanded instanceof Map && ((Map) expanded).containsKey("@graph")
                && ((Map) expanded).size() == 1) {
            expanded = ((Map<String, Object>) expanded).get("@graph");
        } else if (expanded == null) {
            expanded = new ArrayList<Object>();
        }

        // normalize to an array
        if (!(expanded instanceof List)) {
            final List<Object> tmp = new ArrayList<Object>();
            tmp.add(expanded);
            expanded = tmp;
        }
        return (List<Object>) expanded;
    }

    public static List<Object> expand(Object input, JsonLdOptions opts) throws JsonLdError {
    	return expand(input, null, opts);
    }
    
    public static List<Object> expand(Object input) throws JsonLdError {
        return expand(input, null, new JsonLdOptions(""));
    }

    
    
    
    
    
    
    
    /////////////////// NOT IMPLEMENTED (just here for convenience)
	public static Map<String, Object> frame(Map<String, Object> rval,
			Map<String, Object> frame, JsonLdOptions jsonLdOptions) throws JsonLdError {
		// TODO Auto-generated method stub
		return null;
	}

	public static Map<String, Object> compact(Map<String, Object> rval,
			Object object, JsonLdOptions jsonLdOptions) throws JsonLdError {
		// TODO Auto-generated method stub
		return null;
	}

	public static Map<String, Object> fromRDF(String manifestFile,
			JsonLdOptions jsonLdOptions) throws JsonLdError {
		// TODO Auto-generated method stub
		return null;
	}
}

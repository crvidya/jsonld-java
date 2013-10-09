package com.github.jsonldjava.core;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.jsonldjava.core.JsonLdError.Error;

/** 
 * http://json-ld.org/spec/latest/json-ld-api/#the-jsonldprocessor-interface
 * 
 * @author tristan
 *
 */
public class JsonLdProcessor {

	public static Map<String,Object> compact(Object input, Object context, JsonLdOptions opts) throws JsonLdError {
		// 1)
		// TODO: look into java futures/promises
		
		// 2-6) NOTE: these are all the same steps as in expand
    	Object expanded = expand(input, opts);
    	// 7)
    	if (context instanceof Map && ((Map<String,Object>) context).containsKey("@context")) {
    		context = ((Map<String,Object>) context).get("@context");
    	}
    	Context activeCtx = new Context(opts);
    	activeCtx = activeCtx.parse(context);
    	// 8)
    	Object compacted = new JsonLdApi(opts).compact(activeCtx, null, expanded, opts.getCompactArrays());
    	
    	// final step of Compaction Algorithm
    	// TODO: SPEC:  the result result is a NON EMPTY array, 
    	if (compacted instanceof List) {
    		if (((List<Object>) compacted).isEmpty()) {
    			compacted = new LinkedHashMap<String, Object>();
    		} else {
    			Map<String,Object> tmp = new LinkedHashMap<String, Object>();
    			// TODO: SPEC: doesn't specify to use vocab = true here
    			tmp.put(activeCtx.compactIri("@graph", true), compacted);
    			compacted = tmp;
    		}
    	}
    	if (compacted != null && context != null) {
    		// TODO: figure out if we can make "@context" appear at the start of the keySet
    		if ((context instanceof Map && !((Map<String,Object>) context).isEmpty()) || (context instanceof List && !((List<Object>) context).isEmpty())) {
    			((Map<String,Object>)compacted).put("@context", context);
    		}
    	}
    	
    	// 9)
    	return (Map<String,Object>)compacted;    	
	}
	
	public static List<Object> expand(Object input, JsonLdOptions opts) throws JsonLdError {
    	// 1) 
    	// TODO: look into java futures/promises
    	
    	// 2) TODO: better verification of DOMString IRI
    	if (input instanceof String && ((String)input).contains(":")) {
    		try {
    			RemoteDocument tmp = opts.documentLoader.loadDocument((String)input);
    			input = tmp.document;
    			// TODO: figure out how to deal with remote context
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
    	if (opts.getExpandContext() != null) {
    		Object exCtx = opts.getExpandContext();
    		if (exCtx instanceof Map && ((Map<String,Object>) exCtx).containsKey("@context")) {
    			exCtx = ((Map<String,Object>) exCtx).get("@context");
    		}
    		activeCtx = activeCtx.parse(exCtx);
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
    
    public static List<Object> expand(Object input) throws JsonLdError {
        return expand(input, new JsonLdOptions(""));
    }

    public static Object flatten(Object input, Object context, JsonLdOptions opts) throws JsonLdError {
    	// 2-6) NOTE: these are all the same steps as in expand
    	Object expanded = expand(input, opts);
    	// 7)
    	if (context instanceof Map && ((Map<String,Object>) context).containsKey("@context")) {
    		context = ((Map<String,Object>) context).get("@context");
    	}
    	// 8) NOTE: blank node generation variables are members of JsonLdApi
    	// 9) NOTE: the next block is the Flattening Algorithm described in
    	// http://json-ld.org/spec/latest/json-ld-api/#flattening-algorithm
    	
    	// 1)
    	Map<String,Object> nodeMap = new LinkedHashMap<String, Object>();
    	nodeMap.put("@default", new LinkedHashMap<String, Object>());
    	// 2)
    	new JsonLdApi(opts).generateNodeMap(expanded, nodeMap);
    	// 3)
    	Map<String,Object> defaultGraph = (Map<String, Object>) nodeMap.remove("@default");
    	// 4)
    	for (String graphName : nodeMap.keySet()) {
    		Map<String,Object> graph = (Map<String, Object>) nodeMap.get(graphName);
    		// 4.1+4.2)
    		Map<String,Object> entry;
    		if (!defaultGraph.containsKey(graphName)) {
    			entry = new LinkedHashMap<String, Object>();
    			entry.put("@id", graphName);
    			defaultGraph.put(graphName, entry);
    		} else {
    			entry = (Map<String, Object>) defaultGraph.get(graphName);
    		}
    		// 4.3)
    		// TODO: SPEC doesn't specify that this should only be added if it doesn't exists
    		if (!entry.containsKey("@graph")) {
    			entry.put("@graph", new ArrayList<Object>());
    		}
    		List<String> keys = new ArrayList<String>(graph.keySet());
    		Collections.sort(keys);
    		for (String id : keys) {
    			Map<String,Object> node = (Map<String, Object>) graph.get(id);
    			if (!(node.containsKey("@id") && node.size() == 1)) {
    				((List<Object>) entry.get("@graph")).add(node);
    			}
    		}
    		
    	}
    	// 5)
    	List<Object> flattened = new ArrayList<Object>();
    	// 6)
    	List<String> keys = new ArrayList<String>(defaultGraph.keySet());
		Collections.sort(keys);
		for (String id : keys) {
			Map<String,Object> node = (Map<String, Object>) defaultGraph.get(id);
			if (!(node.containsKey("@id") && node.size() == 1)) {
				flattened.add(node);
			}
		}
		// 8)
		if (context != null && !flattened.isEmpty()) {
			Context activeCtx = new Context(opts);
			activeCtx = activeCtx.parse(context);
			// TODO: only instantiate one jsonldapi
			Object compacted = new JsonLdApi(opts).compact(activeCtx, null, flattened, opts.getCompactArrays());
			if (!(compacted instanceof List)) {
				List<Object> tmp = new ArrayList<Object>();
				tmp.add(compacted);
				compacted = tmp;
			}
			String alias = activeCtx.compactIri("@graph");
			Map<String,Object> rval = activeCtx.serialize();
			rval.put(alias, compacted);
			return rval;
		}
    	return flattened;
    }
    
    public static Object flatten(Object input, JsonLdOptions opts) throws JsonLdError {
    	return flatten(input, null, opts);
    }
    
    /////////////////// NOT IMPLEMENTED (just here for convenience)
	public static Map<String, Object> frame(Map<String, Object> rval,
			Map<String, Object> frame, JsonLdOptions jsonLdOptions) throws JsonLdError {
		// TODO Auto-generated method stub
		return null;
	}

	public static Map<String, Object> fromRDF(String manifestFile,
			JsonLdOptions jsonLdOptions) throws JsonLdError {
		// TODO Auto-generated method stub
		return null;
	}
}

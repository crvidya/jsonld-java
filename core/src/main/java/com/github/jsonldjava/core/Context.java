package com.github.jsonldjava.core;

import static com.github.jsonldjava.core.JSONLDUtils.compareShortestLeast;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.jsonldjava.utils.JSONUtils;
import com.github.jsonldjava.utils.Obj;
import com.github.jsonldjava.utils.URL;
import com.github.jsonldjava.core.JsonLdError.Error;

/**
 * A helper class which still stores all the values in a map but gives member
 * variables easily access certain keys
 * 
 * @author tristan
 * 
 */
public class Context extends LinkedHashMap<String, Object> {
	
	private Options options;
	private Map<String, Object> termDefinitions;	
    public Map<String, Object> inverse = null;
    
	public Context() {
        this(new Options());
    }

    public Context(Options options) {
        super();
        init(options);
    }

    public Context(Map<String, Object> map, Options options) {
        super(map);
        init(options);
    }

    public Context(Map<String, Object> map) {
        super(map);
        init(new Options());
    }

	
    private void init(Options options) {
    	this.options = options;
    	if (options.base != null) { 
    		this.put("@base", URL.parse(options.base).toString());
    	}
        this.termDefinitions = new LinkedHashMap<String, Object>();
    }
	
	private String resolveURI(String baseUri, String pathToResolve) {
		// NOTE: if uri doesn't end with /, resolve just joins the two strings (without adding a / itself). adding an extra / if one doesn't exist
		// doesn't break anything, so it's safer to add it
		try {
			return new URI(baseUri != null ? baseUri + "/" : "").resolve(pathToResolve).toString();
		} catch (URISyntaxException e) {
			return null;
		}
	}
	
    /**
     * Context Processing Algorithm
     * 
     * http://json-ld.org/spec/latest/json-ld-api/#context-processing-algorithms
     * 
     * @param localContext
     * @param remoteContexts
     * @return
     * @throws JsonLdError 
     */
    public Context parse(Object localContext, List<String> remoteContexts) throws JsonLdError {
    	// 1. Initialize result to the result of cloning active context.
    	Context result = this;
    	
    	if (!(localContext instanceof List)) {
    		Object temp = localContext;
    		localContext = new ArrayList<Object>();
    		((List<Object>) localContext).add(temp);
    	}
    	for (Object context : ((List<Object>)localContext)) {
    		if (context == null) {
    			result = new Context(this.options);
    		} if (context instanceof String) {
    			String uri = (String)result.get("@base");
    			uri = resolveURI(uri, (String)context);
				// 3.2.2
				if (remoteContexts.contains(uri)) {
					throw new JsonLdError(Error.RERCURSIVE_CONTEXT_INCLUSION, (String)context);
				}
				remoteContexts.add((String)context);

				// 3.2.3: Dereference context
				try {
					Object remoteContext = JSONUtils.fromURL(new java.net.URL(uri));
					if (!(remoteContext instanceof Map) || !((Map<String,Object>)remoteContext).containsKey("@context")) {
						// If the dereferenced document has no top-level JSON object with an @context member
						throw new JsonLdError(Error.INVALID_REMOTE_CONTEXT, (String)context);
					}
					context = ((Map<String,Object>)remoteContext).get("@context");
				} catch (Exception e) {
					// If context cannot be dereferenced
					throw new JsonLdError(Error.LOADING_REMOTE_CONTEXT_FAILED, (String)context);
				}
				
				// 3.2.4
				result = result.parse(context, remoteContexts);
				// 3.2.5
				continue;
    		} else if (!(context instanceof Map)) {
    			// 3.3
    			throw new JsonLdError(Error.INVALID_LOCAL_CONTEXT, context);
    		}
    		
    		// 3.4
    		if (remoteContexts.isEmpty() && ((Map<String, Object>) context).containsKey("@base")) {
    			String value = (String)((Map<String,Object>) context).get("@base");
    			if (value == null) {
    				result.remove("@base");
    			} else if (JSONLDUtils.isAbsoluteIri(value)) {
    				result.put("@base", value);
    			} else {
    				String baseUri = (String)result.get("@base");
    				if (!JSONLDUtils.isAbsoluteIri(baseUri)) {
    					throw new JsonLdError(Error.INVALID_BASE_IRI, baseUri);
    				}
    				result.put("@base", resolveURI(baseUri, value));
    			}
    		}
    		
    		// 3.5
    		if (((Map<String, Object>) context).containsKey("@vocab")) {
    			String value = (String)((Map<String,Object>) context).get("@vocab");
    			if (value == null) {
    				result.remove("@vocab");
    			} else if (JSONLDUtils.isAbsoluteIri(value)) {
    				result.put("@vocab", value);
    			} else {
    				throw new JsonLdError(Error.INVALID_VOCAB_MAPPING, value);
    			}
    		}

    		// 3.6
    		if (((Map<String, Object>) context).containsKey("@language")) {
    			Object value = ((Map<String,Object>) context).get("@language");
    			if (value == null) {
    				result.remove("@language");
    			} else if (value instanceof String) {
    				result.put("@language", ((String)value).toLowerCase());
    			} else {
    				throw new JsonLdError(Error.INVALID_DEFAULT_LANGUAGE, value);
    			}
    		}
    		
    		// 3.7
    		Map<String,Boolean> defined = new LinkedHashMap<String, Boolean>();
    		for (String key : ((Map<String,Object>) context).keySet()) {
    			if ("@base".equals(key) || "@vocab".equals(key) || "@language".equals(key)) {
    				continue;
    			}
    			result.createTermDefinition((Map<String, Object>) context, key, defined);
    		}
    	}
    	return result;
    }

	public Context parse(Object localContext) throws JsonLdError {
    	return this.parse(localContext, new ArrayList<String>());
    }
	
    /**
     * Create Term Definition Algorithm
     * 
     * http://json-ld.org/spec/latest/json-ld-api/#create-term-definition
     * 
     * @param result
     * @param context
     * @param key
     * @param defined
     * @throws JsonLdError 
     */
    private void createTermDefinition(Map<String,Object> context,
			String term, Map<String, Boolean> defined) throws JsonLdError {
		if (defined.containsKey(term)) {
			if (Boolean.TRUE.equals(defined.get(term))) {
				return;
			}
			throw new JsonLdError(Error.CYCLIC_IRI_MAPPING, term);
		}
		
		defined.put(term, false);
		
		if (JSONLDUtils.isKeyword(term)) {
            throw new JsonLdError(Error.KEYWORD_REDEFINITION, term);
		}
		
		this.termDefinitions.remove(term);
		Object value = context.get(term);
		if (value == null || (value instanceof Map && ((Map<String, Object>) value).containsKey("@id") && ((Map<String, Object>) value).get("@id") == null)) {
			this.termDefinitions.put(term, null);
			defined.put(term, true);
			return;
		}
		
		if (value instanceof String) {
			final Map<String, Object> tmp = new LinkedHashMap<String, Object>();
            tmp.put("@id", value);
            value = tmp;
		}
		
		if (!(value instanceof Map)) {
			throw new JsonLdError(Error.INVALID_TERM_DEFINITION, value);
		}
		
		// casting the value so it doesn't have to be done below everytime
		final Map<String, Object> val = (Map<String, Object>) value;

        // 9) create a new term definition
        final Map<String, Object> definition = new LinkedHashMap<String, Object>();
        
        // 10)
        if (val.containsKey("@type")) {
        	if (!(val.get("@type") instanceof String)) {
        		throw new JsonLdError(Error.INVALID_TYPE_MAPPING, val.get("@type"));
        	}
        	String type = (String) val.get("@type");
        	try {
        		type = this.expandIri((String) val.get("@type"), true, true, context, defined);
        	} catch (JsonLdError error) {
        		if (error.getType() != Error.INVALID_IRI_MAPPING) {
        			throw error;
        		}
        		throw new JsonLdError(Error.INVALID_TYPE_MAPPING, type);
        	}
        	if ("@id".equals(type) || "@vocab".equals(type) || JSONLDUtils.isAbsoluteIri(type)) {
        		definition.put("@type", type);
        	} else {
        		throw new JsonLdError(Error.INVALID_TYPE_MAPPING, type);
        	}        
        }
        
        // 11)
        if (val.containsKey("@reverse")) {
        	if (val.containsKey("@id")) {
        		throw new JsonLdError(Error.INVALID_REVERSE_PROPERTY, val);
        	}
        	if (!(val.get("@reverse") instanceof String)) {
        		throw new JsonLdError(Error.INVALID_IRI_MAPPING, "Expected String for @reverse value. got " + (val.get("@reverse") == null ? "null" : val.get("@reverse").getClass()));
        	}
        	final String reverse = this.expandIri((String) val.get("@reverse"), false, true, context, defined);
        	if (!JSONLDUtils.isAbsoluteIri(reverse)) {
        		throw new JsonLdError(Error.INVALID_IRI_MAPPING, "Non-absolute @reverse IRI: " + reverse);
        	}
            definition.put("@id", reverse);
            if (val.containsKey("@container")) {
            	final String container = (String) val.get("@container");
            	if (container == null || "@set".equals(container) || "@index".equals(container)) {
            		definition.put("@container", container);
            	} else {
            		throw new JsonLdError(Error.INVALID_REVERSE_PROPERTY, "reverse properties only support set- and index-containers");
            	}
            }
            definition.put("@reverse", true);
            this.termDefinitions.put(term, definition);
            defined.put(term, true);
            return;
        }
        
        // 12)
        definition.put("@reverse", false);
        
        // 13)
        if (val.containsKey("@id")) {
        	if (!(val.get("@id") instanceof String)) {
                throw new JsonLdError(Error.INVALID_IRI_MAPPING, "expected value of @id to be a string");
            }
            final String id = (String) val.get("@id");
            if (id != null && !id.equals(term)) { 
                String res = this.expandIri(id, false, true, context, defined);
                if (JSONLDUtils.isKeyword(res) || JSONLDUtils.isAbsoluteIri(res)) {
                	if ("@context".equals(res)) {
                		throw new JsonLdError(Error.INVALID_KEYWORD_ALIAS, "cannot alias @context");
                	}
                	definition.put("@id", res);	
                } else {
                	throw new JsonLdError(Error.INVALID_IRI_MAPPING, "resulting IRI mapping should be a keyword, absolute IRI or blank node");
                }
            }
        }
        
        // 14)
        int colIndex = term.indexOf(":");
        if (colIndex >= 0) {
        	String prefix = term.substring(0, colIndex);
        	String suffix = term.substring(colIndex+1);
        	if (context.containsKey(prefix)) {
        		this.createTermDefinition(context, prefix, defined);
        	}
        	if (termDefinitions.containsKey(prefix)) {
        		definition.put("@id", termDefinitions.get(prefix) + suffix);
        	} else {
        		definition.put("@id", term);
        	}
        // 15)
        } else if (this.containsKey("@vocab")) {
        	definition.put("@id", this.get("@vocab") + term);
        } else {
        	throw new JsonLdError(Error.INVALID_IRI_MAPPING, "relative term definition without vocab mapping");
        }
 
        // 16)
        if (val.containsKey("@container")) {
        	final String container = (String) val.get("@container");
            if (!"@list".equals(container) && !"@set".equals(container)
                    && !"@index".equals(container) && !"@language".equals(container)) {
            	throw new JsonLdError(Error.INVALID_CONTAINER_MAPPING, "@container must be either @list, @set, @index, or @language");
            }
            definition.put("@container", container);
        }
        
        // 17)
        if (val.containsKey("@language") && !val.containsKey("@type")) {
        	final String language = (String) val.get("@language");
        	if (language == null || language instanceof String) {
        		definition.put("@language", language != null ? language.toLowerCase() : null);
        	} else {
        		throw new JsonLdError(Error.INVALID_LANGUAGE_MAPPING, "@language must be a string or null");
        	}            
        }
        
        // 18)
        this.termDefinitions.put(term, definition);
        defined.put(term, true);
	}
	
    /**
     * IRI Expansion Algorithm
     * 
     * http://json-ld.org/spec/latest/json-ld-api/#iri-expansion
     * 
     * @param value
     * @param relative
     * @param vocab
     * @param context
     * @param defined
     * @return
     * @throws JsonLdError
     */
    String expandIri(String value, boolean relative, boolean vocab,
			Map<String, Object> context, Map<String, Boolean> defined) throws JsonLdError {
    	// 1)
    	if (value == null || JSONLDUtils.isKeyword(value)) {
    		return value;
    	}
    	// 2)
    	if (context != null && context.containsKey(value) && !(defined.get(value))) {
    		this.createTermDefinition(context, value, defined);
    	}
    	// 3)
    	if (vocab && this.termDefinitions.containsKey(value)) {
    		return (String) ((LinkedHashMap<String, Object>) this.termDefinitions.get(value)).get("@id");
    	}
    	// 4)
    	int colIndex = value.indexOf(":");
    	if (colIndex >= 0) {
    		// 4.1)
    		String prefix = value.substring(0, colIndex);
        	String suffix = value.substring(colIndex+1);
        	// 4.2)
        	if ("_".equals(prefix) || suffix.startsWith("//")) {
        		return value;
        	}
        	// 4.3)
        	if (context != null && context.containsKey(prefix) && (!defined.containsKey(prefix) || defined.get(prefix) == false)) {
        		this.createTermDefinition(context, prefix, defined);
        	}
        	// 4.4)
        	if (this.termDefinitions.containsKey(prefix)) {
        		return (String) ((LinkedHashMap<String, Object>) this.termDefinitions.get(prefix)).get("@id") + suffix;	
        	}
        	// 4.5)
        	return value;
    	}
    	// 5)
    	if (vocab && this.containsKey("@vocab")) {
    		return this.get("@vocab") + value;
    	}
    	// 6)
    	if (relative) {
    		// TODO: make sure this is the right place to get base from.
    		// 		 i'm not sure if the base that's passed in as an option
    		//		 should override the @base in the context or if the @context
    		//		 should even be able to have a @base
    		value = resolveURI((String) this.get("@base"), value);
    	}
    	// 7)
		return value;
	}
    

    /**
     * IRI Compaction Algorithm
     * 
     * http://json-ld.org/spec/latest/json-ld-api/#iri-compaction
     * 
     * Compacts an IRI or keyword into a term or prefix if it can be. If the IRI
     * has an associated value it may be passed.
     * 
     * @param iri
     *            the IRI to compact.
     * @param value
     *            the value to check or null.
     * @param relativeTo
     *            options for how to compact IRIs: vocab: true to split after
     * @vocab, false not to.
     * @param reverse
     *            true if a reverse property is being compacted, false if not.
     * 
     * @return the compacted term, prefix, keyword alias, or the original IRI.
     */
    String compactIri(String iri, Object value, boolean relativeToVocab, boolean reverse) {
        // 1)
        if (iri == null) {
            return null;
        }

        // 2)
        if (relativeToVocab && getInverse().containsKey(iri)) {
        	// 2.1)
            String defaultLanguage = (String) this.get("@language");
            if (defaultLanguage == null) {
                defaultLanguage = "@none";
            }

            // 2.2)
            final List<String> containers = new ArrayList<String>();
            // 2.3)
            String typeLanguage = "@language";
            String typeLanguageValue = "@null";

            // 2.4)
            if (value instanceof Map && ((Map<String, Object>) value).containsKey("@index")) {
                containers.add("@index");
            }

            // 2.5)
            if (reverse) {
                typeLanguage = "@type";
                typeLanguageValue = "@reverse";
                containers.add("@set");
            }
            // 2.6)
            else if (JSONLDUtils.isList(value)) {
                // 2.6.1)
                if (!((Map<String, Object>) value).containsKey("@index")) {
                    containers.add("@list");
                }
                // 2.6.2)
                final List<Object> list = (List<Object>) ((Map<String, Object>) value).get("@list");
                // 2.6.3)
                String commonLanguage = (list.size() == 0) ? defaultLanguage : null;
                String commonType = null;
                // 2.6.4)
                for (final Object item : list) {
                	// 2.6.4.1)
                    String itemLanguage = "@none";
                    String itemType = "@none";
                    // 2.6.4.2)
                    if (JSONLDUtils.isValue(item)) {
                    	// 2.6.4.2.1)
                        if (((Map<String, Object>) item).containsKey("@language")) {
                            itemLanguage = (String) ((Map<String, Object>) item).get("@language");
                        } 
                        // 2.6.4.2.2)
                        else if (((Map<String, Object>) item).containsKey("@type")) {
                            itemType = (String) ((Map<String, Object>) item).get("@type");
                        }
                        // 2.6.4.2.3)
                        else {
                            itemLanguage = "@null";
                        }
                    }
                    // 2.6.4.3)
                    else {
                        itemType = "@id";
                    }
                    // 2.6.4.4)
                    if (commonLanguage == null) {
                        commonLanguage = itemLanguage;
                    } 
                    // 2.6.4.5)
                    else if (!commonLanguage.equals(itemLanguage) && JSONLDUtils.isValue(item)) {
                        commonLanguage = "@none";
                    }
                    // 2.6.4.6)
                    if (commonType == null) {
                        commonType = itemType;
                    }
                    // 2.6.4.7)
                    else if (!commonType.equals(itemType)) {
                        commonType = "@none";
                    }
                    // 2.6.4.8)
                    if ("@none".equals(commonLanguage) && "@none".equals(commonType)) {
                        break;
                    }
                }
                // 2.6.5)
                commonLanguage = (commonLanguage != null) ? commonLanguage : "@none";
                // 2.6.6)
                commonType = (commonType != null) ? commonType : "@none";
                // 2.6.7)
                if (!"@none".equals(commonType)) {
                    typeLanguage = "@type";
                    typeLanguageValue = commonType;
                }
                // 2.6.8)
                else {
                    typeLanguageValue = commonLanguage;
                }
            }
            // 2.7)
            else {
            	// 2.7.1)
                if (JSONLDUtils.isValue(value)) {
                	// 2.7.1.1)
                    if (((Map<String, Object>) value).containsKey("@language")
                            && !((Map<String, Object>) value).containsKey("@index")) {
                        containers.add("@language");
                        typeLanguageValue = (String) ((Map<String, Object>) value)
                                .get("@language");
                    }
                    // 2.7.1.2)
                    else if (((Map<String, Object>) value).containsKey("@type")) {
                        typeLanguage = "@type";
                        typeLanguageValue = (String) ((Map<String, Object>) value).get("@type");
                    }
                }
                // 2.7.2)
                else {
                    typeLanguage = "@type";
                    typeLanguageValue = "@id";
                }
                // 2.7.3)
                containers.add("@set");
            }

            // 2.8)
            containers.add("@none");
            // 2.9)
            if (typeLanguageValue == null) {
            	typeLanguageValue = "@null";
            }
            // 2.10)
            List<String> preferredValues = new ArrayList<String>();
            // 2.11)
            if ("@reverse".equals(typeLanguageValue)) {
            	preferredValues.add("@reverse");
            }
            // 2.12)
            if (("@reverse".equals(typeLanguageValue) || "@id".equals(typeLanguageValue)) && 
            		((Map<String,Object>)value).containsKey("@id")) {
            	// 2.12.1)
            	String result = this.compactIri((String)((Map<String,Object>)value).get("@id"), null, true, true);
            	if (termDefinitions.containsKey(result) && 
            			((Map<String,Object>) termDefinitions.get(result)).containsKey("@id") && 
            			((Map<String, Object>) value).get("@id").equals(
            					((Map<String, Object>) termDefinitions.get(result)).get("@id"))) {
            		preferredValues.add("@vocab");
            		preferredValues.add("@id");
            	}
            	// 2.12.2)
            	else {
            		preferredValues.add("@id");
            		preferredValues.add("@vocab");
            	}
            }
            // 2.13)
            else {
            	preferredValues.add(typeLanguageValue);
            }
            preferredValues.add("@none");
            
            // 2.14)
            String term = selectTerm(iri, containers, typeLanguage, preferredValues);
            // 2.15)
            if (term != null) {
            	return term;
            }
        }

        // 3)
        if (relativeToVocab && this.containsKey("@vocab")) {
            // determine if vocab is a prefix of the iri
            final String vocab = (String) this.get("@vocab");
            // 3.1)
            if (iri.indexOf(vocab) == 0 && !iri.equals(vocab)) {
                // use suffix as relative iri if it is not a term in the
                // active context
                final String suffix = iri.substring(vocab.length());
                if (!termDefinitions.containsKey(suffix)) {
                    return suffix;
                }
            }
        }

        // 4)
        String compactIRI = null;
        // 5)
        for (final String term : termDefinitions.keySet()) {
        	final Map<String,Object> termDefinition = (Map<String,Object>) termDefinitions.get(term);
            // 5.1)
            if (term.contains(":")) {
                continue;
            }
            // 5.2)
            if (termDefinition == null || iri.equals(termDefinition.get("@id"))
                    || !iri.startsWith((String) termDefinition.get("@id"))) {
                continue;
            }

            // 5.3)
            final String candidate = term + ":" + iri.substring(((String) termDefinition.get("@id")).length());
            // 5.4)
            if ((compactIRI == null || compareShortestLeast(candidate, compactIRI) < 0) && 
            	(!termDefinition.containsKey(candidate) || iri.equals(((Map<String,Object>) termDefinitions.get(candidate)).get("@id")))) {
            	compactIRI = candidate;
            }
            
        }

        // 6)
        if (compactIRI != null) {
            return compactIRI;
        }

        // 7)
        if (!relativeToVocab) {
            return removeBase(iri);
        }

        // 8)
        return iri;
    }

    private String removeBase(String iri) {
		try {
			return ((new URI((String)this.get("@base"))).relativize(new URI(iri))).toString();
		} catch (URISyntaxException e) {
			// TODO: should this be the case
			return iri;
		}
	}

	String compactIri(String iri) {
        return compactIri(iri, null, false, false);
    }

        
    @Override
    public Context clone() {
        Context rval = (Context) super.clone();
        // TODO: should we clone all the termDefinitions/inverse etc as well?
        return rval;
    }
    
    /**
     * Inverse Context Creation
     * 
     * http://json-ld.org/spec/latest/json-ld-api/#inverse-context-creation
     * 
     * Generates an inverse context for use in the compaction algorithm, if not
     * already generated for the given active context.
     * 
     * @return the inverse context.
     */
    public Map<String, Object> getInverse() {

        // lazily create inverse
        if (inverse != null) {
            return inverse;
        }

        // 1)
        inverse = new LinkedHashMap<String, Object>();

        // 2)
        String defaultLanguage = (String) this.get("@language");
        if (defaultLanguage == null) {
            defaultLanguage = "@none";
        }
        
        // create term selections for each mapping in the context, ordererd by
        // shortest and then lexicographically least
        final List<String> terms = new ArrayList<String>(termDefinitions.keySet());
        Collections.sort(terms, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return compareShortestLeast(a, b);
            }
        });

        for (final String term : terms) {
            final Map<String, Object> definition = (Map<String, Object>) termDefinitions.get(term);
            // 3.1)
            if (termDefinitions.containsKey(term) && definition == null) {
                continue;
            }

            // 3.2)
            String container = (String) definition.get("@container");
            if (container == null) {
                container = "@none";
            }

            // 3.3)
            String iri = (String) definition.get("@id");
            
            // 3.4 + 3.5)
            Map<String, Object> containerMap = (Map<String, Object>) inverse.get(iri);
            if (containerMap == null) {
                containerMap = new LinkedHashMap<String, Object>();
                inverse.put(iri, containerMap);
            }
            
            // 3.6 + 3.7)
            Map<String,Object> typeLanguageMap = (Map<String, Object>) containerMap.get(container);
            if (typeLanguageMap == null) {
            	typeLanguageMap = new LinkedHashMap<String,Object>();
            	typeLanguageMap.put("@language", new LinkedHashMap<String, Object>());
            	typeLanguageMap.put("@type", new LinkedHashMap<String, Object>());
            }
            
            // 3.8)
            if (Boolean.TRUE.equals(definition.get("@reverse"))) {
            	Map<String,Object> typeMap = (Map<String, Object>) typeLanguageMap.get("@type");
            	if (!typeMap.containsKey("@reverse")) {
            		typeMap.put("@reverse", term);
            	}
            // 3.9)
            } else if (definition.containsKey("@type")) {
            	Map<String,Object> typeMap = (Map<String, Object>) typeLanguageMap.get("@type");
            	if (!typeMap.containsKey(definition.get("@type"))) {
            		typeMap.put((String)definition.get("@type"), term);
            	}
            // 3.10)
            } else if (definition.containsKey("@language")) {
            	Map<String,Object> languageMap = (Map<String, Object>) typeLanguageMap.get("@language");
            	String language = (String) definition.get("@language");
            	if (language == null) {
            		language = "@null";
            	}
            	if (!languageMap.containsKey(language)) {
            		languageMap.put(language, term);
            	}
            // 3.11)
            } else {
            	// 3.11.1)
            	Map<String,Object> languageMap = (Map<String, Object>) typeLanguageMap.get("@language");
            	// 3.11.2)
            	if (!languageMap.containsKey("@language")) {
            		languageMap.put("@language", term);
            	}
            	// 3.11.3)
            	if (!languageMap.containsKey("@none")) {
            		languageMap.put("@none", term);
            	}
            	// 3.11.4)
            	Map<String,Object> typeMap = (Map<String, Object>) typeLanguageMap.get("@type");
            	// 3.11.5)
            	if (!typeMap.containsKey("@none")) {
            		typeMap.put("@none", term);
            	}
            }
        }
        // 4)
        return inverse;
    }
    
    
    /**
     * Term Selection
     * 
     * http://json-ld.org/spec/latest/json-ld-api/#term-selection
     * 
     * This algorithm, invoked via the IRI Compaction algorithm, makes use of an active context's inverse context to find 
     * the term that is best used to compact an IRI. Other information about a value associated with the IRI is given, 
     * including which container mappings and which type mapping or language mapping would be best used to express the value.
     * 
     * @return the selected term.
     */
    private String selectTerm(String iri, List<String> containers, String typeLanguage, 
    		List<String> preferredValues) {
    	Map<String, Object> inv = getInverse();
    	// 1)
    	Map<String, Object> containerMap = (Map<String, Object>) inv.get(iri); 
    	// 2)
    	for (final String container : containers) {
    		// 2.1)
            if (!containerMap.containsKey(container)) {
                continue;
            }
            // 2.2)
            final Map<String, Object> typeLanguageMap = (Map<String, Object>) containerMap.get(container);
            // 2.3)
            final Map<String, Object> valueMap = (Map<String, Object>) typeLanguageMap.get(typeLanguage);
            // 2.4 )
            for (final String item : preferredValues) {
                // 2.4.1
                if (!valueMap.containsKey(item)) {
                    continue;
                }
                // 2.4.2
                return (String) typeLanguageMap.get(item);
            }
        }
    	// 3)
        return null;
    }

    /**
     * Retrieve container mapping.
     * 
     * @param property
     * @return
     */
	public String getContainer(String property) {
		if ("@graph".equals(property)) {
			return "@set";
		}
		if (JSONLDUtils.isKeyword(property)) {
			return property;
		}
		return (String) ((Map<String,Object>)termDefinitions.get(property)).get("@container");
	}

	
	Map<String, Object> getTermDefinition(String key) {
		return ((Map<String,Object>)termDefinitions.get(key));
	}

	public Object expandValue(String activeProperty, Object value) throws JsonLdError {
		Map<String,Object> rval = new LinkedHashMap<String, Object>();
		Map<String,Object> td = getTermDefinition(activeProperty);
		// 1)
		if ("@id".equals(td.get("@type"))) {
			// TODO: i'm pretty sure value should be a string if the @type is @id
			rval.put("@id", expandIri(value.toString(), true, false, null, null));
			return rval;
		}
		// 2)
		if ("@vocab".equals(td.get("@type"))) {
			// TODO: same as above
			rval.put("@id", expandIri(value.toString(), true, true, null, null));
			return rval;
		}
		rval.put("@value", value);
		if (td.containsKey("@type")) {
			rval.put("@type", td.get("@type"));
		} else if (value instanceof String) {
			if (td.get("@language") != null) {
				rval.put("@language", td.get("@language"));
			} else if (this.get("@language") != null) {
				rval.put("@language", this.get("@language"));
			}
		}
		return rval;
	}
}
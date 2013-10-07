package com.github.jsonldjava.core;

import static com.github.jsonldjava.core.JSONLDUtils.isKeyword;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jsonldjava.core.JsonLdError.Error;

public class JsonLdApi {

    private static final Logger LOG = LoggerFactory.getLogger(JsonLdApi.class);

    JsonLdOptions opts;

    public JsonLdApi() {
        opts = new JsonLdOptions("");
    }

    public JsonLdApi(JsonLdOptions opts) {
        if (opts == null) {
            opts = new JsonLdOptions("");
        } else {
            this.opts = opts;
        }
    }

    /**
     * Expansion Algorithm
     * 
     * http://json-ld.org/spec/latest/json-ld-api/#expansion-algorithm
     * 
     * @return the expanded value.
     */
    public Object expand(Context activeCtx, String activeProperty, Object element) throws JsonLdError {
        // 1)
        if (element == null) {
            return null;
        }
        
        // 3)
        if (element instanceof List) {
        	// 3.1)
            final List<Object> result = new ArrayList<Object>();
            // 3.2)
            for (final Object item : (List<Object>) element) {
                // 3.2.1)
                final Object v = expand(activeCtx, activeProperty, item);
                // 3.2.2)
                if (("@list".equals(activeProperty) || "@list".equals(activeCtx.getContainer(activeProperty))) && 
                	(v instanceof List || (v instanceof Map && ((Map<String,Object>) v).containsKey("@list")))) {
                	throw new JsonLdError(Error.LIST_OF_LISTS, "lists of lists are not permitted.");
                }
                // 3.2.3)
                else if (v != null) {
                    if (v instanceof List) {
                        result.addAll((Collection<? extends Object>) v);
                    } else {
                        result.add(v);
                    }
                }
            }
            // 3.3)
            return result;
        }
        // 4)
        else if (element instanceof Map) {
        	// access helper
            final Map<String, Object> elem = (Map<String, Object>) element;
            // 5)
            if (elem.containsKey("@context")) {
            	activeCtx = activeCtx.parse(elem.get("@context"));
            }
            // 6)
            Map<String,Object> result = new LinkedHashMap<String, Object>();
            // 7)
            final List<String> keys = new ArrayList<String>(elem.keySet());
            Collections.sort(keys);
            for (final String key : keys) {
            	final Object value = elem.get(key);
            	// 7.1)
            	if (key.equals("@context")) {
                    continue;
                }
            	// 7.2)
            	final String expandedProperty = activeCtx.expandIri(key, false, true, null, null);
            	Object expandedValue = null;
                // 7.3)
            	if (expandedProperty == null || (!expandedProperty.contains(":") && !isKeyword(expandedProperty))) {
            		continue;
            	}
            	// 7.4)
            	if (isKeyword(expandedProperty)) {
            		// 7.4.1)
            		if ("@reverse".equals(activeProperty)) {
            			throw new JsonLdError(Error.INVALID_REVERSE_PROPERY_MAP, "a keyword cannot be used as a @reverse propery");
            		}
            		// 7.4.2)
            		if (result.containsKey(expandedProperty)) {
            			throw new JsonLdError(Error.COLLIDING_KEYWORDS, expandedProperty + " already exists in result");
            		}
            		// 7.4.3)
            		if ("@id".equals(expandedProperty)) {
            			if (!(value instanceof String)) {
            				throw new JsonLdError(Error.INVALID_ID_VALUE, "value of @id must be a string");
            			}
            			expandedValue = activeCtx.expandIri((String)value, true, false, null, null);
            		}
            		// 7.4.4)
            		else if ("@type".equals(expandedProperty)) {
            			if (value instanceof List) {
            				expandedValue = new ArrayList<String>();
            				for (Object v : (List)value) {
            					if (!(v instanceof String)) {
            						throw new JsonLdError(Error.INVALID_TYPE_VALUE, "@type value must be a string or array of strings");
            					}
            					((List<String>) expandedValue).add(
            						activeCtx.expandIri((String)v, true, true, null, null)
            					);
            				}
            			} else if (value instanceof String) {
            				expandedValue = activeCtx.expandIri((String)value, true, true, null, null);
            			} else {
            				throw new JsonLdError(Error.INVALID_TYPE_VALUE, "@type value must be a string or array of strings");
            			}
            		}
            		// 7.4.5)
            		else if ("@graph".equals(expandedProperty)) {
            			expandedValue = expand(activeCtx, "@graph", value);
            		}
            		// 7.4.6)
            		else if ("@value".equals(expandedProperty)) {
            			if (value != null && (value instanceof Map || value instanceof List)) {
            				throw new JsonLdError(Error.INVALID_VALUE_OBJECT_VALUE, "value of " + expandedProperty + " must be a scalar or null");
            			}
            			expandedValue = value;
            			if (expandedValue == null) {
            				result.put("@value", null);
            				continue;
            			}
            		}
            		// 7.4.7)
            		else if ("@language".equals(expandedProperty)) {
            			if (!(value instanceof String)) {
            				throw new JsonLdError(Error.INVALID_LANGUAGE_TAGGED_STRING, "Value of " + expandedProperty + " must be a string");
            			}
            			expandedValue = ((String)value).toLowerCase();
            		}
            		// 7.4.8)
            		else if ("@index".equals(expandedProperty)) {
            			if (!(value instanceof String)) {
            				throw new JsonLdError(Error.INVALID_INDEX_VALUE, "Value of " + expandedProperty + " must be a string");
            			}
            			expandedValue = value;
            		}
            		// 7.4.9)
            		else if ("@list".equals(expandedProperty)) {
            			// 7.4.9.1)
            			if (activeProperty == null || "@graph".equals(activeProperty)) {
            				continue;
            			}
            			// 7.4.9.2)
            			expandedValue = expand(activeCtx, activeProperty, value);
            			
            			// NOTE: step not in the spec yet
            			if (!(expandedValue instanceof List)) {
            				List<Object> tmp = new ArrayList<Object>();
            				tmp.add(expandedValue);
            				expandedValue = tmp;
            			}
            			
            			// 7.4.9.3)
            			for (Object o : (List<Object>)expandedValue) {
            				if (o instanceof Map && ((Map<String,Object>) o).containsKey("@list")) {
            					throw new JsonLdError(Error.LIST_OF_LISTS, "A list may not contain another list");
            				}
            			}
            		}
            		// 7.4.10)
            		else if ("@set".equals(expandedProperty)) {
            			expandedValue = expand(activeCtx, activeProperty, value);
            		}
            		// 7.4.11)
            		else if ("@reverse".equals(expandedProperty)) {
            			if (!(value instanceof Map)) {
            				throw new JsonLdError(Error.INVALID_REVERSE_VALUE, "@reverse value must be an object");
            			}
            			// 7.4.11.1)
            			expandedValue = expand(activeCtx, "@reverse", value);
            			// NOTE: algorithm assumes the result is a map
            			// 7.4.11.2)
            			if (((Map<String,Object>) expandedValue).containsKey("@reverse")) {
            				Map<String,Object> reverse = (Map<String, Object>) ((Map<String,Object>) expandedValue).get("@reverse");
            				for (String property : reverse.keySet()) {
            					Object item = reverse.get(property);
            					// 7.4.11.2.1)
            					if (!result.containsKey(property)) {
            						result.put(property, new ArrayList<Object>());
            					}
            					// 7.4.11.2.2)
            					if (item instanceof List) {
            						((List<Object>) result.get(property)).addAll((List<Object>)item);
            					} else {
            						((List<Object>) result.get(property)).add(item);
            					}
            				}
            			}
            			// 7.4.11.3)
            			if (((Map<String,Object>) expandedValue).size() > (((Map<String,Object>) expandedValue).containsKey("@reverse") ? 1 : 0)) {
            				// 7.4.11.3.1)
            				if (!result.containsKey("@reverse")) {
            					result.put("@reverse", new LinkedHashMap<String, Object>());
            				}
            				// 7.4.11.3.2)
            				Map<String,Object> reverseMap = (Map<String, Object>) result.get("@reverse");
            				// 7.4.11.3.3)
            				for (String property : ((Map<String,Object>)expandedValue).keySet()) {
            					if ("@reverse".equals(property)) {
            						continue;
            					}
            					// 7.4.11.3.3.1)
            					List<Object> items = (List<Object>) ((Map<String,Object>) expandedValue).get(property);
            					for (Object item : items) {
                					// 7.4.11.3.3.1.1)
            						if (item instanceof Map && (((Map<String,Object>) item).containsKey("@value") || ((Map<String,Object>) item).containsKey("@list"))) {
            							throw new JsonLdError(Error.INVALID_REVERSE_PROPERTY_VALUE, "");
            						}
            						// 7.4.11.3.3.1.2)
            						if (!reverseMap.containsKey(property)) {
            							reverseMap.put(property, new ArrayList<Object>());
            						}
            						// 7.4.11.3.3.1.3)
            						((List<Object>) reverseMap.get(property)).add(item);
            					}
            				}
            			}
            			// 7.4.11.4)
            			continue;
            		}
            		// 7.4.12)
            		if (expandedValue != null) {
            			result.put(expandedProperty, expandedValue);
            		}
            		// 7.4.13)
            		continue;
            	}
            	// 7.5
            	else if ("@language".equals(activeCtx.getContainer(key)) && value instanceof Map) {
            		// 7.5.1)
            		expandedValue = new ArrayList<Object>();
            		// 7.5.2)
            		for (final String language : ((Map<String,Object>) value).keySet()) {
            			Object languageValue = ((Map<String,Object>) value).get(language);
            			// 7.5.2.1)
            			if (!(languageValue instanceof List)) {
            				Object tmp = languageValue;
            				languageValue = new ArrayList<Object>();
            				((List<Object>) languageValue).add(tmp);
            			}
            			// 7.5.2.2)
            			for (Object item : (List<Object>)languageValue) {
            				// 7.5.2.2.1)
            				if (!(item instanceof String)) {
            					throw new JsonLdError(Error.INVALID_LANGUAGE_MAP_VALUE, "Expected " + item.toString() + " to be a string");
            				}
            				// 7.5.2.2.2)
            				Map<String,Object> tmp = new LinkedHashMap<String, Object>();
            				tmp.put("@value", item);
            				tmp.put("@language", language.toLowerCase());
            				((List<Object>) expandedValue).add(tmp);
            			}
            		}
            	}
            	// 7.6)
            	else if ("@index".equals(activeCtx.getContainer(key)) && value instanceof Map) {
            		// 7.6.1)
            		expandedValue = new ArrayList<Object>();
            		// 7.6.2)
            		final List<String> indexKeys = new ArrayList<String>(((Map<String,Object>) value).keySet());
                    Collections.sort(indexKeys);
            		for (final String index : indexKeys) {
            			Object indexValue = ((Map<String,Object>) value).get(index);
            			// 7.6.2.1)
            			if (!(indexValue instanceof List)) {
            				Object tmp = indexValue;
            				indexValue = new ArrayList<Object>();
            				((List<Object>) indexValue).add(tmp);
            			}
            			// 7.6.2.2)
            			indexValue = expand(activeCtx, key, indexValue);
            			// 7.6.2.3)
            			for (Map<String,Object> item : (List<Map<String,Object>>)indexValue) {
            				// 7.6.2.3.1)
            				if (!item.containsKey("@index")) {
            					item.put("@index", index);
            				}
            				// 7.6.2.3.2)
            				((List<Object>) expandedValue).add(item);
            			}
            		}
            	}
            	// 7.7)
            	else {
            		expandedValue = expand(activeCtx, key, value);
            	}
            	// 7.8)
            	if (expandedValue == null) {
            		continue;
            	}
            	// 7.9)
            	if ("@list".equals(activeCtx.getContainer(key))) {
            		if (!(expandedValue instanceof Map) || !((Map<String,Object>) expandedValue).containsKey("@list")) {
            			Object tmp = expandedValue;
            			if (!(tmp instanceof List)) {
            				tmp = new ArrayList<Object>();
            				((List<Object>) tmp).add(expandedValue);
            			}
            			expandedValue = new LinkedHashMap<String,Object>();
            			((Map<String,Object>) expandedValue).put("@list", tmp);
            		}
            	}
            	// 7.10)
            	if (activeCtx.isReverseProperty(key)) {
            		// 7.10.1)
            		if (!result.containsKey("@reverse")) {
            			result.put("@reverse", new LinkedHashMap<String, Object>());
            		}
            		// 7.10.2)
            		Map<String,Object> reverseMap = (Map<String, Object>) result.get("@reverse");
            		// 7.10.3)
            		if (!(expandedValue instanceof List)) {
            			Object tmp = expandedValue;
            			expandedValue = new ArrayList<Object>();
            			((List<Object>) expandedValue).add(tmp);
            		}
            		// 7.10.4)
            		for (Object item : (List<Object>)expandedValue) {
            			// 7.10.4.1)
            			if (item instanceof Map && (((Map<String,Object>) item).containsKey("@value") || ((Map<String,Object>) item).containsKey("@list"))) {
            				throw new JsonLdError(Error.INVALID_REVERSE_PROPERTY_VALUE, "");
            			}
            			// 7.10.4.2)
            			if (!reverseMap.containsKey(expandedProperty)) {
            				reverseMap.put(expandedProperty, new ArrayList<Object>());
            			}
            			// 7.10.4.3)
            			if (item instanceof List) {
            				((List<Object>) reverseMap.get(expandedProperty)).addAll((List<Object>)item);
            			} else {
            				((List<Object>) reverseMap.get(expandedProperty)).add(item);
            			}
            		}
            	}
            	// 7.11)
            	else {
            		// 7.11.1)
        			if (!result.containsKey(expandedProperty)) {
        				result.put(expandedProperty, new ArrayList<Object>());
        			}
        			// 7.11.2)
        			if (expandedValue instanceof List) {
        				((List<Object>) result.get(expandedProperty)).addAll((List<Object>)expandedValue);
        			} else {
        				((List<Object>) result.get(expandedProperty)).add(expandedValue);
        			}
            	}
            }
            // 8)
            if (result.containsKey("@value")) {
            	// 8.1)
            	// TODO: is this method faster than just using containsKey for each?
            	Set<String> keySet = new HashSet(result.keySet());
            	keySet.remove("@value");
            	keySet.remove("@index");
            	boolean langremoved = keySet.remove("@language");
            	boolean typeremoved = keySet.remove("@type");
            	if ((langremoved && typeremoved) || !keySet.isEmpty()) {
            		throw new JsonLdError(Error.INVALID_VALUE_OBJECT, "value object has unknown keys");
            	}
            	// 8.2)
            	Object rval = result.get("@value");
            	if (rval == null) {
            		// nothing else is possible with result if we set it to null, so simply return it
            		return null;
            	}
            	// 8.3)
            	else if (!(rval instanceof String) && result.containsKey("@language")) {
            		throw new JsonLdError(Error.INVALID_LANGUAGE_TAGGED_VALUE, "when @language is used, @value must be a string");
            	}
            	// 8.4)
            	else if (result.containsKey("@type")) {
            		// TODO: is this enough for "is an IRI"
            		if (!(result.get("@type") instanceof String) || !((String)result.get("@type")).contains(":")) {
            			throw new JsonLdError(Error.INVALID_TYPED_VALUE, "value of @type must be an IRI");
            		}
            	}
            }
            // 9)
            else if (result.containsKey("@type")) {
            	Object rtype = result.get("@type");
            	if (!(rtype instanceof List)) {
            		List<Object> tmp = new ArrayList<Object>();
            		tmp.add(rtype);
            		result.put("@type", tmp);
            	}
            }
            // 10)
            else if (result.containsKey("@set") || result.containsKey("@list")) {
            	// 10.1)
            	if (result.size() > (result.containsKey("@index") ? 2 : 1)) {
            		throw new JsonLdError(Error.INVALID_SET_OR_LIST_OBJECT, "@set or @list may only contain @index");
            	}
            	// 10.2)
            	if (result.containsKey("@set")) {
            		// result becomes an array here, thus the remaining checks will never be true from here on
            		// so simply return the value rather than have to make result an object and cast it with every
            		// other use in the function.
            		return result.get("@set");
            	}
            }
            // 11)
            if (result.containsKey("@language") && result.size() == 1) {
            	result = null;
            }
            // 12)
            if (activeProperty == null || "@graph".equals(activeProperty)) {
            	// 12.1)
            	if (result != null && (result.size() == 0 || result.containsKey("@value") || result.containsKey("@list"))) {
            		result = null;
            	}
            	// 12.2)
            	else if (result != null && result.containsKey("@id") && result.size() == 1) {
            		result = null;
            	}
            }
            // 13)
            return result;
        }
        // 2) If element is a scalar
        else {
        	// 2.1)
        	if (activeProperty == null || "@graph".equals(activeProperty)) {
        		return null;
        	}
        	return activeCtx.expandValue(activeProperty, element);
        }
    }
    
    public Object expand(Context activeCtx, Object element) throws JsonLdError {
    	return expand(activeCtx, null, element);
    }
}

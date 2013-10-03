package com.github.jsonldjava.core;

import static com.github.jsonldjava.core.JSONLDConsts.RDF_FIRST;
import static com.github.jsonldjava.core.JSONLDConsts.RDF_NIL;
import static com.github.jsonldjava.core.JSONLDConsts.RDF_REST;
import static com.github.jsonldjava.core.JSONLDConsts.RDF_TYPE;
import static com.github.jsonldjava.core.JSONLDUtils.addValue;
import static com.github.jsonldjava.core.JSONLDUtils.compactIri;
import static com.github.jsonldjava.core.JSONLDUtils.compactValue;
import static com.github.jsonldjava.core.JSONLDUtils.compareValues;
import static com.github.jsonldjava.core.JSONLDUtils.createNodeMap;
import static com.github.jsonldjava.core.JSONLDUtils.createTermDefinition;
import static com.github.jsonldjava.core.JSONLDUtils.expandIri;
import static com.github.jsonldjava.core.JSONLDUtils.expandLanguageMap;
import static com.github.jsonldjava.core.JSONLDUtils.expandValue;
import static com.github.jsonldjava.core.JSONLDUtils.hasValue;
import static com.github.jsonldjava.core.JSONLDUtils.isAbsoluteIri;
import static com.github.jsonldjava.core.JSONLDUtils.isArray;
import static com.github.jsonldjava.core.JSONLDUtils.isKeyword;
import static com.github.jsonldjava.core.JSONLDUtils.isList;
import static com.github.jsonldjava.core.JSONLDUtils.isObject;
import static com.github.jsonldjava.core.JSONLDUtils.isString;
import static com.github.jsonldjava.core.JSONLDUtils.isSubjectReference;
import static com.github.jsonldjava.core.JSONLDUtils.isValue;
import static com.github.jsonldjava.core.JSONLDUtils.removeValue;
import static com.github.jsonldjava.core.JSONLDUtils.validateTypeValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jsonldjava.core.JsonLdError.Error;
import com.github.jsonldjava.utils.JSONUtils;
import com.github.jsonldjava.utils.Obj;
import com.github.jsonldjava.utils.URL;

public class JSONLDProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(JSONLDProcessor.class);

    Options opts;

    public JSONLDProcessor() {
        opts = new Options("");
    }

    public JSONLDProcessor(Options opts) {
        if (opts == null) {
            opts = new Options("");
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
            	Object expandedValue;
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
            			expandedValue = activeCtx.expandIri((String)value, true, true, null, null);
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
            			// 7.4.9.3)
            			if (expandedValue instanceof Map && ((Map<String,Object>) expandedValue).containsKey("@list")) {
            				throw new JsonLdError(Error.LIST_OF_LISTS, "A list may not contain another list");
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
            					((List<Object>) result.get(property)).add(item);
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
            	if (activeCtx.getTermDefinition(key).containsKey("@reverse")) {
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
            			((List<Object>) reverseMap.get(expandedProperty)).add(item);
            		}
            	}
            	// 7.11)
            	else if (!Boolean.TRUE.equals(activeCtx.getTermDefinition(key).get("@reverse"))) {
            		// 7.11.1)
        			if (!result.containsKey(expandedProperty)) {
        				result.put(expandedProperty, new ArrayList<Object>());
        			}
        			// 7.11.2)
        			((List<Object>) result.get(expandedProperty)).add(expandedValue);
            	}
            }
            // 8)
            if (result.containsKey("@value")) {
            	// 8.1)
            	Set<String> keySet = result.keySet();
            	keySet.remove("@value");
            	keySet.remove("@index");
            	if ((keySet.remove("@language") && keySet.remove("@type")) || !keySet.isEmpty()) {
            		throw new JsonLdError(Error.INVALID_VALUE_OBJECT, "value object has unknown keys");
            	}
            	// 8.2)
            	Object rval = result.get("@value");
            	if (rval == null) {
            		result = null;
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

    /**
     * Recursively compacts an element using the given active context. All
     * values must be in expanded form before this method is called.
     * 
     * @param activeCtx
     *            the active context to use.
     * @param activeProperty
     *            the compacted property associated with the element to compact,
     *            null for none.
     * @param element
     *            the element to compact.
     * @param options
     *            the compaction options.
     * 
     * @return the compacted value.
     */
    public Object compact(Context activeCtx, String activeProperty, Object element)
            throws JsonLdError {

        // recursively compact array
        if (isArray(element)) {
            final List<Object> rval = new ArrayList<Object>();
            for (final Object i : (List<Object>) element) {
                // compact, dropping any null values
                final Object compacted = compact(activeCtx, activeProperty, i);
                if (compacted != null) {
                    rval.add(compacted);
                }
            }
            if (opts.compactArrays && rval.size() == 1) {
                // use single element if no container is specified
                final Object container = activeCtx.getContextValue(activeProperty, "@container");
                if (container == null) {
                    return rval.get(0);
                }
            }
            return rval;
        }

        // recursively compact object
        if (isObject(element)) {
            // access helper
            final Map<String, Object> elem = (Map<String, Object>) element;

            // do value compaction on @value and subject references
            if (isValue(element) || isSubjectReference(element)) {
                return compactValue(activeCtx, activeProperty, element);
            }

            // FIXME: avoid misuse of active property as an expanded property?
            final boolean insideReverse = ("@reverse".equals(activeProperty));

            // process element keys in order
            final List<String> keys = new ArrayList<String>(elem.keySet());
            Collections.sort(keys);
            final Map<String, Object> rval = new LinkedHashMap<String, Object>();
            for (final String expandedProperty : keys) {
                final Object expandedValue = elem.get(expandedProperty);

                /*
                 * TODO: // handle ignored keys if (opts.isIgnored(key)) {
                 * //JSONLDUtils.addValue(rval, key, value, false);
                 * rval.put(key, value); continue; }
                 */

                // compact @id and @type(s)
                if ("@id".equals(expandedProperty) || "@type".equals(expandedProperty)) {
                    Object compactedValue;

                    // compact single @id
                    if (isString(expandedValue)) {
                        compactedValue = compactIri(activeCtx, (String) expandedValue, null,
                                "@type".equals(expandedProperty), false);
                    }
                    // expanded value must be a @type array
                    else {
                        final List<String> types = new ArrayList<String>();
                        for (final String i : (List<String>) expandedValue) {
                            types.add(compactIri(activeCtx, i, null, true, false));
                        }
                        compactedValue = types;
                    }

                    // use keyword alias and add value
                    final String alias = compactIri(activeCtx, expandedProperty);
                    addValue(rval, alias, compactedValue, isArray(compactedValue)
                            && ((List<Object>) expandedValue).size() == 0);
                    continue;
                }

                // handle @reverse
                if ("@reverse".equals(expandedProperty)) {
                    // recursively compact expanded value
                    // TODO: i'm assuming this will always be a map due to the
                    // rest of the code
                    final Map<String, Object> compactedValue = (Map<String, Object>) compact(
                            activeCtx, "@reverse", expandedValue);

                    // handle double-reversed properties
                    for (final String compactedProperty : compactedValue.keySet()) {

                        if (Boolean.TRUE.equals(Obj.get(activeCtx.mappings, compactedProperty,
                                "reverse"))) {
                            if (!rval.containsKey(compactedProperty) && !opts.compactArrays) {
                                rval.put(compactedProperty, new ArrayList<Object>());
                            }
                            addValue(rval, compactedProperty, compactedValue.get(compactedProperty));
                            compactedValue.remove(compactedProperty);
                        }
                    }

                    if (compactedValue.size() > 0) {
                        // use keyword alias and add value
                        addValue(rval, compactIri(activeCtx, expandedProperty), compactedValue);
                    }

                    continue;
                }

                // handle @index property
                if ("@index".equals(expandedProperty)) {
                    // drop @index if inside an @index container
                    final String container = (String) activeCtx.getContextValue(activeProperty,
                            "@container");
                    if ("@index".equals(container)) {
                        continue;
                    }

                    // use keyword alias and add value
                    addValue(rval, compactIri(activeCtx, expandedProperty), expandedValue);
                    continue;
                }

                // NOTE: expanded value must be an array due to expansion
                // algorithm.

                // preserve empty arrays
                if (((List<Object>) expandedValue).size() == 0) {
                    addValue(
                            rval,
                            compactIri(activeCtx, expandedProperty, expandedValue, true,
                                    insideReverse), expandedValue, true);
                }

                // recusively process array values
                for (final Object expandedItem : (List<Object>) expandedValue) {
                    // compact property and get container type
                    final String itemActiveProperty = compactIri(activeCtx, expandedProperty,
                            expandedItem, true, insideReverse);
                    final String container = (String) activeCtx.getContextValue(itemActiveProperty,
                            "@container");

                    // get @list value if appropriate
                    final boolean isList = isList(expandedItem);
                    Object list = null;
                    if (isList) {
                        list = ((Map<String, Object>) expandedItem).get("@list");
                    }

                    // recursively compact expanded item
                    Object compactedItem = compact(activeCtx, itemActiveProperty, isList ? list
                            : expandedItem);

                    // handle @list
                    if (isList) {
                        // ensure @list value is an array
                        if (!isArray(compactedItem)) {
                            final List<Object> tmp = new ArrayList<Object>();
                            tmp.add(compactedItem);
                            compactedItem = tmp;
                        }

                        if (!"@list".equals(container)) {
                            // wrap using @list alias
                            final Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
                            wrapper.put(compactIri(activeCtx, "@list"), compactedItem);
                            compactedItem = wrapper;

                            // include @index from expanded @list, if any
                            if (((Map<String, Object>) expandedItem).containsKey("@index")) {
                                ((Map<String, Object>) compactedItem).put(
                                        compactIri(activeCtx, "@index"),
                                        ((Map<String, Object>) expandedItem).get("@index"));
                            }
                        }
                        // can't use @list container for more than 1 list
                        else if (rval.containsKey(itemActiveProperty)) {
                            throw new JsonLdError(
                                    "Invalid JSON-LD compact error; property has a \"@list\" @container "
                                            + "rule but there is more than a single @list that matches "
                                            + "the compacted term in the document. Compaction might mix "
                                            + "unwanted items into the list.")
                                    .setType(JsonLdError.Error.SYNTAX_ERROR);
                        }
                    }

                    // handle language and index maps
                    if ("@language".equals(container) || "@index".equals(container)) {
                        // get or create the map object
                        Map<String, Object> mapObject;
                        if (rval.containsKey(itemActiveProperty)) {
                            mapObject = (Map<String, Object>) rval.get(itemActiveProperty);
                        } else {
                            mapObject = new LinkedHashMap<String, Object>();
                            rval.put(itemActiveProperty, mapObject);
                        }

                        // if container is a language map, simplify compacted
                        // value to
                        // a simple string
                        if ("@language".equals(container) && isValue(compactedItem)) {
                            compactedItem = ((Map<String, Object>) compactedItem).get("@value");
                        }

                        // add compact value to map object using key from
                        // expanded value
                        // based on the container type
                        addValue(mapObject,
                                (String) ((Map<String, Object>) expandedItem).get(container),
                                compactedItem);
                    } else {
                        // use an array if: compactArrays flag is false,
                        // @container is @set or @list, value is an empty
                        // array, or key is @graph
                        final Boolean isArray = (!opts.compactArrays
                                || "@set".equals(container)
                                || "@list".equals(container)
                                || (isArray(compactedItem) && ((List<Object>) compactedItem).size() == 0)
                                || "@list".equals(expandedProperty) || "@graph"
                                .equals(expandedProperty));

                        // add compact value
                        addValue(rval, itemActiveProperty, compactedItem, isArray);
                    }
                }
            }

            return rval;
        }

        // only primatives remain which are already compact
        return element;
    }

    private class FramingContext {
        public Map<String, Object> embeds = null;
        public Map<String, Object> graphs = null;
        public Map<String, Object> subjects = null;
        public Options options = opts;
    }

    /**
     * Performs JSON-LD framing.
     * 
     * @param input
     *            the expanded JSON-LD to frame.
     * @param frame
     *            the expanded JSON-LD frame to use.
     * @param options
     *            the framing options.
     * 
     * @return the framed output.
     * @throws JsonLdError
     */
    public Object frame(Object input, Object frame) throws JsonLdError {
        // create framing state
        final FramingContext state = new FramingContext();
        // Map<String,Object> state = new HashMap<String, Object>();
        // state.put("options", this.opts);
        state.graphs = new LinkedHashMap<String, Object>();
        state.graphs.put("@default", new LinkedHashMap<String, Object>());
        state.graphs.put("@merged", new LinkedHashMap<String, Object>());

        // produce a map of all graphs and name each bnode
        // FIXME: currently uses subjects from @merged graph only
        final UniqueNamer namer = new UniqueNamer("_:b");
        createNodeMap(input, state.graphs, "@merged", namer);
        state.subjects = (Map<String, Object>) state.graphs.get("@merged");

        // frame the subjects
        final List<Object> framed = new ArrayList<Object>();
        final List<String> sortedKeys = new ArrayList<String>(state.subjects.keySet());
        Collections.sort(sortedKeys);
        frame(state, sortedKeys, frame, framed, null);
        return framed;
    }

    /**
     * Frames subjects according to the given frame.
     * 
     * @param state
     *            the current framing state.
     * @param subjects
     *            the subjects to filter.
     * @param frame
     *            the frame.
     * @param parent
     *            the parent subject or top-level array.
     * @param property
     *            the parent property, initialized to null.
     * @throws JsonLdError
     */
    private void frame(FramingContext state, Collection<String> subjects, Object frame,
            Object parent, String property) throws JsonLdError {
        // validate the frame
        validateFrame(state, frame);
        // NOTE: once validated we move to the function where the frame is
        // specifically a map
        frame(state, subjects, (Map<String, Object>) ((List<Object>) frame).get(0), parent,
                property);
    }

    private void frame(FramingContext state, Collection<String> subjects,
            Map<String, Object> frame, Object parent, String property) throws JsonLdError {
        // filter out subjects that match the frame
        final Map<String, Object> matches = filterSubjects(state, subjects, frame);

        // get flags for current frame
        final Options options = state.options;
        Boolean embedOn = (frame.containsKey("@embed")) ? (Boolean) ((List) frame.get("@embed"))
                .get(0) : options.embed;
        final Boolean explicicOn = (frame.containsKey("@explicit")) ? (Boolean) ((List) frame
                .get("@explicit")).get(0) : options.explicit;

        // add matches to output
        final List<String> ids = new ArrayList<String>(matches.keySet());
        Collections.sort(ids);
        for (final String id : ids) {

            // Note: In order to treat each top-level match as a
            // compartmentalized
            // result, create an independent copy of the embedded subjects map
            // when the
            // property is null, which only occurs at the top-level.
            if (property == null) {
                state.embeds = new LinkedHashMap<String, Object>();
            }

            // start output
            final Map<String, Object> output = new LinkedHashMap<String, Object>();
            output.put("@id", id);

            // prepare embed meta info
            final Map<String, Object> embed = new LinkedHashMap<String, Object>();
            embed.put("parent", parent);
            embed.put("property", property);

            // if embed is on and there is an existing embed
            if (embedOn && state.embeds.containsKey(id)) {
                // only overwrite an existing embed if it has already been added
                // to its
                // parent -- otherwise its parent is somewhere up the tree from
                // this
                // embed and the embed would occur twice once the tree is added
                embedOn = false;

                // existing embed's parent is an array
                final Map<String, Object> existing = (Map<String, Object>) state.embeds.get(id);
                if (isArray(existing.get("parent"))) {
                    for (final Object o : (List<Object>) existing.get("parent")) {
                        if (compareValues(output, o)) {
                            embedOn = true;
                            break;
                        }
                    }
                }
                // existing embed's parent is an object
                else if (hasValue((Map<String, Object>) existing.get("parent"),
                        (String) existing.get("property"), output)) {
                    embedOn = true;
                }

                // existing embed has already been added, so allow an overwrite
                if (embedOn) {
                    removeEmbed(state, id);
                }
            }

            // not embedding, add output without any other properties
            if (!embedOn) {
                addFrameOutput(state, parent, property, output);
            } else {
                // add embed meta info
                state.embeds.put(id, embed);

                // iterate over subject properties
                final Map<String, Object> subject = (Map<String, Object>) matches.get(id);
                List<String> props = new ArrayList<String>(subject.keySet());
                Collections.sort(props);
                for (final String prop : props) {

                    // handle ignored keys
                    if (opts.isIgnored(prop)) {
                        output.put(prop, JSONLDUtils.clone(subject.get(prop)));
                        continue;
                    }

                    // copy keywords to output
                    if (isKeyword(prop)) {
                        output.put(prop, JSONLDUtils.clone(subject.get(prop)));
                        continue;
                    }

                    // if property isn't in the frame
                    if (!frame.containsKey(prop)) {
                        // if explicit is off, embed values
                        if (!explicicOn) {
                            embedValues(state, subject, prop, output);
                        }
                        continue;
                    }

                    // add objects
                    final Object objects = subject.get(prop);
                    // TODO: i've done some crazy stuff here because i'm unsure
                    // if objects is always a list or if it can
                    // be a map as well. I think it's always a map, but i'll get
                    // it working like this first
                    for (final Object i : objects instanceof List ? (List) objects
                            : ((Map) objects).keySet()) {
                        final Object o = objects instanceof List ? i : ((Map) objects).get(i);

                        // recurse into list
                        if (isList(o)) {
                            // add empty list
                            final Map<String, Object> list = new LinkedHashMap<String, Object>();
                            list.put("@list", new ArrayList<Object>());
                            addFrameOutput(state, output, prop, list);

                            // add list objects
                            final List src = (List) ((Map) o).get("@list");
                            for (final Object n : src) {
                                // recurse into subject reference
                                if (isSubjectReference(n)) {
                                    final List tmp = new ArrayList();
                                    tmp.add(((Map) n).get("@id"));
                                    frame(state, tmp, frame.get(prop), list, "@list");
                                } else {
                                    // include other values automatcially
                                    addFrameOutput(state, list, "@list", JSONLDUtils.clone(n));
                                }
                            }
                            continue;
                        }

                        // recurse into subject reference
                        if (isSubjectReference(o)) {
                            final List tmp = new ArrayList();
                            tmp.add(((Map) o).get("@id"));
                            frame(state, tmp, frame.get(prop), output, prop);
                        } else {
                            // include other values automatically
                            addFrameOutput(state, output, prop, JSONLDUtils.clone(o));
                        }
                    }
                }

                // handle defaults
                props = new ArrayList<String>(frame.keySet());
                Collections.sort(props);
                for (final String prop : props) {
                    // skip keywords
                    if (isKeyword(prop)) {
                        continue;
                    }

                    // if omit default is off, then include default values for
                    // properties
                    // that appear in the next frame but are not in the matching
                    // subject
                    final Map<String, Object> next = (Map<String, Object>) ((List<Object>) frame
                            .get(prop)).get(0);
                    final boolean omitDefaultOn = (next.containsKey("@omitDefault")) ? (Boolean) ((List) next
                            .get("@omitDefault")).get(0) : options.omitDefault;
                    if (!omitDefaultOn && !output.containsKey(prop)) {
                        Object preserve = "@null";
                        if (next.containsKey("@default")) {
                            preserve = JSONLDUtils.clone(next.get("@default"));
                        }
                        if (!isArray(preserve)) {
                            final List<Object> tmp = new ArrayList<Object>();
                            tmp.add(preserve);
                            preserve = tmp;
                        }
                        final Map<String, Object> tmp1 = new LinkedHashMap<String, Object>();
                        tmp1.put("@preserve", preserve);
                        final List<Object> tmp2 = new ArrayList<Object>();
                        tmp2.add(tmp1);
                        output.put(prop, tmp2);
                    }
                }

                // add output to parent
                addFrameOutput(state, parent, property, output);
            }
        }
    }

    /**
     * Embeds values for the given subject and property into the given output
     * during the framing algorithm.
     * 
     * @param state
     *            the current framing state.
     * @param subject
     *            the subject.
     * @param property
     *            the property.
     * @param output
     *            the output.
     */
    private void embedValues(FramingContext state, Map<String, Object> subject, String property,
            Object output) {
        // embed subject properties in output
        final Object objects = subject.get(property);

        // TODO: more craziness due to lack of knowledge about whether objects
        // should
        // be an array or an object
        for (final Object i : objects instanceof List ? (List) objects : ((Map) objects).keySet()) {
            Object o = objects instanceof List ? i : ((Map) objects).get(i);

            // recurse into @list
            if (isList(o)) {
                final Map<String, Object> list = new LinkedHashMap<String, Object>();
                list.put("@list", new ArrayList());
                addFrameOutput(state, output, property, list);
                embedValues(state, (Map<String, Object>) o, "@list", list.get("@list"));
                return;
            }

            // handle subject reference
            if (isSubjectReference(o)) {
                final String id = (String) ((Map<String, Object>) o).get("@id");

                // embed full subject if isn't already embedded
                if (!state.embeds.containsKey(id)) {
                    // add embed
                    final Map<String, Object> embed = new LinkedHashMap<String, Object>();
                    embed.put("parent", output);
                    embed.put("property", property);
                    state.embeds.put(id, embed);

                    // recurse into subject
                    o = new LinkedHashMap<String, Object>();
                    final Map<String, Object> s = (Map<String, Object>) state.subjects.get(id);
                    for (final String prop : s.keySet()) {
                        // copy keywords
                        if (isKeyword(prop) || opts.isIgnored(prop)) {
                            ((Map<String, Object>) o).put(prop, JSONLDUtils.clone(s.get(prop)));
                            continue;
                        }
                        embedValues(state, s, prop, o);
                    }
                }
                addFrameOutput(state, output, property, o);
            }
            // copy non-subject value
            else {
                addFrameOutput(state, output, property, JSONLDUtils.clone(o));
            }
        }

    }

    /**
     * Adds framing output to the given parent.
     * 
     * @param state
     *            the current framing state.
     * @param parent
     *            the parent to add to.
     * @param property
     *            the parent property.
     * @param output
     *            the output to add.
     */
    private static void addFrameOutput(FramingContext state, Object parent, String property,
            Object output) {
        if (isObject(parent)) {
            addValue((Map<String, Object>) parent, property, output, true);
        } else {
            ((List) parent).add(output);
        }

    }

    /**
     * Removes an existing embed.
     * 
     * @param state
     *            the current framing state.
     * @param id
     *            the @id of the embed to remove.
     */
    private static void removeEmbed(FramingContext state, String id) {
        // get existing embed
        final Map<String, Object> embeds = state.embeds;
        final Map<String, Object> embed = (Map<String, Object>) embeds.get(id);
        final Object parent = embed.get("parent");
        final String property = (String) embed.get("property");

        // create reference to replace embed
        final Map<String, Object> subject = new LinkedHashMap<String, Object>();
        subject.put("@id", id);

        // remove existing embed
        if (isArray(parent)) {
            // replace subject with reference
            for (int i = 0; i < ((List) parent).size(); i++) {
                if (compareValues(((List) parent).get(i), subject)) {
                    ((List) parent).set(i, subject);
                    break;
                }
            }
        } else {
            // replace subject with reference
            removeValue(((Map<String, Object>) parent), property, subject,
                    ((Map<String, Object>) parent).get(property) instanceof List);
            addValue(((Map<String, Object>) parent), property, subject,
                    ((Map<String, Object>) parent).get(property) instanceof List);
        }

        // recursively remove dependent dangling embeds
        removeDependents(embeds, id);
    }

    private static void removeDependents(Map<String, Object> embeds, String id) {
        // get embed keys as a separate array to enable deleting keys in map
        final Set<String> ids = embeds.keySet();
        for (final String next : ids) {
            if (embeds.containsKey(next)
                    && ((Map<String, Object>) embeds.get(next)).get("parent") instanceof Map
                    && id.equals(((Map<String, Object>) ((Map<String, Object>) embeds.get(next))
                            .get("parent")).get("@id"))) {
                embeds.remove(next);
                removeDependents(embeds, next);
            }
        }
    }

    /**
     * Returns a map of all of the subjects that match a parsed frame.
     * 
     * @param state
     *            the current framing state.
     * @param subjects
     *            the set of subjects to filter.
     * @param frame
     *            the parsed frame.
     * 
     * @return all of the matched subjects.
     */
    private static Map<String, Object> filterSubjects(FramingContext state,
            Collection<String> subjects, Map<String, Object> frame) {
        // filter subjects in @id order
        final Map<String, Object> rval = new LinkedHashMap<String, Object>();
        for (final String id : subjects) {
            final Map<String, Object> subject = (Map<String, Object>) state.subjects.get(id);
            if (filterSubject(subject, frame)) {
                rval.put(id, subject);
            }
        }
        return rval;
    }

    /**
     * Returns true if the given subject matches the given frame.
     * 
     * @param subject
     *            the subject to check.
     * @param frame
     *            the frame to check.
     * 
     * @return true if the subject matches, false if not.
     */
    private static boolean filterSubject(Map<String, Object> subject, Map<String, Object> frame) {
        // check @type (object value means 'any' type, fall through to
        // ducktyping)
        final Object t = frame.get("@type");
        // TODO: it seems @type should always be a list
        if (frame.containsKey("@type")
                && !(t instanceof List && ((List) t).size() == 1 && ((List) t).get(0) instanceof Map)) {
            for (final Object i : (List) t) {
                if (hasValue(subject, "@type", i)) {
                    return true;
                }
            }
            return false;
        }

        // check ducktype
        for (final String key : frame.keySet()) {
            if ("@id".equals(key) || !isKeyword(key) && !(subject.containsKey(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates a JSON-LD frame, throwing an exception if the frame is invalid.
     * 
     * @param state
     *            the current frame state.
     * @param frame
     *            the frame to validate.
     * @throws JsonLdError
     */
    private static void validateFrame(FramingContext state, Object frame)
            throws JsonLdError {
        if (!(frame instanceof List) || ((List) frame).size() != 1
                || !(((List) frame).get(0) instanceof Map)) {
            throw new JsonLdError(
                    "Invalid JSON-LD syntax; a JSON-LD frame must be a single object.").setType(
                    JsonLdError.Error.SYNTAX_ERROR).setDetail("frame", frame);
        }
    }

    /**
     * Performs RDF normalization on the given JSON-LD input.
     * 
     * @param input
     *            the expanded JSON-LD object to normalize.
     * @param options
     *            the normalization options.
     * @param callback
     *            (err, normalized) called once the operation completes.
     * @throws JsonLdError
     */
    public Object normalize(Map<String, Object> dataset) throws JsonLdError {
        // create quads and map bnodes to their associated quads
        final List<Object> quads = new ArrayList<Object>();
        final Map<String, Object> bnodes = new LinkedHashMap<String, Object>();
        for (String graphName : dataset.keySet()) {
            final List<Map<String, Object>> triples = (List<Map<String, Object>>) dataset
                    .get(graphName);
            if ("@default".equals(graphName)) {
                graphName = null;
            }
            for (final Map<String, Object> quad : triples) {
                if (graphName != null) {
                    if (graphName.indexOf("_:") == 0) {
                        final Map<String, Object> tmp = new LinkedHashMap<String, Object>();
                        tmp.put("type", "blank node");
                        tmp.put("value", graphName);
                        quad.put("name", tmp);
                    } else {
                        final Map<String, Object> tmp = new LinkedHashMap<String, Object>();
                        tmp.put("type", "IRI");
                        tmp.put("value", graphName);
                        quad.put("name", tmp);
                    }
                }
                quads.add(quad);

                final String[] attrs = new String[] { "subject", "object", "name" };
                for (final String attr : attrs) {
                    if (quad.containsKey(attr)
                            && "blank node".equals(((Map<String, Object>) quad.get(attr))
                                    .get("type"))) {
                        final String id = (String) ((Map<String, Object>) quad.get(attr))
                                .get("value");
                        if (!bnodes.containsKey(id)) {
                            bnodes.put(id, new LinkedHashMap<String, List<Object>>() {
                                {
                                    put("quads", new ArrayList<Object>());
                                }
                            });
                        }
                        ((List<Object>) ((Map<String, Object>) bnodes.get(id)).get("quads"))
                                .add(quad);
                    }
                }
            }
        }

        // mapping complete, start canonical naming
        final NormalizeUtils normalizeUtils = new NormalizeUtils(quads, bnodes, new UniqueNamer(
                "_:c14n"), opts);
        return normalizeUtils.hashBlankNodes(bnodes.keySet());
    }

    /**
     * Adds RDF triples for each graph in the given node map to an RDF dataset.
     * 
     * @param nodeMap
     *            the node map.
     * 
     * @return the RDF dataset.
     */
    public RDFDataset toRDF(Map<String, Object> nodeMap) {
        final UniqueNamer namer = new UniqueNamer("_:b");
        final RDFDataset dataset = new RDFDataset(namer);
        for (String graphName : nodeMap.keySet()) {
            final Map<String, Object> graph = (Map<String, Object>) nodeMap.get(graphName);
            if (graphName.indexOf("_:") == 0) {
                graphName = namer.getName(graphName);
            }
            dataset.graphToRDF(graphName, graph);
        }
        return dataset;
    }

    /**
     * Converts RDF statements into JSON-LD.
     * 
     * @param statements
     *            the RDF statements.
     * @param options
     *            the RDF conversion options.
     * @param callback
     *            (err, output) called once the operation completes.
     * @throws JsonLdError
     */
    public List<Object> fromRDF(final RDFDataset dataset) throws JsonLdError {
        final Map<String, Object> defaultGraph = new LinkedHashMap<String, Object>();
        final Map<String, Map<String, Object>> graphMap = new LinkedHashMap<String, Map<String, Object>>() {
            {
                put("@default", defaultGraph);
            }
        };

        // For each graph in RDF dataset
        for (final String name : dataset.graphNames()) {

            final List<RDFDataset.Quad> graph = dataset.getQuads(name);

            // If graph map has no name member, create one and set its value to
            // an empty JSON object.
            Map<String, Object> nodeMap;
            if (!graphMap.containsKey(name)) {
                nodeMap = new LinkedHashMap<String, Object>();
                graphMap.put(name, nodeMap);
            } else {
                nodeMap = graphMap.get(name);
            }

            // If graph is not the default graph and default graph does not have
            // a name member, create such
            // a member and initialize its value to a new JSON object with a
            // single member @id whose value is name.
            if (!"@default".equals(name) && !Obj.contains(defaultGraph, name)) {
                Obj.put(defaultGraph, name, new LinkedHashMap<String, Object>() {
                    {
                        put("@id", name);
                    }
                });
            }

            // For each RDF triple in graph consisting of subject, predicate,
            // and object
            for (final RDFDataset.Quad triple : graph) {
                final String subject = triple.getSubject().getValue();
                final String predicate = triple.getPredicate().getValue();
                final RDFDataset.Node object = triple.getObject();

                // If node map does not have a subject member, create one and
                // initialize its value to a new JSON object
                // consisting of a single member @id whose value is set to
                // subject.
                Map<String, Object> node;
                if (!nodeMap.containsKey(subject)) {
                    node = new LinkedHashMap<String, Object>() {
                        {
                            put("@id", subject);
                        }
                    };
                    nodeMap.put(subject, node);
                } else {
                    node = (Map<String, Object>) nodeMap.get(subject);
                }

                // If object is an IRI or blank node identifier, does not equal
                // rdf:nil, and node map does not have an object member,
                // create one and initialize its value to a new JSON object
                // consisting of a single member @id whose value is set to
                // object.
                if ((object.isIRI() || object.isBlankNode()) && !RDF_NIL.equals(object.getValue())
                        && !nodeMap.containsKey(object.getValue())) {
                    nodeMap.put(object.getValue(), new LinkedHashMap<String, Object>() {
                        {
                            put("@id", object.getValue());
                        }
                    });
                }

                // If predicate equals rdf:type, and object is an IRI or blank
                // node identifier, append object to the value of the
                // @type member of node. If no such member exists, create one
                // and initialize it to an array whose only item is object.
                // Finally, continue to the next RDF triple
                if (RDF_TYPE.equals(predicate) && (object.isIRI() || object.isBlankNode())) {
                    addValue(node, "@type", object.getValue(), true);
                    continue;
                }

                // If object equals rdf:nil and predicate does not equal
                // rdf:rest, set value to a new JSON object
                // consisting of a single member @list whose value is set to an
                // empty array.
                Map<String, Object> value;
                if (RDF_NIL.equals(object.getValue()) && !RDF_REST.equals(predicate)) {
                    value = new LinkedHashMap<String, Object>() {
                        {
                            put("@list", new ArrayList<Object>());
                        }
                    };
                } else {
                    // Otherwise, set value to the result of using the RDF to
                    // Object Conversion algorithm, passing object and use
                    // native types.
                    value = object.toObject(opts.useNativeTypes);
                }

                // If node does not have an predicate member, create one and
                // initialize its value to an empty array.
                // Add a reference to value to the to the array associated with
                // the predicate member of node.
                addValue(node, predicate, value, true);

                // If object is a blank node identifier and predicate equals
                // neither rdf:first nor rdf:rest, it might represent the head
                // of a RDF list
                if (object.isBlankNode() && !RDF_FIRST.equals(predicate)
                        && !RDF_REST.equals(predicate)) {
                    // If the object member of node map has an usages member,
                    // add a reference to value to it;
                    // otherwise create such a member and set its value to an
                    // array whose only item is a reference to value.
                    addValue((Map<String, Object>) nodeMap.get(object.getValue()), "usages", value,
                            true);
                }
            }
        }

        // build @lists
        for (final String name : graphMap.keySet()) {
            final Map<String, Object> graph = graphMap.get(name);

            final List<String> subjects = new ArrayList<String>(graph.keySet());

            for (final String subj : subjects) {
                // If graph object does not have a subj member, it has been
                // removed as it was part of a list. Continue with the next
                // subj.
                if (!graph.containsKey(subj)) {
                    continue;
                }

                // If node has no usages member or its value is not an array
                // consisting of one item, continue with the next subj.
                Map<String, Object> node = (Map<String, Object>) graph.get(subj);
                if (!node.containsKey("usages") || !(node.get("usages") instanceof List)
                        || ((List<Object>) node.get("usages")).size() != 1) {
                    continue;
                }
                final Map<String, Object> value = (Map<String, Object>) ((List<Object>) node
                        .get("usages")).get(0);
                List<Object> list = new ArrayList<Object>();
                final List<String> listNodes = new ArrayList<String>();
                String subject = subj;

                while (!RDF_NIL.equals(subject) && list != null) {
                    // If node is null; the value of its @id member does not
                    // begin with _: ...
                    boolean test = node == null || ((String) node.get("@id")).indexOf("_:") != 0;
                    if (!test) {
                        int cnt = 0;
                        for (final String i : new String[] { "@id", "usages", RDF_FIRST, RDF_REST }) {
                            if (node.containsKey(i)) {
                                cnt++;
                            }
                        }
                        // it has members other than @id, usages, rdf:first, and
                        // rdf:rest ...
                        test = (node.keySet().size() > cnt);
                        if (!test) {
                            // the value of its rdf:first member is not an array
                            // consisting of a single item ...
                            test = !(node.get(RDF_FIRST) instanceof List)
                                    || ((List<Object>) node.get(RDF_FIRST)).size() != 1;
                            if (!test) {
                                // or the value of its rdf:rest member is not an
                                // array containing a single item which is a
                                // JSON object that has an @id member ...
                                test = !(node.get(RDF_REST) instanceof List)
                                        || ((List<Object>) node.get(RDF_REST)).size() != 1;
                                if (!test) {
                                    final Object o = ((List<Object>) node.get(RDF_REST)).get(0);
                                    test = (!(o instanceof Map && ((Map<String, Object>) o)
                                            .containsKey("@id")));
                                }
                            }
                        }
                    }
                    if (test) {
                        // it is not a valid list node. Set list to null
                        list = null;
                    } else {
                        list.add(((List<Object>) node.get(RDF_FIRST)).get(0));
                        listNodes.add((String) node.get("@id"));
                        subject = (String) ((Map<String, Object>) ((List<Object>) node
                                .get(RDF_REST)).get(0)).get("@id");
                        node = (Map<String, Object>) graph.get(subject);
                        if (listNodes.contains(subject)) {
                            list = null;
                        }
                    }
                }

                // If list is null, continue with the next subj.
                if (list == null) {
                    continue;
                }

                // Remove the @id member from value.
                value.remove("@id");

                // Add an @list member to value and initialize it to list.
                value.put("@list", list);

                for (final String subject_ : listNodes) {
                    graph.remove(subject_);
                }

            }
        }

        final List<Object> result = new ArrayList<Object>();
        final List<String> ids = new ArrayList<String>(defaultGraph.keySet());
        Collections.sort(ids);
        for (final String subject : ids) {
            final Map<String, Object> node = (Map<String, Object>) defaultGraph.get(subject);
            if (graphMap.containsKey(subject)) {
                node.put("@graph", new ArrayList<Object>());
                final List<String> keys = new ArrayList<String>(graphMap.get(subject).keySet());
                Collections.sort(keys);
                for (final String s : keys) {
                    final Map<String, Object> n = (Map<String, Object>) graphMap.get(subject)
                            .get(s);
                    n.remove("usages");
                    ((List<Object>) node.get("@graph")).add(n);
                }
            }
            node.remove("usages");
            result.add(node);
        }

        return result;
    }

    /**
     * Performs JSON-LD flattening.
     * 
     * @param input
     *            the expanded JSON-LD to flatten.
     * 
     * @return the flattened output.
     * @throws JsonLdError
     */
    public List<Object> flatten(List<Object> input) throws JsonLdError {
        // produce a map of all subjects and name each bnode
        final UniqueNamer namer = new UniqueNamer("_:b");
        final Map<String, Object> graphs = new LinkedHashMap<String, Object>() {
            {
                put("@default", new LinkedHashMap<String, Object>());
            }
        };
        createNodeMap(input, graphs, "@default", namer);

        // add all non-default graphs to default graph
        final Map<String, Object> defaultGraph = (Map<String, Object>) graphs.get("@default");
        final List<String> graphNames = new ArrayList<String>(graphs.keySet());
        Collections.sort(graphNames);
        for (final String graphName : graphNames) {
            if ("@default".equals(graphName)) {
                continue;
            }
            final Map<String, Object> nodeMap = (Map<String, Object>) graphs.get(graphName);
            Map<String, Object> subject = (Map<String, Object>) defaultGraph.get(graphName);
            if (subject == null) {
                subject = new LinkedHashMap<String, Object>();
                subject.put("@id", graphName);
                subject.put("@graph", new ArrayList<Object>());
                defaultGraph.put(graphName, subject);
            } else if (!subject.containsKey("@graph")) {
                subject.put("@graph", new ArrayList<Object>());
            }
            final List<Object> graph = (List<Object>) subject.get("@graph");
            final List<String> ids = new ArrayList<String>(nodeMap.keySet());
            Collections.sort(ids);
            for (final String id : ids) {
                graph.add(nodeMap.get(id));
            }
        }

        // produce flattened output
        final List<Object> flattened = new ArrayList<Object>();
        final List<String> keys = new ArrayList<String>(defaultGraph.keySet());
        Collections.sort(keys);
        for (final String key : keys) {
            flattened.add(defaultGraph.get(key));
        }
        return flattened;
    }

    /**
     * Generates a unique simplified key from a URI and add it to the context
     * 
     * @param key
     *            to full URI to generate the simplified key from
     * @param ctx
     *            the context to add the simplified key too
     * @param isid
     *            whether to set the type to @id
     */
    private static void processKeyVal(Map<String, Object> ctx, String key, Object val) {
        int idx = key.lastIndexOf('#');
        if (idx < 0) {
            idx = key.lastIndexOf('/');
        }
        String skey = key.substring(idx + 1);
        Object keyval = key;
        final Map entry = new LinkedHashMap();
        entry.put("@id", keyval);
        Object v = val;
        while (true) {
            if (v instanceof List && ((List) v).size() > 0) {
                // use the first entry as a reference
                v = ((List) v).get(0);
                continue;
            }
            if (v instanceof Map && ((Map) v).containsKey("@list")) {
                v = ((Map) v).get("@list");
                entry.put("@container", "@list");
                continue;
            }
            if (v instanceof Map && ((Map) v).containsKey("@set")) {
                v = ((Map) v).get("@set");
                entry.put("@container", "@set");
                continue;
            }
            break;
        }
        if (v instanceof Map && ((Map) v).containsKey("@id")) {
            entry.put("@type", "@id");
        }
        if (entry.size() == 1) {
            keyval = entry.get("@id");
        } else {
            keyval = entry;
        }
        while (true) {
            // check if the key is already in the frame ctx
            if (ctx.containsKey(skey)) {
                // if so, check if the values are the same
                if (JSONUtils.equals(ctx.get(skey), keyval)) {
                    // if they are, skip adding this
                    break;
                }
                // if not, add a _ to the simple key and try again
                skey += "_";
            } else {
                ctx.put(skey, keyval);
                break;
            }
        }
    }

    /**
     * Generates the context to be used by simplify.
     * 
     * @param input
     * @param ctx
     */
    private static void generateSimplifyContext(Object input, Map<String, Object> ctx) {
        if (input instanceof List) {
            for (final Object o : (List) input) {
                generateSimplifyContext(o, ctx);
            }
        } else if (input instanceof Map) {
            final Map<String, Object> o = (Map<String, Object>) input;
            final Map<String, Object> localCtx = (Map<String, Object>) o.remove("@context");
            for (final String key : o.keySet()) {
                Object val = o.get(key);
                if (key.matches("^https?://.+$")) {
                    processKeyVal(ctx, key, val);
                }
                if ("@type".equals(key)) {
                    if (!(val instanceof List)) {
                        final List<Object> tmp = new ArrayList<Object>();
                        tmp.add(val);
                        val = tmp;
                    }
                    for (final Object t : (List<Object>) val) {
                        if (t instanceof String) {
                            processKeyVal(ctx, (String) t, new LinkedHashMap<String, Object>() {
                                {
                                    put("@id", "");
                                }
                            });
                        } else {
                            throw new RuntimeException(
                                    "TODO: don't yet know how to handle non-string types in @type");
                        }
                    }
                } else if (val instanceof Map || val instanceof List) {
                    generateSimplifyContext(val, ctx);
                }
            }
        }
    }

    /**
     * Automatically builds a context which attempts to simplify the keys and
     * values as much as possible and uses that context to compact the input
     * 
     * NOTE: this is experimental and only built for specific conditions
     * 
     * @param input
     * @return the simplified version of input
     * @throws JsonLdError
     */
    public Object simplify(Object input) throws JsonLdError {

        final Object expanded = JSONLD.expand(input, opts);
        final Map<String, Object> ctx = new LinkedHashMap<String, Object>();

        generateSimplifyContext(expanded, ctx);

        final Map<String, Object> tmp = new LinkedHashMap<String, Object>();
        tmp.put("@context", ctx);

        // add optimize flag to opts (clone the opts so we don't change the flag
        // for the base processor)
        final Options opts1 = opts.clone();
        // opts1.optimize = true;
        return JSONLD.compact(input, tmp, opts1);
    }

}

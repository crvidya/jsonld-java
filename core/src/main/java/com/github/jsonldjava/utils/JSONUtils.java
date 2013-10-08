package com.github.jsonldjava.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.RequestAcceptEncoding;
import org.apache.http.client.protocol.ResponseContentEncoding;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClient;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * A bunch of functions to make loading JSON easy
 * 
 * @author tristan
 * 
 */
public class JSONUtils {

    public static Object fromString(String jsonString) throws JsonParseException,
            JsonMappingException {
        final ObjectMapper objectMapper = new ObjectMapper();
        Object rval = null;
        if (jsonString.trim().startsWith("[")) {
            try {
                rval = objectMapper.readValue(jsonString, List.class);
            } catch (final IOException e) {
                // TODO: what?
                if (e instanceof JsonParseException) {
                    throw (JsonParseException) e;
                } else if (e instanceof JsonMappingException) {
                    throw (JsonMappingException) e;
                } else {
                    // TODO: Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else if (jsonString.trim().startsWith("{")) {
            try {
                rval = objectMapper.readValue(jsonString, Map.class);
            } catch (final IOException e) {
                if (e instanceof JsonParseException) {
                    throw (JsonParseException) e;
                } else if (e instanceof JsonMappingException) {
                    throw (JsonMappingException) e;
                } else {
                    // TODO: Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else if (jsonString.trim().startsWith("\"")) {
            try {
                rval = objectMapper.readValue(jsonString, String.class);
            } catch (final IOException e) {
                if (e instanceof JsonParseException) {
                    throw (JsonParseException) e;
                } else if (e instanceof JsonMappingException) {
                    throw (JsonMappingException) e;
                } else {
                    // TODO: Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else if (jsonString.trim().equals("true") || (jsonString.trim().equals("false"))) {
            try {
                rval = objectMapper.readValue(jsonString, Boolean.class);
            } catch (final IOException e) {
                if (e instanceof JsonParseException) {
                    throw (JsonParseException) e;
                } else if (e instanceof JsonMappingException) {
                    throw (JsonMappingException) e;
                } else {
                    // TODO: Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else if (jsonString.trim().matches("[0-9.e+-]+")) {
            try {
                rval = objectMapper.readValue(jsonString, Number.class);
            } catch (final IOException e) {
                if (e instanceof JsonParseException) {
                    throw (JsonParseException) e;
                } else if (e instanceof JsonMappingException) {
                    throw (JsonMappingException) e;
                } else {
                    // TODO: Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else if (jsonString.trim().equals("null")) {
            rval = null;
        } else {
            throw new JsonParseException("document doesn't start with a valid json element",
                    new JsonLocation(
                            "\"" + jsonString.substring(0, Math.min(jsonString.length(), 100))
                                    + "...\"", 0, 1, 0));
        }
        return rval;
    }

    public static Object fromReader(Reader r) throws IOException {
        final StringBuilder sb = new StringBuilder(4096);
        char[] buffer = new char[4096];
        int b;
        while ((b = r.read(buffer)) > 0) {
            sb.append(buffer, 0, b);
        }
        return fromString(sb.toString());
    }

    public static void write(Writer w, Object jsonObject) throws JsonGenerationException,
            JsonMappingException, IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        objectMapper.writeValue(w, jsonObject);
    }

    public static void writePrettyPrint(Writer w, Object jsonObject)
            throws JsonGenerationException, JsonMappingException, IOException {
        final ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.getFactory().disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        final ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter();

        objectWriter.writeValue(w, jsonObject);
    }

    public static Object fromInputStream(InputStream content) throws IOException {
        return fromInputStream(content, "UTF-8"); // no readers from
                                                  // inputstreams w.o.
                                                  // encoding!!
    }

    public static Object fromInputStream(InputStream content, String enc) throws IOException {
        return fromReader(new InputStreamReader(content, enc));
    }

    public static String toPrettyString(Object obj) {
        final StringWriter sw = new StringWriter();
        try {
            writePrettyPrint(sw, obj);
        } catch (final IOException e) {
            // TODO Is this really possible with stringwriter?
            // I think it's only there because of the interface
            // however, if so... well, we have to do something!
            // it seems weird for toString to throw an IOException
            e.printStackTrace();
        }
        return sw.toString();
    }

    public static String toString(Object obj) { // throws
                                                // JsonGenerationException,
                                                // JsonMappingException {
        final StringWriter sw = new StringWriter();
        try {
            write(sw, obj);
        } catch (final IOException e) {
            // TODO Is this really possible with stringwriter?
            // I think it's only there because of the interface
            // however, if so... well, we have to do something!
            // it seems weird for toString to throw an IOException
            e.printStackTrace();
        }
        return sw.toString();
    }

    /**
     * A null-safe equals check using v1.equals(v2) if they are both not null.
     * 
     * @param v1
     *            The source object for the equals check.
     * @param v2
     *            The object to be checked for equality using the first objects
     *            equals method.
     * @return True if the objects were both null. True if both objects were not
     *         null and v1.equals(v2). False otherwise.
     */
    public static boolean equals(Object v1, Object v2) {
        return v1 == null ? v2 == null : v1.equals(v2);
    }
}

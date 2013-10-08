package com.github.jsonldjava.core;

/**
 * http://json-ld.org/spec/latest/json-ld-api/#the-jsonldoptions-type
 * 
 * @author tristan
 *
 */
public class JsonLdOptions {
    public JsonLdOptions() {
        this.setBase("");
    }

    public JsonLdOptions(String base) {
        this.setBase(base);
    }

    private String base = null;
    private Boolean compactArrays = true;
    
    private Object expandContext = null;
    private String processingMode = "json-ld-1.0";

    @Override
    public JsonLdOptions clone() {
        final JsonLdOptions rval = new JsonLdOptions(getBase());
        return rval;
    }

	public Boolean getCompactArrays() {
		return compactArrays;
	}

	public void setCompactArrays(Boolean compactArrays) {
		this.compactArrays = compactArrays;
	}

	public Object getExpandContext() {
		return expandContext;
	}

	public void setExpandContext(Object expandContext) {
		this.expandContext = expandContext;
	}

	public String getProcessingMode() {
		return processingMode;
	}

	public void setProcessingMode(String processingMode) {
		this.processingMode = processingMode;
	}

	public String getBase() {
		return base;
	}

	public void setBase(String base) {
		this.base = base;
	}
	
	
	// TODO: THE FOLLOWING ONLY EXIST SO I DON'T HAVE TO DELETE A LOT OF CODE, REMOVE IT WHEN DONE
	public String format = null;
	public Boolean useNamespaces = false;
	public String outputForm = null;
	public DocumentLoader documentLoader;
}

package it.cnr.ncss.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OllamaResponse {

	    public String model;
	    public String created_at;
	    public String response;
	    public boolean done;
	    public String done_reason;

	    public String asString() {
	    	String res = "model:"+model+"\n"+"creation date"+created_at+"\n"+"done:"+done+"\n"+"reason:"+done_reason+"\n"+"answer:"+response+"\n";
	    	return res;
	    }
}

package it.cnr.ncss.utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.llm.OllamaResponse;


public class JSONParser {

	

	 public static OllamaResponse parseOllamaResponse(String json) throws Exception {

	        ObjectMapper mapper = new ObjectMapper();

	        OllamaResponse result =
	                mapper.readValue(json, OllamaResponse.class);

	        //System.out.println(result.response);
	        
	        return result;
	    }
	
	
}

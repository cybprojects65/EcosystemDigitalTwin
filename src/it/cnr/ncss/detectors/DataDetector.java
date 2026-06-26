package it.cnr.ncss.detectors;


import java.io.File;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;


public class DataDetector extends AbstractDetector{

	
	
	@Override
	public File getReference() {
		
		return new File("prompting/data.txt");
		
	}

	@Override
	public double getSimilarityThreshold() {
		return 0.6;
	}
    
	public DataDetector(Ollama llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	String query = "show me the latest data";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Ollama llm = new Ollama();
	    	
	    	DataDetector gdt = new DataDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

		
}

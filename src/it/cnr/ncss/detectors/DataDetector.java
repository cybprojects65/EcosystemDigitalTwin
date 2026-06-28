package it.cnr.ncss.detectors;


import java.io.File;

import it.cnr.ncss.llm.Llm;
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
    
	public DataDetector(Llm llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	String query = "show me the latest data";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Llm llm = new Llm();
	    	
	    	DataDetector gdt = new DataDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

		
}

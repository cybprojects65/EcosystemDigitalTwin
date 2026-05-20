package it.cnr.ncss.detectors;


import java.io.File;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;


public class DriversDetector extends AbstractDetector{

	
	
	@Override
	public File getReference() {
		
		return new File("prompting/drivers.txt");
		
	}

	@Override
	public double getSimilarityThreshold() {
		return 0.8;
	}
    
	
	public boolean matchesRules(String q) {
    	
    	boolean matches = super.matchesRules(q);
    	if (!matches) {
    		
    		 boolean hasDriverWord =
    		            q.contains("drivers of")
    		            || q.contains("what drives")
    		            || q.contains("factors driving")
    		            || q.contains("factors that drive")
    		            || q.contains("main drivers")
    		            || q.contains("key drivers")
    		            || q.contains("driving factors");

    		    boolean hasDomainWord =
    		            q.contains("habitat")
    		            || q.contains("suitability")
    		            || q.contains("biodiversity")
    		            || q.contains("ecosystem")
    		            || q.contains("risk")
    		            || q.contains("species")
    		            || q.contains("temperature")
    		            || q.contains("precipitation")
    		            || q.contains("climate");

    		matches = hasDriverWord && hasDomainWord;
    		
    		
    		if (matches) return true;
    		
    	}
        
    	return false;
    }

	public DriversDetector(Ollama llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	//String query = "hello man. I would like to have information on the current temperature"; 
	    	String query = "which are the main drivers of risk?";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Ollama llm = new Ollama();
	    	
	    	DriversDetector gdt = new DriversDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

	
}

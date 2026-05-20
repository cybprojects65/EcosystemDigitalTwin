package it.cnr.ncss.detectors;


import java.io.File;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;


public class ImportanceDetector extends AbstractDetector{

	
	
	@Override
	public File getReference() {
		
		return new File("prompting/importance.txt");
		
	}

	@Override
	public double getSimilarityThreshold() {
		return 0.8;
	}
    
	
	public boolean matchesRules(String q) {
    	
    	boolean matches = super.matchesRules(q);
    	if (!matches) {
    		matches = 
    				q.contains("most important")
    	            || q.contains("most influential")
    	            || q.contains("top factor")
    	            || q.contains("top factors")
    	            || q.contains("main factor")
    	            || q.contains("main factors")
    	            || q.contains("key factor")
    	            || q.contains("key factors")
    	            || q.contains("important")
    	            || q.contains("importance")
    	            || q.contains("influential")
    	            || q.contains("strongest")
    	            || q.contains("dominant")
    	            || q.contains("highest impact")
    	            || q.contains("largest impact")
    	            || q.contains("most relevant")
    	            || q.contains("primary factor")
    	            || q.contains("critical factor");
    		
    		if (matches) return true;
    		
    	}
        
    	return false;
    }

	public ImportanceDetector(Ollama llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	//String query = "hello man. I would like to have information on the current temperature"; 
	    	String query = "which is the most relevant factor affecting risk?";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Ollama llm = new Ollama();
	    	
	    	ImportanceDetector gdt = new ImportanceDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

	
}

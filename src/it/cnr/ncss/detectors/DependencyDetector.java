package it.cnr.ncss.detectors;


import java.io.File;

import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;


public class DependencyDetector extends AbstractDetector{

	
	
	@Override
	public File getReference() {
		
		return new File("prompting/dependency.txt");
		
	}

	@Override
	public double getSimilarityThreshold() {
		return 0.8;
	}
    
	
	public boolean matchesRules(String q) {
    	
    	boolean matches = super.matchesRules(q);
    	if (!matches) {
    		matches = 
    		q.contains("how does")
            || q.contains("effect of")
            || q.contains("affect")
            || q.contains("affects")
            || q.contains("influence")
            || q.contains("influences")
            || q.contains("impact")
            || q.contains("impacts")
            || q.contains("relationship between");
    		
    		if (matches) return true;
    		
    	}
        
    	return false;
    }

	public DependencyDetector(Llm llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	//String query = "hello man. I would like to have information on the current temperature"; 
	    	String query = "how does oxigenation affect water temperature?";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Llm llm = new Llm();
	    	
	    	DependencyDetector gdt = new DependencyDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

	
}

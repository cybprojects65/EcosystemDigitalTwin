package it.cnr.ncss.detectors;


import java.io.File;
import java.util.regex.Pattern;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;


public class DeltaDetector extends AbstractDetector{

	
	
	@Override
	public File getReference() {
		
		return new File("prompting/delta.txt");
		
	}

	@Override
	public double getSimilarityThreshold() {
		return 0.8;
	}
    
	
	private static final Pattern DELTA_FROM_TO =
	        Pattern.compile("\\bfrom\\b.+\\bto\\b");

	private static final Pattern DELTA_GOES_FROM =
	        Pattern.compile("\\bgo(es)?\\s+from\\b");

	private static final Pattern DELTA_CHANGES_FROM =
	        Pattern.compile("\\bchange(s|d)?\\s+from\\b");

	private static final Pattern DELTA_INCREASE_BY =
	        Pattern.compile("\\bincrease(s|d)?\\s+by\\s+[-+]?\\d+(\\.\\d+)?\\s*(%|°|degrees?|celsius|mm|m|km)?\\b");

	private static final Pattern DELTA_DECREASE_BY =
	        Pattern.compile("\\bdecrease(s|d)?\\s+by\\s+[-+]?\\d+(\\.\\d+)?\\s*(%|°|degrees?|celsius|mm|m|km)?\\b");

	private static final Pattern DELTA_CHANGE_BY =
	        Pattern.compile("\\bchange(s|d)?\\s+by\\s+[-+]?\\d+(\\.\\d+)?\\s*(%|°|degrees?|celsius|mm|m|km)?\\b");
	
	private static final Pattern DELTA_WHAT_IF =
	        Pattern.compile("\\bwhat if\\b.+\\b(increase|increases|increased|decrease|decreases|decreased|change|changes|changed)\\s+by\\b");
	
	public boolean matchesRules(String q) {
    	
    	boolean matches = super.matchesRules(q);
    	if (!matches) {
    		matches = 
    				DELTA_FROM_TO.matcher(q).find()
    	            || DELTA_GOES_FROM.matcher(q).find()
    	            || DELTA_CHANGES_FROM.matcher(q).find()
    	            || DELTA_INCREASE_BY.matcher(q).find()
    	            || DELTA_DECREASE_BY.matcher(q).find()
    	            || DELTA_CHANGE_BY.matcher(q).find()
    	            || DELTA_WHAT_IF.matcher(q).find();
    		
    		if (matches) return true;
    		
    	}
        
    	return false;
    }

	public DeltaDetector(Ollama llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	//String query = "hello man. I would like to have information on the current temperature"; 
	    	String query = "what if biodiversity increases by 1%";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Ollama llm = new Ollama();
	    	
	    	DeltaDetector gdt = new DeltaDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

	
}

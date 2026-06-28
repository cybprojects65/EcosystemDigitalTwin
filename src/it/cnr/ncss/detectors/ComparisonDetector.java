package it.cnr.ncss.detectors;


import java.io.File;
import java.util.regex.Pattern;

import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;


public class ComparisonDetector extends AbstractDetector{

	
	
	@Override
	public File getReference() {
		
		return new File("prompting/comparison.txt");
		
	}

	@Override
	public double getSimilarityThreshold() {
		return 0.8;
	}
    
	
	private static final Pattern COMPARISON_COMPARE =
	        Pattern.compile("\\bcompare\\b");

	private static final Pattern COMPARISON_VS =
	        Pattern.compile("\\bvs\\b|\\bversus\\b");

	private static final Pattern COMPARISON_HIGHER =
	        Pattern.compile("\\bhigher\\b");

	private static final Pattern COMPARISON_LOWER =
	        Pattern.compile("\\blower\\b");

	private static final Pattern COMPARISON_MORE_LESS =
	        Pattern.compile("\\bmore\\b.+\\bthan\\b|\\bless\\b.+\\bthan\\b");

	private static final Pattern COMPARISON_BETTER_WORSE =
	        Pattern.compile("\\bbetter\\b.+\\bthan\\b|\\bworse\\b.+\\bthan\\b");

	private static final Pattern COMPARISON_DIFFERENCE =
	        Pattern.compile("\\bdifference\\b.+\\bbetween\\b");

	private static final Pattern COMPARISON_WHICH =
	        Pattern.compile("\\bwhich\\b.+\\b(riskier|higher|lower|better|worse)\\b");
	
	public boolean matchesRules(String q) {
    	
    	boolean matches = super.matchesRules(q);
    	if (!matches) {
    		matches = 
    				COMPARISON_COMPARE.matcher(q).find()
    	            || COMPARISON_VS.matcher(q).find()
    	            || COMPARISON_HIGHER.matcher(q).find()
    	            || COMPARISON_LOWER.matcher(q).find()
    	            || COMPARISON_MORE_LESS.matcher(q).find()
    	            || COMPARISON_BETTER_WORSE.matcher(q).find()
    	            || COMPARISON_DIFFERENCE.matcher(q).find()
    	            || COMPARISON_WHICH.matcher(q).find();
    		
    		if (matches) return true;
    		
    	}
        
    	return false;
    }

	public ComparisonDetector(Llm llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	//String query = "hello man. I would like to have information on the current temperature"; 
	    	String query = "Will biodiversity get better in the future?";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Llm llm = new Llm();
	    	
	    	ComparisonDetector gdt = new ComparisonDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

	
}

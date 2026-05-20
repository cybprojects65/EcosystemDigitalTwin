package it.cnr.ncss.detectors;


import java.io.File;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;


public class GreetingsDetector extends AbstractDetector{

	
	
	@Override
	public File getReference() {
		
		return new File("prompting/greetings.txt");
		
	}

	@Override
	public double getSimilarityThreshold() {
		return 0.8;
	}
    
	public GreetingsDetector(Ollama llm) throws Exception {
		super(llm);
	}

		public static void main(String [] args) throws Exception{
	    	
	    	//String query = "hello man. I would like to have information on the current temperature"; 
	    	String query = "hello man.";
	    	
	    	query = StringUtilsDTO.normalizeQuery(query);
	    	
	    	Ollama llm = new Ollama();
	    	
	    	GreetingsDetector gdt = new GreetingsDetector(llm);
	    	
	    	double result = gdt.matches(query);
	    	
	    	System.out.println("Caught: "+result);
	    	
	    }

		
}

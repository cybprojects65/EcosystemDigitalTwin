package it.cnr.ncss.detectors;

import java.io.File;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;

public abstract class AbstractDetector {

	public File referenceFile = null;
	public double similarityThreshold;
	String [] referenceStrings = null;
	public Ollama llm;
	
	
	public AbstractDetector(Ollama llm) throws Exception {
		this.llm = llm;
		referenceStrings = StringUtilsDTO.getLines(getReference());
		this.similarityThreshold = getSimilarityThreshold();
	}
	
	public abstract File getReference();
	
	public abstract double getSimilarityThreshold();
	
	
	public double matches(String q) {
        //String q = StringUtilsDTO.normalizeQuery(query);

        if (q.isBlank()) {
            return 0;
        }
        
        if (matchesRules(q)) {
        	System.out.println("[AbstractDetector] exact match");
            return 1;
        }
        System.out.println("[[AbstractDetector]] checking semantics");
        return matchesSemantics(q);
    }

    public boolean matchesRules(String q) {
    	
    	for (String s:referenceStrings) {
    		if (s.equals(q))
    			return true;
    	}
        
    	return false;
    }

    public double matchesSemantics(String q) {
        try {
            double[] queryEmbedding = llm.embed(q,false);
            double bestScore = -1.0;
            
            for (String example : referenceStrings) {
            	//System.out.println("[AbstractDetector] getting embedding");
            	double[] exampleEmbedding = llm.embed(example,true);
            	//System.out.println("[AbstractDetector] got embedding");
                double score = StringUtilsDTO.cosineSimilarity(queryEmbedding, exampleEmbedding);
                //System.out.println("vs "+example+" = "+score);
                if (score > bestScore) {
                    bestScore = score;
                }
                if (bestScore >= similarityThreshold) {
                	System.out.println("similarity found for: "+example);
                	break;
                }
            }
            
            llm.cacheEmbedding();
            
            System.out.println("[AbstractDetector] matchesSemantics - Max score: "+bestScore);
            return bestScore;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
    
	
}

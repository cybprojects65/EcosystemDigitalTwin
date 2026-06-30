package it.cnr.ncss.detectors;

import java.io.File;

import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;

public abstract class AbstractDetector {

	public File referenceFile = null;
	public double similarityThreshold;
	String [] referenceStrings = null;
	public Llm llm;
	
	
	public AbstractDetector(Llm llm) throws Exception {
		this.llm = llm;
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
    	
    	if (referenceStrings ==null) {
			try {
				referenceStrings = StringUtilsDTO.getLines(getReference());
			} catch (Exception e) {
				e.printStackTrace();
			}
    	}
    	
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
            
            if (referenceStrings ==null) {
    			try {
    				referenceStrings = StringUtilsDTO.getLines(getReference());
    			} catch (Exception e) {
    				e.printStackTrace();
    			}
        	}
            
            this.similarityThreshold = getSimilarityThreshold();
            
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

package it.cnr.ncss.embeddings;

import java.util.HashMap;

public class Embedding {
//to be called by Ollama class

	HashMap<String,double []> embeds = new HashMap<String,double []>();
	
	public void add(String query, double [] vector) {
		embeds.put(query, vector);
	}
	public double [] get(String query) {
		return embeds.get(query);
	}
}

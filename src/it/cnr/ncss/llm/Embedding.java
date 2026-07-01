package it.cnr.ncss.llm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;

public class Embedding {
//to be called by Ollama class

	HashMap<String,double []> embeds = new HashMap<String,double []>();
	int initialEmbedding = 0;
		
	public static File cacheFile = new File("./cache/embedding_cache.bin");
	
	public Embedding() throws Exception{
		
		if (cacheFile.exists())
			decache();
	}
	
	public void add(String query, double [] vector) {
		embeds.put(query, vector);
		
	}
	public double [] get(String query) {
		return embeds.get(query);
	}
	
	public void cache() throws Exception {
		
		if (embeds != null && !(embeds.size()==initialEmbedding)) {
			System.out.println("[DEBUG] SAVING EMBEDDING TO CACHE");
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile));
			oos.writeObject(embeds);
			oos.close();
			initialEmbedding = embeds.size();
		}
		//else
			//System.out.println("[DEBUG] CACHE ALREADY FULFILLED");
	}
	
	public void decache() throws Exception{
		ObjectInputStream ooi = new ObjectInputStream(new FileInputStream(cacheFile));
		embeds = (HashMap<String,double []>) ooi.readObject();
		ooi.close();
		initialEmbedding=embeds.size();
	}
	
}

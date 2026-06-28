package it.cnr.ncss.llm;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.embeddings.Embedding;
import it.cnr.ncss.rag.Rag;
import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.JSONParser;
import it.cnr.ncss.utils.StringUtilsDTO;


public class Llm {

	OllamaModel modelInUse=null;
	Embedding embedder = null;
	Config config = new Config();
	
	Rag rag = null;
	
	public Llm() throws Exception{
		embedder = new Embedding();
		config = new Config();
	}
	
	public String getProperty(String property) {
		return  config.getProperty(property);
	}
	public OllamaTagResponse listModels() throws Exception {

		HttpClient client = HttpClient.newHttpClient();

	    HttpRequest request = HttpRequest.newBuilder()
	            .uri(URI.create("http://localhost:11434/api/tags"))
	            .GET()
	            .build();

	    HttpResponse<String> response =
	            client.send(request, HttpResponse.BodyHandlers.ofString());

	    //System.out.println(response.body()); // debug first

	    ObjectMapper mapper = new ObjectMapper();

	    OllamaTagResponse tags =
	            mapper.readValue(response.body(), OllamaTagResponse.class);
/*
	    if (tags.models != null) {
	        for (OllamaModel model : tags.models) {
	            System.out.println("model name: "+model.name);
	        }
	    }
*/
	    return tags;
	}
	
	
	
	public void cacheEmbedding() throws Exception{
		embedder.cache();
	}
	
	public double[] embed(String text) throws Exception {
		return embed(text,true);
	}
	
	
	public double[] embed(String text, boolean memorycache) throws Exception {
		if (embedder == null)
			embedder = new Embedding();
		
		double[] vectorcached = embedder.get(text);
		if (vectorcached!=null)
			return vectorcached;
		else {
	    ObjectMapper mapper = new ObjectMapper();
	    text = StringUtilsDTO.normalizeQuery(text);
	    
	    String json = mapper.writeValueAsString(
	            java.util.Map.of(
	                    "model", "nomic-embed-text",
	                    "input", text
	            )
	    );

	    HttpClient client = HttpClient.newHttpClient();

	    HttpRequest request = HttpRequest.newBuilder()
	            .uri(URI.create("http://localhost:11434/api/embed"))
	            .header("Content-Type", "application/json")
	            .POST(HttpRequest.BodyPublishers.ofString(json))
	            .build();

	    HttpResponse<String> response =
	            client.send(request, HttpResponse.BodyHandlers.ofString());

	    var root = mapper.readTree(response.body());

	    var embeddingNode = root
	            .get("embeddings")
	            .get(0);

	    double[] vector = new double[embeddingNode.size()];

	    for (int i = 0; i < embeddingNode.size(); i++) {
	        vector[i] = embeddingNode.get(i).asDouble();
	    }
	    if (memorycache)
	    	embedder.add(text, vector);
	    
	    return vector;
		}
	}
	
	public class OllamaGenerateRequest {

	    public String model;
	    public String prompt;
	    public boolean stream;
	}
	
	 public String send(String query) throws Exception{
		 
		 if (modelInUse==null) {
			 OllamaTagResponse models =  listModels();
			 OllamaModel firstmodel = null;
			 if (models != null) {
			        for (OllamaModel model : models.models) {
			            if (model.name.startsWith("llama"))
			            	firstmodel = model;
			        }
			    }
			 
			 modelInUse=firstmodel;
		 }
		 System.out.println("[OLLAMA] model: "+modelInUse.name);
		 
		 ObjectMapper mapper = new ObjectMapper();

		 OllamaGenerateRequest req = new OllamaGenerateRequest();

		 req.model = modelInUse.name;
		 req.prompt = query;
		 req.stream = false;

		 String json = mapper.writeValueAsString(req);

		 HttpClient client = HttpClient.newHttpClient();

	     HttpRequest request = HttpRequest.newBuilder()
	                .uri(URI.create("http://localhost:11434/api/generate"))
	                .header("Content-Type", "application/json")
	                .POST(HttpRequest.BodyPublishers.ofString(json))
	                .build();

	     System.out.println("[OLLAMA] sending query");
	     long t0 = System.currentTimeMillis();
	     
	        HttpResponse<String> response =
	                client.send(request, HttpResponse.BodyHandlers.ofString());

	     long t1 = System.currentTimeMillis();
	     System.out.println("[OLLAMA] answer generated in "+(t1-t0)+"ms");
	        
	        String jsonResponse = response.body();
	        OllamaResponse responseObj = JSONParser.parseOllamaResponse(jsonResponse);
	        
	        if (!responseObj.done) {
	        	
	        	System.out.println("Issue with Ollama server: "+responseObj.done_reason);
	        	
	        }
	        
	        return responseObj.response;
	 }
	 
	 public List<String> retrieveDocuments(String query, String collection, File localrepo, int top_k, double similarity) throws Exception{
		 if (rag ==null)
			 rag = new Rag(this);
		 
		 return rag.retrieveDocuments(query,collection, localrepo, top_k, similarity);
	 }
	 

	 public Object sendRequestWithJsonOutput(String question, String promptFile, Class<?> outputClass) throws Exception{
	 	String prompt = buildPrompt(question, null, promptFile);
		String json_entities = send(prompt);
		System.out.println("[LLM] json received: " + json_entities);
		ObjectMapper mapper = new ObjectMapper();
		Object entity = mapper.readValue(json_entities, outputClass);
		return entity;
	 }
	 
	 
	 public String buildPrompt(String query, List<String> documents, String promptFile) throws Exception {

			String context = "";
			if (documents != null) {
				context = String.join("\n\n", documents.stream().toList());
			}
			String legacyText = StringUtilsDTO.getText(new File(promptFile));
			legacyText = legacyText.replace("#QUERY#", query);
			legacyText = legacyText.replace("#CONTEXT#", context);

			String prompt = """
					%s
					""".formatted(legacyText);

			return prompt;
		}
	 
		public String normalizeFeatureName(String question) throws Exception {

			// get the header
			String header = StringUtilsDTO.readFirstLine(config.getProperty("knowledge_base_data").replace("\"", ""));
			String headers[] = header.split(",");
			double threshold_for_feature_name_similarity=Double.parseDouble(config.getProperty("threshold_for_feature_name_similarity"));
			double[] queryEmbedding = embed(question, false);

			// get the most similar features
			String normalized = null;
			double maxscore = 0;
			for (String head : headers) {

				// similarity
				double[] exampleEmbedding = embed(head, true);
				double score = StringUtilsDTO.cosineSimilarity(queryEmbedding, exampleEmbedding);
				// get the feautres values
				System.out.println("[F-NORMALIZATION] feature " + head + " vs " + question + ": " + score);

				if (score > threshold_for_feature_name_similarity) {
					if (score > maxscore) {
						maxscore = score;
						normalized = head;
					}
				}
			}

			cacheEmbedding();

			return normalized;
		}
		
	 public static void main(String[] args) throws Exception {
		 String query = "translate from english to italian: sky appears blue because of a phenomenon called Rayleigh scattering, named after the British physicist Lord Rayleigh";
		 Llm ollama = new Llm();
		 
		 String answer = ollama.send(query);
		 
		 System.out.println("A:"+answer);
		 
	 }
	 
	 
	 public static void main1(String[] args) throws Exception {

	        String json = """
	        {
	          "model": "llama3.2",
	          "prompt": "Why is the sky blue?",
	          "stream": false
	        }
	        """;

	        HttpClient client = HttpClient.newHttpClient();

	        HttpRequest request = HttpRequest.newBuilder()
	                .uri(URI.create("http://localhost:11434/api/generate"))
	                .header("Content-Type", "application/json")
	                .POST(HttpRequest.BodyPublishers.ofString(json))
	                .build();

	        HttpResponse<String> response =
	                client.send(request, HttpResponse.BodyHandlers.ofString());

	        String jsonResponse = response.body();
	        OllamaResponse responseObj = JSONParser.parseOllamaResponse(jsonResponse);
	        System.out.println("#####################");
	        System.out.println(responseObj.asString());
	        System.out.println("#####################");
	        //System.out.println(jsonResponse);
	    }

	
}

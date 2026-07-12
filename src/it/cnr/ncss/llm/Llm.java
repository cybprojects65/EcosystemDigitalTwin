package it.cnr.ncss.llm;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.rag.Rag;
import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.StringUtilsDTO;

public class Llm {

	OllamaModel modelInUse = null;
	static Embedding embedder = null;
	Config config = new Config();

	Rag rag = null;

	public Llm() throws Exception {
		embedder = new Embedding();
		config = new Config();
		OllamaModel model = new OllamaModel();
		model.name = config.getProperty("llm_model");
		model.address = config.getProperty("llm_address");
		model.serviceType = config.getProperty("llm_service_type");
		model.embedderName = config.getProperty("embedder_model_name");
		model.embedderAddress = config.getProperty("embedder_address");
		model.token = config.getProperty("llm_token");
		modelInUse = model;

	}

	public String getProperty(String property) {
		return config.getProperty(property);
	}

	/*
	 * public OllamaTagResponse listModels() throws Exception {
	 * 
	 * HttpClient client = HttpClient.newHttpClient();
	 * 
	 * HttpRequest request = HttpRequest.newBuilder()
	 * .uri(URI.create("http://localhost:11434/api/tags")) .GET() .build();
	 * 
	 * HttpResponse<String> response = client.send(request,
	 * HttpResponse.BodyHandlers.ofString());
	 * 
	 * //System.out.println(response.body()); // debug first
	 * 
	 * ObjectMapper mapper = new ObjectMapper();
	 * 
	 * OllamaTagResponse tags = mapper.readValue(response.body(),
	 * OllamaTagResponse.class);
	 * 
	 * return tags; }
	 * 
	 */

	public void cacheEmbedding() throws Exception {
		embedder.cache();
	}

	public double[] embed(String text) throws Exception {
		return embed(text, true);
	}

	public double[] embed(String text, boolean memorycache) throws Exception {

		double[] vectorcached = embedder.get(text);
		if (vectorcached != null)
			return vectorcached;
		else {
			ObjectMapper mapper = new ObjectMapper();
			// text = StringUtilsDTO.normalizeQuery(text);

			String json = mapper.writeValueAsString(java.util.Map.of("model", modelInUse.embedderName, "input", text));

			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create("http://" + modelInUse.embedderAddress + "/api/embed"))
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			var root = mapper.readTree(response.body());

			var embeddingNode = root.get("embeddings").get(0);

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
		public Map<String, Object> options;
		
	}

	public String send(String query) throws Exception {

		System.out.println("[OLLAMA] model: " + modelInUse.name);

		ObjectMapper mapper = new ObjectMapper();

		OllamaGenerateRequest req = new OllamaGenerateRequest();

		req.model = modelInUse.name;
		req.prompt = query;
		req.stream = false;
		req.options = new HashMap<>();
		//req.options.put("num_ctx", 32768);
		req.options.put("num_ctx", Integer.parseInt(config.getProperty("num_ctx")));
		//req.options.put("num_predict", 4096);
		req.options.put("num_predict", Integer.parseInt(config.getProperty("num_predict")));
		req.options.put("temperature", Integer.parseInt(config.getProperty("temperature")));
		req.options.put("top_p", Integer.parseInt(config.getProperty("top_p")));
		req.options.put("seed", 42);
		
		String json = mapper.writeValueAsString(req);

		HttpClient client = HttpClient.newHttpClient();
		String llm_url = "http://" + modelInUse.address + "/api/generate";
		if (modelInUse.token.length()>0) {
			llm_url += "?token="+modelInUse.token;
			llm_url = llm_url.replace("http://", "https://");
		}
		
		System.out.println("[LLM] JSON request chars: " + json.length());
		System.out.println("[LLM] JSON request bytes UTF-8: " + json.getBytes(StandardCharsets.UTF_8).length);
		
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(llm_url))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

		System.out.println("[LLM] sending query");
		long t0 = System.currentTimeMillis();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		long t1 = System.currentTimeMillis();
		System.out.println("[LLM] answer generated in " + (t1 - t0) + "ms");

		String jsonResponse = response.body();
		OllamaResponse responseObj = parseOllamaResponse(jsonResponse);

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
		    throw new RuntimeException(
		        "LLM server returned HTTP " + response.statusCode() + ": " + response.body()
		    );
		}
		
		if (!responseObj.done) {

			System.out.println("Issue with llm server: " + responseObj.done_reason);

		}

		System.out.println("[LLM] Java prompt chars: " + query.length());
		System.out.println("[LLM] Java prompt bytes UTF-8: " + query.getBytes(StandardCharsets.UTF_8).length);
		System.out.println("[LLM] prompt_eval_count: " + responseObj.prompt_eval_count);
		System.out.println("[LLM] response chars: " + responseObj.response.length());
		System.out.println("[LLM] eval_count: " + responseObj.eval_count);
		
		return responseObj.response;
	}
	
	public static OllamaResponse parseOllamaResponse(String json) throws Exception {

        ObjectMapper mapper = new ObjectMapper();

        OllamaResponse result =
                mapper.readValue(json, OllamaResponse.class);

        //System.out.println(result.response);
        
        return result;
    }

	public List<String> retrieveDocuments(String query, String collection, File localrepo, int top_k, double similarity)
			throws Exception {
		if (rag == null)
			rag = new Rag(this);

		return rag.retrieveDocuments(query, collection, localrepo, top_k, similarity);
	}

	public String sendRequestWithJsonOutputString(String question, String promptFile) throws Exception {
		String prompt = buildPrompt(question, null, promptFile);
		String json_entities = send(prompt);
		System.out.println("[LLM] json received: " + json_entities);
		return json_entities;
	}

	public Object sendRequestWithJsonOutput(String question, String promptFile, Class<?> outputClass) throws Exception {
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
		legacyText = legacyText.replace("{{QUERY}}", query);
		legacyText = legacyText.replace("{{CONTEXT}}", context);

		String prompt = """
				%s
				""".formatted(legacyText);

		return prompt;
	}

	public String normalizeFeatureName(String question) throws Exception {

		// get the header
		KbManager kb = new KbManager();
		String headers[] = kb.getAllFeatures();

		double threshold_for_feature_name_similarity = Double
				.parseDouble(config.getProperty("threshold_for_feature_name_similarity"));
		double[] queryEmbedding = embed(question.toLowerCase(), false);

		// get the most similar features
		String normalized = null;
		double maxscore = 0;
		for (String head : headers) {

			// similarity
			double[] exampleEmbedding = embed(head.toLowerCase(), true);
			double score = StringUtilsDTO.cosineSimilarity(queryEmbedding, exampleEmbedding);
			// get the feautres values
			// System.out.println("[F-NORMALIZATION] feature " + head + " vs " + question +
			// ": " + score);

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

		System.out.println("A:" + answer);

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

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:11434/api/generate"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

		String jsonResponse = response.body();
		OllamaResponse responseObj = parseOllamaResponse(jsonResponse);
		System.out.println("#####################");
		System.out.println(responseObj.asString());
		System.out.println("#####################");
		// System.out.println(jsonResponse);
	}

}

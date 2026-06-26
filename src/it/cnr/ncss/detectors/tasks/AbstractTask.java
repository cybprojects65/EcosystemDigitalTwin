package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.util.List;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.StringUtilsDTO;

public class AbstractTask {

	public Ollama ollama;
	public String fallbackFile = "./task_prompts/abstract_answer_fallback.txt";
	public String legacyFile = "./task_prompts/abstract_answer.txt";
	public String referenceCollection = "pdf_documents";
	public String referenceFolder = "./pdfs/";
	public Config conf = new Config();

	public int top_k = 8;
	public double similarity = 0.4;

	public AbstractTask(Ollama ollama) throws Exception {

		top_k = Integer.parseInt(conf.getProperty("top_k"));
		similarity = Double.parseDouble(conf.getProperty("similarity"));

		this.ollama = ollama;
	}

	public String handle(String question) throws Exception {

		System.out.println("\n========== HANDLING GENERAL QUESTION ==========");

		String response;

		try {
			String ragResponse = generateAnswer(question);

			if (ragResponse == null || ragResponse.contains("No relevant information")) {
				System.out.println("[ATASK] Switching to LLM fallback");
				response = generateFallback(question);
			} else {
				response = ragResponse;
			}

		} catch (Exception e) {
			System.out.println("[ATASK ERROR] " + e.getMessage());
			response = generateFallback(question);
		}

		return response;

	}

	public String generateFallback(String question) throws Exception {
		String fallbackText = StringUtilsDTO.getText(new File(fallbackFile));
		fallbackText = fallbackText.replace("#QUERY#", question);

		String prompt = """
				%s
				""".formatted(fallbackText);

		try {
			String response = ollama.send(prompt);

			if (response != null && !response.isBlank()) {
				return response.trim();
			}

		} catch (Exception e) {
			System.out.println("[LLM FALLBACK ERROR] " + e.getMessage());
		}

		return "Hello! I'm the Digital Twin assistant. "
				+ "I can help you explore ecosystem risk, biodiversity, and environmental changes.";
	}

	public String generateAnswer(String query) {

		System.out.println("\n[RAG-GENERAL] START");

		try {
			List<String> docs = ollama.retrieveDocuments(query, referenceCollection, new File(referenceFolder), top_k,
					similarity);

			System.out.println("[RAG-GENERAL] Retrieved docs: " + docs.size());

			if (docs.isEmpty()) {
				docs = null;
			}

			String prompt = buildPrompt(query, docs);

			String raw = ollama.send(prompt);

			if (raw == null || raw.isBlank() || raw.contains("Interpretation not available")) {
				return "The system could not generate a reliable answer from the available data.";
			}

			return raw.trim();

		} catch (Exception e) {
			System.out.println("[RAG-GENERAL ERROR] " + e.getMessage());
			return "An error occurred while generating the answer.";
		}
	}

	public String buildPrompt(String query, List<String> documents) throws Exception {

		String context = "";
		if (documents != null) {
			context = String.join("\n\n", documents.stream().toList());
		}
		String legacyText = StringUtilsDTO.getText(new File(legacyFile));
		legacyText = legacyText.replace("#QUERY#", query);
		legacyText = legacyText.replace("#CONTEXT#", context);

		String prompt = """
				%s
				""".formatted(legacyText);

		return prompt;
	}

}

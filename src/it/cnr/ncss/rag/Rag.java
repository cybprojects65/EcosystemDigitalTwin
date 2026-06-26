package it.cnr.ncss.rag;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.llm.Ollama;

public class Rag {

	private static final String CHROMA_URL = "http://localhost:8000";
	//private String COLLECTION_NAME = "pdf_documents";
	//private File localrepository = null;
	private static final String CHROMA_BASE = CHROMA_URL + "/api/v2/tenants/default_tenant/databases/default_database";
	private static final String COLLECTIONS_URL = CHROMA_BASE + "/collections";
	private final HttpClient http = HttpClient.newHttpClient();
	private final ObjectMapper mapper = new ObjectMapper();
	private Ollama llm;

	public Rag(Ollama llm) {
		this.llm = llm;
		//this.COLLECTION_NAME = COLLECTION_NAME;
		//this.localrepository = localrepo;
	}

	private boolean collectionExists(String name) throws Exception {

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(COLLECTIONS_URL)).GET().build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() >= 300) {
			throw new RuntimeException(
					"Chroma collection list failed: " + response.statusCode() + " - " + response.body());
		}

		JsonNode root = mapper.readTree(response.body());

		for (JsonNode collection : root) {
			if (collection.has("name") && collection.get("name").asText().equals(name)) {
				return true;
			}
		}

		return false;
	}

	private String createCollection(String name) throws Exception {

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", name);
		payload.put("get_or_create", false);

		String json = mapper.writeValueAsString(payload);

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(COLLECTIONS_URL))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() >= 300) {
			throw new RuntimeException(
					"Chroma collection creation failed: " + response.statusCode() + " - " + response.body());
		}

		JsonNode root = mapper.readTree(response.body());
		return root.get("id").asText();
	}

	public void ingestFolder(String COLLECTION_NAME, File folder) throws Exception {

		if (collectionExists(COLLECTION_NAME)) {
			System.out.println("Collection already exists: " + COLLECTION_NAME);
			System.out.println("Skipping ingestion.");
			return;
		}

		String collectionId = createCollection(COLLECTION_NAME);

		File[] pdfFiles = folder.listFiles(file -> file.isFile() && file.getName().toLowerCase().endsWith(".pdf"));

		if (pdfFiles == null || pdfFiles.length == 0) {
			System.out.println("No PDF files found.");
			return;
		}

		for (File pdf : pdfFiles) {
			System.out.println("Importing: " + pdf.getName());

			String text = extractText(pdf);
			List<String> chunks = chunkText(text, 1200);

			for (int i = 0; i < chunks.size(); i++) {
				String chunk = chunks.get(i);

				if (chunk.isBlank()) {
					continue;
				}

				double[] embedding = llm.embed(chunk,false);

				String id = pdf.getName() + "_chunk_" + i;

				addToChroma(collectionId, id, chunk, embedding, Map.of("source", pdf.getName(), "chunk", i));
			}
		}

		System.out.println("Ingestion completed.");
	}

	private String extractText(File pdfFile) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdfFile)) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(document);
		}
	}

	private List<String> chunkText(String text, int maxChars) {
		List<String> chunks = new ArrayList<>();

		String clean = text.replaceAll("\\s+", " ").trim();

		for (int start = 0; start < clean.length(); start += maxChars) {
			int end = Math.min(start + maxChars, clean.length());
			chunks.add(clean.substring(start, end));
		}

		return chunks;
	}

	private void addToChroma(String collectionId, String id, String document, double[] embedding,
			Map<String, Object> metadata) throws Exception {

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("ids", List.of(id));
		payload.put("documents", List.of(document));
		payload.put("embeddings", List.of(embedding));
		payload.put("metadatas", List.of(metadata));

		String json = mapper.writeValueAsString(payload);

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(COLLECTIONS_URL + "/" + collectionId + "/add"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() >= 300) {
			throw new RuntimeException("Chroma add failed: " + response.body());
		}
	}

	private String getCollectionId(String name) throws Exception {

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(COLLECTIONS_URL)).GET().build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() >= 300) {
			throw new RuntimeException("Chroma collection list failed: " + response.body());
		}

		JsonNode root = mapper.readTree(response.body());

		for (JsonNode collection : root) {
			if (collection.has("name") && collection.get("name").asText().equals(name)) {
				return collection.get("id").asText();
			}
		}

		return null;
	}

	private int countDocuments(String collectionId) throws Exception {

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(COLLECTIONS_URL + "/" + collectionId + "/count"))
				.GET().build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() >= 300) {
			throw new RuntimeException("Chroma count failed: " + response.body());
		}

		return Integer.parseInt(response.body());
	}

	public List<String> retrieveDocuments(String query, String COLLECTION_NAME, File localrepository, int TOP_K, double SIMILARITY_THRESHOLD) throws Exception {

		String collectionId = getCollectionId(COLLECTION_NAME);

		if (collectionId == null && localrepository!=null) {
			System.out.println("[RAG] Importing documents to Chroma DB");
			ingestFolder(COLLECTION_NAME, localrepository);
			collectionId = getCollectionId(COLLECTION_NAME);
			// return List.of();
		} else {
			System.out.println("[RAG] Calling RAG");
		}

		if (countDocuments(collectionId) == 0) {
			return List.of();
		}

		Map<String, RetrievedDocument> unique = new LinkedHashMap<>();

		double[] embedding = llm.embed(query,false);

		List<RetrievedDocument> results = queryChroma(collectionId, embedding, TOP_K);

		for (RetrievedDocument doc : results) {
			if (doc.distance <= SIMILARITY_THRESHOLD) {
				RetrievedDocument existing = unique.get(doc.text);

				if (existing == null || doc.distance < existing.distance) {
					unique.put(doc.text, doc);
				}
			}
		}

		return unique.values().stream().sorted(Comparator.comparingDouble(d -> d.distance)).map(d -> d.text).toList();
	}

	private record RetrievedDocument(String text, double distance) {
	}

	private List<RetrievedDocument> queryChroma(String collectionId, double[] embedding, int topK) throws Exception {

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("query_embeddings", List.of(embedding));
		payload.put("n_results", topK);

		String json = mapper.writeValueAsString(payload);

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(COLLECTIONS_URL + "/" + collectionId + "/query"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() >= 300) {
			throw new RuntimeException("Chroma query failed: " + response.body());
		}

		JsonNode root = mapper.readTree(response.body());

		JsonNode documents = root.get("documents").get(0);
		JsonNode distances = root.get("distances").get(0);

		List<RetrievedDocument> results = new ArrayList<>();

		for (int i = 0; i < documents.size(); i++) {
			results.add(new RetrievedDocument(documents.get(i).asText(), distances.get(i).asDouble()));
		}

		return results;
	}

	public static void main(String[] args) throws Exception {
		Ollama llm = new Ollama();
		Rag ingestor = new Rag(llm);
		ingestor.ingestFolder("pdf_documents",new File("./pdfs/"));
	}

}

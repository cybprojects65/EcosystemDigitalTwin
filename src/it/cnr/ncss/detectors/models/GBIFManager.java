package it.cnr.ncss.detectors.models;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.StringUtilsDTO;

public class GBIFManager {

	private static final String GBIF_API = "https://api.gbif.org/v1";
	private static final int GBIF_MAX_PAGE_SIZE = 300;

	private final HttpClient http;
	private final ObjectMapper mapper;

	public GBIFManager() {
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
		this.mapper = new ObjectMapper();
	}

	/**
	 * Common names are searched through /species/search?q=... The first accepted
	 * species-level match is preferred.
	 */
	private TaxonMatch resolveTaxon(String commonName) throws IOException, InterruptedException {

		String url = GBIF_API + "/species/search" + "?q=" + enc(commonName) + "&rank=SPECIES" + "&limit=20";

		JsonNode json = getJson(url);
		JsonNode results = json.path("results");

		if (!results.isArray() || results.isEmpty()) {
			return null;
		}

		JsonNode best = null;
		double best_MED = 1;
		for (JsonNode node : results) {
			String status = node.path("taxonomicStatus").asText("");
			if ("ACCEPTED".equalsIgnoreCase(status)) {

				String scientificName = node.path("scientificName").asText();

				double med = StringUtilsDTO.normalizedMinimumEditDistance(scientificName, commonName);
				if (med < best_MED) {
					best = node;
					best_MED = med;
				}

				JsonNode vernacularNames = node.path("vernacularNames");

				if (vernacularNames.isArray()) {
					for (JsonNode vernacular : vernacularNames) {
						String name = vernacular.path("vernacularName").asText();

						if (!name.isBlank()) {
							//System.out.println(name);

							med = StringUtilsDTO.normalizedMinimumEditDistance(commonName, name);
							if (med < best_MED) {
								best = node;
								best_MED = med;
							}
						}
					}

				}

				// break;
			}
		}

		if (best == null) {
			best = results.get(0);
		}

		/*
		url = GBIF_API + "/species/match?name=" + enc(best.path("scientificName").asText("")) + "&rank=SPECIES";
		json = getJson(url);
		results = json.path("results");
		best = results.get(0);
*/
		//TaxonMatch(int taxonKey, String scientificName, String canonicalName, String rank, String status)
		
		return new TaxonMatch(Integer.parseInt(best.path("key").asText("")),
				best.path("scientificName").asText(""),
				best.path("canonicalName").asText(""),
				best.path("rank").asText(""), 
				best.path("taxonomicStatus").asText("")
				);
	}

	public File downloadOccurrencesToCsv(String name) throws IOException, InterruptedException {

		TaxonMatch taxon = resolveTaxon(name);
		if (taxon==null)
				new IllegalArgumentException("No GBIF taxon match found for: " + name);

		Config conf = new Config();
		String cache = conf.getProperty("cache_folder");
		String wktPolygon = conf.getProperty("area_polygon");
		int maxRecords = Integer.parseInt(conf.getProperty("gbif_max_samples"));

		File outputCsv = new File(cache, taxon.scientificName.replace(" ", "_") + ".csv");

		
		if (outputCsv.exists()) {
			System.out.println("[GBIF] spp data taken from cache");
			return outputCsv;
		}
		validateWktPolygon(wktPolygon);
		int written = 0;
		int offset = 0;
		BufferedWriter writer = Files.newBufferedWriter(outputCsv.toPath(), StandardCharsets.UTF_8);
		writer.write("species,longitude,latitude\n");

		while (written < maxRecords) {
			int limit = Math.min(GBIF_MAX_PAGE_SIZE, maxRecords - written);

			String url = GBIF_API + "/occurrence/search" + "?scientificName=" + enc(taxon.scientificName()) + "&geometry="
					+ enc(wktPolygon) + "&limit=" + limit + "&offset=" + offset + "&hasCoordinate=true";

			JsonNode json = getJson(url);
			JsonNode results = json.path("results");

			if (!results.isArray() || results.isEmpty()) {
				break;
			}

			for (JsonNode occurrence : results) {
				writer.write(csv(occurrence.path("species").asText("")));
				writer.write(",");
				writer.write(csv(occurrence.path("decimalLongitude").asText("")));
				writer.write(",");
				writer.write(csv(occurrence.path("decimalLatitude").asText("")));
				writer.newLine();

				written++;

				if (written >= maxRecords) {
					break;
				}
			}
			offset += results.size();

			boolean endOfRecords = json.path("endOfRecords").asBoolean(false);
			if (endOfRecords) {
				break;
			}

			if (offset >= 100_000) {
				throw new IllegalStateException("GBIF occurrence/search has a 100,000 record offset limit. "
						+ "Use requestAsyncOccurrenceDownload(...) for larger downloads.");
			}
		}

		writer.close();
		System.out.println("Resolved taxon: " + taxon);
		System.out.println("WKT polygon: " + wktPolygon);
		System.out.println("Wrote " + written + " records to " + outputCsv.getAbsolutePath());
		return outputCsv;
	}

	private static void validateWktPolygon(String wktPolygon) {
		if (wktPolygon == null || wktPolygon.isBlank()) {
			throw new IllegalArgumentException("WKT polygon must not be null or blank.");
		}

		String normalized = wktPolygon.trim().toUpperCase();

		if (!normalized.startsWith("POLYGON") && !normalized.startsWith("MULTIPOLYGON")) {
			throw new IllegalArgumentException("GBIF geometry should be a WKT POLYGON or MULTIPOLYGON.");
		}
	}

	private JsonNode getJson(String url) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(60))
				.header("Accept", "application/json").GET().build();

		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("GBIF request failed. HTTP " + response.statusCode() + ": " + response.body());
		}

		return mapper.readTree(response.body());
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}

		boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
				|| value.contains("\r");
		String escaped = value.replace("\"", "\"\"");

		return needsQuotes ? "\"" + escaped + "\"" : escaped;
	}

	public record TaxonMatch(int taxonKey, String scientificName, String canonicalName, String rank, String status) {
	}

	public static void main(String[] args) throws Exception {
		GBIFManager gbif = new GBIFManager();
		Config conf = new Config();
		String areapoly = conf.getProperty("area_polygon");

		String common_name = "kingfisher";
		String scientific_name = "Alcedo atthis";

		gbif.downloadOccurrencesToCsv(common_name);

		/*
		 * String key = gbif.requestAsyncOccurrenceDownload( scientific_name, null,
		 * areapoly, )
		 */
		// Example 1: scientific name
		// gbif.downloadOccurrencesToCsv("Panthera leo", false,
		// Path.of("panthera_leo_occurrences.csv"), 1000, true);

		// Example 2: common name
		// gbif.downloadOccurrencesToCsv("lion", true, Path.of("lion_occurrences.csv"),
		// 1000, true);

		// Example 3: large GBIF download request
		// String key = gbif.requestAsyncOccurrenceDownload(
		// "Panthera leo",
		// false,
		// "your_gbif_username",
		// "your_gbif_password",
		// "you@example.com"
		// );
		// System.out.println("GBIF download key: " + key);
	}

}

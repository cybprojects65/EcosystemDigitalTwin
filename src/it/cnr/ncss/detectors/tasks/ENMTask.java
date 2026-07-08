package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.util.List;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.detectors.models.ASCManager;
import it.cnr.ncss.detectors.models.ENMManager;
import it.cnr.ncss.detectors.models.ENMManager.ENMData;
import it.cnr.ncss.detectors.models.GBIFManager;
import it.cnr.ncss.detectors.models.SpatialAnalysis;
import it.cnr.ncss.detectors.models.SpatialAnalysis.SpatialStats;
import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;

public class ENMTask extends AbstractTask {

	public ENMTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("enm_answer");
	}

	private String report;
	
	@Override
	public String handle(String question) throws Exception {


		System.out.println("[ENM Task] informationRetrieval through LLM");

		String information_extraction_json = llm.sendRequestWithJsonOutputString(question,
				this.conf.getProperty("enm_extraction_prompt"));
		
		ENMScenario enm_scenario = ENMScenario.fromJson(information_extraction_json);
		
		System.out.println("[ENM Task] species to process "+enm_scenario.species_name);
		
		GBIFManager gbif = new GBIFManager();
		
		File occurrenceData = gbif.downloadOccurrencesToCsv(enm_scenario.species_name);
		
		System.out.println("[ENM Task] species data taken from GBIF");
		
		KbManager kb = new KbManager();
		
		ASCManager manager = new ASCManager(kb);
		
		File asc_file_folder = manager.Kb2ASC();
		
		System.out.println("[ENM Task] training Maximum Entropy model for "+enm_scenario.species_name);
		ENMManager enm = new ENMManager(kb);
		ENMData enm_data = enm.trainMaxEnt(asc_file_folder, occurrenceData);
		
		System.out.println("[ENM Task] extracting spatial characterization");
		
		SpatialAnalysis spatial = new SpatialAnalysis();
		SpatialStats ecological_stats = spatial.clusterBinarySpace(enm_data.distribution_binarized());
		
		System.out.println("[ENM Task] summarising the extracted information:\n");
		
		report = "{\n"+
				"\"maximum_entropy_ecological_nice_model_results\":\n"+
				enm_data.results_json()+",\n"
				+"\"habitat_characterization\":\n"+
				ecological_stats.toJson()+
				"\n}";
		
		System.out.println(report);
		
		String answer = generateAnswer(question);
		return answer;
	}
	
	
	@Override
	public String buildPrompt(String query, List<String> docs, String promptFile) throws Exception {

		String context = "";
		if (docs != null) {
			context = String.join("\n\n", docs.stream().toList());
		}

		String promptText = StringUtilsDTO.getText(new File(answerFile));

		String knowledgejson = report;

		promptText = promptText.replace("{{KNOWLEDGE}}", knowledgejson);
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		promptText = promptText.replace("{{CONTEXT}}", context);

		String prompt = """
				%s
				""".formatted(promptText);

		//System.out.println("[ENM Task] prompt:\n" + prompt);
		return prompt;
	}
	
	public static class ENMScenario {
	    public String species_name;

	    public static ENMScenario fromJson(String json) throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	        json = cleanJson(json);

	        return mapper.readValue(json, ENMScenario.class);
	    }

	    private static String cleanJson(String json) {
	        if (json == null) {
	            return "";
	        }

	        json = json.trim();

	        if (json.startsWith("```")) {
	            json = json.replaceFirst("^```json\\s*", "");
	            json = json.replaceFirst("^```\\s*", "");
	            json = json.replaceFirst("\\s*```$", "");
	        }

	        int start = json.indexOf('{');
	        int end = json.lastIndexOf('}');

	        if (start >= 0 && end >= start) {
	            json = json.substring(start, end + 1);
	        }

	        return json.trim();
	    }

	    @Override
	    public String toString() {
	        return species_name;
	    }
	}
	
}

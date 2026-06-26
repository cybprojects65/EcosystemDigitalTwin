package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;

public class DriversTask extends AbstractTask {
	public String legacyFile = "./task_prompts/drivers_answer.txt";
	public String information_retrieval_File = "./task_prompts/drivers_entity_extraction.txt";
	public static double threshold = 0.6;

	public static class EntityDriver {
        public String target_variable;
        public String goal;
        public double confidence;

        public EntityDriver() {
        }
        public String toString() {
			String tos = target_variable + "|" + goal + "|" + confidence;
			System.out.println(tos);
			return tos;
		}
    }
	
	

	public DriversTask(Ollama ollama) throws Exception {
		super(ollama);
		super.legacyFile = this.legacyFile;
	}

	//TODO: move to another class
	public String normalizeFeatureName(String question) throws Exception {

		// get the header
		String header = StringUtilsDTO.readFirstLine(conf.getProperty("knowledge_base_data").replace("\"", ""));
		String headers[] = header.split(",");

		double[] queryEmbedding = ollama.embed(question, false);

		// get the most similar features
		String normalized = null;
		double maxscore = 0;
		for (String head : headers) {

			// similarity
			double[] exampleEmbedding = ollama.embed(head, true);
			double score = StringUtilsDTO.cosineSimilarity(queryEmbedding, exampleEmbedding);
			// get the feautres values
			System.out.println("[F-NORMALIZATION] feature " + head + " vs " + question + ": " + score);

			if (score > threshold) {
				if (score > maxscore) {
					maxscore = score;
					normalized = head;
				}
			}
		}

		ollama.cacheEmbedding();

		return normalized;
	}

	@Override
	public String handle(String question) throws Exception {
		System.out.println("\n========== HANDLING DRIVERS QUESTION ==========");

		System.out.println("[DRIVERS] informationRetrieval through LLM");
		//TODO: move to OLLAMA
		super.legacyFile = information_retrieval_File;
		String prompt = buildPrompt(question, null);
		String json_entities = ollama.send(prompt);
		System.out.println("[DRIVERS] json received: " + json_entities);
		ObjectMapper mapper = new ObjectMapper();
		EntityDriver entity = mapper.readValue(json_entities, EntityDriver.class);

		if (entity.target_variable == null || entity.target_variable.equals("null"))
			return "Sorry, I could not understand which variable you want to analyse.";

		System.out.println("[DRIVERS] entity extraction result: " + entity.toString());

		//TODO: move to another class
		String targetRaw = entity.target_variable;
		System.out.println("[DRIVERS] normalising user variable: " + targetRaw);
		String target = normalizeFeatureName(targetRaw);

		if (target == null) {
			return "Sorry, the target variable is not present in my knowledge base.";
		}

		System.out.println("[DRIVERS] inferred normalized target var: " + target);
		
		//TODO: move to 
		System.out.println("[DRIVERS] reading KB file");
		List<String> kblines = Files.readAllLines(new File(conf.getProperty("knowledge_base_data")).toPath());
		System.out.println("[DRIVERS] getting target var elements");
		double targetValues[] = UtilsDTO.getColumnNumericValues(kblines, target);
		// read the matrix: create a Table object with headers and numbers

		System.out.println("[DRIVERS] getting KB explanation and data");
		String header = kblines.get(0);
		String explanations = kblines.get(1);
		String explanationElements[] = StringUtilsDTO.getCSVElements(explanations);

		
		String headers[] = header.split(",");
		List<String> drivers = new ArrayList<String>();
		// for each feature not in Longitude, Latitude and target
		int i = 0;
		for (String head : headers) {
			if (head.toLowerCase().contains("longitude") || head.toLowerCase().contains("latitude")
					|| head.toLowerCase().equals(target.toLowerCase())) {
				i++;
				continue;
			}
			// take all data for that feature
			//System.out.println("[DRIVERS] getting " + head + " var elements");
			double headerValues[] = UtilsDTO.getColumnNumericValues(kblines, head);

			// calculate Pearson correlation

			Double corr = null;

			try {
				corr = UtilsDTO.pearsonCorrelation(targetValues, headerValues);
			} catch (Exception e) {
			}

			System.out.println("[DRIVERS] corr " + target + " vs " + head+"="+corr);
			if (corr != null) {
				// exclude |corr|<0.1
 				if ( (corr > 0.1 && entity.goal.equals("increase")) || (corr <- 0.1 && entity.goal.equals("decrease")) ) {
					// if entity.goal == increase and corr>0.1 -> report the target and its
					// explanation
					// if entity.goal == decrease and corr<-0.1 -> report the target and its
					// explanation
						//drivers.add(head + " (explanation: " + explanationElements[i] + ")");
 					drivers.add(head);
				} else if (entity.goal.equals("neutral")) {
					//drivers.add(head + " (explanation: " + explanationElements[i] + ")");
					drivers.add(head);
				}
			}
			// if entity.goal == neutral and corr>0.1 || corr<-0.1 -> report the target and
			// its explanation

			i++;
		}
		
		String statement = "";
		if (entity.goal.equals("increase"))
			statement = "Variables exhibiting a positive correlation with the target variable";
		else if (entity.goal.equals("decrease"))
			statement = "Variables exhibiting a negative correlation with the target variable";
		else
			statement = "Variables exhibiting no statistically significant association with the target variable";
		
		String query = "Target variable:\n{" + target + "}\n" + 
				//"Observed trend:\n{" + entity.goal + "}\n" +
				statement+":\n{" + 
				String.join(",\n", drivers) + "}\n";
		
		super.legacyFile = this.legacyFile;

		System.out.println("[DRIVER TASK] query: \n"+query);
		List<String> docs = ollama.retrieveDocuments(query, referenceCollection, new File(referenceFolder), top_k,
				similarity);

		System.out.println("[DRIVER TASK] Retrieved docs: " + docs.size());

		if (docs.isEmpty()) {
			docs = null;
		}

		String prompt2 = buildPrompt(query, docs);
		String answer = ollama.send(prompt2);
		// answer
		return answer;
	}

}

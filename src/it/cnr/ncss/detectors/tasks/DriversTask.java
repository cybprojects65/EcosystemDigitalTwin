package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.UtilsDTO;

public class DriversTask extends AbstractTask {
	public String legacyFile = "./task_prompts/drivers_answer.txt";
	public String information_retrieval_File = "./task_prompts/drivers_entity_extraction.txt";

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
	
	

	public DriversTask(Llm ollama) throws Exception {
		super(ollama);
		super.answerFile = this.legacyFile;
	}

	

	@Override
	public String handle(String question) throws Exception {
		System.out.println("\n========== HANDLING DRIVERS QUESTION ==========");

		System.out.println("[DRIVERS] informationRetrieval through LLM");
		EntityDriver entity = (EntityDriver) llm.sendRequestWithJsonOutput(question, information_retrieval_File, EntityDriver.class);

		if (entity.target_variable == null || entity.target_variable.equals("null"))
			return "Sorry, I could not understand which variable you want to analyse.";

		System.out.println("[DRIVERS] entity extraction result: " + entity.toString());

		String targetRaw = entity.target_variable;
		System.out.println("[DRIVERS] normalising user variable: " + targetRaw);
		String target = llm.normalizeFeatureName(targetRaw);

		if (target == null) {
			return "Sorry, the target variable is not present in my knowledge base.";
		}

		System.out.println("[DRIVERS] inferred normalized target var: " + target);
		
		System.out.println("[DRIVERS] reading KB file");
		KbManager kb = new KbManager();
		String features[] = kb.getFeatures();
		double targetValues[] = kb.getColumnNumericValues(target);
		
		List<String> drivers = new ArrayList<String>();
		// for each feature not in Longitude, Latitude and target
		for (String head : features) {
			
			// take all data for that feature
			//System.out.println("[DRIVERS] getting " + head + " var elements");
			double headerValues[] = kb.getColumnNumericValues(head);
			if (headerValues==null)
				continue;
			
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
		
		super.answerFile = this.legacyFile;

		System.out.println("[DRIVER TASK] query: \n"+query);
		List<String> docs = llm.retrieveDocuments(query, referenceCollection, new File(referenceFolder), top_k,
				similarity);

		System.out.println("[DRIVER TASK] Retrieved docs: " + docs.size());

		if (docs.isEmpty()) {
			docs = null;
		}

		String prompt2 = llm.buildPrompt(query, docs, this.legacyFile);
		String answer = llm.send(prompt2);
		// answer
		return answer;
	}

}

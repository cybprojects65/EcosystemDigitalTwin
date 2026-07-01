package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.cnr.ncss.detectors.models.KernelShapExplainer;
import it.cnr.ncss.detectors.models.RandomForestModel;
import it.cnr.ncss.detectors.tasks.RiskVariationTask.SimulationScenario.ScenarioVariable;
import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;
import weka.core.Instances;

public class RiskVariationTask extends AbstractTask{

	public RiskVariationTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("risk_variation_answer");
	}

	public String information_extraction_json;
	SimulationResultReport report;
	
	@Override
	public String handle(String question) throws Exception {

		System.out.println("[RISK VARIATION] informationRetrieval through LLM");

		information_extraction_json = llm.sendRequestWithJsonOutputString(question,this.conf.getProperty("risk_variation_extraction_prompt"));
		String risk_column = conf.getProperty("risk_column");
		int trainingClassIndex = Integer.parseInt(conf.getProperty("risk_index_to_predict"));
		KbManager kb = new KbManager();
		ObjectMapper mapper = new ObjectMapper();
		SimulationScenario simulation = (SimulationScenario) mapper.readValue(information_extraction_json, SimulationScenario.class);
		
		// read the result
		List<ScenarioVariable> variables = simulation.scenario;
		List<List<Object>> reanalysedMatrix = kb.getFeatureMatrix();
		
		for (ScenarioVariable variable:variables) {
			
			System.out.println("[RISK VARIATION] variable: " + variable.variable);
			System.out.println("[RISK VARIATION] variation: " + variable.change.value);
			System.out.println("[RISK VARIATION] variation units: " + variable.change.unit);
			String variableNameNorm = llm.normalizeFeatureName(variable.variable);
			System.out.println("[RISK VARIATION] normalised variable: " + variableNameNorm);
			boolean ispercentage = (variable.change.unit.contains("percent") || variable.change.unit.contains("fraction"));
			Double variation = Double.parseDouble(variable.change.value.replace("+", "").replace("%", ""));
			String s = variable.original_expression.toLowerCase();

	        boolean increase =
	                s.matches(".*\\b(increase|increases|increased|increasing|rise|rises|rising|grow|grows|growing|higher|more|greater|gain|gains|improve|improves)\\b.*");

	        boolean decrease =
	                s.matches(".*\\b(decrease|decreases|decreased|decreasing|decline|declines|declining|reduce|reduces|reduced|reducing|lower|less|loss|losses|drop|drops|fall|falls|falling|cooler|colder)\\b.*");

	        if (increase && !decrease && variation < 0) {
	            variation = Math.abs(variation);
	        }

	        if (decrease && !increase && variation > 0) {
	            variation = -Math.abs(variation);
	        }
	        
			if (ispercentage) {
				if (variation >1)
					variation  = variation /100;
			}
			reanalysedMatrix = kb.reanalysis(reanalysedMatrix, variableNameNorm, variation , ispercentage);
			
		}

		System.out.println("[RISK VARIATION] training baseline RF");
		
		String cached_model = new File(conf.getProperty("cache_folder"), "risk_variation_rf.bin").getAbsolutePath();
		RandomForestModel rf = null;
				
		try {
			ObjectInputStream ooi = new ObjectInputStream(new FileInputStream(cached_model));
			rf = (RandomForestModel) ooi.readObject();
			ooi.close();
					
		}catch(Exception e) {
			System.out.println("[RISK VARIATION] cached model not available");
		}
		
		List<List<Object>> matrix = kb.getFeatureMatrix();
		
		if(rf==null) {
			rf = new RandomForestModel();
			rf.trainRandomForest(matrix, kb.getFeatures(), risk_column);
			System.out.println("[RISK VARIATION] caching the model");
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cached_model));
			oos.writeObject(rf);
			oos.close();
			
		}
		
		
		Instances dataset = rf.getTrainingSet();
		
		System.out.println("[RISK VARIATION] explaining the relations");
		// initialise explainer
		KernelShapExplainer explainer = new KernelShapExplainer(rf.getModel(), dataset, 42);
		Map<String, Double> explanation = explainer.explain(dataset, trainingClassIndex);
		
		
		List<double[]> testPreditions = rf.predict(reanalysedMatrix);
		List<double[]> baselinePredictions = rf.predictProbabilityTrainingSet();
		
		double relativeVar = rf.outputRelativeVariation(baselinePredictions, testPreditions , trainingClassIndex);
		
		Map<String, Double> explanationSimulated = explainer.explain(rf.getLatestTestSet(), trainingClassIndex);
		
		relativeVar = UtilsDTO.toPercentage(relativeVar);
		
		System.out.println("[RISK VARIATION] relative variation of risk: "+relativeVar+"%");
		
		
		Map<String, Double> importanceForDelta = explainer.deltaShap( explanation ,explanationSimulated);
		System.out.println("[RISK VARIATION] relative variations of shap:\n");
		
		report = new SimulationResultReport(
		        question,
		        "ecosystem risk",
		        new SimulationResultReport.RiskChange("relative variation", relativeVar, "%")
		);
		
		for (String key:importanceForDelta.keySet()) {
			Double val = importanceForDelta.get(key);
			if (Math.abs(val)>0.1) {
				
				String contrib = "";
				if (Math.abs(val)>1)
					contrib = "major";
				else
					contrib = "minor";
				
				if (relativeVar>0 && val>0) {
					System.out.println(key+" incr-> "+val+"\n");
				report.addContributor(
				        key,
				        key,
				        contrib,
				        "interpretation",
				        "dominant factor"
				);
				
				}else if (relativeVar<0 && val<0) {
					
					System.out.println(key+" decr-> "+val+"\n");
						report.addContributor(
				        key,
				        key,
				        contrib,
				        "interpretation",
				        "dominant factor"
				);
				
				}
			}
		}
		
		
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

		String knowledgejson = report.toJson();
		
		promptText = promptText.replace("{{KNOWLEDGE}}", knowledgejson);
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		promptText = promptText.replace("{{CONTEXT}}", context);
		
		
		String prompt = """
				%s
				""".formatted(promptText);

		System.out.println("[RISK VARIATION] prompt:\n" + prompt);
		return prompt;
	}
	
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class SimulationScenario {

	    public String target;
	    public List<ScenarioVariable> scenario;

	    @JsonIgnoreProperties(ignoreUnknown = true)
	    public static class ScenarioVariable {

	        public String variable;

	        public String original_expression;

	        public String interpretation;

	        public Double confidence;

	        public Change change;

	        @Override
	        public String toString() {
	            return variable + " -> " + change;
	        }
	    }

	    @JsonIgnoreProperties(ignoreUnknown = true)
	    public static class Change {

	        public String value;

	        public String unit;

	        @Override
	        public String toString() {
	            return value + " " + unit;
	        }
	    }

	    /**
	     * Parse the JSON produced by Llama.
	     */
	    public static SimulationScenario fromJson(String json) throws Exception {

	        ObjectMapper mapper = new ObjectMapper();

	        mapper.configure(
	                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
	                false);

	        return mapper.readValue(json, SimulationScenario.class);
	    }

	    @Override
	    public String toString() {

	        StringBuilder sb = new StringBuilder();

	        sb.append("Target: ").append(target).append("\n");

	        if (scenario != null) {
	            for (ScenarioVariable v : scenario) {
	                sb.append(v.variable)
	                  .append(" = ")
	                  .append(v.change.value)
	                  .append(" ")
	                  .append(v.change.unit)
	                  .append("\n");
	            }
	        }

	        return sb.toString();
	    }
	}


	@JsonIgnoreProperties(ignoreUnknown = true)
	public class SimulationResultReport {

	    public String user_question;
	    public String target;
	    public RiskChange risk_change;
	    public List<Contributor> main_contributors_to_high_risk_increase = new ArrayList<>();

	    @JsonIgnoreProperties(ignoreUnknown = true)
	    public static class RiskChange {
	        public String type;
	        public double value;
	        public String unit;

	        public RiskChange() {}

	        public RiskChange(String type, double value, String unit) {
	            this.type = type;
	            this.value = value;
	            this.unit = unit;
	        }
	    }

	    @JsonIgnoreProperties(ignoreUnknown = true)
	    public static class Contributor {
	        public String variable;
	        public String display_name;
	        public String value;
	        public String unit;
	        public String interpretation;

	        public Contributor() {}

	        public Contributor(
	                String variable,
	                String displayName,
	                String value,
	                String unit,
	                String interpretation
	        ) {
	            this.variable = variable;
	            this.display_name = displayName;
	            this.value = value;
	            this.unit = unit;
	            this.interpretation = interpretation;
	        }
	    }

	    public SimulationResultReport() {}

	    public SimulationResultReport(String userQuestion, String target, RiskChange riskChange) {
	        this.user_question = userQuestion;
	        this.target = target;
	        this.risk_change = riskChange;
	    }

	    public void addContributor(
	            String variable,
	            String displayName,
	            String value,
	            String unit,
	            String interpretation
	    ) {
	        main_contributors_to_high_risk_increase.add(
	                new Contributor(variable, displayName, value, unit, interpretation)
	        );
	    }

	    public String toJson() throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.enable(SerializationFeature.INDENT_OUTPUT);
	        return mapper.writeValueAsString(this);
	    }

	    public static SimulationResultReport fromJson(String json) throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        return mapper.readValue(json, SimulationResultReport.class);
	    }
	}

}

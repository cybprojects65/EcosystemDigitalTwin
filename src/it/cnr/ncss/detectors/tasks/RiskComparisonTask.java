package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.cnr.ncss.detectors.models.KernelShapExplainer;
import it.cnr.ncss.detectors.models.RandomForestModel;
import it.cnr.ncss.detectors.tasks.RiskComparisonTask.ComparisonScenario.ScenarioVariable;
import it.cnr.ncss.detectors.tasks.RiskVariationTask.SimplifiedSimulationResultReport;
import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;
import weka.core.Instances;

public class RiskComparisonTask extends RiskVariationTask {

	public RiskComparisonTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("risk_comparison_answer");
	}

	public String information_extraction_json;
	SimpleSimulationReport report;

	public List<List<Object>> buildScenarioMatrix(List<ScenarioVariable> variables, List<List<Object>> baseline)
			throws Exception {
		if (variables == null) {
			System.out.println("[RISK COMPARISON] variable information is not available");
			return baseline;
		}

		KbManager kb = new KbManager();
		List<List<Object>> reanalysedMatrix = UtilsDTO.deepCopy(baseline);

		for (ScenarioVariable variable : variables) {
			String variableName = "";
			double value = 0;
			String unit = "";
			String originalExpression = "";

			if (variable.variable != null) {
				variableName = variable.variable;
				value = variable.change.value;
				unit = variable.change.unit;
				originalExpression = variable.original_expression;
			}

			System.out.println("[RISK COMPARISON] variable: " + variableName);
			System.out.println("[RISK COMPARISON] value: " + value);
			System.out.println("[RISK COMPARISON] unit: " + unit);

			String variableNameNorm = llm.normalizeFeatureName(variableName);

			System.out.println("[RISK COMPARISON] normalised variable: " + variableNameNorm);

			boolean ispercentage = (unit.contains("percent") || unit.contains("fraction"));

			String s = originalExpression.toLowerCase();

			boolean increase = s.matches(
					".*\\b(increase|increases|increased|increasing|rise|rises|rising|grow|grows|growing|higher|more|greater|gain|gains|improve|improves)\\b.*");

			boolean decrease = s.matches(
					".*\\b(decrease|decreases|decreased|decreasing|decline|declines|declining|reduce|reduces|reduced|reducing|lower|less|loss|losses|drop|drops|fall|falls|falling|cooler|colder)\\b.*");

			if (increase && !decrease && value < 0) {
				value = Math.abs(value);
			}

			if (decrease && !increase && value > 0) {
				value = -Math.abs(value);
			}

			if (ispercentage) {
				if (value > 1)
					value = value / 100;
			}

			reanalysedMatrix = kb.reanalysis(reanalysedMatrix, variableNameNorm, value, ispercentage);

		}

		return reanalysedMatrix;

	}

	@Override
	public String handle(String question) throws Exception {

		System.out.println("[RISK COMPARISON] informationRetrieval through LLM");

		information_extraction_json = llm.sendRequestWithJsonOutputString(question,
				this.conf.getProperty("risk_comparison_extraction_prompt"));
		String risk_column = conf.getProperty("risk_column");
		String high_risk_label = conf.getProperty("high_risk_label");
		
		
		KbManager kb = new KbManager();
		ObjectMapper mapper = new ObjectMapper();
		ComparisonScenario simulation = (ComparisonScenario) mapper.readValue(information_extraction_json,
				ComparisonScenario.class);

		// read the result
		List<ScenarioVariable> variablesA = simulation.scenario_a;
		
		System.out.println("[RISK COMPARISON] processing scenario A");
		
		List<List<Object>> baseline = kb.getFeatureMatrix();
		List<List<Object>> scenarioA_Matrix = buildScenarioMatrix(variablesA, baseline);
		
		System.out.println("[RISK COMPARISON] processing scenario B");
		List<ScenarioVariable> variablesB = simulation.scenario_b;
		
		List<List<Object>> scenarioB_Matrix = buildScenarioMatrix(variablesB, baseline);

		System.out.println("[RISK COMPARISON] training baseline RF");

		String cached_model = new File(conf.getProperty("cache_folder"), "risk_variation_rf.bin").getAbsolutePath();
		RandomForestModel rf = null;

		try {

			ObjectInputStream ooi = new ObjectInputStream(new FileInputStream(cached_model));
			rf = (RandomForestModel) ooi.readObject();
			ooi.close();
			System.out.println("[RISK COMPARISON] model cached");
		} catch (Exception e) {
			System.out.println("[RISK COMPARISON] cached model not available");
		}

		List<List<Object>> matrix = kb.getFeatureMatrix();

		if (rf == null) {
			
			rf = new RandomForestModel();
			rf.trainRandomForest(matrix, kb.getFeatures(), risk_column);
			System.out.println("[RISK COMPARISON] caching the model");
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cached_model));
			oos.writeObject(rf);
			oos.close();

		}
		
		// initialise explainer
		List<String> baselinePredictions = rf.predictClass(matrix);
		List<String> testPreditionsA = rf.predictClass(scenarioA_Matrix);

		double relativeVarA = rf.outputRelativeVariation(baselinePredictions, testPreditionsA , high_risk_label);
		relativeVarA = UtilsDTO.toPercentage(relativeVarA);
		System.out.println("[RISK COMPARISON] relative variation of risk in scenario A: "+relativeVarA+"%");
		
		
		List<String> testPreditionsB = rf.predictClass(scenarioB_Matrix);		
		double relativeVarB = rf.outputRelativeVariation(baselinePredictions, testPreditionsB , high_risk_label);
		relativeVarB = UtilsDTO.toPercentage(relativeVarB);
		System.out.println("[RISK COMPARISON] relative variation of risk in scenario B: "+relativeVarB+"%");
		
		SimplifiedSimulationResultReport simpleReportA = new SimplifiedSimulationResultReport();
		simpleReportA.risk_change = UtilsDTO.roundToDigits(relativeVarA, 3)+"%";
		simpleReportA.interpretation = "";
		
		for (ScenarioVariable v: variablesA) {
			if (v.original_expression !=null && v.original_expression.trim().length()>0)
				simpleReportA.interpretation += v.original_expression+"; ";
		}
		
		SimplifiedSimulationResultReport simpleReportB = new SimplifiedSimulationResultReport();
		simpleReportB.risk_change = UtilsDTO.roundToDigits(relativeVarB, 3)+"%";
		simpleReportB.interpretation = "";
		
		for (ScenarioVariable v: variablesB) {
			if (v.original_expression !=null && v.original_expression.trim().length()>0)
				simpleReportB.interpretation += v.original_expression+"; ";
		}
		
		report = new SimpleSimulationReport(simpleReportA, simpleReportB);
		
		System.out.println("[RISK COMPARISON] report:\n "+report.toJson());
		
		String answer = generateAnswer(question);
		return answer;
	}

	
	public static class SimpleSimulationReport {

	    public SimplifiedSimulationResultReport scenario_A;
	    public SimplifiedSimulationResultReport scenario_B;

	    public SimpleSimulationReport(
	            SimplifiedSimulationResultReport scenario_A,
	            SimplifiedSimulationResultReport scenario_B) {

	        this.scenario_A = scenario_A;
	        this.scenario_B = scenario_B;
	    }

	    public String toJson() throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.enable(SerializationFeature.INDENT_OUTPUT);
	        return mapper.writeValueAsString(this);
	    }
	}
	
	
	@Override
	public String buildPrompt(String query, List<String> docs, String promptFile) throws Exception {

		String context = "";
		if (docs != null) {
			context = String.join("\n\n", docs.stream().toList());
		}

		String promptText = StringUtilsDTO.getText(new File(answerFile));

		String uuid = ""+UUID.randomUUID();
		java.nio.file.Files.writeString(java.nio.file.Path.of("./prompt_testing/prompt_template_"+uuid+".txt"),promptText);

		String knowledgejson = report.toJson();

		promptText = promptText.replace("{{KNOWLEDGE}}", knowledgejson);
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		promptText = promptText.replace("{{CONTEXT}}", context);

		
		//System.out.println("[CORRELATION] prompt:\n"+promptText);
		java.nio.file.Files.writeString(java.nio.file.Path.of("./prompt_testing/prompt_"+uuid+".txt"),promptText);
		
		String prompt = """
				%s
				""".formatted(promptText);

		//System.out.println("[RISK VARIATION] prompt:\n" + prompt);
		return prompt;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class ComparisonScenario {

		public String target;
		public boolean comparison;

		public List<ScenarioVariable> scenario_a = new ArrayList<>();
		public List<ScenarioVariable> scenario_b = new ArrayList<>();

		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class ScenarioVariable {
			public String variable;
			public String original_expression;
			public String interpretation;
			public Change change;

			@Override
			public String toString() {
				return variable + " = " + change;
			}
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class Change {
			public double value;
			public String unit;

			@Override
			public String toString() {
				return value + " " + unit;
			}
		}

		public static ComparisonScenario fromJson(String json) throws Exception {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			return mapper.readValue(json, ComparisonScenario.class);
		}

		public boolean hasScenarioA() {
			return scenario_a != null && !scenario_a.isEmpty();
		}

		public boolean hasScenarioB() {
			return scenario_b != null && !scenario_b.isEmpty();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();

			sb.append("Target: ").append(target).append("\n");
			sb.append("Comparison: ").append(comparison).append("\n");

			sb.append("Scenario A:\n");
			if (scenario_a != null) {
				for (ScenarioVariable v : scenario_a) {
					sb.append("  ").append(v).append("\n");
				}
			}

			sb.append("Scenario B:\n");
			if (scenario_b != null) {
				for (ScenarioVariable v : scenario_b) {
					sb.append("  ").append(v).append("\n");
				}
			}

			return sb.toString();
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public class SimulationResultReport {

		public String user_question;
		public String target;
		public RiskChange risk_change_in_scenario_A;
		public RiskChange risk_change_in_scenario_B;
		public List<Contributor> main_contributors_to_high_risk_increase_in_scenario_A = new ArrayList<>();
		public List<Contributor> main_contributors_to_high_risk_increase_in_scenario_B = new ArrayList<>();

		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class RiskChange {
			public String type;
			public double value;
			public String unit;
			public String scenario_summary;

			public RiskChange() {
			}

			public RiskChange(String type, double value, String unit, String scenario_summary) {
				this.type = type;
				this.value = value;
				this.unit = unit;
				this.scenario_summary = scenario_summary;
			}
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		public static class Contributor {
			public String variable;
			public String display_name;
			public String value;
			public String unit;
			public String interpretation;

			public Contributor() {
			}

			public Contributor(String variable, String displayName, String value, String unit, String interpretation) {
				this.variable = variable;
				this.display_name = displayName;
				this.value = value;
				this.unit = unit;
				this.interpretation = interpretation;
			}
		}

		public SimulationResultReport() {
		}

		public SimulationResultReport(String userQuestion, String target, RiskChange riskChangeA,
				RiskChange riskChangeB) {
			this.user_question = userQuestion;
			this.target = target;
			this.risk_change_in_scenario_A = riskChangeA;
			this.risk_change_in_scenario_B = riskChangeB;
		}

		public void addContributor(String variable, String displayName, String value, String unit,
				String interpretation, String scenario) {
			if (scenario == "A")
				main_contributors_to_high_risk_increase_in_scenario_A
						.add(new Contributor(variable, displayName, value, unit, interpretation));
			else
				main_contributors_to_high_risk_increase_in_scenario_B
						.add(new Contributor(variable, displayName, value, unit, interpretation));
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
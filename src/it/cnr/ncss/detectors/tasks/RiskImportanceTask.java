package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.cnr.ncss.detectors.models.StatisticalExplainer;
import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;
import weka.core.Instances;

public class RiskImportanceTask extends AbstractTask {

	public static String cached_model;
	ContributionReport report;
	ImportanceReport importanceReport;
	
	public RiskImportanceTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("risk_importance_answer");
	}

	@SuppressWarnings("unchecked")
	@Override
	public String handle(String question) throws Exception {

		System.out.println("[RISK IMPORTANCE] estimating multivariate relations");
		String risk_column = conf.getProperty("risk_column");
		String highRiskLabel = conf.getProperty("high_risk_label");
		String lowRiskLabel = conf.getProperty("low_risk_label");
		
		Map<String, Double> explanation = null;
		KbManager kb = new KbManager();
		List<List<Object>> matrix = kb.getFeatureMatrix();
		String [] features = kb.getFeatures();
		
		Instances dataset = UtilsDTO.matrixToWekaInstance(matrix, kb.getFeatures(), risk_column);
		
		StatisticalExplainer statEx = new StatisticalExplainer();
		
		explanation = statEx.explain(dataset, highRiskLabel, lowRiskLabel);
		
		System.out.println("[RISK IMPORTANCE] explanation: \n"+explanation.toString().replace(",", "\n"));
		importanceReport = new ImportanceReport();
		
		importanceReport.setReport("High ecosystem risk is due to the concurrency of high levels of the variables in [variable_ranking] in specific areas, the variables are ordered by strength in the high-risk regions. The [lesser_contributing_variables] indicate variables with important but not exceptionally high values in the high-risk areas.");
		
		List<String> variables = new ArrayList<String>();
		
		for (String key:explanation.keySet()) {
			variables.add(key.toLowerCase());
		}
		
		
		importanceReport.setVariableRanking(variables);
		List<String> lesservariables = new ArrayList<String>();
		
		for (String key:features) {
			
			if (!key.equals(risk_column) && !explanation.keySet().contains(key)) {
				lesservariables.add(key.toLowerCase());
			}
		}
		
		importanceReport.setLesser_contributing_variables(lesservariables);
		
		importanceReport.setAdditional_remarks("Ecosystem risk lowering can be achieved by avoiding this concurrency, by lowering the climatic and anthropogenic stressors while keeping the ecological variables at their levels.");
		
		System.out.println("[RISK IMPORTANCE] risk importance report: \n"+importanceReport.toJson());
		
		String answer = generateAnswer(question);
		return answer;
	}
	
	/*
	public String handle1(String question) throws Exception {

		System.out.println("[RISK IMPORTANCE] estimating multivariate relations");
		String risk_column = conf.getProperty("risk_column");
		int trainingClassIndex = Integer.parseInt(conf.getProperty("risk_index_to_predict"));
		cached_model = new File(conf.getProperty("cache_folder"), "risk_importance_rf.bin").getAbsolutePath();
		
		
		Map<String, Double> explanation = null;
		try {
			ObjectInputStream ooi = new ObjectInputStream(new FileInputStream(cached_model));
			explanation = (Map<String, Double>) ooi.readObject();
			ooi.close();
					
		}catch(Exception e) {
			System.out.println("[RISK IMPORTANCE] cached model not available");
		}
		KbManager kb = new KbManager();
		
		// generate multivariate connections wrt ecosystem risk
		if(explanation==null){
			RandomForestModel rf = new RandomForestModel();
			
			List<List<Object>> matrix = kb.getFeatureMatrix();
			rf.trainRandomForest(matrix, kb.getFeatures(), risk_column);
			Instances dataset = rf.getTrainingSet();

			System.out.println("[RISK IMPORTANCE] class attribute: " + dataset.classAttribute());
			for (int i = 0; i < dataset.classAttribute().numValues(); i++) {
			    System.out.println("[RISK IMPORTANCE] class index " + i + " = " + dataset.classAttribute().value(i));
			}
			System.out.println("[RISK IMPORTANCE] target class index = " + trainingClassIndex);
			System.out.println("[RISK IMPORTANCE] target class label = " + dataset.classAttribute().value(trainingClassIndex));
			
			System.out.println("[RISK IMPORTANCE] explaining the relations");
			// initialise explainer
			KernelShapExplainer explainer = new KernelShapExplainer(rf.getModel(), dataset, 42);
			explanation = explainer.explain(dataset, trainingClassIndex);
			
			System.out.println("[RISK IMPORTANCE] caching the model");
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cached_model));
			oos.writeObject(explanation);
			oos.close();
		}
		
		report = new ImportanceReport();
		
		report.analysis = new ContributionReport.Analysis(
		        "ecosystem risk",
		        "%",
		        "relative contribution"
		);
		
		System.out.println("[RISK IMPORTANCE] explanations for high-risk:");
		System.out.println(explanation.toString());
		
		boolean positive = true;
		Map<String, Double> positiveContributions = KernelShapExplainer.interpretContributors(explanation, positive);
		
		System.out.println("[RISK IMPORTANCE] positive contributions:");
		System.out.println(positiveContributions.toString());
		
		for (String key:positiveContributions.keySet()) {
			report.contributors.positive.add(
			        new ContributionReport.Contribution(
			                key,
  			                kb.explainFeature(key),
			                positiveContributions.get(key)
			        )
			);
		}
		
		Map<String, Double> negativeContributions = KernelShapExplainer.interpretContributors(explanation, !positive);
		
		System.out.println("[RISK IMPORTANCE] negative contributions:");
		System.out.println(negativeContributions.toString());
		
		for (String key:negativeContributions.keySet()) {
			
			report.contributors.negative.add(
			        new ContributionReport.Contribution(
			                key,
			                kb.explainFeature(key),
			                negativeContributions.get(key)
			        )
			);
			
		}
		
		for (String key:explanation.keySet()) {
			if (positiveContributions.get(key)==null && negativeContributions.get(key)==null) {
				report.contributors.negligible.add(
				        new ContributionReport.Contribution(
				                key,
				                kb.explainFeature(key)
				        )
				);
			}
			
		}

		String answer = generateAnswer(question);
		return answer;
	}
*/
	@Override
	public String buildPrompt(String query, List<String> docs, String promptFile) throws Exception {

		String context = "";
		if (docs != null) {
			context = String.join("\n\n", docs.stream().toList());
		}
/*
		report.context.add(
		        context
		);
	*/	
		String promptText = StringUtilsDTO.getText(new File(answerFile));
		String uuid = ""+UUID.randomUUID();
		java.nio.file.Files.writeString(java.nio.file.Path.of("./prompt_testing/prompt_template_"+uuid+".txt"),promptText);

		//String knowledgejson = report.toJson();
		String knowledgejson = importanceReport.toJson();
		
		
		promptText = promptText.replace("{{KNOWLEDGE}}", knowledgejson);
		promptText = promptText.replace("{{CONTEXT}}", context);
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		
		java.nio.file.Files.writeString(java.nio.file.Path.of("./prompt_testing/prompt_"+uuid+".txt"),promptText);
		
		String prompt = """
				%s
				""".formatted(promptText);

		//System.out.println("[RISK IMPORTANCE] prompt:\n" + prompt);
		return prompt;
	}
	
	public class ImportanceReport {
		
		public String report;
		public List<String> variable_ranking;
		public List<String> lesser_contributing_variables;
		public List<String> getLesser_contributing_variables() {
			return lesser_contributing_variables;
		}

		public void setLesser_contributing_variables(List<String> lesser_contributing_variables) {
			this.lesser_contributing_variables = lesser_contributing_variables;
		}

		public String additional_remarks;
		
		public String getAdditional_remarks() {
			return additional_remarks;
		}

		public void setAdditional_remarks(String additional_remarks) {
			this.additional_remarks = additional_remarks;
		}

		public ImportanceReport() {
			this.report = "";
		}
		
		public void setReport(String report) {
			this.report = report;
		}
		
		public void setVariableRanking(List<String> variable_ranking) {
			this.variable_ranking = variable_ranking;
		}
		
		public String toJson() throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.enable(SerializationFeature.INDENT_OUTPUT);
	        return mapper.writeValueAsString(this);
	    }
		
	}
	
	public class ContributionReport {

	    public Analysis analysis;
	    public Contributors contributors;
	    public List<String> context;

	    public ContributionReport() {
	        this.contributors = new Contributors();
	        this.context = new ArrayList<>();
	    }

	    public static class Analysis {
	        public String target;
	        public String unit;
	        public String ranking;

	        public Analysis(String target, String unit, String ranking) {
	            this.target = target;
	            this.unit = unit;
	            this.ranking = ranking;
	        }
	    }

	    public static class Contributors {
	        public List<Contribution> positive = new ArrayList<>();
	        public List<Contribution> negative = new ArrayList<>();
	        public List<Contribution> negligible = new ArrayList<>();
	    }

	    public static class Contribution {
	        public String variable;
	        public Double value;
	        public String explanation;

	        public Contribution(String variable, String explanation, Double value) {
	            this.variable = variable;
	            this.explanation = explanation;
	            this.value = value;
	        }

	        public Contribution(String variable,String explanation) {
	            this.variable = variable;
	            this.explanation = explanation;
	            this.value = null;
	        }
	    }

	    public String toJson() throws Exception {
	        ObjectMapper mapper = new ObjectMapper();
	        mapper.enable(SerializationFeature.INDENT_OUTPUT);
	        return mapper.writeValueAsString(this);
	    }
	}
	
}

package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.cnr.ncss.detectors.models.KernelShapExplainer;
import it.cnr.ncss.detectors.models.RandomForestModel;
import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;
import weka.core.Instances;

public class RiskImportanceTask extends AbstractTask {

	public static String cached_model;
	ContributionReport report;
	
	public RiskImportanceTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("risk_importance_answer");
	}

	@SuppressWarnings("unchecked")
	@Override
	public String handle(String question) throws Exception {

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

			System.out.println("[RISK IMPORTANCE] explaining the relations");
			// initialise explainer
			KernelShapExplainer explainer = new KernelShapExplainer(rf.getModel(), dataset, 42);
			explanation = explainer.explain(dataset, trainingClassIndex);
			
			System.out.println("[RISK IMPORTANCE] caching the model");
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cached_model));
			oos.writeObject(explanation);
			oos.close();
		}
		
		report = new ContributionReport();
		
		report.analysis = new ContributionReport.Analysis(
		        "ecosystem risk",
		        "%",
		        "relative contribution"
		);
		
		System.out.println("[RISK IMPORTANCE] explanations for high-risk:");
		System.out.println(explanation.toString());
		
		boolean positive = true;
		Map<String, Double> positiveContributions = KernelShapExplainer.interpretContributors(explanation, positive);
		
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

	@Override
	public String buildPrompt(String query, List<String> docs, String promptFile) throws Exception {

		String context = "";
		if (docs != null) {
			context = String.join("\n\n", docs.stream().toList());
		}

		report.context.add(
		        context
		);
		
		String promptText = StringUtilsDTO.getText(new File(answerFile));

		String knowledgejson = report.toJson();
		
		
		promptText = promptText.replace("{{KNOWLEDGE}}", knowledgejson);
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		
		String prompt = """
				%s
				""".formatted(promptText);

		System.out.println("[RISK IMPORTANCE] prompt:\n" + prompt);
		return prompt;
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

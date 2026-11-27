package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.detectors.models.CorrelationModel;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;

public class CorrelationTask extends AbstractTask {

	public String information_extraction_json;
	public StringBuffer associationSummary;
	public Map<String, Map<String, Double>> associations;
	private String targetUser;
	private String source;
	private String target;
	private String raw;
	
	public static class CorrelationQueryInterpretation {

		private String intent;
		private Source source;
		private Target target;
		private String relation;
		private String polarity;
		private double confidence;

		public CorrelationQueryInterpretation() {
		}

		public CorrelationQueryInterpretation(String intent, Source source, Target target, String relation,
				String polarity, double confidence) {
			this.intent = intent;
			this.source = source;
			this.target = target;
			this.relation = relation;
			this.polarity = polarity;
			this.confidence = confidence;
		}

		public String getIntent() {
			return intent;
		}

		public void setIntent(String intent) {
			this.intent = intent;
		}

		public Source getSource() {
			return source;
		}

		public void setSource(Source source) {
			this.source = source;
		}

		public Target getTarget() {
			return target;
		}

		public void setTarget(Target target) {
			this.target = target;
		}

		public String getRelation() {
			return relation;
		}

		public void setRelation(String relation) {
			this.relation = relation;
		}

		public String getPolarity() {
			return polarity;
		}

		public void setPolarity(String polarity) {
			this.polarity = polarity;
		}

		public double getConfidence() {
			return confidence;
		}

		public void setConfidence(double confidence) {
			this.confidence = confidence;
		}

		public static class Source {
			private String name;
			private boolean generic;
			private String type;

			public Source() {
			}

			public Source(String name, boolean generic, String type) {
				this.name = name;
				this.generic = generic;
				this.type = type;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}

			public boolean isGeneric() {
				return generic;
			}

			public void setGeneric(boolean generic) {
				this.generic = generic;
			}

			public String getType() {
				return type;
			}

			public void setType(String type) {
				this.type = type;
			}
		}

		public static class Target {
			private String name;

			public Target() {
			}

			public Target(String name) {
				this.name = name;
			}

			public String getName() {
				return name;
			}

			public void setName(String name) {
				this.name = name;
			}
		}
	}

	public CorrelationTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("correlation_answer");
		super.similarity = 0.5;
	}

	@Override
	public String handle(String question) throws Exception {
		// get the IE template
		// send query to the LLM
		
		//TODO: manage multiple variables vs multiple variables
		
		System.out.println("[CORRELATION] informationRetrieval through LLM");

		information_extraction_json = llm.sendRequestWithJsonOutputString(question,
				this.conf.getProperty("correlation_extraction_prompt"));

		ObjectMapper mapper = new ObjectMapper();
		CorrelationQueryInterpretation entity = (CorrelationQueryInterpretation) mapper
				.readValue(information_extraction_json, CorrelationQueryInterpretation.class);

		// read the result
		System.out.println("[CORRELATION] intent: " + entity.getIntent());
		System.out.println("[CORRELATION] source: " + entity.getSource().getName());
		System.out.println("[CORRELATION] target: " + entity.getTarget().getName());
		System.out.println("[CORRELATION] polarity: " + entity.getPolarity());

		String risk_feature = conf.getProperty("risk_column");

		String sourceUser = entity.getSource().getName().replace("_", " ");

		source = entity.getSource().getName();
		if (source != null && !source.equals("driver") && source.length() > 0) {
			source = llm.normalizeFeatureName(source);
		} else
			source = null;

		targetUser = entity.getTarget().getName().replace("_", " ");
		target = entity.getTarget().getName();
		if (target != null && !target.equals("driver") && !target.startsWith("ecosystem") && target.length() > 0) {
			target = llm.normalizeFeatureName(target);
		} else
			target = null;

		if (target == null && source != null) {
			System.out.println("[CORRELATION] switching source (!=null) and target (=null)");

			target = source;
			targetUser = target;
			source = null;

		}

		if (target == null && source == null) {
			System.out.println("[CORRELATION] target is null - guessing target from normalization");
			target = llm.normalizeFeatureName(question);
			targetUser = target;
			source = null;

		}

		System.out.println("[CORRELATION] normalised source: " + source);
		System.out.println("[CORRELATION] normalised target: " + target);

		if (target == null && source == null) {
			associations = null;
		} else {
			// manage the request with all cases
			CorrelationModel model = new CorrelationModel();
			associations = model.correlateAll(target);
			System.out.println("[CORRELATION] full analysis:\n" + associations.toString().replace(",", "\n"));

			associationSummary = new StringBuffer();

			for (String key : associations.keySet()) {
				if (source != null && source.equals(risk_feature))
					continue;
				else if (source == null)
					associationSummary.append(key + " -> " + target + " : "
							+ associations.get(key).keySet().toString().replaceAll("[\\[\\]]", "") + "\n");
				else if (source != null && key.equals(source))
					associationSummary.append(source + " -> " + target + " : "
							+ associations.get(key).keySet().toString().replaceAll("[\\[\\]]", "") + "\n");

			}
			System.out.println("[CORRELATION] correlation outcome:\n" + associationSummary.toString());
		}

		String answer = generateAnswer(question);

		return answer;
	}

	@Override
	public String generateAnswer(String query) {

		System.out.println("\n[CORRELATION] ANSWER QUERY START");

		try {
			raw = "";
			if (associations == null) {
				raw = "Sorry, I could not manage the requested query for correlation\n";
			} else if (associationSummary.length() > 0) {
				if (source != null) {
					//raw = "Highlight of the correlation(s) between the requested variables:\n";
					//raw += associationSummary + "\n";

					//raw += "Additionally, the overall correlations between the requested target and currently available variables are the following:\n";
					raw = "Report of the correlations between pertinent variables:\n";
					raw += formatAssociationSummary(associations, target);
				}else {
					
						raw = "Report of the correlations between pertinent variables:\n";
						raw += formatAssociationSummary(associations, target);
					
				}
			} else {
				raw = "The overall correlations between the requested target currently available variables are the following:\n";
				raw += formatAssociationSummary(associations, target);
			}

			System.out.println("\n[CORRELATION] RAW ANSWER: \n"+raw);
			
			System.out.println("\n[CORRELATION] ANSWER QUERY END");
			
			
			String answer = super.generateAnswer(query);
			
			answer+="\n\n**Numerical details**\n\n"+raw;
			
			return answer.trim();

		} catch (Exception e) {
			System.out.println("[CORRELATION ERROR] " + e.getMessage());
			return "An error occurred while generating the answer for correlation request.";
		}

	}

	private String formatAssociationSummary(Map<String, Map<String, Double>> associations, String targetVariableName) {
		StringBuffer sb = new StringBuffer();

		for (String variable : associations.keySet()) {
			Map<String, Double> association = associations.get(variable);
			for (String correlationLabel : association.keySet()) {

				Double correlationValue = association.get(correlationLabel);
				double correlationValueUser = UtilsDTO.roundDecimal(correlationValue.doubleValue(), 3);

				sb.append(variable + " -> " + targetVariableName + " : " + correlationLabel + " (corr. coeff.="
						+ correlationValueUser + ")" + "\n");

			}

		}

		return sb.toString();

	}

	@Override
	public String buildPrompt(String query, List<String> docs, String promptFile) throws Exception {

		String context = "";
		if (docs != null) {
			context = String.join("\n\n", docs.stream().toList());
		}

		String promptText = StringUtilsDTO.getText(new File(answerFile));
		String uuid = "" + UUID.randomUUID();
		java.nio.file.Files.writeString(java.nio.file.Path.of("./prompt_testing/prompt_template_" + uuid + ".txt"),
				promptText);

		promptText = promptText.replace("{{EXTRACTED_JSON}}", information_extraction_json);
		promptText = promptText.replace("{{CORRELATIVE_INTERPRETATIONS}}", raw);
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		promptText = promptText.replace("{{CONTEXTUAL_INFORMATION}}", context);

		// System.out.println("[CORRELATION] prompt:\n"+promptText);
		java.nio.file.Files.writeString(java.nio.file.Path.of("./prompt_testing/prompt_" + uuid + ".txt"), promptText);

		String prompt = """
				%s
				""".formatted(promptText);

		return prompt;
	}

}

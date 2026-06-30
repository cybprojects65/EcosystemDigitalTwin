package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.cnr.ncss.detectors.models.CorrelationModel;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;

public class CorrelationTask extends AbstractTask {

	public String information_extraction_json;
	public StringBuffer associationSummary;
	
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
		System.out.println("[CORRELATION] informationRetrieval through LLM");

		information_extraction_json = llm.sendRequestWithJsonOutputString(question,this.conf.getProperty("correlation_extraction_prompt"));
		
		ObjectMapper mapper = new ObjectMapper();
		CorrelationQueryInterpretation entity = (CorrelationQueryInterpretation) mapper.readValue(information_extraction_json, CorrelationQueryInterpretation.class);
		
		// read the result
		System.out.println("[CORRELATION] intent: " + entity.getIntent());
		System.out.println("[CORRELATION] source: " + entity.getSource().getName());
		System.out.println("[CORRELATION] target: " + entity.getTarget().getName());
		System.out.println("[CORRELATION] polarity: " + entity.getPolarity());

		String sourceUser = entity.getSource().getName();
		String source = entity.getSource().getName();
		if (source != null && !source.equals("driver") && source.length() > 0) {
			source = llm.normalizeFeatureName(source);
		} else
			source = null;
		
		String targetUser = entity.getTarget().getName();
		String target = entity.getTarget().getName();
		if (target != null && !target.equals("driver") && target.length() > 0) {
			target = llm.normalizeFeatureName(target);
		} else
			target = null;

		System.out.println("[CORRELATION] normalised source: " + source);
		System.out.println("[CORRELATION] normalised target: " + target);

		// manage the request with all cases
		CorrelationModel model = new CorrelationModel();
		Map<String, Map<String, Double>> associations = model.correlateAll(source, target);
		System.out.println("[CORRELATION] full analysis:\n"+associations.toString().replace(",", "\n") );

		associationSummary = new StringBuffer();

		for (String key : associations.keySet()) {
			if (source==null)
				associationSummary.append(key + " -> " + targetUser +" : " + associations.get(key).keySet().toString().replaceAll("[\\[\\]]", "") + "\n");
			else if (source!=null && key.equals(source))
				associationSummary.append(sourceUser + " -> " + targetUser+" : " + associations.get(key).keySet().toString().replaceAll("[\\[\\]]", "") + "\n");

		}
		System.out.println("[CORRELATION] correlation outcome:\n" + associationSummary.toString());
		
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
		promptText = promptText.replace("{{EXTRACTED_JSON}}", information_extraction_json);
		promptText = promptText.replace("{{CORRELATIVE_INTERPRETATIONS}}", associationSummary);	
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		promptText = promptText.replace("{{CONTEXTUAL_INFORMATION}}", context);
		
		String prompt = """
				%s
				""".formatted(promptText);

		return prompt;
	}

}

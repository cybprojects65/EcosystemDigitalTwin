package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.LiveUpdateFile;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;

public class DataTask extends AbstractTask {

	public StringBuffer availableData;
	public StringBuffer compliantData;

	public DataTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("data_answer");
	}

	@Override
	public String handle(String question) throws Exception {

		System.out.println("[DATA] getting the data from remote url");
		// download the remote file
		LiveUpdateFile liveFile = new LiveUpdateFile();
		File liveData = liveFile.getLiveData();

		// get the header
		List<String> lines = Files.readAllLines(liveData.toPath());
		String header = lines.get(0);
		String headers[] = header.split(",");

		List<String> target_variables = llm.normalizeFeatureNameSetToReference(question, headers);
		int num_var = headers.length;

		System.out.println("[DATA] Nvars in the live data: " + num_var + "; Vars compliant with the query ("
				+ target_variables.size() + "): " + target_variables);

		compliantData = new StringBuffer();

		for (String target_variable : target_variables) {
			int i = 0;
			for (String head : headers) {
				if (target_variable.equals(head)) {
					double average = UtilsDTO.averageColumn(lines, i);
					compliantData.append(head + ": " + UtilsDTO.formatNumber(average) + "\n");
					break;
				}
				i++;
			}

		}

		availableData = new StringBuffer();
		int i = 0;
		for (String head : headers) {
			double average = UtilsDTO.averageColumn(lines, i);
			availableData.append(head + ": " + UtilsDTO.formatNumber(average) + "\n");
			i++;
		}

		String answer = generateAnswer(question);
		return answer;
	}

	@Override
	public String generateAnswer(String query) {

		System.out.println("\n[DATA] ANSWER QUERY START");

		try {
			String raw = "";

			if (compliantData.length() > 0) {

				raw = "Here are the current live data that most comply to the request:\n";
				raw += compliantData + "\n";
				raw += "Additionally, the overall live data currently available are the following:\n";
			} else {
				raw = "The overall live data currently available are the following:\n";
			}

			raw += availableData + "\n";

			if (raw == null || raw.isBlank() || raw.contains("Interpretation not available")) {
				return "Please, reformulate the question";
			}

			System.out.println("\n[DATA] ANSWER QUERY END");

			return raw.trim();

		} catch (Exception e) {
			System.out.println("[DATA ERROR] " + e.getMessage());
			return "An error occurred while generating the answer for data request.";
		}

	}

	@Override
	public String buildPrompt(String query, List<String> docs, String promptFile) throws Exception {

		String context = "";
		if (docs != null) {
			context = String.join("\n\n", docs.stream().toList());
		}

		String promptText = StringUtilsDTO.getText(new File(answerFile));

		promptText = promptText.replace("{{AVAILABLE_DATA}}", availableData);
		promptText = promptText.replace("{{USER_REQUEST}}", query);
		promptText = promptText.replace("{{CONTEXTUAL_INFORMATION}}", context);

		String prompt = """
				%s
				""".formatted(promptText);

		System.out.println("[DATA] prompt:\n" + prompt);
		return prompt;
	}
}

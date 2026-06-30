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
	
	public DataTask(Llm ollama) throws Exception {
		super(ollama);
		answerFile = conf.getProperty("data_answer");
	}

	@Override
	 public String handle(String question) throws Exception{
		
		System.out.println("[DATA] getting the data from remote url");
		//download the remote file
		LiveUpdateFile liveFile = new LiveUpdateFile();
		File liveData = liveFile.getLiveData();
		
		//get the header
		List<String> lines = Files.readAllLines(liveData.toPath());
		String header = lines.get(0);
		String headers [] = header.split(",");
		
		availableData = new StringBuffer();
		int i = 0;
		for (String head:headers) {
			double average = UtilsDTO.averageColumn(lines, i);
			availableData.append(head+":"+average+"\n");
			i++;
		}

		
		String answer = generateAnswer(question);
        return  answer;
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

		System.out.println("[DATA] prompt:\n"+prompt);
		return prompt;
	}
}

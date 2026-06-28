package it.cnr.ncss.detectors.tasks;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.LiveUpdateFile;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;

public class DataTask extends AbstractTask {

	
	public static double threshold = 0.6;
	public String legacyFile = "./task_prompts/data_answer.txt"; 
	
	public DataTask(Llm ollama) throws Exception {
		super(ollama);
		super.legacyFile = this.legacyFile;
	}

	@Override
	 public String handle(String question) throws Exception{
		System.out.println("\n========== HANDLING DATA QUESTION ==========");

		System.out.println("[DATA] getting the data from remote url");
		//download the remote file
		LiveUpdateFile liveFile = new LiveUpdateFile();
		File liveData = liveFile.getLiveData();
		
		System.out.println("[DATA] retrieving the live data");
		//get the header
		List<String> lines = Files.readAllLines(liveData.toPath());
		String header = lines.get(0);
		String headers [] = header.split(",");
		
		double[] queryEmbedding = llm.embed(question,false);
		StringBuffer requestedFeatures = new StringBuffer();
		StringBuffer otherFeatures = new StringBuffer();
		
		//get the most similar features
		int i = 0;
		for (String head:headers) {
			
			//similarity
			double[] exampleEmbedding = llm.embed(head,true);
            double score = StringUtilsDTO.cosineSimilarity(queryEmbedding, exampleEmbedding);
          //get the feautres values
            System.out.println("[DATA] feature "+head+" vs "+question+": "+score);
            
            if (score>threshold) {
            	String feature = "current average "+headers[i]+"="+UtilsDTO.averageColumn(lines, i)+"\n";
            	requestedFeatures.append(feature);
            }else {
            	String feature = "current average "+headers[i]+"="+UtilsDTO.averageColumn(lines, i)+"\n";
            	otherFeatures.append(feature);
            }
            
            i++;
		}
		
		llm.cacheEmbedding();
		
		String query = "The user has asked for the following live data and values."+
		"These are the values requested: \n"+requestedFeatures+"\n"+
				"And these are the other variables and values available: \n"+otherFeatures;
		
		//add a summary of the feature values
		
		
		//produce an answer: use the dataset as the context
		System.out.println("[DATA] assembled query:\n "+query);
		
        String response;

        try {
            String llmResponse = generateAnswer(query);

            if (llmResponse == null || llmResponse.contains("No relevant information")) {
                System.out.println("[DATA TASK] Switching to LLM fallback");
                response = generateFallback(question);
            } else {
                response = llmResponse;
            }

        } catch (Exception e) {
            System.out.println("[DATA TASK ERROR] " + e.getMessage());
            response = generateFallback(question);
        }

        return  response;
	}
	
	public String generateAnswer(String query) {

    	System.out.println("\n[DATA-ANSWER] START");

        try {

            String prompt = llm.buildPrompt(query, null,this.legacyFile);
            String raw = llm.send(prompt);

            if (raw == null || raw.isBlank() || raw.contains("Interpretation not available")) {
                return "The system could not generate a reliable answer from the available data.";
            }

            return raw.trim();

        } catch (Exception e) {
            System.out.println("[DATA-ANSWER ERROR] " + e.getMessage());
            return "An error occurred while generating the answer.";
        }
    }
	
	public String generateFallback(String question) throws Exception{
    	return "I cannot access the data right now. Sorry, ask me this question later.";
    }
	

	public static void main(String[] args) throws Exception{
		
		//String query = "hello man!";
    	String query = "I would like to know the latest temperature data";
		
		long t0 = System.currentTimeMillis();
		
		Llm llm = new Llm();
		
		DataTask chat = new DataTask(llm);
		String answer = chat.handle(query);
		long t1 = System.currentTimeMillis();
		System.out.println(">"+answer+"\n Time overall ("+(t1-t0)+" ms)");
		
	}
}

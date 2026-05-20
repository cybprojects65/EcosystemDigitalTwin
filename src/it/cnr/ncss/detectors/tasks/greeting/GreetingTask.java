package it.cnr.ncss.detectors.tasks.greeting;
import java.io.File;
import java.util.List;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;



public class GreetingTask {

	    private final Ollama ollama;
	    static String fallbackFile = "./task_prompts/greeting_fallback.txt"; 
	    static String legacyFile = "./task_prompts/greeting.txt"; 
	    public GreetingTask(Ollama ollama) {
	        this.ollama = ollama;
	    }

	    public String handleChat(String question) throws Exception{

	        System.out.println("\n========== CHAT TASK ==========");

	        String response;

	        try {
	            String ragResponse = generateChatAnswer(question);

	            if (ragResponse == null || ragResponse.contains("No relevant information")) {
	                System.out.println("[CHAT] Switching to LLM fallback");
	                response = generateFallbackChat(question);
	            } else {
	                response = ragResponse;
	            }

	        } catch (Exception e) {
	            System.out.println("[CHAT ERROR] " + e.getMessage());
	            response = generateFallbackChat(question);
	        }

	        return  response;
	        
	    }

	    private String generateFallbackChat(String question) throws Exception{
	    	String fallbackText = StringUtilsDTO.getText(new File(fallbackFile));
	    	fallbackText = fallbackText.replace("#QUERY#", question);
	    	
	        String prompt = """
	                %s
	                """.formatted(fallbackText);

	        try {
	            String response = ollama.send(prompt);

	            if (response != null && !response.isBlank()) {
	                return response.trim();
	            }

	        } catch (Exception e) {
	            System.out.println("[LLM FALLBACK ERROR] " + e.getMessage());
	        }

	        return "Hello! I'm the Digital Twin assistant. "
	                + "I can help you explore ecosystem risk, biodiversity, and environmental changes.";
	    }

	    private String generateChatAnswer(String query) {

	    	System.out.println("\n[RAG-CHAT] START");

	        try {
	            List<String> docs =  ollama.retrieveDocuments(query);

	            System.out.println("[RAG-CHAT] Retrieved docs: " + docs.size());

	            if (docs.isEmpty()) {
	                return "No relevant information found in the knowledge base.";
	            }

	            String prompt = buildPrompt(query, docs);

	            String raw = ollama.send(prompt);

	            if (raw == null || raw.isBlank() || raw.contains("Interpretation not available")) {
	                return "The system could not generate a reliable answer from the available data.";
	            }

	            return raw.trim();

	        } catch (Exception e) {
	            System.out.println("[RAG-CHAT ERROR] " + e.getMessage());
	            return "An error occurred while generating the answer.";
	        }
	    }

	   

	    
	    private String buildPrompt(String query, List<String> documents) throws Exception{

	        String context = String.join(
	                "\n\n",
	                documents.stream().toList()
	        );

	        String legacyText = StringUtilsDTO.getText(new File(legacyFile));
	        legacyText = legacyText.replace("#QUERY#", query);
	        legacyText = legacyText.replace("#CONTEXT#", context);
	        
	        String prompt = """
	                %s
	                """.formatted(legacyText);
	        
	        return prompt;
	    }
	    
	   
	
	    public static void main(String[] args) throws Exception{
			
			//String query = "hello man!";
	    	String query = "I would like to know more about a multi-model generalisable ecosystem risk assessment methodology for the Massaciuccoli Lake basin wetland";
			
			long t0 = System.currentTimeMillis();
			
			Ollama llm = new Ollama();
			
			GreetingTask chat = new GreetingTask(llm);
			String answer = chat.handleChat(query);
			long t1 = System.currentTimeMillis();
			System.out.println(">"+answer+" ("+(t1-t0)+" ms)");
			
		}
	    
}

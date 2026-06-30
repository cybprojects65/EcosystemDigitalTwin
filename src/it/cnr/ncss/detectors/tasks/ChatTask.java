package it.cnr.ncss.detectors.tasks
;
import it.cnr.ncss.llm.Llm;



public class ChatTask extends AbstractTask {
	
	    public ChatTask(Llm ollama) throws Exception {
		super(ollama);
		super.answerFile = conf.getProperty("chat_answer");
		
	}

	    public String handle(String question) throws Exception {

			System.out.println("[CHAT] answering to: "+question);

			String response = generateAnswer(question);

			return response;

		}
	    
		public static void main(String[] args) throws Exception{
			
			//String query = "hello man!";
	    	String query = "I would like to know more about a multi-model generalisable ecosystem risk assessment methodology for the Massaciuccoli Lake basin wetland";
			
			long t0 = System.currentTimeMillis();
			
			Llm llm = new Llm();
			
			ChatTask chat = new ChatTask(llm);
			String answer = chat.handle(query);
			long t1 = System.currentTimeMillis();
			System.out.println(">"+answer+"\n Time overall ("+(t1-t0)+" ms)");
			
		}
	    
}

package it.cnr.ncss.detectors.tasks
;
import java.io.File;
import java.util.List;

import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;



public class GreetingsTask extends AbstractTask {
	
	    public GreetingsTask(Ollama ollama) throws Exception {
		super(ollama);

	}

		public static void main(String[] args) throws Exception{
			
			//String query = "hello man!";
	    	String query = "I would like to know more about a multi-model generalisable ecosystem risk assessment methodology for the Massaciuccoli Lake basin wetland";
			
			long t0 = System.currentTimeMillis();
			
			Ollama llm = new Ollama();
			
			GreetingsTask chat = new GreetingsTask(llm);
			String answer = chat.handle(query);
			long t1 = System.currentTimeMillis();
			System.out.println(">"+answer+"\n Time overall ("+(t1-t0)+" ms)");
			
		}
	    
}

package it.cnr.ncss.orchestrator;

import it.cnr.ncss.detectors.tasks.greeting.GreetingTask;
import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;

public class DigitalTwin {

	public static void main(String[] args) throws Exception {

		// String query = "what impacts biodiversity most?";
		String query = "hello man!";
		
		long t0 = System.currentTimeMillis();

		String queryNorm = StringUtilsDTO.normalizeQuery(query);
		
		Ollama llm = new Ollama();
		Router rout = new Router(llm);
		QueryIntent route = rout.route(queryNorm);
		
		long t01 = System.currentTimeMillis();
		System.out.println("Routed 1: " + QueryIntent.valueOf(route.name()) + " in " + (t01 - t0) + "ms");
		
		if (route == QueryIntent.GREETING) {
			GreetingTask chat = new GreetingTask(llm);
			String answer = chat.handleChat(query);
			long t1 = System.currentTimeMillis();
			System.out.println(">"+answer+" ("+(t1-t0)+" ms)");
			System.out.println(">");
		
		}
		

	}
}

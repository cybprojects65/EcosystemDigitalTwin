package it.cnr.ncss.orchestrator;

import it.cnr.ncss.detectors.tasks.AbstractTask;
import it.cnr.ncss.llm.Ollama;
import it.cnr.ncss.utils.StringUtilsDTO;

public class DigitalTwin {

	public static void main(String[] args) throws Exception {

		// String query = "what impacts biodiversity most?";
		//String query = "hello man!";
		//String query = "give me the latest data on precipitation";
		//String query = "which factors increase temperature?";
		String query = "which factors have no effect on temperature?";
		
		long t0 = System.currentTimeMillis();

		String queryNorm = StringUtilsDTO.normalizeQuery(query);
		
		Ollama llm = new Ollama();
		Router rout = new Router(llm);
		QueryIntent route = rout.route(queryNorm);
		
		long t01 = System.currentTimeMillis();
		
		System.out.println("Routed 1: " + QueryIntent.valueOf(route.name()) + " in " + (t01 - t0) + "ms");
		
		AbstractTask task = rout.getTask();
		String answer = task.handle(query);
		
		long t1 = System.currentTimeMillis();
		System.out.println(">"+answer+" ("+(t1-t0)+" ms)");
		
	}
	
}

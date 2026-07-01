package it.cnr.ncss.orchestrator;

import it.cnr.ncss.detectors.tasks.AbstractTask;
import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.StringUtilsDTO;

public class DigitalTwin {

	public static void main(String[] args) throws Exception {

		// String query = "what impacts biodiversity most?";
		//String query = "hello man!";
		//String query = "show me the latest data";
//		String query = "Which aspects of ecosystem functioning are represented in the dataset?";
		//String query = "Which ecological variables are available in the dataset for ecosystem assessment?";
		//String query = 	"Which environmental variables contribute most to ecosystem risk under changing climatic conditions?";
		//String query = 	"Which climate-related variable has the strongest impact on ecosystem risk?";
		//String query = 	"What is the ecosystem risk if temperature increases by 3°C but tree cover also increases significantly?";
		//String query = 			"What happens to ecosystem risk when tree cover increases from 30% to 70%?";
		String query = 			"What happens to ecosystem risk when tree cover increases of 30%?";
		
		//String query = "give me the latest data on precipitation";
		//String query = "which factors increase temperature?";
		//String query = "which factors have no effect on temperature?";
		//String query = "Does tree cover alone control biodiversity?";
		//String query = "How does biodiversity influence ecosystem risk in the Massaciuccoli basin?";
		
		System.out.println("[MAIN] User's query: " + query);
		
		long t0 = System.currentTimeMillis();

		String queryNorm = StringUtilsDTO.normalizeQuery(query);
		
		Llm llm = new Llm();
		Router rout = new Router(llm);
		//QueryIntent route = rout.route(queryNorm);
		Intents route = rout.lightroute(queryNorm);
		
		long t01 = System.currentTimeMillis();
		
		//System.out.println("[MAIN] Routed: " + QueryIntent.valueOf(route.name()) + " in " + (t01 - t0) + "ms");
		System.out.println("[MAIN] Routed: " + Intents.valueOf(route.name()) + " in " + (t01 - t0) + "ms");
		
		AbstractTask task = rout.getTask();
		String answer = task.handle(query);
		
		long t1 = System.currentTimeMillis();
		System.out.println("[MAIN] Request processed in ("+(t1-t0)+" ms)");
		System.out.println("\nINTERPRETATION:");
		System.out.println(answer);
		System.out.println("\n---------------------------");

			
	}
	
}

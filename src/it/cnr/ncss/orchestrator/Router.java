package it.cnr.ncss.orchestrator;

import it.cnr.ncss.detectors.GeneralDetector;
import it.cnr.ncss.detectors.tasks.AbstractTask;
import it.cnr.ncss.detectors.tasks.ChatTask;
import it.cnr.ncss.detectors.tasks.CorrelationTask;
import it.cnr.ncss.detectors.tasks.DataTask;
import it.cnr.ncss.detectors.tasks.RiskImportanceTask;
import it.cnr.ncss.detectors.tasks.RiskVariationTask;
import it.cnr.ncss.llm.Llm;

public class Router {

	Llm llm = null;
	AbstractTask currentTask;
	
	public Router(Llm llm) {
		this.llm = llm;
	}
	
	public AbstractTask getTask() {
		return currentTask;
	}
	
	public Intents lightroute(String q) throws Exception{
		
		
	    //String q = StringUtilsDTO.normalizeQuery(query);
	    System.out.println("[ROUTER] routing request: "+q);
	    
	    int intents = Intents.values().length;
	    Intents [] qi = new Intents[intents];
	    double [] qs = new double[intents];
	    AbstractTask [] tasks = new AbstractTask[intents];
	    
	    System.out.println("[ROUTER] checking correlation");
	    
	    System.out.println("[ROUTER-DATA]");
	    qs[1] = new GeneralDetector(llm, "data_query_example", "data_query_similarity_threshold").matches(q);
	    qi[1] = Intents.DATA;
	    tasks[1] = new DataTask(llm);
	    
	    System.out.println("[ROUTER-CHAT]");
	    qs[0] = new GeneralDetector(llm, "chat_query_example", "chat_query_similarity_threshold").matches(q);
	    qi[0] = Intents.CHAT;
	    tasks[0] = new ChatTask(llm);

	    
	    
	    System.out.println("[ROUTER-CORRELATION]");
	    qs[2] = new GeneralDetector(llm, "correlation_query_example", "correlation_query_similarity_threshold").matches(q);
	    qi[2] = Intents.CORRELATION;
	    tasks[2] = new CorrelationTask(llm);
	    
	    System.out.println("[ROUTER-IMPORTANCE]");
	    qs[3] = new GeneralDetector(llm, "risk_importance_query_example", "risk_importance_query_similarity_threshold").matches(q);
	    qi[3] = Intents.RISK_IMPORTANCE;
	    tasks[3] = new RiskImportanceTask(llm);
	    
	    
	    System.out.println("[ROUTER-VARIATION]");
	    qs[4] = new GeneralDetector(llm, "risk_variation_query_example", "risk_variation_query_similarity_threshold").matches(q);
	    qi[4] = Intents.RISK_VARIATION;
	    tasks[4] = new RiskVariationTask(llm);
	    
	    
	    System.out.println("[ROUTER] checking optimal routing");
	    int optimal = -1;
	    double optimalScore = 0;
	    for (int i = 0; i<qs.length;i++) {
	    	if (qs[i]>optimalScore) {
	    		optimal = i;
	    		optimalScore = qs[i];
	    	}
	    } 
	    
	    System.out.println("[ROUTER] Optimal routing: "+qi[optimal]+ " with score "+optimalScore);
	    currentTask = tasks[optimal];
	    
	    return qi[optimal];
	}
	
	
	public static void main(String[] args) throws Exception{
		
		//String query = "what impacts biodiversity most?";
		//String query = "show me the latest data";
		//String query = "hello there";
		String query = "show me the latest data";
		long t0 = System.currentTimeMillis();
		
		Llm llm = new Llm();
		Router rout = new Router(llm);
		/*
		QueryIntent route = rout.route(query);
		
		System.out.println("Routed 1: "+QueryIntent.valueOf(route.name()) + " in "+(t1-t0)+"ms");
		*/
		Intents route = rout.lightroute(query);
		long t1 = System.currentTimeMillis();
		System.out.println("Routed 1: "+Intents.valueOf(route.name()) + " in "+(t1-t0)+"ms");
		
		query = "what impacts biodiversity most?";
		/*
		route = rout.route(query);
		long t11 = System.currentTimeMillis();
		System.out.println("Routed 2: "+QueryIntent.valueOf(route.name())  + " in "+(t11-t1)+"ms");
		*/
		route = rout.lightroute(query);
		long t11 = System.currentTimeMillis();
		System.out.println("Routed 2: "+Intents.valueOf(route.name()) + " in "+(t11-t1)+"ms");
		
	}
}

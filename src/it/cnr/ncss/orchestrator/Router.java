package it.cnr.ncss.orchestrator;

import it.cnr.ncss.detectors.ComparisonDetector;
import it.cnr.ncss.detectors.DataDetector;
import it.cnr.ncss.detectors.DeltaDetector;
import it.cnr.ncss.detectors.DependencyDetector;
import it.cnr.ncss.detectors.DriversDetector;
import it.cnr.ncss.detectors.GreetingsDetector;
import it.cnr.ncss.detectors.ImportanceDetector;
import it.cnr.ncss.detectors.tasks.AbstractTask;
import it.cnr.ncss.detectors.tasks.DataTask;
import it.cnr.ncss.detectors.tasks.DriversTask;
import it.cnr.ncss.detectors.tasks.GreetingsTask;
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
	
	public QueryIntent route(String q) throws Exception{
		
		
	    //String q = StringUtilsDTO.normalizeQuery(query);
	    System.out.println("[ROUTER] routing request: "+q);
	    
	    int intents = QueryIntent.values().length;
	    QueryIntent [] qi = new QueryIntent[intents];
	    double [] qs = new double[intents];
	    AbstractTask [] tasks = new AbstractTask[intents];
	    
	    System.out.println("[ROUTER] checking Greetings");
	    qs[0] = new GreetingsDetector(llm).matches(q);
	    qi[0] = QueryIntent.GREETING;
	    tasks[0] = new GreetingsTask(llm);
	    
	    System.out.println("[ROUTER] checking Data");
	    qs[6] = new DataDetector(llm).matches(q);
	    qi[6] = QueryIntent.DATA;
	    tasks[6] = new DataTask(llm);
	    
	    System.out.println("[ROUTER] checking Drivers");
	    qs[4] = new DriversDetector(llm).matches(q);
	    qi[4] = QueryIntent.DRIVERS;
	    tasks[4] = new DriversTask(llm);
	    
	    System.out.println("[ROUTER] checking Delta");
	    qs[1] = new DeltaDetector(llm).matches(q);
	    qi[1] = QueryIntent.DELTA;
	    
	    System.out.println("[ROUTER] checking Comparison");
	    qs[2] = new ComparisonDetector(llm).matches(q);
	    qi[2] = QueryIntent.COMPARISON;
	    
	    System.out.println("[ROUTER] checking Importance");
	    qs[3] = new ImportanceDetector(llm).matches(q);
	    qi[3] = QueryIntent.IMPORTANCE;
	    
	    System.out.println("[ROUTER] checking Dependency");
	    qs[5] = new DependencyDetector(llm).matches(q);
	    qi[5] = QueryIntent.DEPENDENCY;
	    
	    qs[qs.length-1] = 0.7;
	    qi[qs.length-1] = QueryIntent.UNKNOWN;
	    
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
		String query = "show me the latest data";
		
		long t0 = System.currentTimeMillis();
		
		Llm llm = new Llm();
		Router rout = new Router(llm);
		QueryIntent route = rout.route(query);
		long t1 = System.currentTimeMillis();
		System.out.println("Routed 1: "+QueryIntent.valueOf(route.name()) + " in "+(t1-t0)+"ms");
		
		
		query = "what impacts biodiversity most?";
		
		route = rout.route(query);
		long t11 = System.currentTimeMillis();
		System.out.println("Routed 2: "+QueryIntent.valueOf(route.name())  + " in "+(t11-t1)+"ms");
	}
}

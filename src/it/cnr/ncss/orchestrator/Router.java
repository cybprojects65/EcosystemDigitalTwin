package it.cnr.ncss.orchestrator;

import it.cnr.ncss.detectors.ComparisonDetector;
import it.cnr.ncss.detectors.DeltaDetector;
import it.cnr.ncss.detectors.DependencyDetector;
import it.cnr.ncss.detectors.DriversDetector;
import it.cnr.ncss.detectors.GreetingsDetector;
import it.cnr.ncss.detectors.ImportanceDetector;
import it.cnr.ncss.llm.Ollama;

public class Router {

	Ollama llm = null;
	
	public Router(Ollama llm) {
		this.llm = llm;
	}
	
	public QueryIntent route(String q) throws Exception{
		
		
	    //String q = StringUtilsDTO.normalizeQuery(query);
	    System.out.println("Debug: routing request: "+q);
	    
	    
	    QueryIntent [] qi = new QueryIntent[7];
	    double [] qs = new double[7];
	    
	    System.out.println("Debug: checking Greetings");
	    qs[0] = new GreetingsDetector(llm).matches(q);
	    qi[0] = QueryIntent.GREETING;
	    
	    System.out.println("Debug: checking Delta");
	    qs[1] = new DeltaDetector(llm).matches(q);
	    qi[1] = QueryIntent.DELTA;
	    
	    System.out.println("Debug: checking Comparison");
	    qs[2] = new ComparisonDetector(llm).matches(q);
	    qi[2] = QueryIntent.COMPARISON;
	    
	    System.out.println("Debug: checking Importance");
	    qs[3] = new ImportanceDetector(llm).matches(q);
	    qi[3] = QueryIntent.IMPORTANCE;
	    
	    System.out.println("Debug: checking Drivers");
	    qs[4] = new DriversDetector(llm).matches(q);
	    qi[4] = QueryIntent.DRIVERS;
	    
	    System.out.println("Debug: checking Dependency");
	    qs[5] = new DependencyDetector(llm).matches(q);
	    qi[5] = QueryIntent.DEPENDENCY;
	    
	    qs[6] = 0.7;
	    qi[6] = QueryIntent.UNKNOWN;
	    		
	    int optimal = -1;
	    double optimalScore = 0;
	    for (int i = 0; i<qs.length;i++) {
	    	if (qs[i]>optimalScore) {
	    		optimal = i;
	    		optimalScore = qs[i];
	    	}
	    } 
	    
	    System.out.println("Optimal routing: "+qi[optimal]);
	    	
	    return qi[optimal];
	}
	
	
	public static void main(String[] args) throws Exception{
		
		//String query = "what impacts biodiversity most?";
		String query = "which environmental variables induce biodiversity loss?";
		long t0 = System.currentTimeMillis();
		
		Ollama llm = new Ollama();
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

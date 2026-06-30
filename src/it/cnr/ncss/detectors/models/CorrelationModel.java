package it.cnr.ncss.detectors.models;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.utils.UtilsDTO;

public class CorrelationModel {

	public KbManager kb;
	
	public CorrelationModel() throws Exception{
		kb = new KbManager();
	}
	
	public static String cramerInterpretation(double v) {

	    if (Double.isNaN(v))
	        return "undefined";

	    if (v < 0.10)
	        return "negligible";

	    if (v < 0.30)
	        return "weak";

	    if (v < 0.50)
	        return "moderate";

	    if (v < 0.70)
	        return "strong";

	    return "very strong";
	}
	
	public static String correlationRatioInterpretation(double eta) {

	    if (Double.isNaN(eta))
	        return "undefined";

	    if (eta < 0.10)
	        return "negligible";

	    if (eta < 0.30)
	        return "weak";

	    if (eta < 0.50)
	        return "moderate";

	    if (eta < 0.70)
	        return "strong";

	    return "very strong";
	}
	
	public static String pearsonInterpretation(double r) {

	    if (Double.isNaN(r))
	        return "undefined";

	    double a = Math.abs(r);

	    if (a < 0.10)
	        return "neutral";

	    if (a < 0.30)
	        //return r > 0 ? "weakly positive" : "weakly negative";
	    	return r > 0 ? "neutral" : "neutral";
	    	
	    if (a < 0.50)
	        return r > 0 ? "moderately positive" : "moderately negative";

	    if (a < 0.70)
	        return r > 0 ? "strongly positive" : "strongly negative";

	    return r > 0 ? "very strongly positive" : "very strongly negative";
	}
	
	public Map<String, Double> association(String source, String target) throws Exception {
		
		String [] sourceColumn = kb.getColumn(source);
		String [] targetColumn = kb.getColumn(target);
		Map<String, Double> interp = new HashMap<String, Double>();
		
		boolean isCategorialSource = UtilsDTO.isCategorical(sourceColumn);
		boolean isCategorialTarget = UtilsDTO.isCategorical(targetColumn);
		
		if (isCategorialSource && isCategorialTarget) {
			double corr = UtilsDTO.cramerV(sourceColumn, targetColumn);
			interp.put(cramerInterpretation(corr),corr);
			return interp;
		}else if (isCategorialSource && !isCategorialTarget) {
			double [] targetNumber = kb.getColumnNumericValues(target);
			double corr = UtilsDTO.correlationRatio(sourceColumn, targetNumber);
			interp.put(correlationRatioInterpretation(corr),corr);
			return interp;
		}else if (!isCategorialSource && isCategorialTarget) {
			double [] sourceNumber = kb.getColumnNumericValues(source);
			double corr = UtilsDTO.correlationRatio(targetColumn, sourceNumber);
			interp.put(correlationRatioInterpretation(corr),corr);
			return interp;
		}else {
			double [] sourceNumber = kb.getColumnNumericValues(source);
			double [] targetNumber = kb.getColumnNumericValues(target);
			double corr = UtilsDTO.pearsonCorrelation(sourceNumber, targetNumber);
			interp.put(pearsonInterpretation(corr),corr);
			return interp;
		}
			
			
	}
	
	
	public Map<String,Map<String,Double>> correlateAll(String source, String target) throws Exception {

		System.out.println("[CORRELATION] finding correlations for target var: " + target);
		System.out.println("[CORRELATION] reading KB file");
		
		String features[] = kb.getFeatures();
		int riskIndex = kb.riskFeatureIndex();
		int targetIndex = riskIndex;
		if (target!=null) 
			targetIndex = kb.featureIndex(target);

		Map<String,Map<String,Double>> correlations = new LinkedHashMap<String, Map<String,Double>>();
		for (int i=0;i<features.length;i++) {
			if (i == targetIndex || i == riskIndex)
				continue;
			System.out.println("[CORRELATION] comparing target vs source var: "+features[i]);	
			Map<String,Double> corr = association(features[i], features[targetIndex]);
			correlations.put(features[i], corr);
		}
		
		// answer
		return correlations;
	}
	
	
	
}

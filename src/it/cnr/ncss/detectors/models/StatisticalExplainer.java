package it.cnr.ncss.detectors.models;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.UtilsDTO;
import weka.core.Instance;
import weka.core.Instances;

public class StatisticalExplainer {

	
	public Map<String, Double> explain(Instances dataset, String highClassInTheModel, String lowClassInTheModel) throws Exception {
		
		System.out.println("[StatisticalExplainer] extracting explanations for high risk class");
		
		Map<String, Double> highshap = explainClass(dataset, highClassInTheModel);
		
		System.out.println("[StatisticalExplainer] extracting explanations for low risk class");
		
		//Map<String, Double> lowshap = explainClass(dataset, lowClassInTheModel);

		/*
		 * Map<String, Double> averageshap = null;
		 * System.out.println("[SHAP] explaining " + indices.length + " rows"); for (int
		 * i = 0; i < indices.length; i++) { //
		 * System.out.println("[SHAP] explaining iteration "+(i+1)); Instance instance =
		 * dataset.get(indices[i]); Map<String, Double> shap = explainSingle(instance,
		 * targetClassInTheOutput); // System.out.println("[SHAP] shap " + i + ": " +
		 * shap.toString()); if (averageshap == null) averageshap = shap; else { for
		 * (String key : shap.keySet()) { Double value = shap.get(key); Double prev =
		 * averageshap.get(key); averageshap.put(key, (value + prev)); } }
		 * 
		 * }
		 */

		System.out.println("[StatisticalExplainer] merging explanations");
		Map<String, Double> averageshap = new HashMap<String, Double>();

		averageshap.putAll(highshap);
		/*
		for (String key : lowshap.keySet()) {

			Double prevValue = averageshap.get(key);
			if (prevValue==null)
				prevValue=0d;
			
			Double value = lowshap.get(key);
			if (value > 0 && (value > prevValue)) {
				averageshap.put(key, -value);
			}

		}
		 */
		/*
		 * for (String key : averageshap.keySet()) { averageshap.put(key,
		 * averageshap.get(key) / (double) indices.length); }
		 */
		System.out.println("[StatisticalExplainer] returning explanations");
		return UtilsDTO.sortByValueDescending(averageshap);
	}

	private Map<String, Double> explainClass(Instances dataset, String classInTheModel) {

		int ncolumns = dataset.numAttributes();
		int targetColumnIdx = dataset.classIndex();
		int nrows = dataset.numInstances();
		
		double perc75ByColum [] = new double[ncolumns];
		
		Map<String,Double> contributions = new HashMap<String, Double>();
		
		for (int i=0;i<ncolumns;i++) {
			
			if (i==targetColumnIdx)
				continue;
			
			double [] column = UtilsDTO.getNumericColumnFromWekaInstances(dataset,i);
			double perc75 = UtilsDTO.percentile(column,50); //changed to 50th perc to give more flexibility
			perc75ByColum[i] = perc75;
			List<Double> classValues = new ArrayList<Double>(); 
			
			for (int j=0;j<nrows;j++) {
				
				Instance row = dataset.instance(j);
		        String targetClassOfRow = row.stringValue(targetColumnIdx);
		        
				//if we are processing a row that corresponds to good classification
				if (targetClassOfRow.equals(classInTheModel)) {
					
					classValues.add(column[j]);
					
				}
				
			}
			
			double averageInClass = UtilsDTO.averageOfList(classValues);
			
			if (averageInClass>=perc75) {
				double strength = (averageInClass-perc75)/(double) perc75;
				String columnName = dataset.attribute(i).name();
				contributions.put(columnName, strength);
			}
		}
		
		
		
		return contributions;
	}
	
	
	
}

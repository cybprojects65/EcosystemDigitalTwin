package it.cnr.ncss.detectors.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.UtilsDTO;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class RandomForestModel implements Serializable{

	
		/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
		public static int seed=42;
		public static int niterations=200;
		public Instances trainingset; 
		public RandomForest forest;
		public ArrayList<Attribute> attributes = new ArrayList<>();
		public Instances testset;
		
		public Instances getTrainingSet() {
			return trainingset;
		}
		
		public RandomForest getModel() {
			return forest;
		}
		
	    public RandomForest trainRandomForest(
	            List<List<Object>> rows,
	            String[] featureNames,
	            String targetName
	    ) throws Exception {

	        if (rows == null || rows.isEmpty()) {
	            throw new IllegalArgumentException("Rows cannot be empty");
	        }
	        
	        int numColumns = rows.get(0).size();
	        int targetIndex = numColumns - 1;
	        for (int i = 0;i<featureNames.length;i++){
	        	if (featureNames[i].equals(targetName)) {
	        		targetIndex = i;
	        		break;
	        	}
	        }
	        System.out.println("[RANDOM FOREST] Training a RF "+(numColumns-1)+"->1 ("+targetIndex+")");
	        

	        //for (int col = 0; col < targetIndex; col++) {
	        for (int col = 0; col < numColumns; col++) {
	            Object firstValue = firstNonNullValue(rows, col);
	            
	            if (firstValue instanceof Number) {
	                attributes.add(new Attribute(featureNames[col]));
	                System.out.println("[RANDOM FOREST] Numeric: "+featureNames[col]);
	            } else {
	                List<String> values = collectCategoricalValues(rows, col);
	                attributes.add(new Attribute(featureNames[col], values));
	                System.out.println("[RANDOM FOREST] Categorial: "+featureNames[col]);
	            }
	        }
	        
	        
	        trainingset = new Instances("training_data", attributes, rows.size());
	        trainingset.setClassIndex(targetIndex);
	        System.out.println("[RANDOM FOREST] Building the training set");
	        for (List<Object> row : rows) {
	            DenseInstance instance = new DenseInstance(numColumns);

	            for (int col = 0; col < numColumns; col++) {
	                Object value = row.get(col);
	                Attribute attr = attributes.get(col);

	                if (value == null) {
	                    instance.setMissing(col);
	                } else if (attr.isNumeric()) {
	                    instance.setValue(attr, ((Number) value).doubleValue());
	                } else {
	                    instance.setValue(attr, value.toString());
	                }
	            }

	            trainingset.add(instance);
	        }

	        System.out.println("[RANDOM FOREST] Training the RF");
	        forest = new RandomForest();
	        forest.setNumIterations(niterations);   // number of trees
	        forest.setMaxDepth(0);          // 0 means unlimited depth
	        forest.setSeed(seed);
	        forest.buildClassifier(trainingset);
	        System.out.println("[RANDOM FOREST] Training done.");
	        return forest;
	    }

	    private static Object firstNonNullValue(List<List<Object>> rows, int column) {
	        for (List<Object> row : rows) {
	            if (row.get(column) != null) {
	                return row.get(column);
	            }
	        }
	        throw new IllegalArgumentException("Column " + column + " contains only null values");
	    }

	    private static List<String> collectCategoricalValues(List<List<Object>> rows, int column) {
	        Set<String> values = new LinkedHashSet<>();

	        for (List<Object> row : rows) {
	            Object value = row.get(column);
	            if (value != null) {
	                values.add(value.toString());
	            }
	        }

	        return new ArrayList<>(values);
	    }
	
	
	    public List<double[]> predictProbabilityTrainingSet() throws Exception {
	    	
	    	List<double[]> predictions = new ArrayList<double[]>();
	    	for (Instance instance:trainingset) {
	    		double[] distribution = forest.distributionForInstance(instance);
	    		predictions.add(distribution);
	    	}
	    	
	        return predictions;
	    }
	    
	    public List<double[]> predict(List<List<Object>> simulation) throws Exception {
	    	
	    	testset = new Instances("test_data", attributes, simulation.size());
	    	testset.setClassIndex(trainingset.classIndex());
	    	int numColumns = simulation.get(0).size();
	    	
	        System.out.println("[RANDOM FOREST] Building the test set");
	        for (List<Object> row : simulation) {
	            DenseInstance instance = new DenseInstance(numColumns);
	            for (int col = 0; col < numColumns; col++) {
	                Object value = row.get(col);
	                Attribute attr = attributes.get(col);

	                if (value == null) {
	                    instance.setMissing(col);
	                } else if (attr.isNumeric()) {
	                    instance.setValue(attr, ((Number) value).doubleValue());
	                } else {
	                    instance.setValue(attr, value.toString());
	                }
	            }

	            testset.add(instance);
	        }
	        
	    	List<double[]> predictions = new ArrayList<double[]>();
	    	for (Instance instance:testset) {
	    		double[] distribution = forest.distributionForInstance(instance);
	    		predictions.add(distribution);
	    	}
	    	
	        return predictions;
	    }
	    
	    public Instances getLatestTestSet() {
	    	return testset;
	    }
	    public double outputRelativeVariation(List<double[]> proj1, List<double[]> proj2, int classToCompare) throws Exception {
	    	
	    	double sum1 = 0;
	    	for(double[] p1:proj1) {
	    		int bestIdx = UtilsDTO.findMax(p1);
	    		if (bestIdx==classToCompare)
	    			sum1=sum1+p1[classToCompare];
	    	}
	    	double sum2 = 0;
	    	for(double[] p2:proj2) {    		
	    		int bestIdx = UtilsDTO.findMax(p2);
	    		if (bestIdx==classToCompare)
	    			sum2=sum2+p2[classToCompare];
	    	}
	    	double var = (sum2-sum1)/sum1;
	    	return var;
	    }
	    
	public static void main(String[] args) throws Exception{
		 RandomForestModel rf = new RandomForestModel();
		 KbManager kb = new KbManager();
		 kb.parseKB();
		 Config conf = new Config(); 
		 int trainingClassIndex = Integer.parseInt(conf.getProperty("risk_index_to_predict")); //high-risk class
		 //String targetVariable = "Ecosystem risk classification according to a Variational Autoencoder with a later application of a Multi K-means process";
		 String targetVariable = conf.getProperty("risk_column");
		 
		 List<List<Object>> matrix = kb.getFeatureMatrix();
		 rf.trainRandomForest(matrix, kb.getFeatures(), targetVariable);
		 List<double[]> allPredictions = rf.predictProbabilityTrainingSet();
		 
		 for (int i =0;i<10;i++) {
			 System.out.println("Prediction "+i+": "+Arrays.toString(allPredictions.get(i)) );
		 }
		 
		Instances dataset = rf.getTrainingSet();
			 
		KernelShapExplainer explainer = new KernelShapExplainer(rf.getModel(), dataset, seed);	 
		
		Map<String, Double> explanation = explainer.explain(dataset, trainingClassIndex);
		System.out.println("Explanation of high-risk:");
		System.out.println(explanation.toString());
		boolean positive = true;
		Map<String, Double> positiveContributions = explainer.interpretContributors(explanation, positive);
		System.out.println("Positive to high-risk:\n"+positiveContributions.toString().replace(",", "\n"));
		
		Map<String, Double> negativeContributions = explainer.interpretContributors(explanation, !positive);
		System.out.println("Negative to high-risk:\n"+negativeContributions.toString().replace(",", "\n"));
		
		String featureToChange = "Change in average temperature compared to a recent past";
		double perc = 2;//0.5;
		
		//TODO: check the simulation to be actually what expected
		//TODO: manage a model that predicts one variable among the others
		List<List<Object>> simulation = kb.simulate(featureToChange, perc);
		List<List<Object>> kbmat = kb.getFeatureMatrix();
		
		List<double[]> preditions = rf.predict(simulation);
		
		for (int i =0;i<10;i++) {
			 System.out.println("Projections "+i+": "+Arrays.toString(preditions.get(i)) );
		 }
		 
		Map<String, Double> explanation2 = explainer.explain(rf.getLatestTestSet(), trainingClassIndex);
		
		double relativeVar = rf.outputRelativeVariation(allPredictions,preditions, trainingClassIndex);
		System.out.println("Relative variation of high-risk "+UtilsDTO.toPercentage(relativeVar));
		
		Map<String, Double> importanceForDelta = explainer.deltaShap( explanation ,explanation2);
		System.out.println("Important variables for delta "+importanceForDelta);
	}

}

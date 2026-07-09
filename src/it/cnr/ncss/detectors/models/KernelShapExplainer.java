package it.cnr.ncss.detectors.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.UtilsDTO;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class KernelShapExplainer {

	private final Classifier model;
	private final Instances background;
	private final int classIndex;
	private final Random random;
	private static int samplesPerFeature = 200;

	public KernelShapExplainer(Classifier model, Instances background, int seed) {
		this.model = model;
		this.background = new Instances(background);
		this.classIndex = background.classIndex();
		this.random = new Random(seed);
	}

	public int[] randomIndicesForTargetClass(Instances dataset, int targetClassInTheOutput, int requestedSamples,
			boolean includeTarget) {
		if (dataset == null || dataset.numInstances() == 0) {
			throw new IllegalArgumentException("Dataset is empty.");
		}

		if (dataset.classIndex() < 0) {
			throw new IllegalArgumentException("Dataset class index is not set.");
		}

		List<Integer> targetClassIndices = new ArrayList<>();

		for (int i = 0; i < dataset.numInstances(); i++) {
			Instance instance = dataset.instance(i);
			boolean classIsMissing = instance.classIsMissing();
			int classValue = (int) instance.classValue();
			if (includeTarget) {
				if (!classIsMissing && classValue == targetClassInTheOutput) {
					targetClassIndices.add(i);
				}
			} else {
				if (!classIsMissing && classValue != targetClassInTheOutput) {
					targetClassIndices.add(i);
				}
			}
		}

		if (targetClassIndices.isEmpty()) {
			throw new IllegalArgumentException(
					"No training instances found for target class index " + targetClassInTheOutput);
		}

		java.util.Collections.shuffle(targetClassIndices, random);

		int n = Math.min(requestedSamples, targetClassIndices.size());
		int[] indices = new int[n];

		for (int i = 0; i < n; i++) {
			indices[i] = targetClassIndices.get(i);
		}

		return indices;
	}

	public Map<String, Double> doShap(Instances dataset, int[] indices, int targetClassInTheOutput) throws Exception {

		Map<String, Double> averageshap = null;
		System.out.println("[SHAP] explaining " + indices.length + " rows");
		for (int i = 0; i < indices.length; i++) {
			// System.out.println("[SHAP] explaining iteration "+(i+1));
			Instance instance = dataset.get(indices[i]);
			Map<String, Double> shap = explainSingle(instance, targetClassInTheOutput);
			// System.out.println("[SHAP] shap " + i + ": " + shap.toString());
			if (averageshap == null)
				averageshap = shap;
			else {
				for (String key : shap.keySet()) {
					Double value = shap.get(key);
					Double prev = averageshap.get(key);
					averageshap.put(key, (value + prev));
				}
			}

		}

		System.out.println("[SHAP] averaging explanations");
		for (String key : averageshap.keySet()) {
			averageshap.put(key, averageshap.get(key) / (double) indices.length);
		}

		return averageshap;
	}

	public Map<String, Double> explain(Instances dataset, int targetClassInTheOutput) throws Exception {
		int datasetSize = dataset.numInstances();
		// int[] indices = UtilsDTO.randomIntegers(1000, 0, (datasetSize - 1));
		Config conf = new Config();
		int n_samples_for_shap = Integer.parseInt(conf.getProperty("n_samples_for_shap"));
		int low_risk_index_to_predict = Integer.parseInt(conf.getProperty("low_risk_index_to_predict"));
		
		int[] indicesTarget = randomIndicesForTargetClass(dataset, targetClassInTheOutput, n_samples_for_shap, true);
		int[] indicesNonTarget = randomIndicesForTargetClass(dataset, low_risk_index_to_predict, n_samples_for_shap, true);
		/*
		int[] indices = new int[indicesTarget.length + indicesNonTarget.length];
		System.arraycopy(indicesTarget, 0, indices, 0, indicesTarget.length);
		System.arraycopy(indicesNonTarget, 0, indices, indicesTarget.length, indicesNonTarget.length);
*/
		Map<String, Double> highshap = doShap(dataset, indicesTarget, targetClassInTheOutput);
		
		
		Map<String, Double> lowshap = doShap(dataset, indicesNonTarget, low_risk_index_to_predict);

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

		System.out.println("[SHAP] averaging explanations");
		Map<String, Double> averageshap = new HashMap<String, Double>();

		for (String key : highshap.keySet()) {
			Double value = highshap.get(key);
			if (value > 0) {
				averageshap.put(key, value / (double) n_samples_for_shap);
			}
		}

		for (String key : lowshap.keySet()) {

			Double prevValue = averageshap.get(key);
			if (prevValue==null)
				prevValue=0d;
			
			Double value = lowshap.get(key);
			if (value > 0 && (value > prevValue)) {
				averageshap.put(key, -value / (double) n_samples_for_shap);
			}

		}

		/*
		 * for (String key : averageshap.keySet()) { averageshap.put(key,
		 * averageshap.get(key) / (double) indices.length); }
		 */
		System.out.println("[SHAP] returning explanations");
		return UtilsDTO.sortByValueDescending(averageshap);
	}

	public static Map<String, Double> interpretContributors(Map<String, Double> shap, boolean positive) {

		double sum = 0;
		for (String key : shap.keySet()) {

			Double val = shap.get(key);

			if (val > 0 && positive) {
				sum = sum + val;
			} else if (val < 0 && !positive) {
				sum = sum + Math.abs(val);
			}

		}

		Map<String, Double> contributors = new HashMap<String, Double>();
		for (String key : shap.keySet()) {

			Double val = shap.get(key);
			//if (Math.abs(val) > 0.001) 
			{
				if (val > 0 && positive) {
					double perc = UtilsDTO.toPercentage(val / sum);
					if (perc>5)
						contributors.put(key,perc);
				} else if (val < 0 && !positive) {
					double perc = UtilsDTO.toPercentage(-1 * val / sum);
					if (Math.abs(perc)>5)
						contributors.put(key, perc);
				}
			}
		}

		return UtilsDTO.sortByValueDescending(contributors);
	}

	public Map<String, Double> explainSingle(Instance instance, int targetClassIndex) throws Exception {

		double[] shap = new double[background.numAttributes()];

		for (int feature = 0; feature < background.numAttributes(); feature++) {
			if (feature == classIndex) {
				continue;
			}

			double contribution = estimateFeatureContribution(instance, feature, targetClassIndex, samplesPerFeature);

			shap[feature] = contribution;
		}

		Map<String, Double> result = new LinkedHashMap<>();

		for (int i = 0; i < background.numAttributes(); i++) {
			if (i == classIndex) {
				continue;
			}

			result.put(background.attribute(i).name(), shap[i]);
		}

		return result;
	}

	private double estimateFeatureContribution(Instance instance, int feature, int targetClassIndex, int samples)
			throws Exception {

		double sum = 0.0;

		for (int s = 0; s < samples; s++) {
			Set<Integer> coalition = randomCoalitionExcluding(feature);

			Instance backgroundRow = background.instance(random.nextInt(background.numInstances()));

			Instance withoutFeature = buildSyntheticInstance(instance, backgroundRow, coalition);

			Set<Integer> coalitionWithFeature = new HashSet<>(coalition);
			coalitionWithFeature.add(feature);

			Instance withFeature = buildSyntheticInstance(instance, backgroundRow, coalitionWithFeature);

			double pWithout = predictProbability(withoutFeature, targetClassIndex);
			double pWith = predictProbability(withFeature, targetClassIndex);

			sum += pWith - pWithout;
		}

		return sum / samples;
	}

	private Set<Integer> randomCoalitionExcluding(int excludedFeature) {
		Set<Integer> coalition = new HashSet<>();

		for (int i = 0; i < background.numAttributes(); i++) {
			if (i == classIndex || i == excludedFeature) {
				continue;
			}

			if (random.nextBoolean()) {
				coalition.add(i);
			}
		}

		return coalition;
	}

	private Instance buildSyntheticInstance(Instance original, Instance backgroundRow, Set<Integer> presentFeatures) {
		double[] values = new double[background.numAttributes()];

		for (int i = 0; i < background.numAttributes(); i++) {
			if (i == classIndex) {
				values[i] = original.classValue();
			} else if (presentFeatures.contains(i)) {
				values[i] = original.value(i);
			} else {
				values[i] = backgroundRow.value(i);
			}
		}

		DenseInstance synthetic = new DenseInstance(1.0, values);
		synthetic.setDataset(background);

		return synthetic;
	}

	public Map<String, Double> deltaShap(Map<String, Double> shap1, Map<String, Double> shap2) {
		Map<String, Double> delta = new HashMap<String, Double>();

		for (String key : shap1.keySet()) {
			Double value1 = shap1.get(key);
			Double value2 = shap2.get(key);
			
			if (value1==null)
				value1=0d;
			else if (value2==null)
				value2=0d;
			
			if (value2 > value1) {
				double perc = (value2 - value1) / value1;
				delta.put(key, perc);
			}
		}

		return UtilsDTO.sortByValueDescending(delta);

	}

	private double predictProbability(Instance instance, int targetClassIndex) throws Exception {

		double[] distribution = model.distributionForInstance(instance);
		return distribution[targetClassIndex];
	}
}
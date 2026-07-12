package it.cnr.ncss.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class UtilsDTO {

	public static int findMax(double []vector) {
		
		double best = vector[0];
		int bestidx = 0;
		for (int i=0;i<vector.length;i++) {
			if (vector[i]>best) {
				best = vector[i];
				bestidx = i;
			}
			
		}
		
		return bestidx;
	}
	
	public static double averageOfList(List<Double> values) {
	    double sum = 0.0;

	    for (Double d : values) {
	        sum += d;
	    }

	    return sum / (double) values.size();
	}
	
	public static double averageColumn(List<String> csvlines, int column) throws Exception{
		
		int m = csvlines.size();
		double cumulative = 0;
		int g = 0;
		for (int i = 1;i<m;i++) {
			String elementS = csvlines.get(i);
			
			String [] elements = StringUtilsDTO.getCSVElements(elementS);
			Double e = null;
			try {
				e = Double.parseDouble(elements[column]);
				cumulative+=e;
				g++;
			}catch(Exception ex) {System.out.println("Skipping line "+i);}
			
			
		}
		cumulative = cumulative/(double)(g);
		
		return cumulative;
	}
	
	public static double [] getColumnNumericValues(List<String> csvlines, String columnName) throws Exception{
		return getColumnNumericValues(csvlines, columnName, false);
	}
	
	public static int getColumnIndex(String [] featureNames, String columnName) throws Exception{
		
        int headerNumber = 0;
		for (String head:featureNames) {
			if (head.toLowerCase().equals(columnName.toLowerCase()))
				break;
			else 
				headerNumber++;
		}
		if (headerNumber==featureNames.length)
			return -1;
		
		return headerNumber;
	}
	
	public static double [] getColumnNumericValues(List<String> csvlines, String columnName, boolean standardize) throws Exception{
		
		String header = csvlines.get(0);
		header = header.replace("\uFEFF", "");
		String headers [] = header.split(",");
			
        int headerNumber = 0;
		for (String head:headers) {
			if (head.toLowerCase().equals(columnName.toLowerCase()))
				break;
			else 
				headerNumber++;
		}
		
		List<Double> values = new ArrayList<Double>();
		
		int m = csvlines.size();
		for (int i = 2;i<m;i++) { //skip headers and explanations
			String elementS = csvlines.get(i);
			String [] elements = StringUtilsDTO.getCSVElements(elementS);
			Double e = null;
			try {
				e = Double.parseDouble(elements[headerNumber]);
				values.add(e);
				
			}catch(Exception ex) {
				//System.out.println(">get "+columnName+" skipping line "+i);
				return null;
				}
		}
		
		double columnE [] = new double[values.size()];
		int k = 0;
		for(Double v:values) {
			columnE[k]=v;
			k++;
		}
		
		if (standardize)
			columnE = standardize(columnE);
		
		return columnE;
	}
	
	public static String [] getColumn(List<List<Object>> matrix, int columnIdx) throws Exception{
		
		List<String> values = new ArrayList<String>();
		
		int m = matrix.size();
		for (int i = 0;i<m;i++) { //skip headers and explanations
			List<Object> elementS = matrix.get(i);
			values.add(""+elementS.get(columnIdx));
		}
		
		String columnE [] = new String[values.size()];
		int k = 0;
		for(String v:values) {
			columnE[k]=v;
			k++;
		}
		
		return columnE;
	}

	public static String [] getColumn(List<String> csvlines, String columnName) throws Exception{
		
		String header = csvlines.get(0);
		String headers [] = header.split(",");
			
        int headerNumber = 0;
		for (String head:headers) {
			if (head.toLowerCase().equals(columnName.toLowerCase()))
				break;
			else 
				headerNumber++;
		}
		
		List<String> values = new ArrayList<String>();
		
		int m = csvlines.size();
		for (int i = 2;i<m;i++) { //skip headers and explanations
			String elementS = csvlines.get(i);
			String [] elements = StringUtilsDTO.getCSVElements(elementS);
			
				values.add(elements[headerNumber]);
		}
		
		String columnE [] = new String[values.size()];
		int k = 0;
		for(String v:values) {
			columnE[k]=v;
			k++;
		}
		
		return columnE;
	}

	public static double[] standardize(double[] values) {
	    if (values == null || values.length == 0) {
	        throw new IllegalArgumentException("Input array must not be null or empty.");
	    }

	    int n = values.length;

	    // Compute mean
	    double sum = 0.0;
	    for (double v : values) {
	        sum += v;
	    }
	    double mean = sum / n;

	    // Compute standard deviation
	    double variance = 0.0;
	    for (double v : values) {
	        variance += Math.pow(v - mean, 2);
	    }
	    variance /= n;               // Population variance
	    double std = Math.sqrt(variance);

	    // Avoid division by zero
	    if (std == 0.0) {
	        return new double[n];    // All zeros
	    }

	    // Standardize
	    double[] standardized = new double[n];
	    for (int i = 0; i < n; i++) {
	        standardized[i] = (values[i] - mean) / std;
	    }

	    return standardized;
	}
	
	public static double pearsonCorrelation(double[] x, double[] y) throws Exception {
	    if (x == null || y == null) {
	        throw new IllegalArgumentException("Input arrays cannot be null.");
	    }

	    if (x.length != y.length) {
	        throw new IllegalArgumentException("Input arrays must have the same length.");
	    }

	    if (x.length < 2) {
	        throw new IllegalArgumentException("At least two observations are required.");
	    }

	    int n = x.length;

	    double meanX = 0.0;
	    double meanY = 0.0;

	    for (int i = 0; i < n; i++) {
	        meanX += x[i];
	        meanY += y[i];
	    }

	    meanX /= n;
	    meanY /= n;

	    double covariance = 0.0;
	    double varianceX = 0.0;
	    double varianceY = 0.0;

	    for (int i = 0; i < n; i++) {
	        double dx = x[i] - meanX;
	        double dy = y[i] - meanY;

	        covariance += dx * dy;
	        varianceX += dx * dx;
	        varianceY += dy * dy;
	    }

	    if (varianceX == 0.0 || varianceY == 0.0) {
	        return Double.NaN; // one vector is constant
	    }

	    return covariance / Math.sqrt(varianceX * varianceY);
	}
	
	public static boolean isCategorical(String [] values) {
	    if (values == null) {
	        throw new IllegalArgumentException("The list cannot be null or empty.");
	    }

	    for (String value : values) {
	        if (value == null || value.isBlank()) {
	            continue; // ignore missing values
	        }

	        try {
	            Double.parseDouble(value);
	        } catch (NumberFormatException e) {
	            return true;
	        }
	    }

	    return false;
	}
	
	public static int[] randomIntegers(int m, int min, int max) {
	    if (m < 0) {
	        throw new IllegalArgumentException("m must be non-negative.");
	    }
	    if (min > max) {
	        throw new IllegalArgumentException("min must be <= max.");
	    }

	    Random random = new Random(42);
	    int[] result = new int[m];

	    for (int i = 0; i < m; i++) {
	        result[i] = random.nextInt(max - min + 1) + min;
	    }

	    return result;
	}
	
	public static double toPercentage(double value) {
	    return Math.round(value * 1000.0) / 10.0;
	}
	
	public static LinkedHashMap<String, Double> sortByValueDescending(Map<String, Double> map) {
	    return map.entrySet()
	            .stream()
	            .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
	            .collect(Collectors.toMap(
	                    Map.Entry::getKey,
	                    Map.Entry::getValue,
	                    (e1, e2) -> e1,
	                    LinkedHashMap::new
	            ));
	}
	
	
	public static List<List<Object>> deepCopy(List<List<Object>> original) {
	    List<List<Object>> copy = new ArrayList<>(original.size());

	    for (List<Object> row : original) {
	        copy.add(new ArrayList<>(row));
	    }

	    return copy;
	}

	public String firstValidElement(String [] vector) {
		
		for (String v:vector) {
			
			if (v==null || v.equals("NA"))
				continue;
			else return v;	
		}
		return null;
		
	}
	
	
	public static String mostFrequentCategory(String[] values) {
	    if (values == null || values.length == 0) {
	        return null;
	    }

	    Map<String, Integer> counts = new HashMap<>();

	    for (String value : values) {
	        if (value == null || value.equals("NA")) {
	            continue;
	        }

	        counts.put(value, counts.getOrDefault(value, 0) + 1);
	    }

	    String mostFrequent = null;
	    int maxCount = 0;

	    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
	        if (entry.getValue() > maxCount) {
	            maxCount = entry.getValue();
	            mostFrequent = entry.getKey();
	        }
	    }

	    return mostFrequent;
	}
	
	
	public static String[] replaceMissingValues(
	        String[] values,
	        String replacement,
	        double percentage
	) {
	    if (values == null) {
	        throw new IllegalArgumentException("Input array cannot be null.");
	    }

	    
	    if (percentage < 0 || percentage > 100) {
	    	System.out.println("[replaceMissingValues] Warning: variation over 1: "+percentage);
	    	percentage = 1;
	        //throw new IllegalArgumentException("Percentage must be between 0 and 100.");
	    }
	     
	    
	    List<Integer> missingIndices = new ArrayList<>();

	    for (int i = 0; i < values.length; i++) {
	        if (values[i] == null || "NA".equals(values[i])) {
	            missingIndices.add(i);
	        }
	    }

	    int nToReplace = (int) Math.round(missingIndices.size() * percentage);
	    if (nToReplace>=missingIndices.size())
	    	nToReplace = missingIndices.size();
	    
	    Collections.shuffle(missingIndices, new Random(42));

	    for (int i = 0; i < nToReplace; i++) {
	        values[missingIndices.get(i)] = replacement;
	    }
	    
	    return values;
	}
	
	public static double correlationRatio(String[] categories, double[] values) {

	    if (categories.length != values.length) {
	        throw new IllegalArgumentException("Arrays must have the same length.");
	    }

	    Map<String, List<Double>> groups = new HashMap<>();

	    // Build groups
	    for (int i = 0; i < categories.length; i++) {

	        String cat = categories[i];

	        if (cat == null || cat.equals("NA") || Double.isNaN(values[i])) {
	            continue;
	        }

	        groups.computeIfAbsent(cat, k -> new ArrayList<>()).add(values[i]);
	    }

	    if (groups.isEmpty()) {
	        return Double.NaN;
	    }

	    // Global mean
	    double globalSum = 0;
	    int totalN = 0;

	    for (List<Double> g : groups.values()) {
	        for (double v : g) {
	            globalSum += v;
	            totalN++;
	        }
	    }

	    double globalMean = globalSum / (double)totalN;

	    // Between-group variance
	    double ssBetween = 0;

	    for (List<Double> g : groups.values()) {

	        double mean = 0;

	        for (double v : g)
	            mean += v;

	        mean /= g.size();

	        ssBetween += g.size() * Math.pow(mean - globalMean, 2);
	    }

	    // Total variance
	    double ssTotal = 0;

	    for (List<Double> g : groups.values()) {
	        for (double v : g)
	            ssTotal += Math.pow(v - globalMean, 2);
	    }

	    if (ssTotal == 0)
	        return 0;

	    return Math.sqrt(ssBetween / ssTotal);
	}

	public static double cramerV(String[] A, String[] B) {

	    if (A.length != B.length) {
	        throw new IllegalArgumentException("Arrays must have the same length.");
	    }

	    // Collect categories
	    Map<String, Integer> rowIndex = new LinkedHashMap<>();
	    Map<String, Integer> colIndex = new LinkedHashMap<>();

	    int n = 0;

	    for (int i = 0; i < A.length; i++) {

	        if (A[i] == null || B[i] == null)
	            continue;

	        if (A[i].equals("NA") || B[i].equals("NA"))
	            continue;

	        rowIndex.putIfAbsent(A[i], rowIndex.size());
	        colIndex.putIfAbsent(B[i], colIndex.size());

	        n++;
	    }

	    if (n == 0)
	        return Double.NaN;

	    int r = rowIndex.size();
	    int c = colIndex.size();

	    if (r <= 1 || c <= 1)
	        return 0.0;

	    // Contingency table
	    double[][] table = new double[r][c];

	    for (int i = 0; i < A.length; i++) {

	        if (A[i] == null || B[i] == null)
	            continue;

	        if (A[i].equals("NA") || B[i].equals("NA"))
	            continue;

	        int row = rowIndex.get(A[i]);
	        int col = colIndex.get(B[i]);

	        table[row][col]++;
	    }

	    // Row totals
	    double[] rowTotals = new double[r];

	    // Column totals
	    double[] colTotals = new double[c];

	    for (int i = 0; i < r; i++) {
	        for (int j = 0; j < c; j++) {

	            rowTotals[i] += table[i][j];
	            colTotals[j] += table[i][j];
	        }
	    }

	    // Chi-square
	    double chi2 = 0;

	    for (int i = 0; i < r; i++) {
	        for (int j = 0; j < c; j++) {

	            double expected = rowTotals[i] * colTotals[j] / n;

	            if (expected > 0) {
	                double diff = table[i][j] - expected;
	                chi2 += diff * diff / expected;
	            }
	        }
	    }

	    double phi2 = chi2 / n;

	    return Math.sqrt(phi2 / Math.min(r - 1, c - 1));
	}
	
	
	
	public static double distance(double x1, double y1, double x2, double y2) {
		
		return Math.sqrt( ((x2-x1)*(x2-x1)) + ((y2-y1)*(y2-y1)));
		
		
	}
	
	
	/**
	 * Calculates the average of an array of doubles.
	 * Returns 0.0 if the array is null or empty to avoid division by zero.
	 */
	public static double average(double[] vector) {
	    if (vector == null || vector.length == 0) {
	        return 0.0;
	    }

	    double sum = 0.0;
	    for (double value : vector) {
	        sum += value;
	    }

	    return sum / (double) vector.length;
	}
	
	
	public static double roundToDigits(double value, int digits) {
	    java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value);
	    bd = bd.setScale(digits, java.math.RoundingMode.HALF_UP);
	    return bd.doubleValue();
	}
	
	public static double[] getNumericColumnFromWekaInstances(Instances data, int columnIndex) {
	    if (data == null) {
	        throw new IllegalArgumentException("Instances object cannot be null");
	    }

	    if (columnIndex < 0 || columnIndex >= data.numAttributes()) {
	        throw new IllegalArgumentException(
	            "Invalid column index: " + columnIndex +
	            ". Valid range is 0 to " + (data.numAttributes() - 1)
	        );
	    }

	    double[] column = new double[data.numInstances()];

	    for (int i = 0; i < data.numInstances(); i++) {
	        Instance row = data.instance(i);
	        column[i] = row.value(columnIndex);
	    }

	    return column;
	}
	
	
	public static double[] quartiles(double[] values) {
	    if (values == null || values.length == 0) {
	        throw new IllegalArgumentException("Input vector cannot be null or empty");
	    }

	    double[] clean = Arrays.stream(values)
	            .filter(v -> !Double.isNaN(v))
	            .sorted()
	            .toArray();

	    if (clean.length == 0) {
	        throw new IllegalArgumentException("Input vector contains only NaN values");
	    }

	    double q1 = percentile(clean, 25);
	    double q2 = percentile(clean, 50); // median
	    double q3 = percentile(clean, 75);

	    return new double[] { q1, q2, q3 };
	}

	public static double percentile(double[] sortedValues, double percentile) {
	    if (sortedValues.length == 1) {
	        return sortedValues[0];
	    }

	    double index = percentile / 100.0 * (sortedValues.length - 1);
	    int lower = (int) Math.floor(index);
	    int upper = (int) Math.ceil(index);

	    if (lower == upper) {
	        return sortedValues[lower];
	    }

	    double weight = index - lower;
	    return sortedValues[lower] * (1.0 - weight) + sortedValues[upper] * weight;
	}
	
	
	public static Object firstNonNullValue(List<List<Object>> rows, int column) {
        for (List<Object> row : rows) {
            if (row.get(column) != null) {
                return row.get(column);
            }
        }
        throw new IllegalArgumentException("Column " + column + " contains only null values");
    }
	
	public static List<String> collectCategoricalValues(List<List<Object>> rows, int column) {
        Set<String> values = new LinkedHashSet<>();

        for (List<Object> row : rows) {
            Object value = row.get(column);
            if (value != null) {
                values.add(value.toString());
            }
        }

        return new ArrayList<>(values);
    }
	
	public static Instances matrixToWekaInstance(List<List<Object>> rows, String[] featureNames, String targetName) {
		
		int numColumns = rows.get(0).size();
        int targetIndex = numColumns - 1;
        for (int i = 0;i<featureNames.length;i++){
        	if (featureNames[i].equals(targetName)) {
        		targetIndex = i;
        		break;
        	}
        }
        
        ArrayList<Attribute> attributes = new ArrayList<>();

        //for (int col = 0; col < targetIndex; col++) {
        for (int col = 0; col < numColumns; col++) {
            Object firstValue = firstNonNullValue(rows, col);
            
            if (firstValue instanceof Number) {
                attributes.add(new Attribute(featureNames[col]));
                System.out.println("[MatrixToWekaInstance] Numeric: "+featureNames[col]);
            } else {
                List<String> values = collectCategoricalValues(rows, col);
                attributes.add(new Attribute(featureNames[col], values));
                System.out.println("[MatrixToWekaInstance] Categorial: "+featureNames[col]);
            }
        }
        
        
        Instances dataset = new Instances("dataset", attributes, rows.size());
        dataset.setClassIndex(targetIndex);
        System.out.println("[MatrixToWekaInstance] Building the training set");
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

            dataset.add(instance);
        }
        
        return dataset;
	}
}



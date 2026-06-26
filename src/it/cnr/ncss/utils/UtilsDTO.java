package it.cnr.ncss.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public class UtilsDTO {

	
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
		
		String header = csvlines.get(0);
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
		for (int i = 1;i<m;i++) {
			String elementS = csvlines.get(i);
			String [] elements = StringUtilsDTO.getCSVElements(elementS);
			Double e = null;
			try {
				e = Double.parseDouble(elements[headerNumber]);
				values.add(e);
			}catch(Exception ex) {
				//System.out.println(">get "+columnName+" skipping line "+i);
				}
		}
		
		double columnE [] = new double[values.size()];
		int k = 0;
		for(Double v:values) {
			columnE[k]=v;
			k++;
		}
		
		return columnE;
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
	
	
	
}

package it.cnr.ncss.llm;

import java.io.File;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;

public class KbManager {
	Config config;
	static String featureNames[];
	static String featureExplanations[];
	static List<String> knowledgebase;
	static List<List<Object>> knowledgebaserows;
	static String featureNamesNoCoords [];
	
	public KbManager() throws Exception {
		config = new Config();
		parseKB();
	}

	public void parseKB() throws Exception{

		if (knowledgebase == null) {
			System.out.println("[KBMANAGER] reading KB file");
			knowledgebase = Files.readAllLines(new File(config.getProperty("knowledge_base_data")).toPath());
			
			// read the matrix: create a Table object with headers and numbers
			System.out.println("[KBMANAGER] parsing KB matrix");
			
			String header = knowledgebase.get(0);
			header = header.replace("\uFEFF", "");
			featureNames = StringUtilsDTO.getCSVElements(header);
			String explanations = knowledgebase.get(1);
			
			featureExplanations = StringUtilsDTO.getCSVElements(explanations);
			knowledgebaserows = new ArrayList<List<Object>>();
			String longitudeColumn = config.getProperty("longitude_column").replace("\"", "").trim();
			String latitudeColumn = config.getProperty("latitude_column").replace("\"", "").trim();
			//for each feature
			int j=0;
			List<String> featureNamesNoCoordsList = new ArrayList<String>();
			for (String f:featureNames) {
				if (f.trim().equals(longitudeColumn) || f.trim().equals(latitudeColumn))
					continue;
				featureNamesNoCoordsList.add(f);
				//get the column
				String [] column = getColumn(f);
				//check if it is categorial
				boolean isCat = UtilsDTO.isCategorical(column);
				//if not, standardize it
				double columnOfNumbers [] = null;
				if (!isCat)
					columnOfNumbers = getColumnNumericValues(f, false);
				//for each row
				for (int kk=0;kk<column.length;kk++) {
					//add a new row if needed
					if (knowledgebaserows.size()<=kk)
						knowledgebaserows.add(new ArrayList<Object>());
					//add the data in the f column
					if (isCat)
						knowledgebaserows.get(kk).add(j,column[kk]);
					else
						knowledgebaserows.get(kk).add(j,Double.valueOf(columnOfNumbers[kk]));

				}
				j++;
			}
			featureNamesNoCoords = new String[featureNamesNoCoordsList.size()];
			featureNamesNoCoords = featureNamesNoCoordsList.toArray(featureNamesNoCoords);
			
		}
	}
	
	
	public String explainFeature(String f) {
		int i=0;
		for (String fn:featureNames) {
			
			if (f.equals(fn))
				return featureExplanations[i];
			
			i++;
		}
		return "";
	}
	
	
	public List<List<Object>> getFeatureMatrix(){
		return knowledgebaserows;
		
	}
	public String [] getAllFeatures() throws Exception{
		return featureNames;
	}
	public String [] getFeatures() throws Exception{
		return featureNamesNoCoords;
	}
	public double[] getColumnNumericValues(String column) throws Exception{
		return getColumnNumericValues(column, false);
	}
	
	public double[] getColumnNumericValues(String column, boolean standardised) throws Exception{
		return UtilsDTO.getColumnNumericValues(knowledgebase, column, standardised);
	}
	
	public String[] getColumn(String column) throws Exception{
		
		return UtilsDTO.getColumn(knowledgebase, column);
	}
	
	public String[] getColumn(List<List<Object>> matrix, int columnIdx) throws Exception{
		
		return UtilsDTO.getColumn(matrix, columnIdx);
	}

	public int featureIndex(String feature)  throws Exception {
		return UtilsDTO.getColumnIndex(featureNamesNoCoords, feature);
	}
	
	public int riskFeatureIndex()  throws Exception {
		return UtilsDTO.getColumnIndex(featureNamesNoCoords, config.getProperty("risk_column"));
	}
	
	public List<List<Object>> reanalysis(List<List<Object>> mixed_matrix, String feature, double variation, boolean isfraction_or_relative) throws Exception {
		
		int colIdx = featureIndex(feature);
		String [] column = getColumn(mixed_matrix,colIdx);
		
		//check if it is categorial
		boolean isCat = UtilsDTO.isCategorical(column);
		int nRowsInKB = mixed_matrix.size();
		List<List<Object>> simulatedKB = UtilsDTO.deepCopy(mixed_matrix);
		if (!isCat) {
			
			for (int i=0;i<nRowsInKB;i++) {
				double c = Double.parseDouble(column[i]);
				double newvalue = c;
				if (isfraction_or_relative) 
					newvalue =  c + (Math.abs(c)*variation);
				else 
					newvalue =  c + (variation);	
				
				simulatedKB.get(i).set(colIdx, newvalue);
			}
 
		}else {
			
			String frequentCat = UtilsDTO.mostFrequentCategory(column);
			String updatedColumn [] = UtilsDTO.replaceMissingValues(column, frequentCat, variation);
			
			for (int i=0;i<nRowsInKB;i++) {
				
				simulatedKB.get(i).set(colIdx, updatedColumn[i]);
			}
		}
		
		return simulatedKB;
	}

	public List<List<Object>> simulate(String feature, double fractionVariation) throws Exception {
		
		String [] column = getColumn(feature);
		int columnIndex = UtilsDTO.getColumnIndex(featureNamesNoCoords, feature);
		
		//check if it is categorial
		boolean isCat = UtilsDTO.isCategorical(column);
		int nRowsInKB = knowledgebaserows.size();
		List<List<Object>> simulatedKB = UtilsDTO.deepCopy(knowledgebaserows);
		if (!isCat) {
			double [] columnNum = getColumnNumericValues(feature, false);
			for (int i=0;i<nRowsInKB;i++) {
				double c = columnNum[i];
				double augval =  c+ (c*fractionVariation);
				simulatedKB.get(i).set(columnIndex, augval);
			}
 
		}else {
			String frequentCat = UtilsDTO.mostFrequentCategory(column);
			String updatedColumn [] = UtilsDTO.replaceMissingValues(column, frequentCat, fractionVariation);
			
			for (int i=0;i<nRowsInKB;i++) {
				
				simulatedKB.get(i).set(columnIndex, updatedColumn[i]);
			}
		}
		
		return simulatedKB;
	}
	
}

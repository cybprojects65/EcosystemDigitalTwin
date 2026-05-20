package it.cnr.ncss.utils;

import java.io.File;
import java.nio.file.Files;

public class StringUtilsDTO {

	public static String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }

        return query
                .toLowerCase()
                .trim()
                .replaceAll("[!?.]+$", "")
                .replaceAll("\\s+", " ");
    }
	
	
	public static double cosineSimilarity(double[] a, double[] b) {
	    double dot = 0.0;
	    double normA = 0.0;
	    double normB = 0.0;

	    for (int i = 0; i < a.length; i++) {
	        dot += a[i] * b[i];
	        normA += a[i] * a[i];
	        normB += b[i] * b[i];
	    }

	    if (normA == 0.0 || normB == 0.0) {
	        return 0.0;
	    }

	    return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}
	
	public static String [] getLines(File input) throws Exception{
		 return Files
	                .readAllLines(input.toPath())
	                .stream()
	                .map(String::trim)
	                .filter(s -> !s.isBlank())
	                .toArray(String[]::new);
	}
	
	public static String getText(File input) throws Exception{
		 String text = Files.readString(input.toPath());
		 return text;
	}
}

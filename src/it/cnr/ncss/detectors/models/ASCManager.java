package it.cnr.ncss.detectors.models;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.utils.StringUtilsDTO;
import it.cnr.ncss.utils.UtilsDTO;

public class ASCManager {
	KbManager kb;
	
	public ASCManager(KbManager kb) {
		this.kb = kb;
	}
	
	
	
	public double [] getLongitude() throws Exception{
		
		String longColumn = kb.config.getProperty("longitude_column");
		double [] longCol =  kb.getColumnNumericValues(longColumn);
		return longCol;
	}

	public double [] getLatitude() throws Exception{
		
		String latColumn = kb.config.getProperty("latitude_column");
		double [] latCol =  kb.getColumnNumericValues(latColumn);
		return latCol;
	}
	
	public File Kb2ASC()throws Exception {
		
		File kbasc = new File(kb.config.getProperty("cache_folder"),"asc_files");
		if (!kbasc.exists())
			kbasc.mkdir();
		else
			return kbasc;
			
		double [] longitudes = getLongitude();
		
		double [] latitudes = getLatitude();
		
		String [] features = kb.getFeatures(); 
		for (String feature:features) {
			System.out.println("[ASCManager] getting values for '"+feature+"'");
			String [] column = kb.getColumn(feature);
			boolean isCat = UtilsDTO.isCategorical(column);
			if (!isCat) {
				System.out.println("[ASCManager] numeric values available - transforming into ASC");
				double [] featureCol = kb.getColumnNumericValues(feature);
				String featureNormalised = StringUtilsDTO.normalizeQuery(feature);
				File ascFile = new File(kbasc,featureNormalised+".asc");
				toAsc(longitudes, latitudes, featureCol, ascFile);
				System.out.println("[ASCManager] ASC file produced: "+ascFile.getAbsolutePath());
			}else
				System.out.println("[ASCManager] numeric values NOT available");
		}
		
		return kbasc;
	}
	
	public File toAsc(double[] longitude, double[] latitude, double[] value, File ascFile)
	        throws IOException {

	    if (longitude == null || latitude == null || value == null) {
	        throw new IllegalArgumentException("longitude, latitude, and value arrays must not be null.");
	    }

	    if (longitude.length != latitude.length || longitude.length != value.length) {
	        throw new IllegalArgumentException("longitude, latitude, and value arrays must have the same length.");
	    }

	    if (longitude.length == 0) {
	        throw new IllegalArgumentException("Input arrays must not be empty.");
	    }

	    final double nodata = -9999.0;

	    TreeSet<Double> xSet = new TreeSet<>();
	    TreeSet<Double> ySet = new TreeSet<>();

	    for (int i = 0; i < longitude.length; i++) {
	        if (Double.isFinite(longitude[i]) && Double.isFinite(latitude[i]) && Double.isFinite(value[i])) {
	            xSet.add(longitude[i]);
	            ySet.add(latitude[i]);
	        }
	    }

	    if (xSet.isEmpty() || ySet.isEmpty()) {
	        throw new IllegalArgumentException("No finite coordinate/value triples found.");
	    }

	    List<Double> xs = new ArrayList<>(xSet);
	    List<Double> ys = new ArrayList<>(ySet);

	    int ncols = xs.size();
	    int nrows = ys.size();

	    double cellSizeX = estimateCellSize(xs);
	    double cellSizeY = estimateCellSize(ys);

	    if (Math.abs(cellSizeX - cellSizeY) > 1e-9) {
	        throw new IllegalArgumentException(
	                "ESRI ASC requires square cells, but longitude and latitude spacing differ: "
	                        + cellSizeX + " vs " + cellSizeY
	        );
	    }

	    double cellSize = cellSizeX;

	    double xllcorner = xs.get(0) - cellSize / 2.0;
	    double yllcorner = ys.get(0) - cellSize / 2.0;

	    Map<Double, Integer> xIndex = new HashMap<>();
	    Map<Double, Integer> yIndex = new HashMap<>();

	    for (int i = 0; i < xs.size(); i++) {
	        xIndex.put(xs.get(i), i);
	    }

	    for (int i = 0; i < ys.size(); i++) {
	        yIndex.put(ys.get(i), i);
	    }

	    double[][] grid = new double[nrows][ncols];

	    for (int row = 0; row < nrows; row++) {
	        Arrays.fill(grid[row], nodata);
	    }

	    for (int i = 0; i < longitude.length; i++) {
	        if (!Double.isFinite(longitude[i]) || !Double.isFinite(latitude[i]) || !Double.isFinite(value[i])) {
	            continue;
	        }

	        Integer col = xIndex.get(longitude[i]);
	        Integer rowFromBottom = yIndex.get(latitude[i]);

	        if (col == null || rowFromBottom == null) {
	            continue;
	        }

	        grid[rowFromBottom][col] = value[i];
	    }

	    File parent = ascFile.getParentFile();
	    if (parent != null) {
	        parent.mkdirs();
	    }

	    try (BufferedWriter writer = Files.newBufferedWriter(ascFile.toPath(), StandardCharsets.UTF_8)) {
	        writer.write("ncols " + ncols);
	        writer.newLine();
	        writer.write("nrows " + nrows);
	        writer.newLine();
	        writer.write("xllcorner " + xllcorner);
	        writer.newLine();
	        writer.write("yllcorner " + yllcorner);
	        writer.newLine();
	        writer.write("cellsize " + cellSize);
	        writer.newLine();
	        writer.write("NODATA_value " + nodata);
	        writer.newLine();

	        for (int row = nrows - 1; row >= 0; row--) {
	            for (int col = 0; col < ncols; col++) {
	                if (col > 0) {
	                    writer.write(" ");
	                }

	                writer.write(Double.toString(grid[row][col]));
	            }

	            writer.newLine();
	        }
	    }

	    return ascFile;
	}

	private static double estimateCellSize(List<Double> sortedCoordinates) {
	    if (sortedCoordinates.size() < 2) {
	        throw new IllegalArgumentException("At least two coordinates are required to estimate cell size.");
	    }

	    double minStep = Double.POSITIVE_INFINITY;

	    for (int i = 1; i < sortedCoordinates.size(); i++) {
	        double step = sortedCoordinates.get(i) - sortedCoordinates.get(i - 1);

	        if (step > 0 && step < minStep) {
	            minStep = step;
	        }
	    }

	    if (!Double.isFinite(minStep)) {
	        throw new IllegalArgumentException("Could not estimate grid cell size.");
	    }

	    return minStep;
	}
	
	public record AscData(
	        double[] longitude,
	        double[] latitude,
	        double[] value,
	        int ncols,
	        int nrows,
	        double cellSize,
	        double nodataValue
	) {}
	
	public static AscData readAsc(File ascFile) throws IOException {
	    if (ascFile == null || !ascFile.isFile()) {
	        throw new IllegalArgumentException("ASC file does not exist: " + ascFile);
	    }

	    try (BufferedReader reader = Files.newBufferedReader(ascFile.toPath(), StandardCharsets.UTF_8)) {
	        int ncols = -1;
	        int nrows = -1;
	        Double xllcorner = null;
	        Double yllcorner = null;
	        Double xllcenter = null;
	        Double yllcenter = null;
	        Double cellSize = null;
	        double nodata = -9999.0;

	        for (int i = 0; i < 6; i++) {
	            String line = reader.readLine();

	            if (line == null) {
	                throw new IOException("Unexpected end of file while reading ASC header.");
	            }

	            String[] parts = line.trim().split("\\s+");

	            if (parts.length < 2) {
	                throw new IOException("Invalid ASC header line: " + line);
	            }

	            String key = parts[0].toLowerCase();
	            String val = parts[1];

	            switch (key) {
	                case "ncols" -> ncols = Integer.parseInt(val);
	                case "nrows" -> nrows = Integer.parseInt(val);
	                case "xllcorner" -> xllcorner = Double.parseDouble(val);
	                case "yllcorner" -> yllcorner = Double.parseDouble(val);
	                case "xllcenter" -> xllcenter = Double.parseDouble(val);
	                case "yllcenter" -> yllcenter = Double.parseDouble(val);
	                case "cellsize" -> cellSize = Double.parseDouble(val);
	                case "nodata_value" -> nodata = Double.parseDouble(val);
	                default -> throw new IOException("Unknown ASC header key: " + key);
	            }
	        }

	        if (ncols <= 0 || nrows <= 0) {
	            throw new IOException("Invalid ASC dimensions: ncols=" + ncols + ", nrows=" + nrows);
	        }

	        if (cellSize == null || cellSize <= 0) {
	            throw new IOException("Invalid or missing ASC cellsize.");
	        }

	        double xStart;
	        double yStart;

	        if (xllcorner != null) {
	            xStart = xllcorner + cellSize / 2.0;
	        } else if (xllcenter != null) {
	            xStart = xllcenter;
	        } else {
	            throw new IOException("Missing xllcorner or xllcenter.");
	        }

	        if (yllcorner != null) {
	            yStart = yllcorner + cellSize / 2.0;
	        } else if (yllcenter != null) {
	            yStart = yllcenter;
	        } else {
	            throw new IOException("Missing yllcorner or yllcenter.");
	        }

	        int size = ncols * nrows;

	        double[] longitude = new double[size];
	        double[] latitude = new double[size];
	        double[] value = new double[size];

	        int index = 0;

	        /*
	         * ASC grid rows are stored north-to-south.
	         * Row 0 in the file corresponds to the maximum latitude.
	         */
	        for (int fileRow = 0; fileRow < nrows; fileRow++) {
	            String line = reader.readLine();

	            while (line != null && line.isBlank()) {
	                line = reader.readLine();
	            }

	            if (line == null) {
	                throw new IOException("Unexpected end of file while reading ASC grid data.");
	            }

	            String[] parts = line.trim().split("\\s+");

	            if (parts.length != ncols) {
	                throw new IOException(
	                        "Invalid number of columns at data row " + fileRow +
	                        ": expected " + ncols + ", found " + parts.length
	                );
	            }

	            int rowFromBottom = nrows - 1 - fileRow;
	            double lat = yStart + rowFromBottom * cellSize;

	            for (int col = 0; col < ncols; col++) {
	                double lon = xStart + col * cellSize;
	                double v = Double.parseDouble(parts[col]);

	                longitude[index] = lon;
	                latitude[index] = lat;
	                value[index] = v == nodata ? Double.NaN : v;

	                index++;
	            }
	        }

	        return new AscData(longitude, latitude, value, ncols, nrows, cellSize, nodata);
	    }
	}
	
	public static void main (String[] args) throws Exception{
		
		KbManager kb = new KbManager();
		
		ASCManager manager = new ASCManager(kb);
		
		manager.Kb2ASC();
		
		
		
		
		
	}
}

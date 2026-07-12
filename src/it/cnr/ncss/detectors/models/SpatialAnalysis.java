package it.cnr.ncss.detectors.models;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import it.cnr.ncss.detectors.models.ASCManager.AscData;
import it.cnr.ncss.utils.UtilsDTO;

public class SpatialAnalysis {

	public boolean isValid(Double latitudev, Double longitudev, Double valuev, Double nodata) {
		return latitudev != null && !latitudev.isNaN() && longitudev != null && !longitudev.isNaN() && valuev != null
				&& !valuev.isNaN() && valuev != nodata;
	}

	public static String[] getOctect(Double longitude, Double latitude, double cellsize) {

		String key1 = coordinateKey(longitude - cellsize, latitude, cellsize);
		String key2 = coordinateKey(longitude + cellsize, latitude, cellsize);
		String key3 = coordinateKey(longitude, latitude - cellsize, cellsize);
		String key4 = coordinateKey(longitude, latitude + cellsize, cellsize);
		String key5 = coordinateKey(longitude + cellsize, latitude + cellsize, cellsize);
		String key6 = coordinateKey(longitude + cellsize, latitude - cellsize, cellsize);
		String key7 = coordinateKey(longitude - cellsize, latitude - cellsize, cellsize);
		String key8 = coordinateKey(longitude - cellsize, latitude + cellsize, cellsize);
		String[] octect = { key1, key2, key3, key4, key5, key6, key7, key8 };

		return octect;
	}

	// retrieves only 1 values around the current cell
	public Set<String> getActiveNeighbors(Double longitude, Double latitude, double cellsize,
			HashMap<String, Double> dataTable) {

		String[] octect = getOctect(longitude, latitude, cellsize);
		Set<String> nei = new HashSet<String>();

		for (String key : octect) {

			Double v = dataTable.get(key);
			if (v != null && v > 0) {
				nei.add(key);
			}

		}

		return nei;
	}

	public HashMap<String, Set<String>> mergePatches(HashMap<String, Set<String>> patches8neighbour) {

		HashMap<String, Set<String>> mergedPatches = new HashMap<>();

		if (patches8neighbour == null || patches8neighbour.isEmpty()) {
			return mergedPatches;
		}

		Set<String> visited = new HashSet<>();

		for (String startCell : patches8neighbour.keySet()) {

			if (visited.contains(startCell)) {
				continue;
			}

			Set<String> patch = new HashSet<>();
			List<String> stack = new ArrayList<>();

			stack.add(startCell);
			visited.add(startCell);

			while (!stack.isEmpty()) {

				String currentCell = stack.remove(stack.size() - 1);
				patch.add(currentCell);

				Set<String> neighbours = patches8neighbour.get(currentCell);

				if (neighbours == null) {
					continue;
				}

				for (String neighbour : neighbours) {

					if (!visited.contains(neighbour)) {
						visited.add(neighbour);
						stack.add(neighbour);
					}
				}
			}

			mergedPatches.put(startCell, patch);
		}

		return mergedPatches;
	}

		public static int decimalDigitsForCellSize(double cellSize) {
		if (cellSize <= 0 || Double.isNaN(cellSize) || Double.isInfinite(cellSize)) {
			throw new IllegalArgumentException("Invalid cell size: " + cellSize);
		}

		String text = java.math.BigDecimal.valueOf(cellSize).stripTrailingZeros().toPlainString();

		int dot = text.indexOf('.');
		if (dot < 0) {
			return 0;
		}

		return text.length() - dot - 1;
	}

	public static double snapToCellSize(double value, double cellSize, int digits) {
		double snapped = Math.round(value / cellSize) * cellSize;
		return UtilsDTO.roundToDigits(snapped, digits);
	}

	public static String coordinateKey(double longitude, double latitude, double cellSize) {
		int digits = decimalDigitsForCellSize(cellSize);

		double lon = snapToCellSize(longitude, cellSize, digits);
		double lat = snapToCellSize(latitude, cellSize, digits);

		return formatCoordinate(lon, digits) + ";" + formatCoordinate(lat, digits);
	}

	public static String formatCoordinate(double value, int digits) {
		return java.math.BigDecimal.valueOf(value).setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString();
	}

	public SpatialStats clusterBinarySpace(File ascBinaryFile) throws Exception {
		
		System.out.println("[SpatialAnalysis] Reading the ASC binary file from "+ascBinaryFile.getAbsolutePath());
		AscData data = ASCManager.readAsc(ascBinaryFile);
		double[] longitudes = data.longitude();
		double[] latitudes = data.latitude();
		double[] values = data.value();

		int n = longitudes.length;
		double nodata = data.nodataValue();
		double cellSize = data.cellSize();

		System.out.println("[SpatialAnalysis] n-values: "+n+"; nodata: "+nodata+"; cellsize: "+cellSize);
		
		HashMap<String, Double> dataTable = new HashMap<String, Double>();
		System.out.println("[SpatialAnalysis] building data table");
		int nValidCells = 0;
		int nOneCells = 0;
		for (int i = 0; i < n; i++) {
			Double longitude = longitudes[i];
			Double latitude = latitudes[i];
			Double value = values[i];

			if (isValid(latitude, longitude, value, nodata)) {

				String key = coordinateKey(longitude, latitude, cellSize);// longitude + ";" + latitude;
				dataTable.put(key, value);
				nValidCells++;
				if (value > 0)
					nOneCells++;
			}
		}

		
		System.out.println("[SpatialAnalysis] n of suitable cells: "+nOneCells+" over "+nValidCells+" valid cells");

		System.out.println("[SpatialAnalysis] building 8-neighbor patches");
		HashMap<String, Set<String>> patches8neighbour = new HashMap<String, Set<String>>();
		for (int i = 0; i < n; i++) {
			Double longitude = longitudes[i];
			Double latitude = latitudes[i];
			Double value = values[i];

			if (isValid(latitude, longitude, value, nodata) && value > 0) {
				String key = coordinateKey(longitude, latitude, cellSize); // longitude + ";" + latitude;
				Set<String> one_neighbours = getActiveNeighbors(longitude, latitude, cellSize, dataTable);
				one_neighbours.add(key);
				patches8neighbour.put(key, one_neighbours);
			}

		}

		System.out.println("[SpatialAnalysis] optimising patches ...");		
		HashMap<String, Set<String>> optimisedPatches = mergePatches(patches8neighbour);

		System.out.println("[SpatialAnalysis] calculating average minimum distance between the patches ...");		
		double minimum_average_distance = averageMinimumPatchDistanceFromLargest(optimisedPatches, cellSize);

		System.out.println("[SpatialAnalysis] minimum average distance between the patches: "+minimum_average_distance);
		
		System.out.println("[SpatialAnalysis] populating statistics");
		
		SpatialStats stats = new SpatialStats();

		//basic info
		stats.valid_cells = nValidCells;
		stats.suitable_cells = nOneCells;
		stats.suitable_habitat_cover_percentage =
		        nValidCells > 0 ? ((double) nOneCells * 100.0 / (double) nValidCells) : 0.0;

		stats.number_of_presence_areas = optimisedPatches.size();

		if (!optimisedPatches.isEmpty()) {
			
			//calculating the largest patch size
		    int largestPatchCells = 0;
		    int totalPatchCells = 0;

		    List<Integer> patchSizes = new ArrayList<>();

		    for (Set<String> patch : optimisedPatches.values()) {
		        if (patch == null) {
		            continue;
		        }

		        int patchSize = patch.size();

		        patchSizes.add(patchSize);
		        totalPatchCells += patchSize;

		        if (patchSize > largestPatchCells) {
		            largestPatchCells = patchSize;
		        }
		    }

		    stats.largest_presence_area_cells = largestPatchCells;

		    stats.largest_presence_area_percentage_of_suitable_habitat =
		            nOneCells > 0 ? ((double) largestPatchCells * 100.0 / (double) nOneCells) : 0.0;

		    stats.mean_presence_area_size_cells =
		            !patchSizes.isEmpty() ? averageIntegerList(patchSizes) : 0.0;

		    stats.median_presence_area_size_cells =
		            !patchSizes.isEmpty() ? medianIntegerList(patchSizes) : 0.0;

		} else {
		    stats.largest_presence_area_cells = 0;
		    stats.largest_presence_area_percentage_of_suitable_habitat = 0.0;
		    stats.mean_presence_area_size_cells = 0.0;
		    stats.median_presence_area_size_cells = 0.0;
		}
		
		//calculate min distance as a fraction of cellsize
		stats.core_to_satellite_mean_distance_cells =
		        cellSize > 0 ? minimum_average_distance / cellSize : 0.0;

		System.out.println("[SpatialAnalysis] inferring properties:");
		stats.inferProperties();
		System.out.println(stats.toJson());
		
		return stats;

	}

	private String findLargestPatchKey(HashMap<String, Set<String>> patches) {

		String largestKey = null;
		int largestSize = -1;

		for (String key : patches.keySet()) {
			Set<String> patch = patches.get(key);

			if (patch != null && patch.size() > largestSize) {
				largestSize = patch.size();
				largestKey = key;
			}
		}

		return largestKey;
	}

	private Set<String> extractBoundaryCells(Set<String> patch, double cellSize) {

		Set<String> boundary = new java.util.HashSet<>();

		if (patch == null || patch.isEmpty()) {
			return boundary;
		}

		for (String cell : patch) {

			double lo1 = Double.parseDouble(cell.substring(0, cell.indexOf(";")));
			double la1 = Double.parseDouble(cell.substring(cell.indexOf(";") + 1));

			boolean isBoundary = false;

			String[] octect = getOctect(lo1, la1, cellSize);

			for (String oct : octect) {

				if (!patch.contains(oct)) {
					isBoundary = true;
					break;
				}
			}

			if (isBoundary) {
				boundary.add(cell);
			}
		}

		return boundary;
	}

	private double minimumDistanceBetweenCellSets(Set<String> cells1, Set<String> cells2) {

		double minimumDistance = Double.MAX_VALUE;

		for (String c1 : cells1) {

			double lo1 = Double.parseDouble(c1.substring(0, c1.indexOf(";")));
			double la1 = Double.parseDouble(c1.substring(c1.indexOf(";") + 1));

			for (String c2 : cells2) {

				double lo2 = Double.parseDouble(c2.substring(0, c2.indexOf(";")));
				double la2 = Double.parseDouble(c2.substring(c2.indexOf(";") + 1));

				double distance = UtilsDTO.distance(lo1, la1, lo2, la2);

				if (distance < minimumDistance) {
					minimumDistance = distance;
				}
			}
		}

		return minimumDistance;
	}

	public double averageMinimumPatchDistanceFromLargest(HashMap<String, Set<String>> patches, double cellSize) {

		if (patches == null || patches.size() <= 1) {
			return 0.0;
		}

		String largestPatchKey = findLargestPatchKey(patches);
		Set<String> largestPatch = patches.get(largestPatchKey);

		Set<String> largestBoundary = extractBoundaryCells(largestPatch, cellSize);

		double sumMinDistances = 0.0;
		int comparisons = 0;

		for (String patchKey : patches.keySet()) {

			if (patchKey.equals(largestPatchKey)) {
				continue;
			}

			Set<String> satellitePatch = patches.get(patchKey);

			if (satellitePatch == null || satellitePatch.isEmpty()) {
				continue;
			}

			Set<String> satelliteBoundary = extractBoundaryCells(satellitePatch, cellSize);

			double minDistance = minimumDistanceBetweenCellSets(largestBoundary, satelliteBoundary);

			if (Double.isFinite(minDistance)) {
				sumMinDistances += minDistance;
				comparisons++;
			}
		}

		if (comparisons == 0) {
			return 0.0;
		}

		return sumMinDistances / comparisons;
	}

	public double averageMinimumPatchDistance2(HashMap<String, Set<String>> patches) {

		String largestSet = "";
		int largestNumber = 0;
		for (String p : patches.keySet()) {

			int setsize = patches.get(p).size();
			if (setsize > largestNumber) {
				largestNumber = setsize;
				largestSet = p;
			}
		}

		// for (String p1 : patches.keySet()) {
		String p1 = largestSet;

		Set<String> cells1 = patches.get(p1);

		double[] min_distances = new double[patches.keySet().size() - 1];

		int j = 0;

		// for every other patch
		for (String p2 : patches.keySet()) {

			if (!p2.equals(p1)) {

				// take all cells
				Set<String> cells2 = patches.get(p2);
				double minimum_distance = Double.MAX_VALUE;
				// detect the minimum distance between the cells1 and cells2
				for (String c : cells1) {

					double lo1 = Double.parseDouble(c.substring(0, c.indexOf(";")));
					double la1 = Double.parseDouble(c.substring(c.indexOf(";") + 1));

					for (String c2 : cells2) {

						double lo2 = Double.parseDouble(c2.substring(0, c2.indexOf(";")));
						double la2 = Double.parseDouble(c2.substring(c2.indexOf(";") + 1));
						double distance = UtilsDTO.distance(lo2, la2, lo1, la1);
						if (distance < minimum_distance)
							minimum_distance = distance;

					}

				}

				// add the minimum distance to the distance vector between p1 and the other
				// patches
				min_distances[j] = minimum_distance;
				j++;
			}

		}

		// calculate the average minimum distance
		double avg_min_distance = UtilsDTO.average(min_distances);
		return avg_min_distance;

	}

	
	private double averageIntegerList(List<Integer> values) {

	    if (values == null || values.isEmpty()) {
	        return 0.0;
	    }

	    double sum = 0.0;

	    for (Integer value : values) {
	        if (value != null) {
	            sum += value;
	        }
	    }

	    return sum / values.size();
	}

	private double medianIntegerList(List<Integer> values) {

	    if (values == null || values.isEmpty()) {
	        return 0.0;
	    }

	    List<Integer> sorted = new ArrayList<>(values);
	    java.util.Collections.sort(sorted);

	    int n = sorted.size();

	    if (n % 2 == 1) {
	        return sorted.get(n / 2);
	    }

	    return (sorted.get((n / 2) - 1) + sorted.get(n / 2)) / 2.0;
	}
	
	public class SpatialStats {

		public int valid_cells;
		public int suitable_cells;
		public double suitable_habitat_cover_percentage;
		public String suitable_habitat_cover_class;

		public int number_of_presence_areas;
		public String presence_area_structure_class;

		public int largest_presence_area_cells;
		public double largest_presence_area_percentage_of_suitable_habitat;
		public String largest_presence_area_dominance_class;

		public double mean_presence_area_size_cells;
		public double median_presence_area_size_cells;

		public double core_to_satellite_mean_distance_cells;
		public String core_to_satellite_distance_class;

		public String fragmentation_class;
		public String connectivity_class;
		public String spreadness_class;

		
		public String habitat_spatial_summary;

		public void inferProperties() {

			double cover = (double) suitable_cells * 100d / (double) valid_cells;
			if (cover < 5)
				suitable_habitat_cover_class = "very restricted suitable habitat";
			else if (cover < 15)
				suitable_habitat_cover_class = "restricted suitable habitat";
			else if (cover < 35)
				suitable_habitat_cover_class = "moderately widespread suitable habitat";
			else if (cover < 60)
				suitable_habitat_cover_class = "widespread suitable habitat";
			else
				suitable_habitat_cover_class = "very widespread suitable habitat";

		

			if (number_of_presence_areas <= 1) {
				fragmentation_class = "not fragmented";
			} else if (largest_presence_area_percentage_of_suitable_habitat >= 60
					&& core_to_satellite_mean_distance_cells <= 5) {
				fragmentation_class = "low to moderate fragmentation with a dominant presence area";
			} else if (largest_presence_area_percentage_of_suitable_habitat >= 40) {
				fragmentation_class = "moderate fragmentation with a dominant presence area and smaller presence areas";
			} else if (number_of_presence_areas > 20 || median_presence_area_size_cells <= 2) {
				fragmentation_class = "high fragmentation into many small presence areas";
			} else {
				fragmentation_class = "moderate fragmentation";
			}

			
			if (core_to_satellite_mean_distance_cells <= 2)
			    core_to_satellite_distance_class = "nearby presence areas";
			else if (core_to_satellite_mean_distance_cells <= 5)
			    core_to_satellite_distance_class = "moderately separated presence areas";
			else
			    core_to_satellite_distance_class = "distant presence areas";
			
			
			if (number_of_presence_areas <= 1) {
			    connectivity_class = "continuous suitable habitat and presence area";
			} else if (largest_presence_area_percentage_of_suitable_habitat >= 60
			        && core_to_satellite_mean_distance_cells <= 5) {
			    connectivity_class = "partly connected habitat dominated by one main presence area";
			} else if (largest_presence_area_percentage_of_suitable_habitat >= 40) {
			    connectivity_class = "dominant presence area and smaller presence areas";
			} else if (core_to_satellite_mean_distance_cells > 5) {
			    connectivity_class = "weakly connected or isolated presence areas";
			} else {
			    connectivity_class = "moderately connected presence areas";
			}
			
			
			if (number_of_presence_areas <= 1) {
			    presence_area_structure_class = "single core presence area";
			} else if (largest_presence_area_percentage_of_suitable_habitat >= 60) {
			    presence_area_structure_class = "one dominant core presence area and smaller presence areas";
			} else if (largest_presence_area_percentage_of_suitable_habitat >= 40) {
			    presence_area_structure_class = "few main presence areas with additional smaller presence areas";
			} else {
			    presence_area_structure_class = "many distributed presence areas without a single dominant presence area";
			}
			
			
			if (suitable_habitat_cover_percentage < 15
			        && largest_presence_area_percentage_of_suitable_habitat >= 60) {
			    spreadness_class = "restricted and concentrated in a main presence area";
			} else if (suitable_habitat_cover_percentage < 15) {
			    spreadness_class = "restricted but scattered";
			} else if (suitable_habitat_cover_percentage < 35
			        && largest_presence_area_percentage_of_suitable_habitat >= 60) {
			    spreadness_class = "moderately widespread but concentrated around one dominant presence area";
			} else if (suitable_habitat_cover_percentage < 35) {
			    spreadness_class = "moderately widespread and distributed across several presence areas";
			} else if (largest_presence_area_percentage_of_suitable_habitat >= 60) {
			    spreadness_class = "widespread but dominated by one main presence area";
			} else {
			    spreadness_class = "widespread and distributed across multiple presence areas";
			}
			
		}
		
		public String toJson() throws Exception {
			
			suitable_habitat_cover_percentage = UtilsDTO.roundToDigits(suitable_habitat_cover_percentage, 2);
			largest_presence_area_percentage_of_suitable_habitat = UtilsDTO.roundToDigits(largest_presence_area_percentage_of_suitable_habitat, 2);
			mean_presence_area_size_cells = UtilsDTO.roundToDigits(mean_presence_area_size_cells, 2);
			median_presence_area_size_cells = UtilsDTO.roundToDigits(median_presence_area_size_cells, 2);
			core_to_satellite_mean_distance_cells = UtilsDTO.roundToDigits(core_to_satellite_mean_distance_cells, 2);
			
		    ObjectMapper mapper = new ObjectMapper();
		    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		    mapper.enable(SerializationFeature.INDENT_OUTPUT);

		    return mapper.writeValueAsString(this);
		}
	}


}

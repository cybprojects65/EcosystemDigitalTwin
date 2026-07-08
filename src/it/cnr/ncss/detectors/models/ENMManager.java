package it.cnr.ncss.detectors.models;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.gcube.datanalysis.ecomod.MainTraining;

import it.cnr.ncss.detectors.models.ASCManager.AscData;
import it.cnr.ncss.llm.KbManager;
import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.StringUtilsDTO;

public class ENMManager {

	KbManager kb;

	// command<-paste0("java -jar ./max_ent_cyb.jar \"",presence_data,"\"
	// \"",env_data_folder, "/\" \"",maxent_out,"\" ",prevalence)
	public ENMManager() {

	}

	public ENMManager(KbManager kb) {

		this.kb = kb;
	}

	public ENMData trainMaxEnt(File environmentalDataFolder, File speciesObservations) throws Exception {
		double prevalence = 0.5;
		Config config = new Config();
		String species = Files.readAllLines(speciesObservations.toPath()).get(1).split(",")[0];
		if (species.contains("("))
			species = species.substring(0, species.indexOf("(")).trim().replace("\"", "");

		species = species.replace(" ", "_");

		System.out.println("[ENM Manager] training model for species " + species);

		File species_folder = new File(config.getProperty("cache_folder"), "max_ent_" + species);
		if (!species_folder.exists())
			species_folder.mkdir();

		File results = new File(species_folder, "maxentResults.csv");
		File resultsASC = new File(species_folder, species + ".asc");
		File resultsASCBinary = new File(species_folder, species + "_bin.asc");

		if (!results.exists() && !resultsASC.exists() && !resultsASCBinary.exists()) {

			String[] args = { speciesObservations.getAbsolutePath(), environmentalDataFolder.getAbsolutePath(),
					species_folder.getAbsolutePath(), "" + prevalence };

			System.out.println("[ENM Manager] training model for " + species + " done.");

			MainTraining.main(args);

			String maxent_threshold_to_use = config.getProperty("maxent_threshold_to_use");

			List<String> resultLines = Files.readAllLines(results.toPath());

			String headers[] = resultLines.get(0).split(",");
			String values[] = resultLines.get(1).split(",");
			double thr_val = 1;
			int i = 0;
			for (String header : headers) {

				if (header.contains(maxent_threshold_to_use)) {
					thr_val = Double.parseDouble(values[i]);
				}
				i++;
			}

			AscData data = ASCManager.readAsc(resultsASC);

			List<Double> longitude = new ArrayList<Double>();
			List<Double> latitude = new ArrayList<Double>();
			List<Double> value = new ArrayList<Double>();
			double nodata = data.nodataValue();

			for (int j = 0; j < data.latitude().length; j++) {

				Double latitudev = data.latitude()[j];
				Double longitudev = data.longitude()[j];
				Double valuev = data.value()[j];

				if (!latitudev.isNaN() && latitudev != null && !longitudev.isNaN() && longitudev != null
						&& !valuev.isNaN() && valuev != null && valuev != nodata) {

					longitude.add(longitudev);
					latitude.add(latitudev);
					if (valuev >= thr_val)
						value.add(1d);
					else
						value.add(0d);

				}
			}

			double[] longitudeV = new double[longitude.size()];
			double[] latitudeV = new double[latitude.size()];
			double[] valueV = new double[value.size()];

			for (int j = 0; j < longitudeV.length; j++) {

				longitudeV[j] = longitude.get(j);
				latitudeV[j] = latitude.get(j);
				valueV[j] = value.get(j);

			}

			ASCManager ascman = new ASCManager(kb);

			ascman.toAsc(longitudeV, latitudeV, valueV, resultsASCBinary);

		} else
			System.out.println("[ENM Manager] Model already present in the cache\n");

		String results_json = resultsToJson(results);

		System.out.println("[ENM Manager] Json results:\n" + results_json);
		return new ENMData(resultsASC, resultsASCBinary, resultsASC, results_json);

	}

	public List<Integer> getVariableIndices(String headers[]) {

		List<Integer> listOfVariables = new ArrayList<Integer>();
		boolean collecting = false;
		int i = 0;
		for (String header : headers) {

			if (collecting && header.equalsIgnoreCase("Entropy"))
				collecting = false;

			if (collecting)
				listOfVariables.add(Integer.valueOf(i));

			if (header.equalsIgnoreCase("#Background points"))
				collecting = true;

			i++;
		}

		return listOfVariables;
	}

	public String resultsToJson(File results) throws Exception {

		List<String> resultLines = Files.readAllLines(results.toPath());

		String headers[] = StringUtilsDTO.getCSVElements(resultLines.get(0));
		String values[] = StringUtilsDTO.getCSVElements(resultLines.get(1));
		StringBuffer sb = new StringBuffer();
		sb.append("{\n");

		List<Integer> featureIndices = getVariableIndices(headers);
		List<String> featureNames = new ArrayList<String>();
		List<Double> featureValues = new ArrayList<Double>();

		for (Integer idx : featureIndices) {

			if (!headers[idx.intValue()].endsWith("permutation importance")) {
				String contrib = headers[idx.intValue()].trim();

				contrib = contrib.replaceAll("\\s*contribution\\s*$", "").trim();

				contrib = "Per cent contribution to habitat suitability of " + contrib;
				Double v = Double.parseDouble(values[idx.intValue()]);

				int j = 0;
				boolean found = false;
				for (Double fv : featureValues) {
					if (v > fv) {
						featureNames.add(j, contrib);
						featureValues.add(j, v);
						found = true;
						break;
					}
					j++;
				}

				if (!found) {
					featureNames.add(contrib);
					featureValues.add(v);
				}
			}
		}
		int i = 0;
		for (String header : headers) {

			if (values[i] != null && values[i].length() > 0 && !featureIndices.contains(Integer.valueOf(i))) {
				if (header.startsWith("Species") || header.startsWith("Prevalence")
						|| header.startsWith("#Training samples") || header.startsWith("Training AUC")
						|| header.startsWith("#Background points") || header.startsWith("Entropy")) {
					sb.append("\"" + header.replace("\"", "").trim() + "\":");
					Double valD = null;
					try {
						valD = Double.parseDouble(values[i]);
						sb.append("" + valD);
					} catch (Exception e) {

						sb.append("\"" + values[i] + "\"");
						
					}
					sb.append(",\n");
				}

				i++;
			}
		}

		double cumulativeContrib = 0;
		int NfeatureNames = featureNames.size();
		for (int k = 0; k < NfeatureNames; k++) {

			String contrib = featureNames.get(k);
			Double value = featureValues.get(k);

			cumulativeContrib += value;

			sb.append("\"" + contrib + "\":" + value);

			if (cumulativeContrib > 85)
				break;
			if (k < NfeatureNames - 1)
				sb.append(",\n");

		}

		sb.append("\n}");

		return sb.toString();
	}

	public record ENMData(File distribution, File distribution_binarized, File results_file, String results_json) {

	}

	public static void main(String[] args) throws Exception {
		KbManager kb = new KbManager();

		ENMManager enm = new ENMManager(kb);

		File envdata = new File("./cache/asc_files/");
		File obsdata = new File("./cache/Alcedo_atthis.csv");

		enm.trainMaxEnt(envdata, obsdata);

	}

}

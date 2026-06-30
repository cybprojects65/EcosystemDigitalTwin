package it.cnr.ncss.detectors;

import java.io.File;

import it.cnr.ncss.llm.Llm;
import it.cnr.ncss.utils.Config;

public class GeneralDetector extends AbstractDetector{
	String information_extraction_reference;
	String similarity_threshold_reference;
	
	Config conf = new Config();
	public GeneralDetector(Llm llm, String information_extraction_reference, String similarity_threshold_reference) throws Exception {
		super(llm);
		this.information_extraction_reference=information_extraction_reference;
		this.similarity_threshold_reference=similarity_threshold_reference;
		
	}
	
	@Override
	public File getReference() {
		File ref = new File (conf.getProperty(information_extraction_reference));
		return ref;
	}

	@Override
	public double getSimilarityThreshold() {
		Double ref = Double.parseDouble(conf.getProperty(similarity_threshold_reference));
		return ref;
	}

}

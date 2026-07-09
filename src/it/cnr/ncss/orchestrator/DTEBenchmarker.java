package it.cnr.ncss.orchestrator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.List;

public class DTEBenchmarker {

	
public static void main(String[] args) throws Exception{

		String benchmark = "C:\\Users\\ashto\\Ricerca\\Experiments\\Ecosystems\\DTO\\benchmarking\\queries_to_test.txt";
		String benchmarkResults = "C:\\Users\\ashto\\Ricerca\\Experiments\\Ecosystems\\DTO\\benchmarking\\JAssagent.txt";
		
		List<String> allLines = Files.readAllLines(new File(benchmark).toPath());
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(benchmarkResults)));
		
		
		int i = 1;
		for (String query : allLines) {
		    DigitalTwin dt = new DigitalTwin();
		    String response = dt.manageRequest(query);
		    
		    response = response.replace("\"", "\"\"");
		    
		    response = response.replace("\r\n", "\n").replace("\r", "\n");
		    
		    bw.write("\"" + response + "\"\r\n");
		    
		    System.out.print(i + " ");
		    if (i % 10 == 0) {
		        System.out.println("");
		    }
		    i++;
		}
		
		
		
		System.out.println("Benchmarking finished.");
		bw.close();
	}


}

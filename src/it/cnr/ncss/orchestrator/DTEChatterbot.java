package it.cnr.ncss.orchestrator;

import java.io.File;
import java.util.Scanner;

import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.StringUtilsDTO;

public class DTEChatterbot {

	//TODO: fix issue on latest data retrieval:
			//v-ChatTask
			//v-DataTask
			//CorrelationTask
			//RiskImportance
			//RiskVariation
			//RiskComparison
			//ENM
	//TODO: revise the reasoning when information is inconsistent: e.g., what causes tree cover density loss? (A and B are equal); what's the current state of the ecosystem? (A = B = empty)
 	//TODO: add loss or increase if undefined in the variable's name: which are the main negative effects on biodiversity?
	
	public static void main(String[] args) throws Exception{
		
		DigitalTwin dt = new DigitalTwin();
		
        Scanner scanner = new Scanner(System.in);
        
        System.out.println(StringUtilsDTO.getText(new File(new Config().getProperty("disclaimer"))));
        
        System.out.println("Type 'exit' to quit.");

        while (true) {
            System.out.print("> ");
            String userInput = scanner.nextLine();

            if ("exit".equalsIgnoreCase(userInput.trim())) {
                System.out.println("Chat ended.");
                break;
            }

            //String response = dummyMethod(userInput);
            String response = dt.manageRequest(userInput); 
            
            System.out.println("\n\nAnswer: " + response);
        }

        scanner.close();
    }

    private static String dummyMethod(String userInput) {
        return "test DTO";
    }
	
	
}

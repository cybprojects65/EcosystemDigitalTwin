package it.cnr.ncss.orchestrator;

import java.io.File;
import java.util.Scanner;

import it.cnr.ncss.utils.Config;
import it.cnr.ncss.utils.StringUtilsDTO;

public class DTEChatterbot {

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

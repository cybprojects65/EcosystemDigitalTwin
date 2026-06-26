package it.cnr.ncss.utils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


public class Config {


	    private final Properties properties;
	    private static File filePath = new File("./configuration/config.properties");
	    
	    public Config() throws IOException {
	        properties = new Properties();

	        try (FileInputStream fis = new FileInputStream(filePath)) {
	            properties.load(fis);
	        }
	    }

	    public String getProperty(String key) {
	        return properties.getProperty(key).replace("\"", "");
	    }

	    public int getInt(String key) {
	        return Integer.parseInt(properties.getProperty(key));
	    }

	    public boolean getBoolean(String key) {
	        return Boolean.parseBoolean(properties.getProperty(key));
	    }

	    public double getDouble(String key) {
	        return Double.parseDouble(properties.getProperty(key));
	    }

	
}

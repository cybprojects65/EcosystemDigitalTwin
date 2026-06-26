package it.cnr.ncss.utils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

public class LiveUpdateFile {

		public LiveUpdateFile() throws Exception{
			Config c= new Config();
			REMOTE_URL = URI.create(c.getProperty("live_update_data").replace("\"", ""));
		}

		public URI REMOTE_URL = null;
	    public Path LOCAL_FILE = Path.of("./cache/live_data.csv");

	    public boolean downloadIfChanged() throws Exception {
	        Files.createDirectories(LOCAL_FILE.getParent());

	        byte[] remoteBytes = downloadRemoteBytes();

	        if (Files.exists(LOCAL_FILE)) {
	            String localHash = sha256(Files.readAllBytes(LOCAL_FILE));
	            String remoteHash = sha256(remoteBytes);

	            if (localHash.equals(remoteHash)) {
	                return false; // local file already equals remote file
	            }
	        }

	        Files.write(LOCAL_FILE, remoteBytes);
	        return true; // downloaded or replaced
	    }

	    private byte[] downloadRemoteBytes() throws IOException, InterruptedException {
	    	
	    	HttpClient client = HttpClient.newBuilder()
	    	        .version(HttpClient.Version.HTTP_1_1)
	    	        .connectTimeout(Duration.ofSeconds(20))
	    	        .followRedirects(HttpClient.Redirect.ALWAYS)
	    	        .build();
	    	
	        HttpRequest request = HttpRequest.newBuilder()
	                .uri(REMOTE_URL)
	                .version(HttpClient.Version.HTTP_1_1)
	                .timeout(Duration.ofMinutes(2))
	                .header("User-Agent", "Mozilla/5.0")
	                .header("Accept", "text/csv,text/plain,*/*")
	                .GET()
	                .build();

	        HttpResponse<byte[]> response =
	                client.send(request, HttpResponse.BodyHandlers.ofByteArray());

	        if (response.statusCode() < 200 || response.statusCode() >= 300) {
	            throw new IOException(
	                "Download failed with HTTP status " + response.statusCode()
	                + "\nBody: " + new String(response.body(), java.nio.charset.StandardCharsets.UTF_8)
	            );
	        }

	        return response.body();
	    }
	    

	    private static String sha256(byte[] bytes) throws Exception {
	        MessageDigest digest = MessageDigest.getInstance("SHA-256");
	        return HexFormat.of().formatHex(digest.digest(bytes));
	    }
	    
	   public File getLiveData() throws Exception{
		   
		   downloadIfChanged();
		   return LOCAL_FILE.toFile();
	   }
	   
	
	
}

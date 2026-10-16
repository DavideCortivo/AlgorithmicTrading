package orderManagement;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;


public class TelegramNotifier {

	private static final String BOT_TOKEN = "INSERT_YOUR_TOKEN";
    private static final String CHAT_ID = "INSERT_YOUR_CHAT_ID";
    
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static void send(String message) {
        try {
            String urlString = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
            
           
            String cleanMessage = message.replace("\"", "\\\"");
            String jsonPayload = "{\"chat_id\": \"" + CHAT_ID + "\", \"text\": \"" + cleanMessage + "\", \"parse_mode\": \"HTML\"}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                  .exceptionally(e -> {
                      System.err.println("[TELEGRAM ERROR] Fail to send notification: " + e.getMessage());
                      return null;
                  });
                  
        } catch (Exception e) {
            System.err.println("[TELEGRAM ERROR] Critical Error: " + e.getMessage());
        }
    }
}

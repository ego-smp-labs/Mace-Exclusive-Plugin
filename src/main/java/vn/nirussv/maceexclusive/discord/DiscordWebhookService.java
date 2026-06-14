package vn.nirussv.maceexclusive.discord;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.util.Scheduler;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Level;

public final class DiscordWebhookService {

    private final Plugin plugin;
    private final ConfigManager configManager;

    public DiscordWebhookService(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void sendMaceNotification(String playerName, String maceName, String action, Location loc, String maceId) {
        if (!configManager.isDiscordWebhookEnabled()) return;
        String urlString = configManager.getDiscordWebhookUrl();
        if (urlString == null || urlString.isBlank() || !urlString.startsWith("http")) return;

        int color = configManager.getDiscordColor(maceId);
        String worldName = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "Unknown";
        String coords = loc != null ? loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() : "Unknown";

        // Strip legacy color codes (e.g., &a, &l) for Discord text
        String cleanMaceName = maceName.replaceAll("(?i)&[0-9a-fk-or]", "");
        String cleanPlayerName = playerName.replaceAll("(?i)&[0-9a-fk-or]", "");
        String cleanAction = action.toLowerCase();

        String embedTitle = applyPlaceholders(configManager.getDiscordEmbedTitle(), cleanPlayerName, cleanMaceName, cleanAction, worldName, coords);
        String embedDesc = applyPlaceholders(configManager.getDiscordEmbedDescription(), cleanPlayerName, cleanMaceName, cleanAction, worldName, coords);
        String playerFieldName = applyPlaceholders(configManager.getDiscordEmbedFieldName("player-name"), cleanPlayerName, cleanMaceName, cleanAction, worldName, coords);
        String methodFieldName = applyPlaceholders(configManager.getDiscordEmbedFieldName("method-name"), cleanPlayerName, cleanMaceName, cleanAction, worldName, coords);
        String locationFieldName = applyPlaceholders(configManager.getDiscordEmbedFieldName("location-name"), cleanPlayerName, cleanMaceName, cleanAction, worldName, coords);
        String locationFieldValue = applyPlaceholders(configManager.getDiscordEmbedLocationValue(), cleanPlayerName, cleanMaceName, cleanAction, worldName, coords);
        String footer = applyPlaceholders(configManager.getDiscordEmbedFooter(), cleanPlayerName, cleanMaceName, cleanAction, worldName, coords);

        String json = "{"
            + "\"embeds\": [{"
            + "\"title\": \"" + escapeJson(embedTitle) + "\","
            + "\"color\": " + color + ","
            + "\"description\": \"" + escapeJson(embedDesc) + "\","
            + "\"fields\": ["
            + "{\"name\": \"" + escapeJson(playerFieldName) + "\", \"value\": \"" + escapeJson(cleanPlayerName) + "\", \"inline\": true},"
            + "{\"name\": \"" + escapeJson(methodFieldName) + "\", \"value\": \"" + escapeJson(cleanAction.toUpperCase()) + "\", \"inline\": true},"
            + "{\"name\": \"" + escapeJson(locationFieldName) + "\", \"value\": \"" + escapeJson(locationFieldValue) + "\", \"inline\": false}"
            + "],"
            + "\"footer\": {\"text\": \"" + escapeJson(footer) + "\"},"
            + "\"timestamp\": \"" + Instant.now().toString() + "\""
            + "}]"
            + "}";

        sendPostAsync(urlString, json);
    }

    private String applyPlaceholders(String template, String playerName, String maceName, String action, String worldName, String coords) {
        if (template == null) return "";
        return template
            .replace("%player%", playerName)
            .replace("%item%", maceName)
            .replace("%action%", action)
            .replace("%world%", worldName)
            .replace("%coords%", coords);
    }

    private void sendPostAsync(String urlString, String jsonPayload) {
        Scheduler.runTaskAsync(plugin, () -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlString);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; utf-8");
                conn.setRequestProperty("User-Agent", "Mace-Exclusive-Plugin");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) {
                    plugin.getLogger().warning("Discord webhook returned status code: " + code);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook notice", e);
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\"') sb.append("\\\"");
            else if (c == '\\') sb.append("\\\\");
            else if (c == '\b') sb.append("\\b");
            else if (c == '\f') sb.append("\\f");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else sb.append(c);
        }
        return sb.toString();
    }
}

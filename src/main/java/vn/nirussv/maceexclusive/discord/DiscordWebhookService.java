package vn.nirussv.maceexclusive.discord;

import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import vn.nirussv.maceexclusive.util.Scheduler;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Level;

public final class DiscordWebhookService {

    private final Plugin plugin;

    public DiscordWebhookService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void sendMaceNotification(String playerName, String maceName, String action, Location loc, String maceId) {
        if (!plugin.getConfig().getBoolean("discord.enabled", false)) return;
        String urlString = plugin.getConfig().getString("discord.webhook-url", "");
        if (urlString == null || urlString.isBlank() || !urlString.startsWith("http")) return;

        int color = getMaceColorDecimal(maceId);
        String worldName = loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "Unknown";
        String coords = loc != null ? loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() : "Unknown";

        // Strip legacy color codes (e.g., &a, &l) for Discord text
        String cleanMaceName = maceName.replaceAll("(?i)&[0-9a-fk-or]", "");
        String cleanPlayerName = playerName.replaceAll("(?i)&[0-9a-fk-or]", "");
        String cleanAction = action.toLowerCase();

        String embedTitle = "⚔️ ANCIENT ARTIFACT CLAIMED ⚔️";
        String embedDesc = "**" + escapeJson(cleanPlayerName) + "** has " + cleanAction + " the legendary **" + escapeJson(cleanMaceName) + "**!";

        String json = "{"
            + "\"embeds\": [{"
            + "\"title\": \"" + escapeJson(embedTitle) + "\","
            + "\"color\": " + color + ","
            + "\"description\": \"" + embedDesc + "\","
            + "\"fields\": ["
            + "{\"name\": \"👤 Player\", \"value\": \"" + escapeJson(cleanPlayerName) + "\", \"inline\": true},"
            + "{\"name\": \"🔨 Method\", \"value\": \"" + escapeJson(cleanAction.toUpperCase()) + "\", \"inline\": true},"
            + "{\"name\": \"📍 Location\", \"value\": \"World: `" + escapeJson(worldName) + "`\\nCoords: `" + escapeJson(coords) + "`\", \"inline\": false}"
            + "],"
            + "\"footer\": {\"text\": \"Mace-Exclusive Integration Status\"},"
            + "\"timestamp\": \"" + Instant.now().toString() + "\""
            + "}]"
            + "}";

        sendPostAsync(urlString, json);
    }

    private int getMaceColorDecimal(String maceId) {
        if (maceId == null) return 9807270;
        return switch (maceId.toLowerCase()) {
            case "chaos_mace" -> 10181046; // Purple
            case "void_mace" -> 3447003;   // Dark Blue
            case "vampiric_mace" -> 15158332; // Red
            case "gravity_mace" -> 15277667; // Pink
            case "power_mace" -> 15844367;   // Gold
            case "sonic_mace" -> 1752220;    // Cyan
            case "soulfire_mace" -> 2062527; // Light Blue
            default -> 9807270;             // Light Grey
        };
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

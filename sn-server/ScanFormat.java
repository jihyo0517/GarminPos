import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public final class ScanFormat {
    private static final DateTimeFormatter HMS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter MD_HMS = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");

    private ScanFormat() {}

    public static Map<String,String> parseFormBody(String body) {
        Map<String,String> out = new HashMap<>();
        if (body == null || body.isEmpty()) return out;
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(dec(k), dec(v));
        }
        return out;
    }

    private static String dec(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static String formatLine(long epochMillis, String sn, ZoneId zone) {
        String hms = HMS.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
        return hms + "  " + sn;
    }

    /** 표의 "날짜/시각" 열에 쓰는 문자열 (MM-dd HH:mm:ss). */
    public static String formatDateTime(long epochMillis, ZoneId zone) {
        return MD_HMS.format(Instant.ofEpochMilli(epochMillis).atZone(zone));
    }
}

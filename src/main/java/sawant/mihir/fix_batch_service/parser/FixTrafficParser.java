package sawant.mihir.fix_batch_service.parser;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Parses lines from a FIX traffic capture, e.g.:
 *
 * <pre>
 * DemoHost1 Tue Jun 17 09:15:00 2025 GMT   000001 DemoFixGateway(PARTNER1) [1001] &lt;  in &gt; 8=FIX.4.2|9=350|...
 * DemoHost1 Tue Jun 17 09:15:00 2025 GMT   000002 DemoFixGateway(PARTNER1) [1001] &lt; evt &gt; Fix engine connection established
 * </pre>
 *
 * Also accepts raw FIX lines (starting with "8=FIX") without an envelope.
 * Field delimiter may be a pipe '|' or SOH (0x01).
 */
@Component
public class FixTrafficParser {

    private static final String FIX_START = "8=FIX";

    /** Envelope: host DOW Mon dd HH:mm:ss yyyy TZ  seq plugin [pid] < dir > rest */
    private static final Pattern ENVELOPE = Pattern.compile(
            "^(\\S+)\\s+" +                       // 1 host
            "(\\w{3})\\s+(\\w{3})\\s+(\\d{1,2})\\s+(\\d{2}:\\d{2}:\\d{2})\\s+(\\d{4})\\s+(\\w+)" + // 2..7 date parts
            "\\s+(\\d+)\\s+" +                   // 8 sequence
            "(\\S+)" +                           // 9 plugin
            "\\s+\\[(\\d+)]\\s+" +               // 10 pid
            "<\\s*(\\w+)\\s*>\\s*" +             // 11 direction
            "(.*)$");                            // 12 body

    private static final DateTimeFormatter ENVELOPE_DATE =
            DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);

    /**
     * @return parsed line, or null if the line is blank / unrecognizable.
     */
    public ParsedLine parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        line = line.trim();

        var matcher = ENVELOPE.matcher(line);
        if (matcher.matches()) {
            return parseEnvelope(matcher);
        }

        int fixStart = findFixStart(line);
        if (fixStart >= 0) {
            // No envelope (or unknown one): treat the FIX body as the payload.
            return message(null, "UNKNOWN", null, null, line.substring(fixStart));
        }
        return null;
    }

    private ParsedLine parseEnvelope(java.util.regex.Matcher m) {
        var logTime = parseDate(m.group(2), m.group(3), m.group(4), m.group(5), m.group(6));
        Long pid = parseLong(m.group(10));
        String direction = m.group(11).toUpperCase(Locale.ROOT);
        String plugin = m.group(9);
        String body = m.group(12).trim();

        int fixStart = findFixStart(body);
        if (fixStart >= 0) {
            return message(logTime, direction, plugin, pid, body.substring(fixStart));
        }
        return new ParsedLine(ParsedLine.Kind.EVENT, logTime, direction, plugin, pid, null, body, null);
    }

    private ParsedLine message(LocalDateTime logTime, String direction, String plugin, Long pid, String rawFix) {
        String normalized = rawFix.trim();
        Map<Integer, String> fields = parseFields(normalized);
        if (fields.isEmpty()) {
            return null;
        }
        return new ParsedLine(ParsedLine.Kind.MESSAGE, logTime, direction, plugin, pid, normalized, null, fields);
    }

    /** Split a FIX body into an ordered tag -> value map. Accepts '|' and SOH delimiters. */
    public static Map<Integer, String> parseFields(String rawFix) {
        Map<Integer, String> fields = new LinkedHashMap<>();
        String normalized = rawFix.replace('\u0001', '|');
        for (String pair : normalized.split("\\|")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                int tag = Integer.parseInt(pair.substring(0, eq));
                fields.put(tag, pair.substring(eq + 1));
            } catch (NumberFormatException ignored) {
                // not a numeric tag - skip fragment
            }
        }
        return fields;
    }

    private static int findFixStart(String s) {
        int idx = s.indexOf(FIX_START);
        return idx >= 0 ? idx : -1;
    }

    private static LocalDateTime parseDate(String dow, String mon, String day, String time, String year) {
        try {
            String joined = dow + " " + mon + " " + Integer.parseInt(day) + " " + time + " " + year;
            return LocalDateTime.parse(joined, ENVELOPE_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

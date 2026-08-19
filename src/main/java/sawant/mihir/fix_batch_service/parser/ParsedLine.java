package sawant.mihir.fix_batch_service.parser;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * One parsed line from a FIX traffic capture file.
 *
 * @param kind     MESSAGE (contains a FIX body), EVENT (engine event, no FIX body)
 * @param logTime  timestamp from the log envelope (may be null for raw FIX lines)
 * @param direction IN / OUT / EVT / UNKNOWN
 * @param plugin   e.g. DemoFixGateway(PARTNER1)
 * @param pid      process id from the envelope
 * @param rawFix   raw FIX message (tag=value delimited), null for events
 * @param eventText free text for EVENT lines
 * @param fields   parsed tag -> value map (messages only), preserves order
 */
public record ParsedLine(
        Kind kind,
        LocalDateTime logTime,
        String direction,
        String plugin,
        Long pid,
        String rawFix,
        String eventText,
        Map<Integer, String> fields) {

    public enum Kind { MESSAGE, EVENT }
}

package sawant.mihir.fix_batch_service.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sawant.mihir.fix_batch_service.dictionary.FixDictionaryService;
import sawant.mihir.fix_batch_service.model.ImportSummary;
import sawant.mihir.fix_batch_service.parser.FixTrafficParser;
import sawant.mihir.fix_batch_service.parser.ParsedLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * Streams a FIX traffic file, parses each line and stores the envelope
 * (fix_log) plus the decoded indexable fields (fix_message).
 * All processing is in-process; nothing leaves the machine.
 */
@Service
public class ImportService {

    private final JdbcTemplate jdbc;
    private final FixTrafficParser parser;
    private final FixDictionaryService dictionary;

    public ImportService(JdbcTemplate jdbc, FixTrafficParser parser, FixDictionaryService dictionary) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.dictionary = dictionary;
    }

    @Transactional
    public ImportSummary importStream(String filename, InputStream in) throws IOException {
        long importId = insertBatch(filename);
        int total = 0, messages = 0, events = 0, skipped = 0;

        try (var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                ParsedLine parsed = parser.parse(line);
                if (parsed == null) {
                    skipped++;
                    continue;
                }
                long logId = insertLog(importId, total, parsed);
                if (parsed.kind() == ParsedLine.Kind.MESSAGE) {
                    insertMessage(importId, logId, parsed);
                    messages++;
                } else {
                    events++;
                }
            }
        }

        jdbc.update("update import_batch set total_lines=?, parsed_messages=?, event_lines=?, skipped_lines=? where id=?",
                total, messages, events, skipped, importId);
        return new ImportSummary(importId, filename, LocalDateTime.now(), total, messages, events, skipped);
    }

    /** Remove an import and everything parsed from it. */
    @Transactional
    public void deleteImport(long importId) {
        jdbc.update("delete from fix_message where import_id = ?", importId);
        jdbc.update("delete from fix_log where import_id = ?", importId);
        jdbc.update("delete from import_batch where id = ?", importId);
    }

    private long insertBatch(String filename) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into import_batch(filename, imported_at) values (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, filename);
            ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private long insertLog(long importId, int lineNo, ParsedLine p) {
        String msgType = p.fields() != null ? p.fields().get(35) : null;
        String raw = p.kind() == ParsedLine.Kind.MESSAGE ? p.rawFix() : p.eventText();
        var keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into fix_log(import_id, line_no, log_time, direction, plugin, pid, msg_type, raw) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, importId);
            ps.setInt(2, lineNo);
            if (p.logTime() != null) {
                ps.setTimestamp(3, Timestamp.valueOf(p.logTime()));
            } else {
                ps.setTimestamp(3, null);
            }
            ps.setString(4, p.direction());
            ps.setString(5, p.plugin());
            if (p.pid() != null) {
                ps.setLong(6, p.pid());
            } else {
                ps.setObject(6, null);
            }
            ps.setString(7, msgType);
            ps.setString(8, raw);
            return ps;
        }, keys);
        return Objects.requireNonNull(keys.getKey()).longValue();
    }

    private void insertMessage(long importId, long logId, ParsedLine p) {
        Map<Integer, String> f = p.fields();
        String version = f.get(8);
        String msgType = f.get(35);
        jdbc.update("insert into fix_message(fix_log_id, import_id, version, msg_type, msg_name, " +
                        "sender_comp_id, target_comp_id, msg_seq_num, sending_time, cl_ord_id, order_id, " +
                        "symbol, security_id, side, order_qty, price, exec_id, exec_type, ord_status) " +
                        "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                logId, importId, version, msgType, dictionary.msgTypeName(version, msgType),
                f.get(49), f.get(56), asLong(f.get(34)), f.get(52), f.get(11), f.get(37),
                f.get(55), f.get(48), f.get(54), asLong(f.get(38)), asDouble(f.get(44)),
                f.get(17), f.get(150), f.get(39));
    }

    private static Long asLong(String s) {
        try {
            return s == null ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double asDouble(String s) {
        try {
            return s == null ? null : Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

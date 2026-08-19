package sawant.mihir.fix_batch_service.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import sawant.mihir.fix_batch_service.dictionary.FixDictionaryService;
import sawant.mihir.fix_batch_service.model.FieldView;
import sawant.mihir.fix_batch_service.model.ImportSummary;
import sawant.mihir.fix_batch_service.model.MessageDetail;
import sawant.mihir.fix_batch_service.model.MessageRow;
import sawant.mihir.fix_batch_service.parser.FixTrafficParser;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QueryService {

    private static final String BASE_SELECT =
            "from fix_message m join fix_log l on m.fix_log_id = l.id ";

    private static final String MESSAGE_COLUMNS =
            "m.id, m.fix_log_id, m.import_id, l.log_time, l.direction, l.plugin, m.version, " +
            "m.msg_type, m.msg_name, m.sender_comp_id, m.target_comp_id, m.msg_seq_num, m.sending_time, " +
            "m.cl_ord_id, m.order_id, m.symbol, m.side, m.order_qty, m.price, m.exec_type, m.ord_status ";

    private final JdbcTemplate jdbc;
    private final FixDictionaryService dictionary;

    public QueryService(JdbcTemplate jdbc, FixDictionaryService dictionary) {
        this.jdbc = jdbc;
        this.dictionary = dictionary;
    }

    public record Page<T>(List<T> content, long total, int page, int size) {
    }

    /** One order group (by ClOrdID) for the orders tab. */
    public record OrderGroup(String clOrdId, String symbol, String side, long messageCount,
                             long firstMessageId, java.time.LocalDateTime firstTime,
                             java.time.LocalDateTime lastTime) {
    }

    /** A sequence-number gap within one session (sender -> target). */
    public record SeqGap(String senderCompId, String targetCompId, long fromSeq, long toSeq,
                         java.time.LocalDateTime afterTime) {
    }

    /** Engine event line (no FIX body). */
    public record EventRow(long id, int lineNo, java.time.LocalDateTime logTime, String plugin, String text) {
    }

    public Page<MessageRow> search(Long importId, String msgType, String direction,
                                   String symbol, String clOrdId, String q,
                                   int page, int size) {
        var where = buildWhere(importId, msgType, direction, symbol, clOrdId, q);

        Long total = jdbc.queryForObject("select count(*) " + BASE_SELECT + where.sql(), Long.class,
                where.params().toArray());

        int safeSize = Math.min(Math.max(size, 1), 500);
        int safePage = Math.max(page, 0);
        var sql = "select " + MESSAGE_COLUMNS + BASE_SELECT + where.sql() +
                " order by l.log_time asc nulls last, l.line_no asc limit ? offset ?";
        var pageParams = new ArrayList<>(where.params());
        pageParams.add(safeSize);
        pageParams.add(safePage * safeSize);

        List<MessageRow> rows = jdbc.query(sql, MESSAGE_ROW_MAPPER, pageParams.toArray());
        return new Page<>(rows, total == null ? 0 : total, safePage, safeSize);
    }

    /** Unpaged search for export (capped). */
    public List<MessageRow> exportRows(Long importId, String msgType, String direction,
                                       String symbol, String clOrdId, String q, int cap) {
        var where = buildWhere(importId, msgType, direction, symbol, clOrdId, q);
        var sql = "select " + MESSAGE_COLUMNS + BASE_SELECT + where.sql() +
                " order by l.log_time asc nulls last, l.line_no asc limit ?";
        var params = new ArrayList<>(where.params());
        params.add(Math.min(Math.max(cap, 1), 100_000));
        return jdbc.query(sql, MESSAGE_ROW_MAPPER, params.toArray());
    }

    private record Where(String sql, List<Object> params) {
    }

    private Where buildWhere(Long importId, String msgType, String direction,
                             String symbol, String clOrdId, String q) {
        var where = new StringBuilder(" where 1=1");
        var params = new ArrayList<>();
        if (importId != null) {
            where.append(" and m.import_id = ?");
            params.add(importId);
        }
        if (nonBlank(msgType)) {
            where.append(" and m.msg_type = ?");
            params.add(msgType.trim());
        }
        if (nonBlank(direction)) {
            where.append(" and upper(l.direction) = ?");
            params.add(direction.trim().toUpperCase());
        }
        if (nonBlank(symbol)) {
            where.append(" and upper(m.symbol) like ?");
            params.add("%" + symbol.trim().toUpperCase() + "%");
        }
        if (nonBlank(clOrdId)) {
            where.append(" and m.cl_ord_id like ?");
            params.add(clOrdId.trim() + "%");
        }
        if (nonBlank(q)) {
            where.append(" and l.raw like ?");
            params.add("%" + q.trim() + "%");
        }
        return new Where(where.toString(), params);
    }

    public MessageDetail detail(long id) {
        var rows = jdbc.query(
                "select " + MESSAGE_COLUMNS + ", l.raw " + BASE_SELECT + " where m.id = ?",
                (rs, rowNum) -> new Object[]{MESSAGE_ROW_MAPPER.mapRow(rs, rowNum), rs.getString("raw")},
                id);
        if (rows.isEmpty()) {
            return null;
        }
        MessageRow row = (MessageRow) rows.get(0)[0];
        String raw = (String) rows.get(0)[1];

        List<FieldView> fields = new ArrayList<>();
        if (raw != null) {
            for (Map.Entry<Integer, String> e : FixTrafficParser.parseFields(raw).entrySet()) {
                int tag = e.getKey();
                String value = e.getValue();
                String name = dictionary.fieldName(row.version(), tag);
                String valueName = dictionary.valueName(row.version(), tag, value);
                fields.add(new FieldView(tag, name, value, valueName));
            }
        }
        return new MessageDetail(row, raw, fields);
    }

    /** Other messages in the same order chain (by ClOrdID or OrderID). */
    public List<MessageRow> orderChain(long id) {
        MessageDetail detail = detail(id);
        if (detail == null) {
            return List.of();
        }
        MessageRow row = detail.row();
        if (row.clOrdId() == null && row.orderId() == null) {
            return List.of(row);
        }
        return jdbc.query(
                "select " + MESSAGE_COLUMNS + BASE_SELECT +
                        " where (m.cl_ord_id is not null and m.cl_ord_id = ?) or (m.order_id is not null and m.order_id = ?) " +
                        " order by l.log_time asc nulls last, l.line_no asc",
                MESSAGE_ROW_MAPPER,
                row.clOrdId(), row.orderId());
    }

    /** Orders tab: one row per ClOrdID, newest activity last. */
    public List<OrderGroup> orders(Long importId) {
        var where = new StringBuilder(" where m.cl_ord_id is not null");
        var params = new ArrayList<>();
        if (importId != null) {
            where.append(" and m.import_id = ?");
            params.add(importId);
        }
        return jdbc.query(
                "select m.cl_ord_id, max(m.symbol) as symbol, max(m.side) as side, count(*) as n, " +
                        "min(m.id) as first_id, min(l.log_time) as first_time, max(l.log_time) as last_time " +
                        BASE_SELECT + where +
                        " group by m.cl_ord_id order by last_time desc nulls last limit 500",
                (rs, n) -> new OrderGroup(
                        rs.getString("cl_ord_id"),
                        rs.getString("symbol"),
                        rs.getString("side"),
                        rs.getLong("n"),
                        rs.getLong("first_id"),
                        ts(rs.getTimestamp("first_time")),
                        ts(rs.getTimestamp("last_time"))),
                params.toArray());
    }

    /**
     * Sequence-number gaps per session (sender -> target), ignoring admin messages
     * and treating seq decreases as session resets (SequenceReset / reconnect).
     */
    public List<SeqGap> gaps(Long importId) {
        var params = new ArrayList<>();
        var where = new StringBuilder(
                " where m.msg_seq_num is not null and m.msg_type not in ('0','1','2','4','5','A')");
        if (importId != null) {
            where.append(" and m.import_id = ?");
            params.add(importId);
        }
        var rows = jdbc.query(
                "select m.sender_comp_id, m.target_comp_id, m.msg_seq_num, l.log_time " + BASE_SELECT + where +
                        " order by m.sender_comp_id, m.target_comp_id, l.log_time asc nulls last, l.line_no asc",
                (rs, n) -> new Object[]{
                        rs.getString("sender_comp_id"),
                        rs.getString("target_comp_id"),
                        rs.getLong("msg_seq_num"),
                        ts(rs.getTimestamp("log_time"))},
                params.toArray());

        var gaps = new ArrayList<SeqGap>();
        String session = null;
        long prevSeq = -1;
        java.time.LocalDateTime prevTime = null;
        for (Object[] r : rows) {
            String s = (String) r[0], t = (String) r[1];
            long seq = (Long) r[2];
            var time = (java.time.LocalDateTime) r[3];
            String key = s + "->" + t;
            if (!key.equals(session)) {
                session = key;
                prevSeq = seq;
                prevTime = time;
                continue;
            }
            if (seq > prevSeq + 1) {
                gaps.add(new SeqGap(s, t, prevSeq + 1, seq - 1, prevTime));
            } else if (seq < prevSeq) {
                // seq reset / restart: start tracking again
            }
            prevSeq = seq;
            prevTime = time;
            if (gaps.size() >= 200) {
                break;
            }
        }
        return gaps;
    }

    /** Engine event lines (no FIX body), e.g. "Fix engine connection established". */
    public List<EventRow> events(Long importId) {
        var params = new ArrayList<>();
        var where = new StringBuilder(" where l.msg_type is null");
        if (importId != null) {
            where.append(" and l.import_id = ?");
            params.add(importId);
        }
        return jdbc.query(
                "select l.id, l.line_no, l.log_time, l.plugin, l.raw from fix_log l " + where +
                        " order by l.id limit 500",
                (rs, n) -> new EventRow(rs.getLong("id"), rs.getInt("line_no"),
                        ts(rs.getTimestamp("log_time")), rs.getString("plugin"), rs.getString("raw")),
                params.toArray());
    }

    /** Replay window for an import: first/last capture timestamps + message count. */
    public Map<String, Object> replayMeta(Long importId) {
        var where = new StringBuilder(" where 1=1");
        var params = new ArrayList<>();
        if (importId != null) {
            where.append(" and m.import_id = ?");
            params.add(importId);
        }
        var rows = jdbc.query(
                "select min(l.log_time) as t_start, max(l.log_time) as t_end, count(*) as n " +
                        BASE_SELECT + where,
                (rs, n) -> {
                    Map<String, Object> meta = new LinkedHashMap<>();
                    var start = ts(rs.getTimestamp("t_start"));
                    var end = ts(rs.getTimestamp("t_end"));
                    meta.put("start", start);
                    meta.put("end", end);
                    meta.put("total", rs.getLong("n"));
                    return meta;
                },
                params.toArray());
        return rows.isEmpty() ? Map.of("total", 0) : rows.get(0);
    }

    /** Ordered rows for replay, from a virtual time, optionally hiding session-level messages. */
    public List<MessageRow> replayRows(Long importId, java.time.LocalDateTime from, boolean hideSession,
                                       String msgType, String direction, String symbol, String clOrdId,
                                       String q, int cap) {
        var where = buildWhere(importId, msgType, direction, symbol, clOrdId, q);
        var sql = new StringBuilder("select " + MESSAGE_COLUMNS + BASE_SELECT + where.sql());
        var params = new ArrayList<>(where.params());
        if (hideSession) {
            sql.append(" and m.msg_type not in ('0','1','2','3','4','5','A')");
        }
        if (from != null) {
            sql.append(" and (l.log_time >= ? or l.log_time is null)");
            params.add(Timestamp.valueOf(from));
        }
        sql.append(" order by l.log_time asc nulls last, l.line_no asc limit ?");
        params.add(Math.min(Math.max(cap, 1), 100_000));
        return jdbc.query(sql.toString(), MESSAGE_ROW_MAPPER, params.toArray());
    }

    public Map<String, Object> stats(Long importId) {
        var stats = new LinkedHashMap<String, Object>();
        String filter = importId != null ? " where m.import_id = " + importId : "";
        String joinFilter = importId != null ? " where m.import_id = " + importId : "";

        var byMsgType = new ArrayList<Map<String, Object>>();
        for (var m : jdbc.queryForList(
                "select m.msg_type, m.msg_name, count(*) as n from fix_message m " + filter +
                        " group by m.msg_type, m.msg_name order by n desc")) {
            var row = new LinkedHashMap<String, Object>();
            row.put("msgType", m.get("msg_type"));
            row.put("msgName", m.get("msg_name"));
            row.put("n", m.get("n"));
            byMsgType.add(row);
        }
        stats.put("byMsgType", byMsgType);

        var byDirection = new ArrayList<Map<String, Object>>();
        for (var d : jdbc.queryForList(
                "select l.direction, count(*) as n from fix_message m join fix_log l on m.fix_log_id = l.id " +
                        joinFilter + " group by l.direction order by n desc")) {
            var row = new LinkedHashMap<String, Object>();
            row.put("direction", d.get("direction"));
            row.put("n", d.get("n"));
            byDirection.add(row);
        }
        stats.put("byDirection", byDirection);

        var topSymbols = new ArrayList<Map<String, Object>>();
        for (var s : jdbc.queryForList(
                "select m.symbol, count(*) as n from fix_message m " + filter +
                        (importId != null ? " and" : " where") + " m.symbol is not null" +
                        " group by m.symbol order by n desc limit 10")) {
            var row = new LinkedHashMap<String, Object>();
            row.put("symbol", s.get("symbol"));
            row.put("n", s.get("n"));
            topSymbols.add(row);
        }
        stats.put("topSymbols", topSymbols);

        Long messages = jdbc.queryForObject("select count(*) from fix_message m" + filter, Long.class);
        Long events = jdbc.queryForObject(
                "select count(*) from fix_log l where l.msg_type is null" +
                        (importId != null ? " and l.import_id = " + importId : ""), Long.class);
        stats.put("totalMessages", messages);
        stats.put("totalEvents", events);
        return stats;
    }

    public List<ImportSummary> imports() {
        return jdbc.query("select id, filename, imported_at, total_lines, parsed_messages, event_lines, skipped_lines " +
                        "from import_batch order by id desc",
                (rs, n) -> new ImportSummary(
                        rs.getLong("id"),
                        rs.getString("filename"),
                        ts(rs.getTimestamp("imported_at")),
                        rs.getInt("total_lines"),
                        rs.getInt("parsed_messages"),
                        rs.getInt("event_lines"),
                        rs.getInt("skipped_lines")));
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static java.time.LocalDateTime ts(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static final RowMapper<MessageRow> MESSAGE_ROW_MAPPER = new RowMapper<>() {
        @Override
        public MessageRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new MessageRow(
                    rs.getLong("id"),
                    rs.getLong("fix_log_id"),
                    rs.getLong("import_id"),
                    ts(rs.getTimestamp("log_time")),
                    rs.getString("direction"),
                    rs.getString("plugin"),
                    rs.getString("version"),
                    rs.getString("msg_type"),
                    rs.getString("msg_name"),
                    rs.getString("sender_comp_id"),
                    rs.getString("target_comp_id"),
                    (Long) rs.getObject("msg_seq_num"),
                    rs.getString("sending_time"),
                    rs.getString("cl_ord_id"),
                    rs.getString("order_id"),
                    rs.getString("symbol"),
                    rs.getString("side"),
                    (Long) rs.getObject("order_qty"),
                    (Double) rs.getObject("price"),
                    rs.getString("exec_type"),
                    rs.getString("ord_status"));
        }
    };
}

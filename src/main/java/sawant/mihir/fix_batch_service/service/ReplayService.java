package sawant.mihir.fix_batch_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sawant.mihir.fix_batch_service.model.MessageRow;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replays captured FIX traffic as a Server-Sent-Events stream, preserving the
 * original capture timing scaled by a speed factor. Entirely local: rows come
 * from the database and are pushed to the browser over SSE.
 */
@Service
public class ReplayService {

    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);

    private final QueryService queryService;
    private final long maxGapMs;

    public ReplayService(QueryService queryService,
                         @Value("${fix.replay.max-gap-ms:2000}") long maxGapMs) {
        this.queryService = queryService;
        this.maxGapMs = maxGapMs;
    }

    /**
     * @param speed       playback multiplier (1 = realtime between messages)
     * @param burst       if true, ignore capture gaps and emit at a fixed msgs/sec rate (speed = msgs/sec)
     * @param hideSession skip session-level messages (heartbeat, logon, etc.)
     */
    public SseEmitter stream(Long importId, LocalDateTime from, double speed, boolean burst, boolean hideSession,
                             String msgType, String direction, String symbol, String clOrdId, String q) {
        List<MessageRow> rows = queryService.replayRows(
                importId, from, hideSession, msgType, direction, symbol, clOrdId, q, 50_000);

        SseEmitter emitter = new SseEmitter(0L); // no timeout; client controls lifetime
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));

        double safeSpeed = speed <= 0 ? 10 : speed;
        Thread thread = new Thread(() -> runStream(emitter, cancelled, rows, safeSpeed, burst), "replay-stream");
        thread.setDaemon(true);
        thread.start();
        return emitter;
    }

    private void runStream(SseEmitter emitter, AtomicBoolean cancelled,
                           List<MessageRow> rows, double speed, boolean burst) {
        try {
            emitter.send(SseEmitter.event().name("meta").data(Map.of("total", rows.size())));
            LocalDateTime prev = null;
            for (MessageRow row : rows) {
                if (cancelled.get()) {
                    return;
                }
                long sleep = sleepMs(prev, row.logTime(), speed, burst);
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
                if (cancelled.get()) {
                    return;
                }
                emitter.send(SseEmitter.event().name("message").data(row));
                if (row.logTime() != null) {
                    prev = row.logTime();
                }
            }
            emitter.send(SseEmitter.event().name("done").data("end"));
            emitter.complete();
        } catch (Exception e) {
            // client disconnected or send failed - stop quietly
            log.debug("replay stream ended: {}", e.getMessage());
            emitter.complete();
        }
    }

    private long sleepMs(LocalDateTime prev, LocalDateTime cur, double speed, boolean burst) {
        if (burst) {
            return (long) Math.max(1, 1000.0 / speed);
        }
        if (prev == null || cur == null) {
            return 0;
        }
        long gap = Duration.between(prev, cur).toMillis();
        if (gap <= 0) {
            return 0;
        }
        return Math.min((long) (gap / speed), maxGapMs);
    }
}

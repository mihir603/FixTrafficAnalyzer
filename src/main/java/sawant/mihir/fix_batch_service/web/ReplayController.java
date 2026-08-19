package sawant.mihir.fix_batch_service.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import sawant.mihir.fix_batch_service.service.QueryService;
import sawant.mihir.fix_batch_service.service.ReplayService;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/replay")
public class ReplayController {

    private final ReplayService replayService;
    private final QueryService queryService;

    public ReplayController(ReplayService replayService, QueryService queryService) {
        this.replayService = replayService;
        this.queryService = queryService;
    }

    /** Time window and message count for the replay scrubber. */
    @GetMapping("/meta")
    public Map<String, Object> meta(@RequestParam(required = false) Long importId) {
        return queryService.replayMeta(importId);
    }

    /**
     * SSE stream of messages in capture-time order.
     *
     * @param from        ISO local datetime to start from (scrub position)
     * @param speed       playback multiplier (realtime mode) or messages/sec (burst mode)
     * @param mode        "realtime" (scaled capture gaps, capped) or "burst" (fixed rate)
     * @param hideSession skip heartbeat/logon/etc. (default true)
     */
    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam(required = false) Long importId,
                             @RequestParam(required = false) String from,
                             @RequestParam(defaultValue = "10") double speed,
                             @RequestParam(defaultValue = "realtime") String mode,
                             @RequestParam(defaultValue = "true") boolean hideSession,
                             @RequestParam(required = false) String msgType,
                             @RequestParam(required = false) String direction,
                             @RequestParam(required = false) String symbol,
                             @RequestParam(required = false) String clOrdId,
                             @RequestParam(required = false) String q) {
        LocalDateTime fromTime = null;
        if (from != null && !from.isBlank()) {
            try {
                fromTime = LocalDateTime.parse(from.trim());
            } catch (Exception ignored) {
                // bad from value - start from the beginning
            }
        }
        return replayService.stream(importId, fromTime, speed, "burst".equalsIgnoreCase(mode),
                hideSession, msgType, direction, symbol, clOrdId, q);
    }
}

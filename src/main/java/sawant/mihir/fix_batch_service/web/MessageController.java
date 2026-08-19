package sawant.mihir.fix_batch_service.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sawant.mihir.fix_batch_service.model.MessageDetail;
import sawant.mihir.fix_batch_service.model.MessageRow;
import sawant.mihir.fix_batch_service.service.QueryService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MessageController {

    private final QueryService queryService;

    public MessageController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/messages")
    public QueryService.Page<MessageRow> search(
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String clOrdId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return queryService.search(importId, msgType, direction, symbol, clOrdId, q, page, size);
    }

    @GetMapping("/messages/{id}")
    public ResponseEntity<MessageDetail> detail(@PathVariable long id) {
        MessageDetail detail = queryService.detail(id);
        return detail == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(detail);
    }

    @GetMapping("/messages/{id}/chain")
    public List<MessageRow> chain(@PathVariable long id) {
        return queryService.orderChain(id);
    }

    @GetMapping("/orders")
    public List<QueryService.OrderGroup> orders(@RequestParam(required = false) Long importId) {
        return queryService.orders(importId);
    }

    @GetMapping("/gaps")
    public List<QueryService.SeqGap> gaps(@RequestParam(required = false) Long importId) {
        return queryService.gaps(importId);
    }

    @GetMapping("/events")
    public List<QueryService.EventRow> events(@RequestParam(required = false) Long importId) {
        return queryService.events(importId);
    }

    /** Download the current filter result as CSV (stays on this machine). */
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam(required = false) Long importId,
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String clOrdId,
            @RequestParam(required = false) String q) {
        var rows = queryService.exportRows(importId, msgType, direction, symbol, clOrdId, q, 50_000);
        var csv = new StringBuilder("id,time,direction,msg_type,msg_name,sender,target,seq,cl_ord_id,order_id,symbol,side,qty,price,exec_type,ord_status\n");
        for (MessageRow r : rows) {
            csv.append(r.id()).append(',')
                    .append(r.logTime() == null ? "" : r.logTime()).append(',')
                    .append(csv(r.direction())).append(csv(r.msgType())).append(csv(r.msgName()))
                    .append(csv(r.senderCompId())).append(csv(r.targetCompId()))
                    .append(r.msgSeqNum() == null ? "" : r.msgSeqNum()).append(',')
                    .append(csv(r.clOrdId())).append(csv(r.orderId())).append(csv(r.symbol())).append(csv(r.side()))
                    .append(r.orderQty() == null ? "" : r.orderQty()).append(',')
                    .append(r.price() == null ? "" : r.price()).append(',')
                    .append(csv(r.execType())).append(csv(r.ordStatus())).append('\n');
        }
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=fix_messages.csv")
                .body(csv.toString());
    }

    private static String csv(String s) {
        if (s == null) {
            return ",";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\",";
        }
        return s + ",";
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) Long importId) {
        return queryService.stats(importId);
    }
}

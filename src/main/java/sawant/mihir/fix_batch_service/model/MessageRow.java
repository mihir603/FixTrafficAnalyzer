package sawant.mihir.fix_batch_service.model;

import java.time.LocalDateTime;

/** Flat row for the message list table. */
public record MessageRow(
        long id,
        long fixLogId,
        long importId,
        LocalDateTime logTime,
        String direction,
        String plugin,
        String version,
        String msgType,
        String msgName,
        String senderCompId,
        String targetCompId,
        Long msgSeqNum,
        String sendingTime,
        String clOrdId,
        String orderId,
        String symbol,
        String side,
        Long orderQty,
        Double price,
        String execType,
        String ordStatus) {
}

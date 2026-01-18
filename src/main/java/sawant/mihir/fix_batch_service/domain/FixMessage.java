package sawant.mihir.fix_batch_service.domain;

import org.springframework.data.annotation.Id;

public record FixMessage(@Id Integer id, String version, String messageType, String senderCompId,
                         String targetCompId, int messageSeqNo, String sendingTime, String clientOrderId,
                         String systemOrderId, String symbol, String securityId, String side, int orderQty,
                         double price, String execId, String execType, String orderStatus, int fixLog) { }
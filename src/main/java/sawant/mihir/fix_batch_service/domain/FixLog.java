package sawant.mihir.fix_batch_service.domain;

import org.springframework.data.annotation.Id;

public record FixLog(@Id Integer id, String fullDateTime, long pid  , String messageType ,String fixPlugin ,String log) {
}

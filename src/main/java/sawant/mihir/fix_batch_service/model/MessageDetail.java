package sawant.mihir.fix_batch_service.model;

import java.util.List;

/** Full decoded message: list row + every tag + raw text. */
public record MessageDetail(MessageRow row, String raw, List<FieldView> fields) {
}

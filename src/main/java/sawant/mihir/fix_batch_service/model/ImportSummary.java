package sawant.mihir.fix_batch_service.model;

import java.time.LocalDateTime;

public record ImportSummary(
        long id,
        String filename,
        LocalDateTime importedAt,
        int totalLines,
        int parsedMessages,
        int eventLines,
        int skippedLines) {
}

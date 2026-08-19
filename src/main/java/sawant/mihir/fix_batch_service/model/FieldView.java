package sawant.mihir.fix_batch_service.model;

/** One decoded FIX field for the detail view. */
public record FieldView(int tag, String name, String value, String valueName) {
}

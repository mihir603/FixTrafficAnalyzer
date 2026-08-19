package sawant.mihir.fix_batch_service.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sawant.mihir.fix_batch_service.model.ImportSummary;
import sawant.mihir.fix_batch_service.service.ImportService;
import sawant.mihir.fix_batch_service.service.QueryService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/imports")
public class ImportController {

    private final ImportService importService;
    private final QueryService queryService;
    private final Path defaultTrafficPath;

    public ImportController(ImportService importService,
                            QueryService queryService,
                            @Value("${fix.traffic.path:}") String defaultTrafficPath) {
        this.importService = importService;
        this.queryService = queryService;
        this.defaultTrafficPath = defaultTrafficPath == null || defaultTrafficPath.isBlank()
                ? null : Path.of(defaultTrafficPath);
    }

    /** Upload a FIX traffic file from the browser (never leaves this machine). */
    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty file"));
        }
        ImportSummary summary = importService.importStream(file.getOriginalFilename(), file.getInputStream());
        return ResponseEntity.ok(summary);
    }

    /** Import the file configured at fix.traffic.path (your sample capture). */
    @PostMapping("/default")
    public ResponseEntity<?> importDefault() throws IOException {
        if (defaultTrafficPath == null || !Files.exists(defaultTrafficPath)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "default traffic file not found",
                    "path", defaultTrafficPath == null ? "" : defaultTrafficPath.toString()));
        }
        try (var in = Files.newInputStream(defaultTrafficPath)) {
            return ResponseEntity.ok(importService.importStream(defaultTrafficPath.getFileName().toString(), in));
        }
    }

    @GetMapping
    public List<ImportSummary> list() {
        return queryService.imports();
    }

    /** Delete one import batch and all its parsed data. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        importService.deleteImport(id);
        return ResponseEntity.noContent().build();
    }
}

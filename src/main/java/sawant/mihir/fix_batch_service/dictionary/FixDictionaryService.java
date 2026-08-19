package sawant.mihir.fix_batch_service.dictionary;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import quickfix.DataDictionary;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Resolves FIX tag numbers, field names, enum values and message-type names using the
 * QuickFIX/J data dictionaries (FIX42.xml etc. bundled in quickfixj-core), plus local
 * overrides from {@code custom-tags.properties} for house tags (e.g. 9200, 9300-9305).
 */
@Service
public class FixDictionaryService {

    /** DataDictionary + msgtype -> message-name index for one FIX version. */
    private record Dict(DataDictionary dictionary, Map<String, String> msgTypeNames) {
    }

    private final Map<String, Dict> dictionaries = new HashMap<>();
    private final Map<Integer, String> customTagNames = new HashMap<>();

    public FixDictionaryService() {
        loadCustomTags();
    }

    private Dict dictionary(String beginString) {
        return dictionaries.computeIfAbsent(beginString == null ? "" : beginString, key -> {
            // beginString like "FIX.4.2" -> "FIX42.xml"
            String resource = key.replace("FIX.", "FIX").replace(".", "") + ".xml";
            DataDictionary dd = tryLoadDictionary(resource);
            if (dd == null) {
                resource = "FIX42.xml"; // sensible default for this capture set
                dd = tryLoadDictionary(resource);
            }
            if (dd == null) {
                throw new IllegalStateException("No FIX data dictionary available");
            }
            return new Dict(dd, loadMsgTypeNames(resource));
        });
    }

    private static DataDictionary tryLoadDictionary(String resource) {
        try {
            return new DataDictionary(resource);
        } catch (Exception e) {
            return null;
        }
    }

    public String fieldName(String beginString, int tag) {
        String name = safe(() -> dictionary(beginString).dictionary().getFieldName(tag));
        if (name != null) {
            return name;
        }
        return customTagNames.getOrDefault(tag, null);
    }

    /** Human-readable enum label, e.g. (54, "1") -> "Buy", or null. */
    public String valueName(String beginString, int tag, String value) {
        if (value == null) {
            return null;
        }
        return safe(() -> dictionary(beginString).dictionary().getValueName(tag, value));
    }

    /** e.g. "8" -> "ExecutionReport", "D" -> "NewOrderSingle". */
    public String msgTypeName(String beginString, String msgType) {
        if (msgType == null) {
            return null;
        }
        return safe(() -> dictionary(beginString).msgTypeNames().get(msgType));
    }

    /** Extract msgtype -> name from the dictionary XML (FIX42.xml etc.) on the classpath. */
    private static Map<String, String> loadMsgTypeNames(String resource) {
        Map<String, String> names = new HashMap<>();
        var classPathResource = new ClassPathResource(resource);
        if (!classPathResource.exists()) {
            return names;
        }
        try (InputStream in = classPathResource.getInputStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document doc = factory.newDocumentBuilder().parse(in);
            NodeList messages = doc.getElementsByTagName("message");
            for (int i = 0; i < messages.getLength(); i++) {
                Element el = (Element) messages.item(i);
                names.put(el.getAttribute("msgtype"), el.getAttribute("name"));
            }
        } catch (Exception ignored) {
            // names are a nice-to-have; tag decoding still works without them
        }
        return names;
    }

    private void loadCustomTags() {
        var resource = new ClassPathResource("custom-tags.properties");
        if (!resource.exists()) {
            return;
        }
        var props = new Properties();
        try (InputStream in = resource.getInputStream()) {
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                try {
                    customTagNames.put(Integer.parseInt(key.trim()), props.getProperty(key).trim());
                } catch (NumberFormatException ignored) {
                    // skip malformed entries
                }
            }
        } catch (IOException ignored) {
            // overrides are optional
        }
    }

    private <T> T safe(IOSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            return null;
        }
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws Exception;
    }
}

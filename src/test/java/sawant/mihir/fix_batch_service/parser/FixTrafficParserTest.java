package sawant.mihir.fix_batch_service.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixTrafficParserTest {

    private final FixTrafficParser parser = new FixTrafficParser();

    private static final String LOGON_IN =
            "DemoHost1 Tue Jun 17 09:15:00 2025 GMT   000001 DemoFixGateway(PARTNER1) [1001] < in > 8=FIX.4.2|9=81|35=A|34=1|49=NSEBRK01|98=0|108=30|141=Y|57=ADMIN|52=20250617-09:15:00|56=MGATE01|10=179|";
    private static final String LOGON_OUT =
            "DemoHost1 Tue Jun 17 09:15:00 2025 GMT   000001 DemoFixGateway(PARTNER1) [1001] < out > 8=FIX.4.2|9=85|35=A|34=1|49=MGATE01|98=0|108=30|141=Y|57=ADMIN|52=20250617-09:15:00.000|56=NSEBRK01|10=117|";
    private static final String EVENT =
            "DemoHost1 Tue Jun 17 09:15:00 2025 GMT   778438 DemoFixGateway(PARTNER1) [1001] < evt > Fix engine connection established";
    private static final String ORDER =
            "DemoHost1 Tue Jun 17 09:16:30 2025 GMT   000005 DemoFixGateway(PARTNER1) [1001] < in > 8=FIX.4.2|9=282|35=D|34=5|49=NSEBRK01|11=DMORDR0001-20250617|1=DMO0001|15=INR|21=3|22=5|37=2025061700000001|38=75|48=DEMOIN01|54=1|55=SUNPHARMA|40=2|44=23950.59|59=1|60=20250617-09:16:30|57=DEMOROUTE|115=DEMOGW|116=U1|9200=1|9300=0.27515703|9303=1254441286.12345678|52=20250617-09:16:30|56=MGATE01|10=195|";

    @Test
    void parsesIncomingLogonEnvelope() {
        ParsedLine p = parser.parse(LOGON_IN);
        assertNotNull(p);
        assertEquals(ParsedLine.Kind.MESSAGE, p.kind());
        assertEquals("IN", p.direction());
        assertEquals("DemoFixGateway(PARTNER1)", p.plugin());
        assertEquals(1001L, p.pid());
        assertEquals(2025, p.logTime().getYear());
        assertEquals(17, p.logTime().getDayOfMonth());
        assertEquals("FIX.4.2", p.fields().get(8));
        assertEquals("A", p.fields().get(35));
        assertEquals("NSEBRK01", p.fields().get(49));
        assertEquals("179", p.fields().get(10));
    }

    @Test
    void parsesOutgoingWithTrailingSpace() {
        ParsedLine p = parser.parse(LOGON_OUT);
        assertNotNull(p);
        assertEquals("OUT", p.direction());
        assertEquals("A", p.fields().get(35));
        assertEquals("MGATE01", p.fields().get(49));
        assertEquals("NSEBRK01", p.fields().get(56));
    }

    @Test
    void parsesEventLineWithoutFixBody() {
        ParsedLine p = parser.parse(EVENT);
        assertNotNull(p);
        assertEquals(ParsedLine.Kind.EVENT, p.kind());
        assertEquals("EVT", p.direction());
        assertNull(p.rawFix());
        assertEquals("Fix engine connection established", p.eventText());
    }

    @Test
    void parsesOrderWithCustomTags() {
        ParsedLine p = parser.parse(ORDER);
        assertNotNull(p);
        assertEquals("D", p.fields().get(35));
        assertEquals("DMORDR0001-20250617", p.fields().get(11));
        assertEquals("SUNPHARMA", p.fields().get(55));
        assertEquals("1", p.fields().get(9200));
        assertEquals("0.27515703", p.fields().get(9300));
        assertEquals("1254441286.12345678", p.fields().get(9303));
        assertEquals("DEMOROUTE", p.fields().get(57));
    }

    @Test
    void parsesRawFixLineWithoutEnvelope() {
        ParsedLine p = parser.parse("8=FIX.4.2|9=80|35=0|34=1|49=A|56=B|10=000|");
        assertNotNull(p);
        assertEquals("UNKNOWN", p.direction());
        assertEquals("0", p.fields().get(35));
    }

    @Test
    void parsesSohDelimitedBody() {
        ParsedLine p = parser.parse("8=FIX.4.29=8035=010=000");
        assertNotNull(p);
        assertEquals("0", p.fields().get(35));
    }

    @Test
    void returnsNullForBlankOrGarbage() {
        assertNull(parser.parse(""));
        assertNull(parser.parse("   "));
        assertNull(parser.parse("some random log line without fix"));
    }
}

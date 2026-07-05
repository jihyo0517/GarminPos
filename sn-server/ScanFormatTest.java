import java.time.ZoneOffset;
import java.util.Map;

public class ScanFormatTest {
    static int failures = 0;

    static void check(String name, Object actual, Object expected) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            System.out.println("FAIL " + name + ": expected <" + expected + "> got <" + actual + ">");
            failures++;
        } else {
            System.out.println("ok   " + name);
        }
    }

    public static void main(String[] args) {
        Map<String,String> m = ScanFormat.parseFormBody("sn=ABC123&ts=1000&device=pos1");
        check("parse.sn", m.get("sn"), "ABC123");
        check("parse.ts", m.get("ts"), "1000");
        check("parse.device", m.get("device"), "pos1");

        check("parse.urldecode", ScanFormat.parseFormBody("sn=A%20B").get("sn"), "A B");
        check("parse.empty", ScanFormat.parseFormBody("").size(), 0);
        check("parse.null", ScanFormat.parseFormBody(null).size(), 0);

        check("format.epoch0", ScanFormat.formatLine(0L, "SN1", ZoneOffset.UTC), "00:00:00  SN1");
        check("format.epoch", ScanFormat.formatLine(3661000L, "X", ZoneOffset.UTC), "01:01:01  X");

        check("datetime.epoch0", ScanFormat.formatDateTime(0L, ZoneOffset.UTC), "01-01 00:00:00");
        check("datetime.epoch", ScanFormat.formatDateTime(3661000L, ZoneOffset.UTC), "01-01 01:01:01");

        if (failures > 0) { System.out.println(failures + " FAILURES"); System.exit(1); }
        System.out.println("ALL PASS");
    }
}

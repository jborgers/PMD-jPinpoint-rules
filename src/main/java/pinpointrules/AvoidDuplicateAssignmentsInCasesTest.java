package nl.rabobank.perf.pinpointrules;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class AvoidDuplicateAssignmentsInCasesTest {
    private static final String DOCUMENT_ATTRIB_XMLNS_LINT001 = "urn:Bank:Internal:tech:xsd:lint-n.001.001.02";
    private static final String DOCUMENT_ATTRIB_XMLNS_LINT008 = "urn:Bank:Internal:tech:xsd:lint-n.008.001.01";
    private static final String GENERAL_CSTMR_TRF_INITN = "TRF";
    private static final String GENERAL_CSTMR_DBT_INITN = "DBT";

    protected Object createXmlStreamWriter(OutputStream outputStream, String type) {
        String xmlNsValue;
        String xmlNsExtValue;
        int schemaLocation;
        String startElement;
        String extra;
        String dummy = null;

        switch (type) {
            case "One":
                xmlNsValue = DOCUMENT_ATTRIB_XMLNS_LINT001;
                xmlNsExtValue = "same";
                schemaLocation = 1;
                startElement = GENERAL_CSTMR_TRF_INITN;
                extra = new String("alsoSame");
                extra = dummy;
                extra = toString();
                break;
            case "Two":
                xmlNsValue = DOCUMENT_ATTRIB_XMLNS_LINT008; //ok
                xmlNsExtValue = "same"; // violation
                schemaLocation = 1; //violation
                startElement = GENERAL_CSTMR_TRF_INITN; // violation
                startElement = GENERAL_CSTMR_DBT_INITN; //ok
                //dummy = GENERAL_CSTMR_TRF_INITN; // ok
                extra = new String("alsoSame"); //violation
                extra = dummy; // violation
                extra = toString(); // violation
                break;
            default:
                throw new IllegalArgumentException("Type " + type + " is not supported.");
        }
        //startElement = GENERAL_CSTMR_TRF_INITN;
        return startElement;
    }

    private void testCorrect() {
        BigDecimal bigDecimal;
        for (Object account : new Object[]{}) {
            switch (account.toString()) {
                case "One":
                    bigDecimal = new BigDecimal("1.1");
                    bigDecimal = bigDecimal.setScale(3, RoundingMode.UNNECESSARY);
                    bigDecimal = new BigDecimal("0").setScale(3,RoundingMode.UNNECESSARY);
                    break;
                case "Two":
                    bigDecimal = new BigDecimal("1.0");
                    bigDecimal = bigDecimal.setScale(0, RoundingMode.UNNECESSARY);
                    bigDecimal = new BigDecimal("0.0");
                    bigDecimal = bigDecimal.setScale(0, RoundingMode.UNNECESSARY);
                    break;
                default:
                    //fail("unexpected currency");
            }
        }
    }

    //false positive found in view-aab
    public String getSelector(String name) {
        String selector;
        switch (name) {
            case "bookTumbleDate":
                selector = "#yumCurrencyGroupBookday";
                break;
            case "currentTumbleDate":
                selector = "#yumCurrencyGroupActual";
                break;
            case "closedBookdayTumbleDate":
                selector = "#yumCurrencyGroupClav";
                break;
            case "currentBookdayTumbleDate":
                selector = "#yumCurrencyGroupItav";
                break;
            case "nextBookdayTumbleDate":
                selector = "#yumCurrencyGroupFwav";
                break;
            default:
                selector = "#yumCurrencyGroupBookday"; // should not violate, default may be identical, should be ignored
        }
        return selector;
    }
}

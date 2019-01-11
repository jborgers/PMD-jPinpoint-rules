package pinpointrules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PMDTest {
	private static final int SIZE = 10000000; // 10 million
	private StringBuffer buffer = new StringBuffer(); // should violate
    private StringBuffer buffer2; // should violate
    private java.lang.StringBuffer buffer3 = null; // should violate
    private AltStringBuffer asb; // OK

	/** Logger for PMDTest. */
	private static final Logger LOG = LoggerFactory.getLogger(PMDTest.class);

	private final java.util.logging.Logger logger; // OK, JIRA PCC-163

    private class AltStringBuffer { // OK
    }

	PMDTest() {
		logger = null;
	}

	/**
	 * @param args
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {
		System.out.println("Testing waste in Strings.");
		System.out.println("-Djava.lang.string.create.unique= "
				+ System.getProperty("java.lang.string.create.unique"));

		StringBuffer sb = new StringBuffer(); // should violate
		String[] wastingString = new String[SIZE];

		sb.append("n");

		int wasted = sb.capacity() - sb.length();
		System.out.println("capacity=" + sb.capacity() + ", wasted=" + wasted
				+ ", wasted >= INITIAL_SIZE: " + (wasted >= 16)
				+ ", (value.length >> 1) = " + (sb.capacity() >> 1)
				+ ", wasted >= \\1: " + (wasted >= sb.capacity() >> 1));
		boolean isCopy = (wasted > 768 || wasted >= 16
				&& wasted >= (sb.capacity() >> 1));
		System.out.println("share thus waste: " + !isCopy);

		// Now create these guys in a loop
		for (int i = 0; i < SIZE; i++) {
			sb = new StringBuffer();
			sb.append("n");
			wastingString[i] = sb.toString(); // 1 or 16 chars each, 15 waste,
												// (1,16) x 100M = (19MB, 300MB)
		}
		System.gc();
		Thread.sleep(1000);
		System.gc();
		Thread.sleep(1000);
		long totalMem = Runtime.getRuntime().totalMemory();
		long freeMem = Runtime.getRuntime().freeMemory();
		System.out.println("TotalMem= " + totalMem + ", FreeMem= " + freeMem
				+ ", usedMem [MB]= " + (totalMem - freeMem) / 1000000);
		LOG.debug(String.format("TotalMem= %s , FreeMem= %s", totalMem, freeMem)
				+ ", usedMem [MB]= "
				+ String.valueOf((totalMem - freeMem) / 1000000));
		LOG.debug(String.format("TotalMem= %s , FreeMem= %s", totalMem, freeMem));
		LOG.debug("buffer = {}", sb.toString());
	}


}

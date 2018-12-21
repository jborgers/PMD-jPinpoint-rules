package nl.rabobank.perf.pinpointrules;

import java.util.Arrays;
import java.util.List;

public class AvoidMultipleConcatStatementsTest {

	public void testMultipleConcatDefect() {
		String logStatement = "";
		List<String> values = Arrays
				.asList(new String[] { "tic", "tac", "toe" });

		logStatement += values.get(0);
		logStatement += values.get(1);
	}

	public void testMultipleConcatCorrect() {
		int log = 0;
		List<Integer> values = Arrays.asList(new Integer[] { 1, 2, 3 });
		log += values.get(0);
		log += values.get(1);
	}

	public void testMultipleConcatDefect2() {
		String logStatement = "";
		List<String> values = Arrays
				.asList(new String[] { "tic", "tac", "toe" });

		logStatement = logStatement + values.get(0);
		logStatement = logStatement + values.get(1);
	}

	public void testMultipleConcatCorrect2() {
		int log = 0;
		List<Integer> values = Arrays.asList(new Integer[] { 1, 2, 3 });
		log = log + values.get(0);
		log = log + values.get(1);
	}

	public void testMultipleConcatCorrect3() {
		int log = 0;
		int i = 0;
		while (i++ < 3) {
			List<Integer> values = Arrays.asList(new Integer[] { 1, 2, 3 });
			log = log + values.get(0);
			log = log + values.get(1);
		}
	}

}

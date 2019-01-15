package pinpointrules;

import java.util.Arrays;
import java.util.List;

public class AvoidConcatInAppendTest {
	public void testMultipleConcatDefect() {
		StringBuilder logStatement = new StringBuilder();
		List<String> values = Arrays
				.asList(new String[] { "tic", "tac", "toe" });

		logStatement.append(values.get(0) + values.get(1));
	}
	
	public void testMultipleConcatCorrect() {
		StringBuilder logStatement = new StringBuilder();
		List<String> values = Arrays
				.asList(new String[] { "tic", "tac", "toe" });

		logStatement.append(values.get(0)).append(values.get(1));
	}
}

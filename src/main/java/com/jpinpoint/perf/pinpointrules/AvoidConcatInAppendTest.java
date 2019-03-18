package com.jpinpoint.perf.pinpointrules;

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


    public void testConcatInAppendFalsePositive() {
        StringBuilder wrappedLine = new StringBuilder();
        String str = "bar";
        int offset = 1;
        int wrapLength = 2;
        wrappedLine.append(str, offset, wrapLength + offset); // false positive: + but no string concat
        // this still is false positive: wrappedLine.append(1 + 2);
    }
}

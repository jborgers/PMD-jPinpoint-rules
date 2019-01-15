package com.jpinpoint.perf.pinpointrules;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class RegexUsage {
	private static final Pattern pat = Pattern.compile("[;: ]"); // ok
	public static final String PATTERN = "^\\s*%s=(.*)";

	public static List createEventNameFromAction(String action) {
		Pattern p = Pattern.compile("(?=\\p{Lu})"); // violation, should not be inside method, make static final
		Pattern q = Pattern.compile("(" + action + "?=\\p{Lu})"); // ok, dynamic
		Pattern r = Pattern.compile(String.format(PATTERN, action)); // ok, dynamic
		Pattern s = Pattern.compile(PATTERN); // violation
		Pattern t = Pattern.compile(action); // dynamic, so ok (fix for JPCC-63)
		
		String[] actionSplit = p.split(action); // ok
		List<String[]> list = new ArrayList<String[]>();
		list.add(actionSplit);
		//list.add(splitString);
		//list.add(splitStringShort);
		//list.add(splitString2);
		System.out.println(""+q+r+s+t);
		return list;
	}


	public void createNameFromAction(String action) {
		Pattern p = Pattern.compile("(?=\\p{Lu})"); // should not be inside method, make static final
	}
}

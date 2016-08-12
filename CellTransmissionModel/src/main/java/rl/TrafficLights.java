package rl;

import java.util.HashMap;
import java.util.Map;

public class TrafficLights {
	private static Map<Integer, String> actionMap = new HashMap<>();
	private static int counter = 0;

	public static Map<Integer, String> getActionMap(int numberOfRamps) {

		// Create an alphabet to work with
		char[] alphabet = new char[] { 'R', 'G' };
		possibleStrings(numberOfRamps, alphabet, new StringBuilder(""));
		return actionMap;
	}

	private static void possibleStrings(int maxLength, char[] alphabet, StringBuilder curr) {

		// If the current string has reached it's maximum length
		if (curr.length() == maxLength) {
			actionMap.put(counter++, curr.toString());

			// Else add each letter from the alphabet to new strings and process
			// these new strings again
		} else {
			for (int i = 0; i < alphabet.length; i++) {
				curr.append(alphabet[i]);
				possibleStrings(maxLength, alphabet, curr);
				curr.deleteCharAt(curr.length() - 1);
			}
		}

	}

}

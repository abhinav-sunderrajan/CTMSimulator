package main;

public enum SimOptions {

	HAVE_VIZ, NO_VIZ, HAVE_ACC, NO_ACC;

	public static boolean getOption(SimOptions option) {

		boolean value = false;

		switch (option) {
		case HAVE_VIZ:
			value = true;
			break;
		case NO_VIZ:
			value = false;
			break;
		case HAVE_ACC:
			value = true;
			break;
		case NO_ACC:
			value = false;
			break;

		}
		return value;

	}
}

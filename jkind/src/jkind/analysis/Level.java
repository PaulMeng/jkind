package jkind.analysis;

public enum Level {
	ERROR, WARNING;

	@Override
	public String toString() {
		switch (this) {
		case ERROR:
			return "Error";
		case WARNING:
			return "Warning";
		default:
			throw new IllegalArgumentException();
		}
	}
}
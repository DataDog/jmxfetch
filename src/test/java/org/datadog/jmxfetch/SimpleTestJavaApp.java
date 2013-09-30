package org.datadog.jmxfetch;

public class SimpleTestJavaApp implements SimpleTestJavaAppMBean {

	private int should_be_100 = 100;
	private int should_be_1000 = 1000;
	private int should_be_counter = 0;
	public int getShouldBe100() {
		return should_be_100;
	}

	public int getShouldBe1000() {
		return should_be_1000;
	}
	
	public int getShouldBeCounter() {
		return should_be_counter;
	}
	
	public void incrementCounter(int inc) {
		should_be_counter += inc;
	}

	
}

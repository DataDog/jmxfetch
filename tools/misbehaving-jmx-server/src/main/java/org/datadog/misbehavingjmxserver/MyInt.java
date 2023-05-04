package org.datadog.misbehavingjmxserver;

public class MyInt implements MyIntMBean {
    private int counter;

    public MyInt() {
    }

    public synchronized int getCounter() {
        return counter;
    }

    public synchronized void setCounter(int value) {
        counter = value;
    }
}


package org.datadog.jmxfetch.util;

import java.lang.management.*;

public class Timer {
	/** Get CPU time in nanoseconds. */
	public static long getCpuTime( ) {
	    ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
	    return bean.isCurrentThreadCpuTimeSupported( ) ?
	        bean.getCurrentThreadCpuTime( ) : 0L;
	}

}

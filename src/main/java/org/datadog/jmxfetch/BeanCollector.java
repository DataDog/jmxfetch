package org.datadog.jmxfetch;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.Runtime;


public class BeanCollector implements Runnable{


    public BeanCollector(){

    }

    public void run(){
        Runtime rt = Runtime.getRuntime();
        long maxMemoryRt = rt.maxMemory();//max heap size
        long usedMemoryRt = rt.totalMemory();//total current heap size
        long heapFreeSize = rt.freeMemory();//amount of free memory in heap

        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long maxMemoryMx = memoryBean.getHeapMemoryUsage().getMax();//max heap size
        long usedMemoryMx = memoryBean.getHeapMemoryUsage().getUsed();//current memory used in heap
        long initMemoryMx = memoryBean.getHeapMemoryUsage().getInit();//initial heap size

        System.out.println("Initial Memory (MXBean) : " +  humanReadableByteCount(initMemoryMx,false));
        System.out.println("Max Memory (Runtime) : " +  humanReadableByteCount(maxMemoryRt,false));
        System.out.println("Max Memory (MXBean) : " +  humanReadableByteCount(maxMemoryMx,false));
        System.out.println("Current Heap Memory Total (Runtime) : " +  humanReadableByteCount(usedMemoryRt,false));
        System.out.println("Current Heap Memory In Use (MXBean) : " +  humanReadableByteCount(usedMemoryMx,false));
        System.out.println("Free Heap Memory (Runtime) : " +  humanReadableByteCount(heapFreeSize,false));

    }

    public static String humanReadableByteCount(long bytes, boolean si) {
		int unit = si ? 1000 : 1024;
		if (bytes < unit)
			return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
        //return String.format("%.4fMB",(float)bytes/1000000);
	}
}

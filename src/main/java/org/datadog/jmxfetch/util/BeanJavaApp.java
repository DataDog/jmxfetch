package org.datadog.jmxfetch.util;


public class BeanJavaApp implements BeanJavaAppMBean {
    
private int beanCount;
private int attributeCount;
private int metricCount;
private String instance;

    public BeanJavaApp(){
        beanCount = 0;
        attributeCount = 0;
        metricCount = 0;
        instance = "none";
    }

    public int getBeanCount(){
        return beanCount;
    }

    public int getAttributeCount(){
        return attributeCount;
    }

    public int getMetricCount(){
        return metricCount;
    }

    public String getInstance(){
        return instance;
    }

    public void setBeanCount(int count){
        beanCount = count;
    }

    public void setAttributeCount(int count){
        attributeCount = count;
    }

    public void setMetricCount(int count){
        metricCount = count;
    }

    public void setInstance(String name){
        instance = name;
    }

}

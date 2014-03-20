package org.datadog.jmxfetch;

import java.util.HashMap;
import java.util.LinkedList;

import dnl.utils.text.table.TextTable;

public class ConsoleReporter extends Reporter{

    private LinkedList<HashMap<String, Object>> metrics = new LinkedList<HashMap<String, Object>>();
  
    @Override
    protected void _sendMetricPoint(String metricName, double value, String[] tags) {
        String tagString = "[" + join(", ", tags) + "]";
        System.out.println(metricName + tagString + " - " + System.currentTimeMillis() / 1000 + " = " + value);

        HashMap<String, Object> m = new HashMap<String, Object>();

        m.put("name", metricName);
        m.put("value", value);
        m.put("tags", tags);
        metrics.add(m);
    }


    public LinkedList<HashMap<String, Object>> getMetrics() {
        LinkedList<HashMap<String, Object>> returned_metrics = metrics;
        metrics = new LinkedList<HashMap<String, Object>>();
        return returned_metrics;
    }

    public String join (String delim, String ... data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(data[i]);
            if (i >= data.length-1) {break;}
            sb.append(delim);
        }
        return sb.toString();
    }


    public void _printAttributesTable(LinkedList<JMXAttribute> attributes) {
        String[] columnNames = {                                       
                "Bean Name",                                          
                "Attribute Name",                                           
                "Attribute Type"
         };

        LinkedList<Object[]> dataList = new LinkedList<Object[]>();
        for(JMXAttribute a : attributes) {
            Object[] data = {a.beanName, a.attributeName, a.attribute.getType()};
            dataList.add(data);

        }

        Object[][] data = new Object[dataList.size()][3];
        dataList.toArray(data);
        TextTable tt = new TextTable(columnNames,data);   
        tt.setAddRowNumbering(true);     
        tt.setSort(0);   
        tt.printTable();          

    }


    @Override
    public void displaySummary(int maxReturned, String instanceName, 
            String action, LinkedList<JMXAttribute> collectedAttributes, LinkedList<JMXAttribute> limitedAttributes,
            LinkedList<JMXAttribute> nonMatchingAttributes) {
        
        if (action.equals(AppConfig.ACTION_LIST_COLLECTED)) {
            _printAttributesTable(collectedAttributes);
        } else if (action.equals(AppConfig.ACTION_LIST_LIMITED)) {
            _printAttributesTable(limitedAttributes);
        } else if (action.equals(AppConfig.ACTION_LIST_MATCHING)) {
            _printAttributesTable(collectedAttributes);
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println("       ------- METRIC LIMIT REACHED: ATTRIBUTES BELOW WON'T BE COLLECTED -------");    
            System.out.println();
            System.out.println();
            System.out.println();
            _printAttributesTable(limitedAttributes);
        } else if (action.equals(AppConfig.ACTION_LIST_NOT_MATCHING)) {
            _printAttributesTable(nonMatchingAttributes);
        } else if (action.equals(AppConfig.ACTION_LIST_EVERYTHING)) {
            _printAttributesTable(collectedAttributes);
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println("       ------- METRIC LIMIT REACHED: ATTRIBUTES BELOW WON'T BE COLLECTED -------");    
            System.out.println();
            System.out.println();
            System.out.println();
            _printAttributesTable(limitedAttributes);
            System.out.println();
            System.out.println();
            System.out.println();
            System.out.println("       ------- ATTRIBUTES BELOW ARE NOT MATCHING ANY CONFIGURATION -------");    
            System.out.println();
            System.out.println();
            System.out.println();
            _printAttributesTable(nonMatchingAttributes);
            
        }
  
        
    }

}

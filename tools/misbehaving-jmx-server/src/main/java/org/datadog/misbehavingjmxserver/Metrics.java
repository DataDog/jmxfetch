package org.datadog.misbehavingjmxserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Metrics implements MetricsMBean {
    public final String name;
    private final MetricsDAO metricsDAO;
    private final TabularDataSupport tabularData;

    public Metrics(final String name, final MetricsDAO metricsDAO) {
        this.name = name;
        this.metricsDAO = metricsDAO;

        try {
            String[] columnNames = { "Product Name", "In Stock", "Price" };
            Map<String, Object> row1 = Map.of(
                "Product Name", "Spam",
                "In Stock", 8,
                "Price", 4.99
            );
            Map<String, Object> row2 = Map.of(
                "Product Name", "Bananas",
                "In Stock", 10,
                "Price", 0.99
            );
    
            CompositeType productType;
            try {
                String[] itemDescriptions = { "The name of the product", "The number of items in stock", "The price of the product" };
                OpenType<?>[] itemTypes = { SimpleType.STRING, SimpleType.INTEGER, SimpleType.DOUBLE };
                productType = new CompositeType("MyProductType", "A composite type with three items",
                        columnNames, itemDescriptions, itemTypes);
            } catch (OpenDataException e) {
                throw new RuntimeException("Failed to create product type", e);
            }
    
            TabularType storeType = new TabularType("MyStore", "A store type with MyProductType rows", productType,
                    new String[] { "Product Name" });
    
            List<CompositeData> products = new ArrayList<>();
            try {
                products.add(new CompositeDataSupport(productType, row1));
                products.add(new CompositeDataSupport(productType, row2));
            } catch (OpenDataException e) {
                throw new RuntimeException("Failed to create product", e);
            }
    
            this.tabularData = new TabularDataSupport(storeType);
            for (CompositeData row : products) {
                tabularData.put(row);
            }
        } catch (OpenDataException e) {
            log.error("Error creating bean: ", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Number getNumberValue() {
        return this.metricsDAO.getNumberValue();
    }

    @Override
    public Double getDoubleValue() {
        return this.metricsDAO.getDoubleValue();
    }

    @Override
    public Float getFloatValue() {
        return this.metricsDAO.getFloatValue();
    }

    @Override
    public Boolean getBooleanValue() {
        return this.metricsDAO.getBooleanValue();
    }

    @Override
    public TabularData getTabularData() {
        return this.tabularData;
    }
    @Override
    public TabularDataSupport getTabularDataSupport() {
        return this.tabularData;
    }
}

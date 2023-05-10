package org.datadog.jmxfetch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;

import static org.mockito.Mockito.spy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

final class ConfigUtil {
    public static Path writeConfigYamlToTemp(String content, String yamlName) throws IOException {
        Path tempDirectory = Files.createTempDirectory("temp-dir");
        // Create a temporary file within the temporary directory
        Path tempFile = Files.createTempFile(tempDirectory, yamlName, ".yaml");

        // Write the contents of the file to the temporary file
        BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()));
        writer.write(content);
        writer.close();

        return tempFile;
    }

    public static String concatWithNewlines(String... lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append(System.lineSeparator());
        }
        return sb.toString();
    }
}


/* 
 * Similar to TestCommon which assumes a local JVM connection,
 * this provides basic app initialization for remote JMX tests
 */
public class TestRemoteAppCommon {
    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("Test Integration");
    AppConfig appConfig = spy(AppConfig.builder().build());
    App app;

    /*
     * Init JMXFetch with the given YAML configuration template 
     * The configuration can be specified as a yaml literal with each arg
     * representing one line of the Yaml file
    */
    protected void initApplicationWithYamlLines(String... yamlLines)
            throws IOException {
        String yamlConfig = ConfigUtil.concatWithNewlines(yamlLines);
        Path tempFile = ConfigUtil.writeConfigYamlToTemp(yamlConfig, "config");

        String confdDirectory = tempFile.getParent().toString();
        String yamlFileName = tempFile.getFileName().toString();
        
        List<String> params = new ArrayList<String>();
        params.add("--reporter");
        params.add("console");

        if (confdDirectory != null) {
            params.add("-c");
            params.add(yamlFileName);
            params.add("--conf_directory");
            params.add(confdDirectory);
            params.add("collect");
        }
        new JCommander(appConfig, params.toArray(new String[params.size()]));
        this.app = new App(appConfig);
        app.init(false);
    }

}

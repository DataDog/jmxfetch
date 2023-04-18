package org.datadog.jmxfetch;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;

import static org.mockito.Mockito.spy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import java.text.MessageFormat;

final class FileUtil {
    public static String readUrlContentsToString(URL url) throws IOException {
        StringBuilder sb = new StringBuilder();
        InputStream inputStream = url.openStream();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } finally {
            inputStream.close();
        }
        return sb.toString();
    }
}


public class TestIntegration {
    protected static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("Test Integration");
    AppConfig appConfig = spy(AppConfig.builder().build());
    App app;


    /** Init JMXFetch with the given YAML configuration file. */
    protected void initApplication(String resourceName, String host, int port)
            throws IOException {

        URL yamlURL =
                Thread.currentThread().getContextClassLoader().getResource(resourceName);
        String yamlContents = FileUtil.readUrlContentsToString(yamlURL);
        String templatedContent = MessageFormat.format(yamlContents, host, port);

        Path tempDirectory = Files.createTempDirectory("temp-dir");
        
        String yamlName = resourceName.substring(0, resourceName.indexOf(".", 0));

        // Create a temporary file within the temporary directory
        Path tempFile = Files.createTempFile(tempDirectory, yamlName, ".yaml");

        // Write the contents of the file to the temporary file
        BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile.toFile()));
        writer.write(templatedContent);
        writer.close();

        String confdDirectory = tempDirectory.toString();
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

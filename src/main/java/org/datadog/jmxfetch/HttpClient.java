package org.datadog.jmxfetch;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.cert.X509Certificate;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;



@Slf4j
public class HttpClient {
    private String token;
    private TrustManager[] dummyTrustManager;
    private SSLContext sc;
    private String host;
    private int port;

    private static final String USER_AGENT = "Datadog/JMXFetch";
    // Per javadocs, this is the only version that all compliant JVMs are required to support
    // I found 'TLS' was the appropriate protocol (will use the latest support TLSv? version)
    // rather than specifically 'TLSv1' as the docs recommend
    // https://docs.oracle.com/javase/7/docs/api/javax/net/ssl/SSLContext.html
    private static final String minRequiredSSLProtocol = "TLS";

    public static class HttpResponse {
        private int responseCode;
        private String responseBody;

        /** HttpResponse constructor for provided response code and response string. */
        public HttpResponse(int responseCode, String responseBody) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        /** HttpResponse constructor for provided response code and response stream. */
        public HttpResponse(int responseCode, InputStreamReader responseStream) throws IOException {
            String inputLine;
            BufferedReader in = new BufferedReader(responseStream);
            StringBuffer responseBuilder = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                responseBuilder.append(inputLine);
            }
            in.close();

            this.responseCode = responseCode;
            this.responseBody = responseBuilder.toString();
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }

        public int getResponseCode() {
            return this.responseCode;
        }

        public String getResponseBody() {
            return this.responseBody;
        }

        public boolean isResponse2xx() {
            return (responseCode >= 200 && responseCode < 300);
        }
    }

    /** HttpClient constructor to provided host and port. */
    public HttpClient(String host, int port, boolean verify) {
        this.host = host;
        this.port = port;
        this.token = System.getenv("SESSION_TOKEN");

        if (!verify) {

            // Add Bouncy Castle FIPS provider
            Security.addProvider(new BouncyCastleFipsProvider());


            // You can now use this truststore for making HTTPS requests
            try {
                // Create a new FIPS-compliant truststore
                KeyStore truststore = KeyStore.getInstance("BCFKS", "BCFIPS");
                truststore.load(null, null);
                String pemEncodedCert = System.getenv("IPC_PEM");

                // Import the PEM-encoded self-signed certificate
                CertificateFactory certFactory = CertificateFactory.getInstance("X.509", "BCFIPS");
                ByteArrayInputStream inputStream = new ByteArrayInputStream(pemEncodedCert.getBytes(StandardCharsets.UTF_8));
                java.security.cert.Certificate cert = certFactory.generateCertificate(inputStream);

                // Add the self-signed certificate to the truststore
                truststore.setCertificateEntry("control_server_cert", cert);

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX", "BCFIPS");
                trustManagerFactory.init(truststore);

                SSLContext sslContext = SSLContext.getInstance("TLS", "BCFIPS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
                log.info("Successfully installed dummyTrustManager");
            } catch (Exception e) {
                log.error("Error installing dummyTrustManager. Communications with the Agent will "
                        + "be affected. Agent Status will be unreliable and AutoDiscovery of new "
                        + "JMX checks will fail. error: ", e);
                log.debug("session token unavailable - not setting");
                this.token = "";
            }
        }
    }

    /** only supports json bodies for now. */
    public HttpResponse request(String method, String body, String path) {
        HttpClient.HttpResponse response = new HttpClient.HttpResponse(0, "");
        try {
            String url = "https://" + host + ":" + port + "/" + path;
            log.debug("attempting to connect to: " + url);
            log.debug("with body: " + body);

            URL uri = new URL(url);
            HttpsURLConnection con = (HttpsURLConnection) uri.openConnection();

            // add request header
            con.setRequestMethod(method.toUpperCase());
            con.setRequestProperty("Authorization", "Bearer " + this.token);
            con.setRequestProperty("User-Agent", USER_AGENT);
            if (method.toUpperCase().equals("GET")) {
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            } else {
                con.setRequestProperty("Content-Type", "application/json");
                con.setDoOutput(true);
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.writeBytes(body);
                wr.flush();
                wr.close();
            }

            int responseCode = con.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                log.debug("HTTP error stream: " + con.getErrorStream());
                response.setResponseCode(responseCode);
            } else {
                response =
                        new HttpClient.HttpResponse(
                                responseCode, new InputStreamReader(con.getInputStream()));
            }

        } catch (Exception e) {
            log.info("problem creating http request: " + e.toString());
        }

        return response;
    }
}

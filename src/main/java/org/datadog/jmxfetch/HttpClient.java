package org.datadog.jmxfetch;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpClient {
    private String token;
    private TrustManager[] dummyTrustManager;
    private SSLContext sc;
    private String host;
    private int port;

    private static final String USER_AGENT = "Datadog/JMXFetch";
    private static final Logger LOGGER = Logger.getLogger(Status.class.getName());

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
            try {
                dummyTrustManager =
                        new TrustManager[] {
                            new X509TrustManager() {
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                    return null;
                                }

                                public void checkClientTrusted(
                                        java.security.cert.X509Certificate[] certs,
                                        String authType) {}

                                public void checkServerTrusted(
                                        java.security.cert.X509Certificate[] certs,
                                        String authType) {}
                            }
                        };
                sc = SSLContext.getInstance("SSL");
                sc.init(null, this.dummyTrustManager, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            } catch (Exception e) {
                LOGGER.debug("session token unavailable - not setting");
                this.token = "";
            }
        }
    }

    /** only supports json bodies for now. */
    public HttpResponse request(String method, String body, String path) {
        HttpClient.HttpResponse response = new HttpClient.HttpResponse(0, "");
        try {
            String url = "https://" + host + ":" + port + "/" + path;
            LOGGER.debug("attempting to connect to: " + url);
            LOGGER.debug("with body: " + body);

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
                LOGGER.debug("HTTP error stream: " + con.getErrorStream());
                response.setResponseCode(responseCode);
            } else {
                response =
                        new HttpClient.HttpResponse(
                                responseCode, new InputStreamReader(con.getInputStream()));
            }

        } catch (Exception e) {
            LOGGER.info("problem creating http request: " + e.toString());
        }

        return response;
    }
}

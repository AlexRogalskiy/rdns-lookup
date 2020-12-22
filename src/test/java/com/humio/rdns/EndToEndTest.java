package com.humio.rdns;

import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class EndToEndTest {
    public static final String HUMIO_IMAGE = "humio/humio:1.18.1";

    @Test
    public void basic() throws IOException {
        basicTest("1.1.1.1");
    }

    @Test
    public void file() throws IOException {
        String path = Objects.requireNonNull(EndToEndTest.class.getClassLoader().getResource("com/humio/rdns/ipranges.txt")).getPath();
        basicTest("@" + path);
    }

    private void basicTest(String range) throws IOException {
        //noinspection rawtypes
        GenericContainer humio = new GenericContainer(HUMIO_IMAGE)
                .withExposedPorts(8080);

        humio.start();

        Integer humioPort = humio.getMappedPort(8080);

        int returnCode = new CommandLine(new StaticRdnsLookup())
                .execute("http://localhost:" + humioPort, "sandbox", "rdns.csv", range);

        assertEquals(0, returnCode);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet("http://localhost:" + humioPort + "/api/v1/repositories/sandbox/files/rdns.csv");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertEquals("ip,hostname\r\n1.1.1.1,one.one.one.one\r\n", EntityUtils.toString(response.getEntity()));
            }
        }
    }

    @Test
    public void auth() throws IOException {
        //noinspection rawtypes
        GenericContainer humio = new GenericContainer(HUMIO_IMAGE)
                .withEnv("AUTHENTICATION_METHOD", "single-user")
                .withEnv("SINGLE_USER_PASSWORD", "foobar")
                .withExposedPorts(8080);

        humio.start();

        File adminTokenFile = Files.createTempFile("rdns", "e2e").toFile();
        adminTokenFile.deleteOnExit();
        humio.copyFileFromContainer("/data/humio-data/local-admin-token.txt", adminTokenFile.getPath());
        String token = Files.readAllLines(adminTokenFile.toPath()).get(0);

        Integer humioPort = humio.getMappedPort(8080);

        int returnCode = new CommandLine(new StaticRdnsLookup())
                .execute("http://localhost:" + humioPort, "sandbox", "rdns.csv", "1.1.1.1", "-p", token);

        assertEquals(0, returnCode);

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet("http://localhost:" + humioPort + "/api/v1/repositories/sandbox/files/rdns.csv");
            request.setHeader(HttpHeaders.AUTHORIZATION, Utils.basicAuthHeader("root", token));
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                assertEquals(200, response.getStatusLine().getStatusCode());
                assertEquals("ip,hostname\r\n1.1.1.1,one.one.one.one\r\n", EntityUtils.toString(response.getEntity()));
            }
        }
    }

    private static class StaticRdnsLookup extends RdnsLookup {
        @Override
        protected String getCanonicalHostName(InetAddress address) {
            try {
                assertEquals(InetAddress.getByName("1.1.1.1"), address);
                return "one.one.one.one";
            } catch (UnknownHostException e) {
                return fail(e);
            }
        }
    }
}

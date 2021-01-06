package com.humio.rdns;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

@Command(name = "rdns-lookup", versionProvider = RdnsLookup.ManifestVersionProvider.class,
    description = "Does reverse DNS queries against a range of IP addresses and uploads the results " +
            "to a Humio cluster as a lookup file",
    footer = "\n" +
            "Ranges can either be specified either as\n" +
            " - a single address, i.e. \"192.168.0.1\"\n" +
            " - CIDR notation, i.e. \"192.168.0.0/24\".\n" +
            " - a range, i.e. \"192.168.0.0-192.168.0.10\". The range is inclusive.\n" +
            " - @file, i.e. \"@path/to/file\". The file will be read and each line will be\n" +
            "   interpreted as one of the above.\n" +
            "\n" +
            "Both IPv4 and IPv6 addresses are supported. \n" +
            "\n" +
            "Example:\n" +
            "\n" +
            "Do RDNS queries against 192.158.0.0 to 192.158.0.255 (inclusive) and any ranges\n" +
            "found in the \"ranges.txt\" file, and upload the results to a file called\n" +
            "\"rdns.csv\" in the repo called \"myRepo\" on the EU Humio cloud:\n" +
            "\n" +
            "  rdns-lookup https://cloud.humio.com myRepo rdns.csv 192.168.0.0/24 @ranges.txt\n" +
            "    --pass L87v9i4J5S6S4i7xsGlw")
public class RdnsLookup implements Callable<Integer> {
    public static void main(String[] args) {
        System.exit(new CommandLine(new RdnsLookup()).execute(args));
    }

    @Parameters(index = "0", description = "URL to Humio cluster to upload to")
    private URI url;

    @Parameters(index = "1", description = "The repository in the cluster to upload to")
    private String repo;

    @Parameters(index = "2", description = "The filename in the repository to upload to")
    private String filename;

    @Parameters(index = "3..*", description = "The IP ranges for which to perform reverse DNS", arity = "1..*")
    private String[] ranges;

    @Option(names = {"-u", "--user"}, description = "Username to use when authenticating")
    private String username;

    @Option(names = {"-p", "--pass"}, description = "Password to use when authenticating (or token)", arity = "0..1", interactive = true)
    private String password;

    @SuppressWarnings("unused")
    @Option(names = {"-V", "--version"}, versionHelp = true, description = "Display version info")
    boolean versionInfoRequested;

    @SuppressWarnings("unused")
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Display this help message")
    boolean usageHelpRequested;

    @Override
    public Integer call() throws Exception {
        if (!filename.endsWith(".csv")) {
            System.err.println("Filename must end with .csv");
            return -1;
        }

        if (username != null && password == null) {
            System.err.println("Password must be set if username is set");
            return -1;
        }

        List<InetAddressRange> parsedRanges = new ArrayList<>();

        for (String range : ranges) {
            if (range.startsWith("@")) {
                String path = range.substring(1);

                try {
                    for (String line : Files.readAllLines(Paths.get(path))) {
                        parsedRanges.add(InetAddressRange.parse(line));
                    }
                } catch (IllegalArgumentException | UnknownHostException e) {
                    System.err.println("Error parsing range in '" +  path + "': " + e.getMessage());
                    return -1;
                }
            } else {
                try {
                    parsedRanges.add(InetAddressRange.parse(range));
                } catch (IllegalArgumentException | UnknownHostException e) {
                    System.err.println("Error parsing range '" + range + "': " + e.getMessage());
                    return -1;
                }
            }
        }

        String[] headers = { "ip", "hostname"};
        CSVFormat format = CSVFormat.DEFAULT.withHeader(headers);

        File temp = Files.createTempFile("rdns", "csv").toFile();
        temp.deleteOnExit();

        try (FileWriter out = new FileWriter(temp);
             CSVPrinter printer = new CSVPrinter(out, format)) {
            for (InetAddressRange range : parsedRanges) {
                for (InetAddress address : range) {
                    String hostAddress = address.getHostAddress();
                    String hostName = getCanonicalHostName(address);

                    if (!hostAddress.equals(hostName)) {
                        printer.printRecord(hostAddress, hostName);
                    }
                }
            }
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            URI urlWithSlash = url.getPath().endsWith("/")
                    ? url
                    : URI.create(url + "/");

            URI resolve = urlWithSlash.resolve("api/v1/repositories/" + repo + "/files");
            HttpPost request = new HttpPost(resolve);

            if (password != null) {
                String user = username == null ? "rdns" : username;
                request.setHeader(HttpHeaders.AUTHORIZATION, Utils.basicAuthHeader(user, password));
            }

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addBinaryBody("file", temp, ContentType.create("text/csv"), filename);
            request.setEntity(builder.build());

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode < 200 || statusCode >= 300) {
                    System.err.println("Failed uploading results: " + EntityUtils.toString(response.getEntity()));
                    return -1;
                }
            }
        }

        return 0;
    }

    protected String getCanonicalHostName(InetAddress address) {
        return address.getCanonicalHostName();
    }

    /** Version provider that reads the version added by maven in the manifest */
    static class ManifestVersionProvider implements CommandLine.IVersionProvider {
        public String[] getVersion() throws Exception {
            Enumeration<URL> resources = CommandLine.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    Manifest manifest = new Manifest(url.openStream());
                    if (isApplicableManifest(manifest)) {
                        Attributes attr = manifest.getMainAttributes();
                        return new String[] { get(attr, "Implementation-Title") + " " +
                                get(attr, "Implementation-Version") };
                    }
                } catch (IOException ex) {
                    return new String[] { "Unable to read from " + url + ": " + ex };
                }
            }
            return new String[0];
        }

        private boolean isApplicableManifest(Manifest manifest) {
            Attributes attributes = manifest.getMainAttributes();
            return "rdns-lookup".equals(get(attributes, "Implementation-Title"));
        }

        private static Object get(Attributes attributes, String key) {
            return attributes.get(new Attributes.Name(key));
        }
    }
}


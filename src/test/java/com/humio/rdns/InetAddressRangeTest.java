package com.humio.rdns;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.fail;

class InetAddressRangeTest {
    private static InetAddress address(String address) {
        try {
            return InetAddress.getByName(address);
        } catch (UnknownHostException e) {
            return fail(e);
        }
    }

    private void assertRangeMatches(InetAddressRange range, String... addresses) {
        List<InetAddress> inetAddresses = Arrays.stream(addresses)
                .map(InetAddressRangeTest::address)
                .collect(Collectors.toList());

        assertIterableEquals(inetAddresses, range);
    }

    @Test
    public void inclusiveLow() {
        InetAddressRange range = InetAddressRange.inclusive(
                address("0.0.0.0"),
                address("0.0.0.2"));

        assertRangeMatches(range, "0.0.0.0", "0.0.0.1", "0.0.0.2");
    }

    @Test
    public void inclusiveLow6() {
        InetAddressRange range = InetAddressRange.inclusive(
                address("::"),
                address("::2"));

        assertRangeMatches(range, "::", "::1", "::2");
    }

    @Test
    public void inclusiveMid() {
        InetAddressRange range = InetAddressRange.inclusive(
                address("16.32.64.128"),
                address("16.32.64.130"));

        assertRangeMatches(range, "16.32.64.128", "16.32.64.129", "16.32.64.130");
    }

    @Test
    public void inclusiveMid6() {
        InetAddressRange range = InetAddressRange.inclusive(
                address("2001:db8:85a3:8d3:1319:8a2e:370:7349"),
                address("2001:db8:85a3:8d3:1319:8a2e:370:734b"));

        assertRangeMatches(range,
                "2001:db8:85a3:8d3:1319:8a2e:370:7349",
                "2001:db8:85a3:8d3:1319:8a2e:370:734a",
                "2001:db8:85a3:8d3:1319:8a2e:370:734b");
    }

    @Test
    public void inclusiveHigh() throws UnknownHostException {
        InetAddressRange range = InetAddressRange.inclusive(
                address("255.255.255.254"),
                InetAddress.getByName("255.255.255.255"));

        assertRangeMatches(range, "255.255.255.254", "255.255.255.255");
    }

    @Test
    public void inclusiveHigh6() {
        InetAddressRange range = InetAddressRange.inclusive(
                address("ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe"),
                address("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff"));

        assertRangeMatches(range, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffe", "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff");
    }

    @Test
    public void parseRange() throws UnknownHostException {
        InetAddressRange range = InetAddressRange.parse("1.2.3.4-1.2.3.5");
        assertRangeMatches(range, "1.2.3.4", "1.2.3.5");
    }

    @Test
    public void parseRange6() throws UnknownHostException {
        InetAddressRange range = InetAddressRange.parse(
                "2001:db8:85a3:8d3:1319:8a2e:370:7349-2001:db8:85a3:8d3:1319:8a2e:370:734b");

        assertRangeMatches(range,
                "2001:db8:85a3:8d3:1319:8a2e:370:7349",
                "2001:db8:85a3:8d3:1319:8a2e:370:734a",
                "2001:db8:85a3:8d3:1319:8a2e:370:734b");
    }

    @Test
    public void parseCidr() throws UnknownHostException {
        InetAddressRange range = InetAddressRange.parse("1.2.3.4/30");
        assertRangeMatches(range, "1.2.3.4", "1.2.3.5", "1.2.3.6", "1.2.3.7");
    }

    @Test
    public void parseCidr6() throws UnknownHostException {
        InetAddressRange range = InetAddressRange.parse("2001:db8:85a3:8d3:1319:8a2e:370:7349/127");
        assertRangeMatches(range, "2001:db8:85a3:8d3:1319:8a2e:370:7348", "2001:db8:85a3:8d3:1319:8a2e:370:7349");
    }

    @Test
    public void parseAddr() throws UnknownHostException {
        InetAddressRange range = InetAddressRange.parse("1.2.3.4");
        assertRangeMatches(range, "1.2.3.4");
    }

    @Test
    public void parseAddr6() throws UnknownHostException {
        InetAddressRange range = InetAddressRange.parse("2001:db8:85a3:8d3:1319:8a2e:370:7349");
        assertRangeMatches(range, "2001:db8:85a3:8d3:1319:8a2e:370:7349");
    }
}
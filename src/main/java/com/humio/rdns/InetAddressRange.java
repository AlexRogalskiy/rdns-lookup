package com.humio.rdns;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

class InetAddressRange implements Iterable<InetAddress> {
    private final int size;
    private final BigInteger begin, end;

    private InetAddressRange(int size, BigInteger begin, BigInteger end) {
        this.size = size;
        this.begin = begin;
        this.end = end;
    }

    private InetAddress toAddress(BigInteger integer) {
        byte[] intBytes = integer.toByteArray();
        byte[] addrBytes = new byte[size];

        // Copy the integer bytes, keeping in mind that:
        // - intBytes contains a sign bit. This is always zero because it is positive, but means it may be 1 larger than
        //   size.
        // - intBytes may be smaller than the size as it doesn't pad zeros.

        System.arraycopy(
                intBytes,
                Math.max(intBytes.length - size, 0),
                addrBytes,
                Math.max(size - intBytes.length, 0),
                Math.min(size, intBytes.length));

        try {
            return InetAddress.getByAddress(addrBytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Size is of illegal length", e);
        }
    }

    @Override
    public Iterator<InetAddress> iterator() {
        return new Iterator<InetAddress>() {
            BigInteger current = begin;

            @Override
            public boolean hasNext() {
                return current.compareTo(end) < 0;
            }

            @Override
            public InetAddress next() {
                InetAddress address = toAddress(current);
                current = current.add(BigInteger.ONE);
                return address;
            }
        };
    }

    public static InetAddressRange inclusive(InetAddress begin, InetAddress end) {
        byte[] beginBytes = begin.getAddress();
        byte[] endBytes = end.getAddress();

        if (beginBytes.length != endBytes.length) {
            throw new IllegalArgumentException("Addresses have different sizes");
        }

        return new InetAddressRange(
                beginBytes.length,
                new BigInteger(1, beginBytes),
                new BigInteger(1, endBytes).add(BigInteger.ONE)
        );
    }

    public static InetAddressRange cidr(InetAddress address, int bits) {
        byte[] addressBytes = address.getAddress();

        if (bits < 0 || bits > addressBytes.length * 8) {
            throw new IllegalArgumentException("Bits in CIDR notation must be non-negative and not be larger than" +
                    " the IP address size");
        }

        int freeBits = addressBytes.length * 8 - bits;
        BigInteger addressInt = new BigInteger(1, addressBytes);

        BigInteger begin = addressInt.shiftRight(freeBits).shiftLeft(freeBits);
        BigInteger end = begin.add(BigInteger.ONE.shiftLeft(freeBits));

        return new InetAddressRange(addressBytes.length, begin, end);
    }

    public static InetAddressRange parse(String range) throws UnknownHostException {
        int cidr = range.lastIndexOf('/');
        if (cidr != -1) {
            return cidr(
                    InetAddress.getByName(range.substring(0, cidr)),
                    Integer.parseInt(range.substring(cidr + 1)));
        }

        int split = range.indexOf('-');
        if (split != -1) {
            return inclusive(
                    InetAddress.getByName(range.substring(0, split)),
                    InetAddress.getByName(range.substring(split + 1)));
        }

        InetAddress address = InetAddress.getByName(range);
        return inclusive(address, address);
    }
}

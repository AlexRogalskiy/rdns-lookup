rdns-lookup
===========

`rdns-lookup` is a Java application for doing reverse DNS requests against a range of IP addresses
and uploading the results as a lookup file in [Humio](https://www.humio.com/).

In general, reverse DNS cannot be trusted. The owner of an IP may be able to modify the results of a
reverse DNS query to an arbitrary value, but for something like an internal network it may be
possible to know that reverse DNS requests are truthful. In addition, Humio may not be running on
the same network as the one we wish to perform reverse DNS in. This application this issue by 
building a reverse DNS lookup locally and uploading it to Humio.

Building
--------

Build using `mvn package -DskipTests`. The resulting uber-jar is located at
`target/rdns-lookup-$VERSION-jar-with-dependencies.jar`.

Usage
-----

```
Usage: rdns-lookup [-hV] [-p[=<password>]] [-u=<username>] <url> <repo>
                   <filename> [<ranges>...]
Does reverse DNS queries against a range of IP addresses and uploads the
results to a Humio cluster as a lookup file
      <url>                 URL to Humio cluster to upload to
      <repo>                The repository in the cluster to upload to
      <filename>            The filename in the repository to upload to
      [<ranges>...]         The IP ranges for which to perform reverse DNS
  -h, --help                Display this help message
  -p, --pass[=<password>]   Password to use when authenticating (or token)
  -u, --user=<username>     Username to use when authenticating
  -V, --version             Display version info

Ranges can either be specified either as
 - a single address, i.e. "192.168.0.1"
 - CIDR notation, i.e. "192.168.0.0/24".
 - a range, i.e. "192.168.0.0-192.168.0.10". The range is inclusive.
 - @file, i.e. "@path/to/file". The file will be read and each line will be
   interpreted as one of the above.

Both IPv4 and IPv6 addresses are supported.

Example:

Do RDNS queries against 192.158.0.0 to 192.158.0.255 (inclusive) and any ranges
found in the "ranges.txt" file, and upload the results to a file called
"rdns.csv" in the repo called "myRepo" on the EU Humio cloud:

  rdns-lookup https://cloud.humio.com myRepo rdns.csv 192.168.0.0/24 @ranges.txt
    --pass L87v9i4J5S6S4i7xsGlw
```
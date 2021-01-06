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

`java -jar rdns-lookup.jar [-hV] [-t[=<token>]] <url> <repo> <filename> [<ranges>...]`

| Parameter          | Usage                                          |
| ------------------ | ---------------------------------------------- |
| `<url>`            | URL to Humio cluster to upload to              |
| `<repo>`           | Repository in cluster to upload to             |
| `<filename>`       | File in repository to upload to                |
| `<ranges>`         | IP ranges to scan                              |
| `-t`, `--token`    | API token to authenticate with                 |
| `-h`, `--help`     | Displays help                                  |
| `-V`, `--version`  | Displays version                               |

If `-t` or `--token` is not set, then the environment variable `HUMIO_TOKEN` is used as API token.
If that is not set either, then no API token will be included in the request.

Ranges can either be specified either as
- a single address, i.e. `192.168.0.1`
- CIDR notation, i.e. `192.168.0.0/24`.
- a range, i.e. `192.168.0.0-192.168.0.10`. The range is inclusive.
- @file, i.e. `@path/to/file`. The file will be read and each line will be
  interpreted as one of the above.

Both IPv4 and IPv6 addresses are supported.

Example
-------

Do RDNS queries against 192.158.0.0 to 192.158.0.255 (inclusive) and upload the results to a file
called `rdns.csv` in the repo called `myRepo` on the EU Humio cloud:

`java -jar rdns-lookup.jar https://cloud.humio.com myRepo rdns.csv 192.168.0.0/24`
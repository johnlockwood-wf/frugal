# Frugal - Cross language test suite

Used to verify that each supported transport and protocol works as expected
across all supported languages.

## To Run:
##### In Skynet:

Push to any Smithy enabled Frugal fork.  Skynet will execute tests with both
Frugal and Thrift code generation, using the current Frugal branch (not the
latest release).

##### Locally:
Setup [skynet-cli](https://github.com/workiva/skynet-cli) and run `skynet run
cross-local`. The 'cross' configuration (which is executed in Skynet) uses
Smithy build artifacts whereas the 'cross-local' configuration does not
require these to execute the test suite.  'cross-local' will only execute
tests using Frugal code generation.

### General Overview
The major components of this test suite include:
* frugalTest.frugal IDL file
* test definitions in tests.json / tests_gen_with_thrift.json
* python cross runner
* language specific clients/servers

##### frugalTest.frugal
The IDL file from which test cases are generated.

The FrugalTest service defines every type of value (int, string, map, list,
map of maps, etc.) that could be sent across the wire.  Please contact Jacob
Moss (jacob.moss@workiva.com) if you believe there are additional test cases
that should be added.

The Events scope is used for verifying pub/sub.

##### tests.json / tests_gen_with_thrift.json
These json files contain a listing of each supported language, client,
server, transport, and protocol, as well as the bash command required to run
a configuration.  As the names suggest, tests.json is for testing Frugal
generated code and tests_gen_with_thrift.json is used with --gen_with_thrift
generation option.

##### Python cross runner
The python cross runner is recycled from Thrift (with very minor
modifications) and is responsible for parsing the json test definitions,
determining the valid client/server pairs, running each pair with a unique
subject, and recording the results.  Successful tests are tar'ed into
successful_tests.tar.gz. Failures are added to unexpected_failures.txt (both
client and server side logs).

##### Language specific clients/servers
Each client/server:

* accepts the following flags:
  * port (used as the NATS subject, 5 digit random number when called by the
  cross runner)
    * defaults to 9090 for manual testing
  * transport
    * defaults to stateless (where supported, otherwise http)
  * protocol
    * defaults to binary
* calls/handles every case defined in the frugalTest service
* implements middleware (where supported) to
  * log
    * name of each RPC
    * arguments each RPC is called with
    * return value of the RPC
  * verify middleware works as expected
* throws a non-zero exit code when an error is encountered

For publish/subscribe testing, servers are set up as a subscriber and publish
an acknowledgement upon receipt of a publish.  Clients act as a publisher
(subscribing to the acknowledgement) and verify that an acknowledgement is
returned after publishing.

### Known Issues

* 	Binary calls across json protocol are serialized differently between go
and java.  [#412](https://github.com/Workiva/frugal/issues/412)
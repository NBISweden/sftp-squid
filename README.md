SFTP-Squid - CLI for transferring files between sftp servers
============================================================

Simple usage:

 $ java -jar sftpsquid.jar user1@host1:file user2@host2:


Build instructions
------------------

### Prerequisites

1. git - http://git-scm.com/book/en/v2/Getting-Started-Installing-Git
2. maven - https://maven.apache.org/
3. Java 1.8 - http://www.oracle.com/technetwork/java/javase/downloads/index.html

### Build

Project is easiest built using maven:

 $ mvn package:package


Test environment
----------------

There is a test environment in the vm/ subdirectory. It uses vagrant with the
virtual box provisioner and ansible for configuration to set up 2 virtual
machines for testing.

### Prerequisites

You need virtualbox installed on your system, https://www.virtualbox.org/.

To setup ansible it is recommended that you create a virtual python environment
using [virtualenv](https://virtualenv.pypa.io/en/latest/). And then install the
requirements from the `requirements.txt` file.

    $ cd vm
    $ virtualenv env
    $ source env/bin/activate
    $ pip install -r requirements.txt

### Setup

Once the virtual environment for testing is setup you can spin up the machines:

    $ vagrant up

This will privision 2 virtual machines with ssh open on localhost ports 4021
and 4022. Both have for the user vagrant password `asdfasdf` and a second
factor password is set as well which is `hej`.


### Test

To then test the transfer of one file do this:

    $ java -jar target/sftpsquid.jar vagrant@localhost:4021:test_file vagrant@localhost:4022:

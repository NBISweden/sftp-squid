SFTP-Squid - CLI for transferring files between sftp servers
============================================================

Simple usage:

    $ sftpsquid user1@host1:file user2@host2:


Installation instructions
-------------------------

### Linux and MacOS:

1. Download the latest release from [github](https://github.com/BILS/sftp-squid/releases)
2. Unzip the file.

Then you can run it either from inside the directory or you can move the two
files (`sftpsquid` and `sftpsquid.jar`) somewhere convenient. We recommend you
to use your `bin` directory, i.e. `/Users/<your_user_name>/bin/` on Mac and
`/home/<your_user_name>/bin` on linux.


Build instructions
------------------

### Prerequisites

1. [git](http://git-scm.com/book/en/v2/Getting-Started-Installing-Git)
2. [maven](https://maven.apache.org/)
3. [Java 1.8](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

### Checkout the source

    $ git clone https://github.com/BILS/sftp-squid.git
    $ cd sftp-squid

### Build

Project is easiest built using maven:

    $ mvn clean compile assembly:single


Test environment
----------------

There is a test environment in the vm/ subdirectory. It uses vagrant with the
virtual box provisioner and ansible for configuration to set up 2 virtual
machines for testing.

### Prerequisites

You need [virtualbox](https://www.virtualbox.org/) installed on your system.

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
and 4022. Both have for the user `test_user` password `asdfasdf` and a second
factor password is set as well which is `hej`.

### Test

To then test the transfer of one directory do this:

    $ ./sftpsquid test_user@localhost:4021:transfer test_user@localhost:4022:

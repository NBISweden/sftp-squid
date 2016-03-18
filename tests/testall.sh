#!/bin/sh

./test_scp.sh
./test_sftp.sh
python test.py
perl test.pl
go run test.go -port 2222 -user vagrant -pass asdfasdf
echo "-- COMPILED GO --"
go build test.go
./test -port 2222 -user vagrant -pass asdfasdf
## CLASSPATH=jsch-0.1.53.jar javac Sftp.java
CLASSPATH=jsch-0.1.53.jar:. java Sftp


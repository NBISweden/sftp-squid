#!/bin/sh

echo SFTP
time sftp \
    -o "CheckHostIp no" \
    -o "StrictHostKeyChecking no" \
    -o "UserKnownHostsFile /dev/null" \
    -i /Users/johanviklund/Work/sftp-squid/vm/.vagrant/machines/default/virtualbox/private_key \
    -P 2222 \
    -b sftp.batch \
    vagrant@localhost


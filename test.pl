#!/usr/bin/env perl
use strict;
use warnings;

use feature ':5.20';

use Fcntl;
use Time::HiRes qw(gettimeofday tv_interval);

use Net::SSH2;

my $ssh = Net::SSH2->new();

my $private_key = "/Users/johanviklund/Work/sftp-squid/vm/.vagrant/machines/default/virtualbox/private_key";
my $public_key  = "/Users/johanviklund/Work/sftp-squid/vm/.vagrant/machines/default/virtualbox/public_key";

$ssh->connect('localhost', 2222) or die "Connect\n";
$ssh->auth_publickey('vagrant', $public_key, $private_key)
    or die "Auth";

my $sftp = $ssh->sftp() or die;

my $start = [gettimeofday()];
my $remote_file = $sftp->open('test_file_perl', O_WRONLY|O_CREAT) or die "$!, $?";
open my $local_file, '<', 'test_file' or die;

my $buffer;
my $LENGTH = 2**20;
while ( sysread $local_file, $buffer, $LENGTH ) {
    my $len = length $buffer;
    my $written = 0;
    while ($written < $len) {
        $written += syswrite $remote_file, $buffer, length $buffer, $written;
    }
}
say "Perl done in ", tv_interval($start), " seconds";

close $remote_file;
close $local_file;

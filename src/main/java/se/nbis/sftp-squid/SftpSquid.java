package se.nbis.sftp_squid;

import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.io.Console;
import java.net.ConnectException;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive;
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.password.Resource;
import net.schmizz.sshj.common.StreamCopier;


public class SftpSquid {
    SSHClient[] ssh_clients;
    SFTPClient[] sftp_clients;
    HostFileInfo[] hfs;

    public static void main(String[] args) throws IOException {
        HostFileInfo[] hf = parseArgs(args);
        SftpSquid ss = new SftpSquid(hf);
        ss.run();
    }

    public static HostFileInfo[] parseArgs(String[] args) {
        if (args.length < 2) {
            System.err.println("You need to supply at least 2 servers");
            System.exit(1);
        }
        HostFileInfo[] hf = new HostFileInfo[args.length];
        for (int i=0; i < args.length ; i++) {
            try {
                hf[i] = new HostFileInfo(args[i]);
            } catch (IOException e) {
                System.err.printf("Malformed host argument \"%s\"\n", args[i]);
                System.exit(1);
            }
        }
        return hf;
    }

    SftpSquid(HostFileInfo[] hf) throws IOException {
        if (hf.length < 2) {
            throw new IOException("Need at least 2 hosts to transfer between");
        }
        this.hfs          = hf;
        this.ssh_clients  = new SSHClient[hf.length];
        this.sftp_clients = new SFTPClient[hf.length];
    }

    public void run() throws IOException {
        try {
            connectAll();
            transferFile();
        }
        catch (Exception e) {
            usage();
        }
        finally {
            closeAll();
        }
    }

    public void usage() {
        System.out.println("Usage: sftp-squid <user1>@<server1>:<file_or_directory> <user2>@<server2>:<file_or_directory>");
    }


    public void connectAll() throws IOException {
        for (int i=0; i<hfs.length; i++) {
            try {
                int tries = 3;
                while (tries-- >= 0) {
                    try {
                        SSHClient ssh = connect(hfs[i]);
                        SFTPClient sftp = ssh.newSFTPClient();

                        ssh_clients[i] = ssh;
                        sftp_clients[i] = sftp;
                        tries = -1;
                    } catch (UserAuthException e) {
                        System.err.println("Incorrect username and/or password for " + hfs[i].userHostSpec() + " try again.");
                    }
                }
            } catch (UserAuthException e) {
                System.err.println("Incorrect username and/or password for " + hfs[i].userHostSpec());
                throw e;
            } catch (TransportException e) {
                System.err.println("Something went wrong: " + e);
                throw e;
            } catch (ConnectException e) {
                System.err.printf("Connection failure for %s: %s\n", hfs[i].userHostSpec(), e.getMessage());
                throw e;
            }
        }
    }

    public void closeAll() throws IOException {
        for (SFTPClient sftp_client : sftp_clients) {
            if (sftp_client != null) {
                sftp_client.close();
            }
        }
        for (SSHClient ssh_client : ssh_clients) {
            if (ssh_client != null) {
                ssh_client.close();
            }
        }
    }

    private SSHClient connect(HostFileInfo hf)
            throws UserAuthException, TransportException, IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(hf.host, hf.port);

        ssh.auth(hf.user, new AuthKeyboardInteractive(new UserKeyboardAuth(hf)));
        return ssh;
    }

    public void transferFile() throws IOException {
        RemoteFile fileFrom = sftp_clients[0].open(hfs[0].file);
        // Check if destination is a directory

        RemoteFile fileTo   = sftp_clients[1].open(hfs[1].file, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC));

        try {
            RemoteFile.ReadAheadRemoteFileInputStream streamFrom = fileFrom.new ReadAheadRemoteFileInputStream(16);
            RemoteFile.RemoteFileOutputStream streamTo           = fileTo.new RemoteFileOutputStream(0, 16);

            try {
                StreamCopier sc = new StreamCopier(streamFrom, streamTo);
                sc.bufSize(calculateMaxBufferSize(fileFrom, fileTo));
                sc.keepFlushing(false);
                sc.listener(new customListener(fileFrom.length()));
                sc.copy();
            } finally {
                streamFrom.close();
                streamTo.close();
            }
        } finally {
            fileFrom.close();
            fileTo.close();
        }
    }

    /* This code is taken from the library docs and adjusted to match the
     * situation we have with two remote servers */
    private int calculateMaxBufferSize(RemoteFile f1, RemoteFile f2) {
        int remoteMaxPacketSize = sftp_clients[0].getSFTPEngine().getSubsystem().getRemoteMaxPacketSize();
        if ( remoteMaxPacketSize > sftp_clients[1].getSFTPEngine().getSubsystem().getRemoteMaxPacketSize() ) {
            remoteMaxPacketSize = sftp_clients[1].getSFTPEngine().getSubsystem().getRemoteMaxPacketSize();
        }
        int packetOverhead = f2.getOutgoingPacketOverhead();
        return remoteMaxPacketSize - packetOverhead;
    }
}

class customListener implements StreamCopier.Listener {
    long length = 0;
    long nextPrint = 0;
    long printDuration = 0;

    customListener(long length) {
        this.length = length;
        printDuration = length/1000;
    }

    @Override
    public void reportProgress(long transferred) throws IOException {
        if (length != 0 && transferred == length) {
            System.out.printf("\r100.0%% Done\n");
        }
        else if (nextPrint < transferred) {
            nextPrint = transferred + printDuration;
            System.out.printf("\r%5.1f%% ", 100 * (double) transferred/ (double) length);
        }
        //System.out.println("Transf: " + transferred);
    }
}

class UserKeyboardAuth implements ChallengeResponseProvider {
    int n = 0;
    Resource r;
    HostFileInfo hf;

    UserKeyboardAuth(HostFileInfo hf) {
        this.hf = hf;
    }

    @Override
    public List<String> getSubmethods() {
        return new ArrayList<String>(0);
    }

    @Override
    public void init(Resource resource, String name, String instruction) {
        r = resource;
        //System.out.printf("Resource %s, Name, %s, Instruction %s\n", resource, name, instruction);
        return;
    }

    @Override
    public char[] getResponse(String prompt, boolean echo) {
        System.out.printf("[%s] %s", hf.userHostSpec(), prompt);
        Console cons = System.console();
        char[] resp = cons.readPassword();
        return resp;
    }

    @Override
    public boolean shouldRetry() {
        return false;
    }
}

class HostFileInfo {
    public String host;
    public String user;
    public String file;
    public int port = 22;

    HostFileInfo(String arg) throws IOException {
        String[] parts = arg.split("@|:");
        if (parts.length < 3 || parts.length > 4) {
            throw new IOException("Malformed hoststring");
        }
        user = parts[0];
        host = parts[1];

        if ( parts.length == 4 ) {
            port = Integer.parseInt(parts[2]);
            file = parts[3];
        }
        else {
            file = parts[2];
        }
    }

    public String userHostSpec() {
        String userHost = user + "@" + host;
        if ( port != 22 ) {
            return userHost + ":" + port;
        }
        return userHost;
    }

    public String toString() {
        return "[HostFileInfo " + user + "@" + host + ":" + port + ":" + file + "]";
    }
}

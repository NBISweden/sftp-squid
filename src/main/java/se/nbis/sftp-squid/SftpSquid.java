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
    SSHClient ssh1;
    SSHClient ssh2;
    SFTPClient client1;
    SFTPClient client2;

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage ....");
            System.exit(1);
        }
        HostFileInfo h1 = new HostFileInfo(args[0]);
        HostFileInfo h2 = new HostFileInfo(args[1]);
        SftpSquid ss = new SftpSquid();
        try {
            try {
                ss.connect1(h1.user, h1.host, h1.port);
            } catch (UserAuthException e) {
                System.out.println("Incorrect username and/or password for " + h1.userHostSpec());
                throw e;
            } catch (TransportException e) {
                System.out.println("Something went wrong: " + e);
                throw e;
            } catch (ConnectException e) {
                System.out.printf("Connection failure for : %s\n", h1.userHostSpec(), e.getMessage());
                throw e;
            }


            try {
                ss.connect2(h2.user, h2.host, h2.port);
            } catch (UserAuthException e) {
                System.out.println("Incorrect username and/or password for " + h2.userHostSpec());
                throw e;
            } catch (TransportException e) {
                System.out.println("Something went wrong: " + e);
                throw e;
            } catch (ConnectException e) {
                System.out.printf("Connection failure for : %s\n", h2.userHostSpec(), e.getMessage());
                throw e;
            }

            ss.transferFile(h1.file, h2.file);
        } finally {
            ss.close();
        }
    }

    public void close() throws IOException {
        if ( client1 != null ) {
            client1.close();
        }
        if ( client2 != null ) {
            client2.close();
        }
        if ( ssh1 != null ) {
            ssh1.close();
        }
        if ( ssh2 != null ) {
            ssh2.close();
        }
    }

    private SSHClient connect(String user, String host, int port)
            throws UserAuthException, TransportException, IOException {

        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(host, port);

        ssh.auth(user, new AuthKeyboardInteractive(new UserKeyboardAuth()));
        return ssh;
    }

    private SSHClient connect(HostFileInfo hf)
            throws UserAuthException, TransportException, IOException {
        return connect(hf.user, hf.host, hf.port);
    }

    public void connect1(String user, String host, int port)
            throws UserAuthException, TransportException, IOException {

        ssh1 = connect(user, host, port);
        client1 = ssh1.newSFTPClient();
    }

    public void connect2(String user, String host, int port)
            throws UserAuthException, TransportException, IOException {

        ssh2 = connect(user, host, port);
        client2 = ssh2.newSFTPClient();
    }

    public void transferFile(String from, String to) throws IOException {
        RemoteFile fileFrom = client1.open(from);
        RemoteFile fileTo   = client2.open(to, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC));

        try {
            RemoteFile.ReadAheadRemoteFileInputStream streamFrom = fileFrom.new ReadAheadRemoteFileInputStream(16);
            RemoteFile.RemoteFileOutputStream streamTo = fileTo.new RemoteFileOutputStream(0, 16);
            try {
                StreamCopier sc = new StreamCopier(streamFrom, streamTo);
                sc.bufSize(calculateBufSize(fileFrom, fileTo));
                sc.keepFlushing(false);
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

    public int calculateBufSize(RemoteFile f1, RemoteFile f2) {
        int remoteMaxPacketSize = client1.getSFTPEngine().getSubsystem().getRemoteMaxPacketSize();
        if ( remoteMaxPacketSize > client2.getSFTPEngine().getSubsystem().getRemoteMaxPacketSize() ) {
            remoteMaxPacketSize = client2.getSFTPEngine().getSubsystem().getRemoteMaxPacketSize();
        }
        int packetOverhead = f2.getOutgoingPacketOverhead();
        return remoteMaxPacketSize - packetOverhead;
    }
}

class UserKeyboardAuth implements ChallengeResponseProvider {
    int n = 0;
    Resource r;

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
        System.out.printf("[%s] %s", r.getDetail(), prompt);
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

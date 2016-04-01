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


/**
 * SftpSquid - Program to transfer files between multiple sftp servers.
 *
 * It is assumed that the user of this program do not have shell access through
 * SSH. Instead it streams the data from one server to the other through the
 * local machine. No data is stored locally.
 *
 * Further the program do not store any authentication information. Passwords
 * and other credentials are just passed through to the servers in question.
 *
 * @author  Johan Viklund <johan.viklund@bils.se>
 * @version 0.1
 * @since   2016-03-31
 */
public class SftpSquid {
    SSHClient[] ssh_clients;
    SFTPClient[] sftp_clients;
    HostFileInfo[] hfs;

    public static void main(String[] args) throws IOException {
        HostFileInfo[] hf = parseArgs(args);
        SftpSquid ss = new SftpSquid(hf);
        ss.run();
    }

    /**
     * Parse command line arguments.
     *
     * @param args the args array from the main method
     */
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

    /**
     * Construct a new SftpSquid object from an array of HostFileInfos
     *
     * @param HostFileInfo[] an array of HostFileInfo
     */
    SftpSquid(HostFileInfo[] hf) throws IOException {
        if (hf.length < 2) {
            throw new IOException("Need at least 2 hosts to transfer between");
        }
        this.hfs          = hf;
        this.ssh_clients  = new SSHClient[hf.length];
        this.sftp_clients = new SFTPClient[hf.length];
    }

    /**
     * The main loop of the program.
     */
    public void run() throws IOException {
        try {
            connectAll();
            transfer();
        }
        catch (Exception e) {
            usage();
        }
        finally {
            closeAll();
        }
    }

    /**
     * Print a simple usage statement to the user
     */
    public void usage() {
        System.out.println("Usage: sftp-squid <user1>@<server1>:<file_or_directory> <user2>@<server2>:<file_or_directory>");
    }


    /**
     * Connect to the servers specified on the command line
     */
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

    /**
     * Close all open connections
     */
    public void closeAll() throws IOException {
        for (SSHClient ssh_client : ssh_clients) {
            if (ssh_client != null && ssh_client.isConnected()) {
                ssh_client.close();
            }
        }
    }

    /**
     * Connect to one SFTP server
     *
     * @param  HostFileInfo A HostFileInfo object representing the server
     * @return SSHClient
     */
    private SSHClient connect(HostFileInfo hf)
            throws UserAuthException, TransportException, IOException {
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(hf.host, hf.port);

        ssh.auth(hf.user, new AuthKeyboardInteractive(new UserKeyboardAuth(hf)));
        return ssh;
    }

    /**
     * Transfer the files
     */
    public void transfer() throws IOException {
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
                sc.listener(new customListener(fileFrom.length(), hfs[0].fileNameOnly()));
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

/**
 * customListener - report progress to the user of the program
 */
class customListener implements StreamCopier.Listener {
    /** Total length of the current file */
    long length = 0;
    /** When to update the progressbar next */
    long nextPrint = 0;
    /** How often we should update the progressbar */
    long printStepSize = 0;
    /** Width of progressbar */
    int width = 60;
    /** File name that is currently beeing transferred */
    String file_name;

    /**
     * Create a new listener
     *
     * @param length size of file in bytes
     */
    customListener(long length, String file_name) {
        this.length    = length;
        this.file_name = renderFileName( file_name ); // Might as well just cache this

        printStepSize  = length/1000;
        width          = 60;
    }

    /**
     * Display the progressbar
     *
     * @param transferred The number of bytes transferred so far
     */
    @Override
    public void reportProgress(long transferred) throws IOException {
        if (length != 0 && transferred == length) {
            String prog = renderBar(transferred);
            String size = renderSize(transferred);
            System.out.printf("\r%-10.10s %s %s %5.1f%%\n", file_name, prog, size, 100.0);
        }
        else if (nextPrint < transferred) {
            nextPrint = transferred + printStepSize;

            String prog = renderBar(transferred);
            String size = renderSize(transferred);

            double percentDone = 100 * (double) transferred/ (double) length;

            System.out.printf("\r%-10.10s %s %s %5.1f%%", file_name, prog, size, percentDone);
        }
    }

    private String renderFileName(String file_name) {
        if (file_name.length() > 10) {
            return String.format("%-8.8s..", file_name);
        }
        return String.format("%-10.10s", file_name);
    }

    private String renderSize(long transferred) {
        String[] sizes = {"", "Kb", "Mb", "Gb", "Tb", "Pb", "Hb"};
        int exponent = (int) Math.floor( Math.log(transferred) / Math.log(1000) );
        double size = transferred / Math.pow(1000.0, (double) exponent);
        return String.format("%6.1f %2.2s", size, sizes[exponent]);
    }

    private String renderBar(long transferred) {
        int bar_length = (int) ((width - 2) * transferred / length);

        String prog = "|";
        for (int i=0; i<bar_length-1; i++) {
            prog += "=";
        }
        if (bar_length > 0 && bar_length < width - 2) {
            prog += ">";
        }
        for (int i=bar_length+1; i<width-2; i++) {
            prog += " ";
        }
        prog += "|";

        return prog;
    }
}

/**
 * Ask the user for passwords
 */
class UserKeyboardAuth implements ChallengeResponseProvider {
    /** Information about the current host, used to show the user what host they are connecting to */
    HostFileInfo hf;

    /**
     * Constructor
     *
     * @param HostFileInfo Information about the current host
     */
    UserKeyboardAuth(HostFileInfo hf) {
        this.hf = hf;
    }

    /**
     * Not sure what this method is for, had to be implemented though.
     */
    @Override
    public List<String> getSubmethods() {
        return new ArrayList<String>(0);
    }

    /**
     * Initialize the object, the parameters are filled in by the SSHj library.
     */
    @Override
    public void init(Resource resource, String name, String instruction) {
    }

    /**
     * Get user response from questions the server sends us, such as passwords.
     *
     * @param prompt the prompt that the server sent us
     * @param echo   whether to echo what the user types to the screen or not
     */
    @Override
    public char[] getResponse(String prompt, boolean echo) {
        System.out.printf("[%s] %s", hf.userHostSpec(), prompt);
        Console cons = System.console();
        char[] resp = cons.readPassword();
        return resp;
    }

    /**
     * Should we retry failed password attempts? Currently no.
     */
    @Override
    public boolean shouldRetry() {
        return false;
    }
}

/**
 * Class representing the sftp host and file to transfer
 */
class HostFileInfo {
    /** Just the hostname */
    public String host;
    /** The user name */
    public String user;
    /** The file to transfer */
    public String file;
    /** The port to use when connecting, default 22 */
    public int port = 22;

    /**
     * Create a new object by parsing a string.
     *
     * The string should have this format:
     *   <user>@<host>[:port]:path
     *
     * @param arg a string representing a user-host-file
     */
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

    /**
     * A string representing only the username/host and possibly custom port number.
     */
    public String userHostSpec() {
        String userHost = user + "@" + host;
        if ( port != 22 ) {
            return userHost + ":" + port;
        }
        return userHost;
    }

    /**
     * Only the filename part of the path
     */
    public String fileNameOnly() {
        String[] parts = file.split("/"); // SFTP Servers always use this...
        return parts[parts.length-1];
    }

    /**
     * toString!!
     */
    public String toString() {
        return "[HostFileInfo " + user + "@" + host + ":" + port + ":" + file + "]";
    }
}

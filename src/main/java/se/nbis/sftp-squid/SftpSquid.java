package se.nbis.sftp_squid;

import java.util.EnumSet;
import java.io.IOException;
import java.net.ConnectException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.BasicConfigurator;

import org.apache.commons.io.FilenameUtils;


import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive;
import net.schmizz.sshj.userauth.UserAuthException;
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
    private SSHClient[] ssh_clients;
    private SFTPClient[] sftp_clients;
    private HostFileInfo[] hfs;

    private Logger log = Logger.getLogger(getClass());

    public static void main(String[] args) throws IOException {
        /* The SSHj library logs a lot, need to setup the logger for it */
        BasicConfigurator.configure();
        Logger.getRootLogger().setLevel(Level.ERROR);
        Logger.getLogger("se.nbis.sftp_squid.SftpSquid").setLevel(Level.DEBUG);

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
        log.debug("run()");
        try {
            connectAll();
            transfer();
        }
        catch (Exception e) {
            System.err.println(e.getMessage());
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
        log.debug("connectAll");
        for (int i=0; i<hfs.length; i++) {
            try {
                int tries = 3;
                while (tries-- > 0) {
                    try {
                        SSHClient ssh = connect(hfs[i]);
                        SFTPClient sftp = ssh.newSFTPClient();

                        ssh_clients[i] = ssh;
                        sftp_clients[i] = sftp;
                        tries = -1;
                    } catch (UserAuthException e) {
                        System.err.println("Incorrect username and/or password for " + hfs[i].userHostSpec() + " try again.");
                        if (tries <= 0) {
                            throw e;
                        }
                    }
                }
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
        log.debug("closeAll()");
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
        log.debug("Connecting to " + hf);
        SSHClient ssh = new SSHClient();
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
        ssh.connect(hf.host, hf.port);

        try {
            ssh.auth(hf.user, new AuthKeyboardInteractive(new UserKeyboardAuth(hf)));
        } catch (Exception e) {
            ssh.close(); // We have to clean this up
            throw e;
        }
        return ssh;
    }

    /**
     * Transfer the files
     */
    public void transfer() throws IOException {
        String source      = hfs[0].file;
        String destination = hfs[1].file;
        log.debug("transfer(): " + source + " -> " + destination);

        try {
            if ( sftp_clients[1].type(destination) == FileMode.Type.DIRECTORY ) {
                destination += '/' + FilenameUtils.getBaseName(source);
            }
        }
        catch (Exception e) {}

        transferFile(source, destination);
    }

    private void transferFile(String source, String destination) throws IOException {
        log.debug("Transfer " + source + " -> " + destination);

        RemoteFile fileSource = sftp_clients[0].open(source);
        RemoteFile fileDestination = sftp_clients[1].open(destination, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC));

        try {
            RemoteFile.ReadAheadRemoteFileInputStream streamSource
                = fileSource.new ReadAheadRemoteFileInputStream(16);
            RemoteFile.RemoteFileOutputStream streamDestination
                = fileDestination.new RemoteFileOutputStream(0, 16);

            try {
                StreamCopier sc = new StreamCopier(streamSource, streamDestination);
                sc.bufSize(calculateMaxBufferSize(fileSource, fileDestination));
                sc.keepFlushing(false);
                sc.listener(new ProgressBarListener(fileSource.length(), fileNameOnly(source)));
                sc.copy();
            } finally {
                streamSource.close();
                streamDestination.close();
            }
        } finally {
            fileSource.close();
            fileDestination.close();
        }
    }

    /**
     * Only the filename part of the path
     */
    private String fileNameOnly(String path) {
        String[] parts = path.split("/"); // SFTP Servers always use this separator
        return parts[parts.length-1];
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

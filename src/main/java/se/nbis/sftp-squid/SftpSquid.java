/*
 * Copyright 2016 Johan Viklund
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.nbis.sftpsquid;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.StreamCopier;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.OpenMode;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive;
import net.schmizz.sshj.userauth.method.AuthMethod;
import net.schmizz.sshj.userauth.method.AuthPassword;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;


/**
 * SftpSquid - Program to transfer files between multiple sftp servers.
 *
 * <p>It is assumed that the user of this program do not have shell access through
 * SSH. Instead it streams the data from one server to the other through the
 * local machine. No data is stored locally.
 *
 * <p>Further the program do not store any authentication information. Passwords
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
    BasicConfigurator.configure();
    Logger.getRootLogger().setLevel(Level.ERROR);
    //Logger.getLogger("se.nbis.sftp_squid.SftpSquid").setLevel(Level.DEBUG);
    HostFileInfo[] hf;

    try {
        hf = parseArgs(args);
        SftpSquid ss = new SftpSquid(hf);
        ss.run();
    } catch (IOException e) {
      System.err.println("ERROR: " + e.getMessage());
      System.exit(1);
    }
  }

  /**
   * Parse command line arguments.
   *
   * @param args the args array from the main method
   */
  public static HostFileInfo[] parseArgs(String[] args) throws IOException {
    if (args.length < 2) {
      throw new IOException("You need to supply at least 2 servers");
    }
    HostFileInfo[] hf = new HostFileInfo[args.length];
    for (int i=0; i < args.length ; i++) {
      try {
        hf[i] = new HostFileInfo(args[i]);
      } catch (IOException e) {
        throw new IOException("Malformed host argument '" + args[i] + "'");
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
    } finally {
      closeAll();
    }
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
  private SSHClient connect(HostFileInfo hf) throws IOException {
    log.debug("Connecting to " + hf);
    SSHClient ssh = new SSHClient();
    ssh.addHostKeyVerifier(new PromiscuousVerifier());
    ssh.connect(hf.host, hf.port);

    try {
      List<AuthMethod> authmethods = new LinkedList<AuthMethod>();
      authmethods.add(new AuthKeyboardInteractive(new UserKeyboardAuth(hf)));
      authmethods.add(new AuthPassword(new PasswordAuth(hf)));
      ssh.auth(hf.user, authmethods);
    } catch (IOException e) {
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

    FileMode.Type sourceType = getType(source, sftp_clients[0]);
    FileMode.Type destType   = getType(destination, sftp_clients[1]);

    if (sourceType == FileMode.Type.DIRECTORY && destType != sourceType) {
      throw new IOException("Destination has to be a directory");
    }

    List<String> sources = buildTransferList(source, sftp_clients[0]);

    int lastSeparatorInSource = FilenameUtils.indexOfLastSeparator(source);
    if ( lastSeparatorInSource == -1 ) {
      if ( sourceType == FileMode.Type.REGULAR ) {
        lastSeparatorInSource = 0;
      } else {
        lastSeparatorInSource = source.length();
      }
    }
    log.debug("transfer() lastSeparatorInSource: " + lastSeparatorInSource);

    for (String s : sources) {
      String d = destination;
      if ( destType == FileMode.Type.DIRECTORY ) {
        d += s.substring(lastSeparatorInSource);
      }

      transferFile(s, d);
    }
  }

  /**
   * Create a list of files to transfer
   */
  private List<String> buildTransferList(String source, SFTPClient c) throws IOException {
    log.debug("Building transferlist from " + source);

    FileMode.Type type = getType(source, c);
    List<String> ret = new LinkedList<String>();

    if (type == FileMode.Type.REGULAR) {
      ret.add(source);
      return ret;
    }
    if (type != FileMode.Type.DIRECTORY) {
      throw new IOException("Can't transfer this type of file (" + type + ")");
    }

    List<RemoteResourceInfo> paths = c.ls(source);
    for (RemoteResourceInfo p : paths) {
      ret.addAll( buildTransferList(p.getPath(), c) );
    }

    return ret;
  }

  /**
   * Remove superflous parts of the path, such as double /
   *
   * @param String path to normalize
   * @return String
   */
  private String normalizePath(String path) {
    String normalized = FilenameUtils.normalize(path);
    return FilenameUtils.separatorsToUnix(normalized); // In case we run on windows
  }

  /**
   * Transfer one file between the two systems
   *
   * @param source The source file as a string
   * @param destination the destination file as a string
   */
  private void transferFile(String source, String destination) throws IOException {
    log.debug("Transfer " + source + " -> " + destination);
    createPath(destination, sftp_clients[1]);

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
   * Helper to create directories if needed on target
   */
  private void createPath(String path, SFTPClient client) {
    path = normalizePath(path);
    log.debug("createPath based on " + path);

    String dir = "";
    if (path.charAt(0) != '/') {
      dir = ".";
    }

    String[] components = path.split("/");
    for(int i=0; i<components.length-1; i++) {
      dir += '/' + components[i];
    }
    dir = normalizePath(dir);
    if (dir.length() == 0) {
      return;
    }

    if (dir.charAt(0) != '/') {
      dir = "./" + dir;
    }

    log.debug("createPath: " + dir);
    try {
      client.mkdirs(dir);
    } catch (IOException e) {
      log.debug("Could not createPath: " + e);
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

  /**
   * Get filetype
   */
  private FileMode.Type getType(String p, SFTPClient c) {
    FileMode.Type t;
    try {
      t = c.type(p);
      if (t == FileMode.Type.SYMKLINK) {
        t = getType(c.readlink(p), c);
      }
    } catch (Exception e) {
      t = FileMode.Type.UNKNOWN;
    }
    return t;
  }
}

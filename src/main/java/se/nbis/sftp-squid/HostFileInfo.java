package se.nbis.sftpsquid;

import java.io.IOException;

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
   * <p>The string should have this format:
   *   <user>@<host>[:port]:<path>
   *
   * @param arg a string representing a user-host-file
   */
  HostFileInfo(String arg) throws IOException {
    String[] parts = arg.split("@|:");

    if (arg.charAt(arg.length() - 1) == ':') {
      String[] np = new String[parts.length + 1];
      for (int i=0; i<parts.length; i++) {
        np[i] = parts[i];
      }
      np[parts.length] = ".";
      parts = np;
    }

    if (parts.length < 3 || parts.length > 4) {
      throw new IOException("Malformed hoststring");
    }
    user = parts[0];
    host = parts[1];

    if ( parts.length == 4 ) {
      port = Integer.parseInt(parts[2]);
      file = parts[3];
    } else {
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
   * toString!!
   */
  public String toString() {
    return "[HostFileInfo " + user + "@" + host + ":" + port + ":" + file + "]";
  }
}

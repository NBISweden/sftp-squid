package se.nbis.sftpsquid;

import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.Resource;

import java.io.Console;

/**
 * Ask the user for passwords
 */
class PasswordAuth implements PasswordFinder {
    private HostFileInfo hf;

  /**
   * Construct a new object, we use the HostFileInfo to show the user what
   * host they are connecting to.
   *
   * @param HostFileInfo Information about the current host
   */
    PasswordAuth(HostFileInfo hf) {
        this.hf = hf;
    }

  /**
   * Get the password from the user
   *
   * @param resource
   */
    @Override
    public char[] reqPassword(Resource<?> resource) {
        System.out.printf("Password for %s:", hf.userHostSpec());
        Console cons = System.console();
        char[] resp = cons.readPassword();
        return resp;
    }

  /**
   * Should we retry failed password attempts? Currently no.
   */
    @Override
    public boolean shouldRetry(Resource<?> resource) {
        return false;
    }
}

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

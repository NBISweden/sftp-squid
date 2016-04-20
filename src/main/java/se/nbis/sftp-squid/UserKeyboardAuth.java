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

import net.schmizz.sshj.userauth.method.ChallengeResponseProvider;
import net.schmizz.sshj.userauth.password.Resource;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;

/**
 * Ask the user for passwords
 */
class UserKeyboardAuth implements ChallengeResponseProvider {
  /** Information about the current host, used to show the user what host they are connecting to */
  private HostFileInfo hf;

  /**
   * Construct a new object, we use the HostFileInfo to show the user what
   * host they are connecting to.
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

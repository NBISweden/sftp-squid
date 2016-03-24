
# This is where password is checked
def pam_sm_authenticate(pamh, flags, args):
    msg = pamh.Message(pamh.PAM_PROMPT_ECHO_OFF, "Second factor pls: ")
    try:
        resp = pamh.conversation(msg)
    except pamh.exception, e:
        return e.pam_result
    if resp.resp == 'hej':
        return pamh.PAM_SUCCESS
    return pamh.PAM_AUTH_ERR

def pam_sm_setcred(pamh, flags, argv):
  return pamh.PAM_SUCCESS

def pam_sm_acct_mgmt(pamh, flags, argv):
  return pamh.PAM_SUCCESS

def pam_sm_open_session(pamh, flags, argv):
  return pamh.PAM_SUCCESS

def pam_sm_close_session(pamh, flags, argv):
  return pamh.PAM_SUCCESS

def pam_sm_chauthtok(pamh, flags, argv):
  return pamh.PAM_SUCCESS

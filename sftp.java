import java.util.Scanner;
import java.io.Console;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import com.jcraft.jsch.*;

class JSCHLogger implements com.jcraft.jsch.Logger {
    @Override
    public boolean isEnabled(int pLevel) {
        return false; // here, all levels enabled
    }

    @Override
    public void log(int pLevel, String pMessage) {
        System.out.printf("%d: %s\n", pLevel, pMessage);
    }
}

class MyMonitor implements SftpProgressMonitor {
    int number;
    long max;
    long sofar = 0;
    long lastprint = 0;
    public MyMonitor (int n) {
        number = n;
    }

    public void init(int op, String src, String dest, long max) {
        this.max = max;
        System.out.printf("Starting %d (%d)\n", max, number);
    }

    public boolean count(long count) {
        sofar += count;
        if ( sofar - lastprint > 1_000_000 ) {
            double percent = 100 * (double) sofar / (double) max;
            System.out.printf("Progress %6.2f (%d/%d)\r", percent, sofar, max);
            lastprint = sofar;
        }
        //System.out.printf("   Transferring %d\n", count);
        return true;
    }

    public void end() {
        System.out.println("\nFinished upload");
    }
}

class MyUserInfo implements UserInfo, UIKeyboardInteractive {
    String passphrase;
    String password;

    public String getPassphrase() {
        return passphrase;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean promptPassphrase(String message) {
        System.out.printf("MSG Passphrase (%s): ", message);
        Console console = System.console();
        char[] res = console.readPassword("");
        passphrase = String.valueOf(res);
        return true;
    }

    @Override
    public boolean promptPassword(String message) {
        System.out.printf("MSG Password (%s): ", message);
        Console console = System.console();
        char[] res = console.readPassword("");
        password = String.valueOf(res);
        return true;
    }

    @Override
    public boolean promptYesNo(String message) {
        System.out.printf("Yes/No (%s)", message);
        Scanner scan = new Scanner(System.in);
        String text = scan.nextLine();
        if (text.startsWith("y") || text.startsWith("Y") ) {
            return true;
        }
        return false;
    }

    @Override
    public void showMessage(String message) {
        System.out.println(message);
    }

    @Override
    public String[] promptKeyboardInteractive(String destination, String name,
            String instruction, String[] prompt, boolean[] echo) {
        System.out.printf("Dest(%s) Name(%s) Instruction(%s)\n", destination, name, instruction);
        Scanner scanner = new Scanner(System.in);
        String[] ret = new String[prompt.length];
        for (int i=0; i < prompt.length; i++) {
            System.out.printf("promptKeyboardInteractive (%s)\n", prompt[i]);
            if (echo[i]) {
                String text = scanner.nextLine();
                ret[i] = text;
            } else {
                Console console = System.console();
                char[] res = console.readPassword("");
                String text = String.valueOf(res);
                ret[i] = text;
            }
        }
        return ret;
    }
}

public class Sftp{
    public static void main(String[] arg){
        Runner runner = new Runner();
        runner.run();
        System.exit(0);
    }
}


class Runner implements Runnable {
    JSch jsch;

    Runner() {
        jsch = new JSch();
    }

    public void run(){
        try{
            JSch.setLogger(new JSCHLogger());

            String host="localhost";
            String user="vagrant";
            String pass="asdfasdf";
            int port=2222;

            Session session = this.createSession(user, host, port, pass);
            ChannelSftp c = this.createSftpChannel(session);

            Session session2 = this.createSession(user, host, port, pass);
            ChannelSftp c2 = this.createSftpChannel(session);


            String p1 = "test_file";
            String p2 = "test_file_java";
            int mode=ChannelSftp.OVERWRITE;

            long startTime = System.nanoTime();
            c2.get(p1, c.put(p2), new MyMonitor(1));
            long endTime = System.nanoTime();

            double ef = (double) (endTime - startTime);

            System.out.printf("JAVA took %.1fs\n", ef/1_000_000_000.0);

            session.disconnect();
        }
        catch(Exception e){
            System.out.println(e);
        }
        System.out.println();
    }

    public ChannelSftp createSftpChannel(Session session) throws JSchException {
        ChannelSftp s = (ChannelSftp) session.openChannel("sftp");
        s.setBulkRequests(128);
        s.setOutputStream(new ByteArrayOutputStream(32768));
        s.connect();
        return s;

    }

    public Session createSession(String user, String host, int port, String pass) throws JSchException {
            Session session=jsch.getSession(user, host, port);
            session.setPassword(pass);
            //session.setUserInfo(new MyUserInfo());
            session.setConfig("StrictHostKeyChecking", "no");
            session.setServerAliveInterval(3000);
            session.setTimeout(3000);

            session.connect();
            return session;
    }
}


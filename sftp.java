import java.io.ByteArrayOutputStream;
import com.jcraft.jsch.*;

class JSCHLogger implements com.jcraft.jsch.Logger {
    @Override
    public boolean isEnabled(int pLevel) {
        return true; // here, all levels enabled 
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
        if ( sofar - lastprint > 10_000_000 ) {
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

public class Sftp{
    public static void main(String[] arg){

        try{
            JSch.setLogger(new JSCHLogger());
            JSch jsch=new JSch();

            String host="localhost";
            String user="vagrant";
            String pass="asdfasdf";
            int port=2222;

            Session session = createSession(jsch, user, host, port, pass);
            ChannelSftp c = createSftpChannel(session);

            Session session2 = createSession(jsch, user, host, port, pass);
            ChannelSftp c2 = createSftpChannel(session);


            String p1 = "test_file";
            String p2 = "test_file_java";
            int mode=ChannelSftp.OVERWRITE;
            long startTime = System.nanoTime();
            c2.get(p1, c.put(p2), new MyMonitor(1));
            //c.put(p1, p2, mode); 
            long endTime = System.nanoTime();

            long elapsed = endTime - startTime;
            double ef = (double) elapsed;

            System.out.printf("JAVA took %.1fs\n", ef/1000000000.0);

            session.disconnect();
        }
        catch(Exception e){
            System.out.println(e);
        }
        System.out.println();
        System.exit(0);
    }

    public static ChannelSftp createSftpChannel(Session session) throws JSchException {
        ChannelSftp s = (ChannelSftp) session.openChannel("sftp");
        s.setBulkRequests(128);
        s.setOutputStream(new ByteArrayOutputStream(32768));
        s.connect();
        return s;

    }

    public static Session createSession(JSch jsch, String user, String host, int port, String pass) throws JSchException {
            Session session=jsch.getSession(user, host, port);
            session.setPassword(pass);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setServerAliveInterval(3000);
            session.setTimeout(500);

            session.connect();
            return session;
    }
}


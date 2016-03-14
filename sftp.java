/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/**
 * This program will demonstrate the sftp protocol support.
 *   $ CLASSPATH=.:../build javac Sftp.java
 *   $ CLASSPATH=.:../build java Sftp
 * You will be asked username, host and passwd. 
 * If everything works fine, you will get a prompt 'sftp>'. 
 * 'help' command will show available command.
 * In current implementation, the destination path for 'get' and 'put'
 * commands must be a file, not a directory.
 *
 */
import com.jcraft.jsch.*;

public class Sftp{
    public static void main(String[] arg){

        try{
            JSch jsch=new JSch();

            String host="localhost";
            String user="vagrant";
            int port=2222;

            Session session=jsch.getSession(user, host, port);
            session.setPassword("asdfasdf");
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();

            Channel channel=session.openChannel("sftp");
            channel.connect();
            ChannelSftp c=(ChannelSftp)channel;


            String p1 = "test_file";
            String p2 = "test_file_java";
            int mode=ChannelSftp.OVERWRITE;
            long startTime = System.nanoTime();
            c.put(p1, p2, mode); 
            long endTime = System.nanoTime();

            long elapsed = endTime - startTime;
            double ef = (double) elapsed;

            System.out.printf("JAVA took %.1fs\n", ef/1000000000.0);

            session.disconnect();
        }
        catch(Exception e){
            System.out.println(e);
        }
        System.exit(0);
    }
}


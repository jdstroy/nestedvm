package tests;

import java.net.*;

import org.xwt.mips.Runtime;

public class Echo {
    private static final int PORT = 2000;
    public static void main(String[] args) throws Exception {
        ServerSocket sock = new ServerSocket(PORT);
        System.err.println("Listening on " + PORT);
        for(;;) new Client(sock.accept()).go();
    }
    
    private static class Client implements Runnable {
        private Socket sock;
        public Client(Socket sock) { this.sock = sock; }
        public void go() { new Thread(this).start(); }
        public void run() {
            try {
                Runtime task = new EchoHelper();
                int status = task.run(
                    new String[]{"EchoHelper"},
                    null,
                    new Runtime.InputStreamFD(sock.getInputStream()),
                    new Runtime.OutputStreamFD(sock.getOutputStream()),
                    null
                );
                System.err.println("Exit status: " + status);
            } catch(Exception e) {
                System.err.println(e);
            }
        }
    }
}

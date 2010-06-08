// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package tests;

import java.net.*;

import org.ibex.nestedvm.Runtime;

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
                Runtime task = (Runtime) Class.forName("tests.EchoHelper").newInstance();
                task.closeFD(0);
                task.closeFD(1);
                //task.closeFD(2);
                task.addFD(new Runtime.InputOutputStreamFD(sock.getInputStream()));
                task.addFD(new Runtime.InputOutputStreamFD(sock.getOutputStream()));
                //task.dupFD(1);
                
                int status = task.run(new String[]{"EchoHelper"} );
                System.err.println("Exit status: " + status);
            } catch(Exception e) {
                System.err.println(e);
            }
        }
    }
}

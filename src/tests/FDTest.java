package tests;

import org.xwt.mips.Runtime;

public class FDTest {
    public static void main(String[] args) throws Exception {
        Runtime rt = new Test();
        int fd = rt.allocFDEnt(new Runtime.SeekableInputStreamFD(System.in));
        int status = rt.run(new String[]{"test","fdtest","/dev/fd/" + fd});
        System.err.println("Exit status: " + status);
    }
}

        
 
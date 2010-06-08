// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package tests;

import org.ibex.nestedvm.Runtime;
import java.io.*;
import java.util.Date;

public class CallTest {
    public static void main(String[] args) throws Exception {
        int a1 = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        int a2 = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int a3 = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int a4 = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int a5 = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        int a6 = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        
        System.out.println("Version is: " + System.getProperty("os.version"));
        final Runtime rt;
        if(a1 == 99) // yeah.. this is ugly
            rt = new org.ibex.nestedvm.Interpreter("build/tests/Test.mips");
        else
            rt = (Runtime) Class.forName("tests.Test").newInstance();
        	
        rt.setCallJavaCB(new Runtime.CallJavaCB() {
                public int call(int a, int b, int c, int d) {
                    switch(a) {
                        case 1: return rt.strdup("OS: " + System.getProperty("os.name"));
                        case 2: return rt.strdup(System.getProperty("os.version"));
                        case 3: return rt.strdup(new Date().toString());
                        case 4: return rt.addFD(new Runtime.InputOutputStreamFD(null,new CustomOS()));
                        case 5:
                            System.out.println("In callJava() in Java"); 
                            try { rt.call("backinmips"); } catch(Runtime.CallException e) { }
                            System.out.println("Back in callJava() in Java");
                            return 0;
                        default: return 0;
                    }
                }
            });
        System.out.println("Runtime: " + rt);
        
        rt.start(new String[]{"Test","calltest"});
        rt.execute();
        
        System.out.println("== Start of CallTest ==");
        System.out.println("Back in java... calling callme()");
        int ret = rt.call("callme",new int[]{a1,a2,a3,a4,a5,a6});
        System.out.println("callme returned: " + ret);
        
        int addr = rt.strdup("Hello, World from java");
        rt.call("echo",addr,4);
        rt.free(addr);
        rt.call("echo",new Object[]{"Hello, World, from the Object[] call method",new Integer(2)});
        System.out.println("== End of CallTest ==");
        
        rt.execute();
        System.exit(rt.exitStatus());
    }
    
    private static class CustomOS extends OutputStream {
        public CustomOS() { }
        public void write(int b) {  byte[] a = new byte[1]; a[0] = (byte)(b&0xff); write(a,0,1); }
        public void write(byte[] b, int off, int len) {
            int len2 = len;
            while(b[len2-1]=='\n') len2--;
            System.out.println("This just in from MIPS: " + new String(b,off,len2));
        }
    }
}

package tests;

import org.ibex.nestedvm.Runtime;
import java.io.*;

public class CallTest {
    public static void main(String[] args) throws Exception {
        int a1 = args.length > 0 ? Integer.parseInt(args[0]) : 0;
        int a2 = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int a3 = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        int a4 = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int a5 = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        int a6 = args.length > 5 ? Integer.parseInt(args[5]) : 0;
        
        System.out.println("Version is: " + System.getProperty("os.version"));
        Runtime rt;
        if(a1 == 99) // yeah.. this is ugly
            rt = new org.ibex.nestedvm.Interpreter("build/tests/Test.mips");
        else
        	//FIXME: Callback not subclass
            rt = new Test() {
                protected int callJava(int a, int b, int c, int d) {
                    switch(a) {
                        case 1: return strdup("OS: " + System.getProperty("os.name"));
                        case 2: return strdup(System.getProperty("os.version"));
                        case 3: return strdup(new Date().toString());
                        case 4: return allocFDEnt(new OutputStreamFD(new CustomOS()));
                        case 5:
                            System.out.println("In callJava() in Java"); 
                            try { call("backinmips"); } catch(CallException e) { }
                            System.out.println("Back in callJava() in Java");
                            return 0;
                        default: return super.callJava(a,b,c,d);
                    }
                }
            };
        System.out.println("Runtime: " + rt);
        
        rt.start(new String[]{"Test","calltest"});
        rt.execute();
        
        System.out.println("== Start of CallTest ==");
        System.out.println("Back in java... calling callme()");
        int ret = rt.call("callme",a1,a2,a3,a4,a5,a6);
        System.out.println("callme returned: " + ret);
        
        int addr = rt.strdup("Hello, World from java");
        rt.call("echo",addr,4);
        rt.free(addr);
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

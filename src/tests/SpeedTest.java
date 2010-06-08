// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package tests;

import org.ibex.nestedvm.Runtime;

//import java.io.*;
//import java.util.*;

class SpeedTest {
    private static long start,end;
    private static long now() { return System.currentTimeMillis(); }
    private static void start()  { start = now(); }
    private static void end() { end = now(); }
    private static float diff() { return ((float)(end-start))/1000; }
    
    /*private static InputStream is = new InputStream() {
        int left = 100*1024*1024;
        int c = 0;
        public int read() { if(left==0) return -1; left--; return (c++)&0xff; }
        public int read(byte[] buf, int pos,int len) {
            len = Math.min(left,len);
            Arrays.fill(buf,pos,len,(byte)c++);
            left -= len;
            return len;
        }
        public void close() { left =  100*1024*1024; }
    };
    
    private static OutputStream os = new OutputStream() {
        public void write(int c) { }
        public void write(byte[] buf, int pos, int len) { }
    };*/
    
    public static void main(String[] args) throws Exception {
        float d;
        
        if(args.length < 2) { System.err.println("Usage: SpeedTest {classname|mips binary} number_of_runs args"); System.exit(1); }
        String className = args[0];
        int runs = Integer.parseInt(args[1]);
        if(runs < 5) throw new Error("Runs must be >= 5");
        String[] appArgs = new String[args.length-1];
        appArgs[0] = className;
        for(int i=2;i<args.length;i++) appArgs[i-1] = args[i];
        
        Class c = null;
        boolean binary = className.endsWith(".mips");
        if(!binary) {
            start();
            c = Class.forName(className);
            end();
            d = diff();
            System.out.println("Class.forName() took " + d + "sec");
            
            start();
            c.newInstance();
            end();
            d = diff();
            System.out.println("c.newInstance() took " + d + "sec");
            
            if(!Runtime.class.isAssignableFrom(c)) { System.err.println(className + " isn't a MIPS compiled class"); System.exit(1); }
        } else {
            throw new Error("Interpreter not supported in speedtest");
        }
            
        float times[] = new float[runs];
        
        for(int i=0;i<runs;i++) {
            //Runtime runtime = binary ? new Interpreter(className) : (Runtime) c.newInstance();
            Runtime runtime = (Runtime) c.newInstance();
            /*runtime.closeFD(0);
            runtime.closeFD(1);
            runtime.addFD(new Runtime.InputStreamFD(is));
            runtime.addFD(new Runtime.OutputStreamFD(os));*/
            System.gc();
            start();
            int status = runtime.run(appArgs);
            if(status != 0) { System.err.println(className + " failed with exit status: " + status); System.exit(1); }
            end();
            //is.close();
            times[i] = diff();
            System.err.println("Run " + (i+1) + ": " + times[i] + " sec");
        }
        
        java.util.Arrays.sort(times);
        
        System.out.println("Best: " + times[0]);
        System.out.println("Worst: " + times[times.length-1]);
        float sum = 0.0f;
        for(int i=2;i<times.length-2;i++)
            sum += times[i];
        float avg = sum / (times.length-4);
        System.out.println("Avg of middle " + (times.length-4) + ": " + avg);
    }
}

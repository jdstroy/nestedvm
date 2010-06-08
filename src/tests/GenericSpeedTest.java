// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package tests;

import java.lang.reflect.*;

class GenericSpeedTest {
    private static long start,end;
    private static long now() { return System.currentTimeMillis(); }
    private static void start()  { start = now(); }
    private static void end() { end = now(); }
    private static float diff() { return ((float)(end-start))/1000; }
    
    public static void main(String[] args) throws Exception {
        float d;
        
        if(args.length < 2) { System.err.println("Usage: GenericSpeedTest runs classname args"); System.exit(1); }
        int runs = Integer.parseInt(args[0]);
        String className = args[1];
        if(runs < 5) throw new Error("Runs must be >= 5");
        String[] appArgs = new String[args.length-2];
        for(int i=2;i<args.length;i++) appArgs[i-2] = args[i];
        
        Class c = Class.forName(className);
        Method m = c.getMethod("main",new Class[]{Class.forName("[Ljava.lang.String;")});
        
        float times[] = new float[runs];
        Object[] mainArgs = new Object[] { appArgs };
        for(int i=0;i<runs;i++) {
            System.gc();
            start();
            m.invoke(null,mainArgs);
            end();
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

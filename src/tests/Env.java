// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package tests;

import org.ibex.nestedvm.Runtime;

class Env {
    public static void main(String[] args) throws Exception {
        int n = 0;
        while(n < args.length && args[n].indexOf("=") != -1) n++;

        if(n==args.length) {
            System.err.println("Usage: Env [name=value ...] classname [args ...]");
            System.exit(1);
        }
        
        String[] env = new String[n];
        String[] appArgs = new String[args.length-n-1];
        for(int i=0;i<n;i++) env[i] = args[i];
        String className = args[n];
        for(int i=n+1;i<args.length;i++) appArgs[i-n-1] = args[i];
        
        Runtime rt;
        if(className.endsWith(".mips")) {
            rt = new org.ibex.nestedvm.Interpreter(className);
        } else {
            Class c = Class.forName(className);
            if(!Runtime.class.isAssignableFrom(c)) { System.err.println(className + " isn't a MIPS compiled class"); System.exit(1); }
            rt = (Runtime) c.newInstance();
        }
        System.exit(rt.run(appArgs,env));
    }
}

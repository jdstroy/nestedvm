package org.ibex.nestedvm;

import java.io.*;

import org.ibex.nestedvm.util.*;

// FEATURE: This need a lot of work to support binaries spanned across many classes
public class RuntimeCompiler {  
    // FEATURE: Do we need to periodicly create a new classloader to allow old clases to be GCed?
    private static SingleClassLoader singleClassLoader = new SingleClassLoader();
    // FEATURE: Is it ok if this overflows?
    private static long nextID = 1;
    private static synchronized String uniqueID() { return Long.toString(nextID++); }
    
    public static Class compile(Seekable data) throws IOException, Compiler.Exn {
        String className = "nextedvm.runtimecompiled_" + uniqueID();
        System.err.println("RuntimeCompiler: Building " + className);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ClassFileCompiler c = new ClassFileCompiler(data,className,baos);
        // FEATURE: make this Optional, pass options on compile arguments
        c.parseOptions("unixruntime,nosupportcall");
        c.go();
        baos.close();
        byte[] bytecode = baos.toByteArray();
        return singleClassLoader.fromBytes(className,bytecode);
    }
    
    private static class SingleClassLoader extends ClassLoader {
        public Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
                System.err.println(this + ": loadClass(\"" + name + "," + resolve + ");");
            return super.loadClass(name,resolve);
        }
        public Class fromBytes(String name, byte[] b) { return fromBytes(name,b,0,b.length); }
        public Class fromBytes(String name, byte[] b, int off, int len) {
            Class c = super.defineClass(name,b,off,len);
            resolveClass(c);
            return c;
        }
    }
    
    public static void main(String[] args) throws Exception {
            if(args.length == 0) {
                    System.err.println("Usage: RuntimeCompiler mipsbinary");
            System.exit(1);
        }
        UnixRuntime r = (UnixRuntime) compile(new Seekable.File(args[0])).newInstance();
        System.err.println("Instansiated: "+ r);
        System.exit(r.run(args));
    }
    
    private RuntimeCompiler() { }
}

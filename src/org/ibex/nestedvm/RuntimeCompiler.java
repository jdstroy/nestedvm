package org.ibex.nestedvm;

import java.io.*;

import org.ibex.nestedvm.util.*;

// This need a lot of work to support binaries spanned across many classes
public class RuntimeCompiler {  
    private static SingleClassLoader singleClassLoader;
    private static int nextID;
    
    public static Class compile(Seekable data) throws IOException, Compiler.Exn { return compile(data,null); }
    public static Class compile(Seekable data, String extraoptions) throws IOException, Compiler.Exn {
        int id;
        synchronized(RuntimeCompiler.class) {
            if(nextID == 32 || singleClassLoader == null) {
                singleClassLoader = new SingleClassLoader();
                nextID = 0;
            }
            id = nextID++;
        }
        String className = "nestedvm.runtimecompiled_" + id;
        System.err.println("RuntimeCompiler: Building " + className);
        byte[] bytecode;
        try {
            bytecode = runCompiler(data,className,extraoptions,null);
        } catch(Compiler.Exn e) {
            if(e.getMessage() != null || e.getMessage().indexOf("constant pool full")  != -1)
                bytecode = runCompiler(data,className,extraoptions,"lessconstants");
            else
                throw e;
        }
        return singleClassLoader.fromBytes(className,bytecode);
    }
    
    private static byte[] runCompiler(Seekable data, String name, String options, String moreOptions) throws IOException, Compiler.Exn {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try {
            ClassFileCompiler c = new ClassFileCompiler(data,name,baos);
            c.parseOptions("nosupportcall,maxinsnpermethod=256");
            if(options != null) c.parseOptions(options);
            if(moreOptions != null) c.parseOptions(moreOptions);
            c.go();
        } finally {
            data.seek(0);
        }
        
        baos.close();
        return baos.toByteArray();        
    }
    
    private static class SingleClassLoader extends ClassLoader {
        public Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
            //System.err.println(this + ": loadClass(\"" + name + "," + resolve + ");");
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
        UnixRuntime r = (UnixRuntime) compile(new Seekable.File(args[0]),"unixruntime").newInstance();
        System.err.println("Instansiated: "+ r);
        System.exit(UnixRuntime.runAndExec(r,args));
    }
    
    private RuntimeCompiler() { }
}

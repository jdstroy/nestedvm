package org.ibex.nestedvm;

import java.io.*;

// FEATURE: This is just a quick hack, it is really ugly and broken

// FEATURE: Cache based on org.ibex.util.Cache
// FEATURE: Base64 encode some id to form package name
// FEATURE: Timestamped cache entries, requests carry minimum timestamp
// NOTE: Need to handle binaries spanned accross many classfiles

public class ClassLoader extends java.lang.ClassLoader {
    public Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class c;
        if(name.startsWith("mips.")) {
            String path = name.substring(5).replace('.','/') + ".mips";
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                new ClassFileCompiler(path,name,bos).go();
                bos.close();
                byte[] buf = bos.toByteArray();
                c = defineClass(name,buf,0,buf.length);
            } catch(IOException e) {
                throw new ClassNotFoundException(name);
            } catch(Compiler.Exn e) {
                throw new ClassNotFoundException(e.getMessage());
            }
        } else {
            c = findSystemClass(name);
        }
            
        if(c == null) throw new ClassNotFoundException(name);
        if(resolve) resolveClass(c);
        return c;
    }
    
    public Class classFromBinary(String path) throws ClassNotFoundException {
        if(!path.endsWith(".mips")) throw new IllegalArgumentException("isn't a .mips");
        return loadClass("mips." + path.substring(0,path.length()-5).replace('/','.'));
    }
    
    public Runtime runtimeFromBinary(String path) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return (Runtime) classFromBinary(path).newInstance();
    }
    
    public static void main(String[] args) throws Exception {
        System.exit(new ClassLoader().runtimeFromBinary(args[0]).run(args));
    }
}

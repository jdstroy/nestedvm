package org.ibex.nestedvm;

//import java.io.*;
import java.util.*;

//import org.ibex.nestedvm.util.*;

// FIXME: This is totally broken now

// FEATURE: This is just a quick hack, it is really ugly and broken

// FEATURE: Cache based on org.ibex.util.Cache
// FEATURE: Base64 encode some id to form package name
// FEATURE: Timestamped cache entries, requests carry minimum timestamp
// NOTE: Need to handle binaries spanned accross many classfiles

public class ClassLoader extends java.lang.ClassLoader {
    private Hashtable cache = new Hashtable();
    
    public Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class c;
        if(name.startsWith("nestedvm.")) {
            throw new Error("probably shouldn't be here");
            /*String path = name.substring(5).replace('.','/') + ".mips";
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
            }*/
        } else {
            c = findSystemClass(name);
        }
            
        if(c == null) throw new ClassNotFoundException(name);
        if(resolve) resolveClass(c);
        return c;
    }
    
    /*public synchronized void clearCache() {
        cache.clear();
    }
    
    public synchronized Class getCachedClass(String key, long timeStamp) {
        String name = "nestedvm." + Base64.encode(key);
        CacheEnt ent = (CacheEnt) cache.get(name);
        if(ent.timeStamp < timeStamp) {
        	    cache.remove(key);
            return null;
        }
        return ent.klass;
    }
    
    public synchronized Class createClass(String key, long timeStamp, Seekable data) {
    	    Class klass = getCachedClass(key,timeStamp);
        if(klass != null) return klass;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            new ClassFileCompiler(data,name,bos).go();
            bos.close();
            byte[] buf = bos.toByteArray();
            klass = defineClass(name,buf,0,buf.length);
            resolveClass(klass);
            cache.put(key,new CacheEnt(klass,timeStamp));
            return klass;
        } catch(Exception e) {
        	    e.printStackTrace();
            return null;
        }
    }
    
    private class CacheEnt {
        public CacheEnt(Class klass, long timeStamp) { this.klass = klass; this.timeStamp = timeStamp; }
    	    public Class klass;
        public long timeStamp;
    }*/
    
    /*public Class classFromBinary(String path) throws ClassNotFoundException {
        if(!path.endsWith(".mips")) throw new IllegalArgumentException("isn't a .mips");
        return loadClass("mips." + path.substring(0,path.length()-5).replace('/','.'));
    }
    
    public Runtime runtimeFromBinary(String path) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return (Runtime) classFromBinary(path).newInstance();
    }
    
    public static void main(String[] args) throws Exception {
        System.exit(new ClassLoader().runtimeFromBinary(args[0]).run(args));
    }*/
}

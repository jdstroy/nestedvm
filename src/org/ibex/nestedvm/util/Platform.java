// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache Public Source License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm.util;

import java.io.*;
import java.net.*;
import java.util.*;

import java.text.DateFormatSymbols;

/*
 GCCLASS_HINT: org.ibex.nestedvm.util.Platform.<clinit> org.ibex.nestedvm.util.Platform$Jdk11.<init>
 GCCLASS_HINT: org.ibex.nestedvm.util.Platform.<clinit> org.ibex.nestedvm.util.Platform$Jdk12.<init>
 GCCLASS_HINT: org.ibex.nestedvm.util.Platform.<clinit> org.ibex.nestedvm.util.Platform$Jdk13.<init>
 GCCLASS_HINT: org.ibex.nestedvm.util.Platform.<clinit> org.ibex.nestedvm.util.Platform$Jdk14.<init>
*/

public abstract class Platform {
    Platform() { }
    private static final Platform p;
    
    static {
        float version;
        try {
            if(getProperty("java.vm.name").equals("SableVM"))
                version = 1.2f;
            else
                version = Float.valueOf(getProperty("java.specification.version")).floatValue();
        } catch(Exception e) {
            System.err.println("WARNING: " + e + " while trying to find jvm version -  assuming 1.1");
            version = 1.1f;
        }
        String platformClass;
        if(version >= 1.4f) platformClass = "Jdk14";
        else if(version >= 1.3f) platformClass = "Jdk13";
        else if(version >= 1.2f) platformClass = "Jdk12";
        else if(version >= 1.1f) platformClass = "Jdk11";
        else throw new Error("JVM Specification version: " + version + " is too old. (see org.ibex.util.Platform to add support)");
        
        try {
            p = (Platform) Class.forName(Platform.class.getName() + "$" + platformClass).newInstance();
        } catch(Exception e) {
            e.printStackTrace();
            throw new Error("Error instansiating platform class");
        }
    }
    
    public static String getProperty(String key) {
        try {
            return System.getProperty(key);
        } catch(SecurityException e) {
            return null;
        }
    }
    
    
    abstract boolean _atomicCreateFile(File f) throws IOException;
    public static boolean atomicCreateFile(File f) throws IOException { return p._atomicCreateFile(f); }
    
    abstract void _socketHalfClose(Socket s, boolean output) throws IOException;
    public static void socketHalfClose(Socket s, boolean output) throws IOException { p._socketHalfClose(s,output); }
    
    abstract void _socketSetKeepAlive(Socket s, boolean on) throws SocketException;
    public static void socketSetKeepAlive(Socket s, boolean on) throws SocketException { p._socketSetKeepAlive(s,on); }
    
    abstract InetAddress _inetAddressFromBytes(byte[] a) throws UnknownHostException;
    public static InetAddress inetAddressFromBytes(byte[] a) throws UnknownHostException { return p._inetAddressFromBytes(a); }
    
    abstract String _timeZoneGetDisplayName(TimeZone tz, boolean dst, boolean showlong, Locale l);
    public static String timeZoneGetDisplayName(TimeZone tz, boolean dst, boolean showlong, Locale l) { return p._timeZoneGetDisplayName(tz,dst,showlong,l); }
    public static String timeZoneGetDisplayName(TimeZone tz, boolean dst, boolean showlong) { return timeZoneGetDisplayName(tz,dst,showlong,Locale.getDefault()); }
    
    abstract RandomAccessFile _truncatedRandomAccessFile(File f, String mode) throws IOException;
    public static RandomAccessFile truncatedRandomAccessFile(File f, String mode) throws IOException { return p._truncatedRandomAccessFile(f,mode); }
    
    static class Jdk11 extends Platform {
        boolean _atomicCreateFile(File f) throws IOException {
            // This is not atomic, but its the best we can do on jdk 1.1
            if(f.exists()) return false;
            new FileOutputStream(f).close();
            return true;
        }
        void _socketHalfClose(Socket s, boolean output) throws IOException {
            throw new IOException("half closing sockets not supported");
        }
        InetAddress _inetAddressFromBytes(byte[] a) throws UnknownHostException {
            if(a.length != 4) throw new UnknownHostException("only ipv4 addrs supported");
            return InetAddress.getByName(""+(a[0]&0xff)+"."+(a[1]&0xff)+"."+(a[2]&0xff)+"."+(a[3]&0xff));
        }
        void _socketSetKeepAlive(Socket s, boolean on) throws SocketException {
            if(on) throw new SocketException("keepalive not supported");
        }
        String _timeZoneGetDisplayName(TimeZone tz, boolean dst, boolean showlong, Locale l) {
            String[][] zs  = new DateFormatSymbols(l).getZoneStrings();
            String id = tz.getID();
            for(int i=0;i<zs.length;i++)
                if(zs[i][0].equals(id))
                    return zs[i][dst ? (showlong ? 3 : 4) : (showlong ? 1 : 2)];
            StringBuffer sb = new StringBuffer("GMT");
            int off = tz.getRawOffset() / 1000;
            if(off < 0) { sb.append("-"); off = -off; }
            else sb.append("+");
            sb.append(off/3600); off = off%3600;
            if(off > 0) sb.append(":").append(off/60); off=off%60;
            if(off > 0) sb.append(":").append(off);
            return sb.toString();
        }
        
        RandomAccessFile _truncatedRandomAccessFile(File f, String mode) throws IOException {
            new FileOutputStream(f).close();
            return new RandomAccessFile(f,mode);
        }
    }
    
    static class Jdk12 extends Jdk11 {
        boolean _atomicCreateFile(File f) throws IOException {
            return f.createNewFile();
        }
        
        String _timeZoneGetDisplayName(TimeZone tz, boolean dst, boolean showlong, Locale l) {
            return tz.getDisplayName(dst,showlong ? TimeZone.LONG : TimeZone.SHORT, l);
        }
        
        RandomAccessFile _truncatedRandomAccessFile(File f, String mode) throws IOException {
            RandomAccessFile raf = new RandomAccessFile(f,mode);
            raf.setLength(0);
            return raf;
        }       
    }
    
    static class Jdk13 extends Jdk12 {
        void _socketHalfClose(Socket s, boolean output) throws IOException {
            if(output) s.shutdownOutput();
            else s.shutdownInput();
        }
        
        void _socketSetKeepAlive(Socket s, boolean on) throws SocketException {
            s.setKeepAlive(on);
        }
    }
    
    static class Jdk14 extends Jdk13 {
        InetAddress _inetAddressFromBytes(byte[] a) throws UnknownHostException { return InetAddress.getByAddress(a); } 
    }
}

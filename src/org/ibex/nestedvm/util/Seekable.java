// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache Public Source License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm.util;

import java.io.*;

public abstract class Seekable { 
    public abstract int read(byte[] buf, int offset, int length) throws IOException;
    public abstract int write(byte[] buf, int offset, int length) throws IOException;
    public abstract int length() throws IOException;
    public abstract void seek(int pos) throws IOException;
    public abstract void close() throws IOException;
    public abstract int pos() throws IOException;
    
    public int read() throws IOException {
        byte[] buf = new byte[1];
        int n = read(buf,0,1);
        return n == -1 ? -1 : buf[0]&0xff;
    }
    
    public int tryReadFully(byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while(len > 0) {
                int n = read(buf,off,len);
                if(n == -1) break;
                off += n;
                len -= n;
            total += n;
        }
        return total == 0 ? -1 : total;
    }
    
    public static class ByteArray extends Seekable {
        protected byte[] data;
        protected int pos;
        private final boolean writable;
        
        public ByteArray(byte[] data, boolean writable) {
            this.data = data;
            this.pos = 0;
            this.writable = writable;
        }
        
        public int read(byte[] buf, int off, int len) {
            len = Math.min(len,data.length-pos);
            if(len <= 0) return -1;
            System.arraycopy(data,pos,buf,off,len);
            pos += len;
            return len;
        }
                
        public int write(byte[] buf, int off, int len) throws IOException {
            if(!writable) throw new IOException("read-only data");
            len = Math.min(len,data.length-pos);
            if(len <= 0) throw new IOException("no space");
            System.arraycopy(buf,off,data,pos,len);        
            pos += len;
            return len;
        }
        
        public int length() { return data.length; }
        public int pos() { return pos; }
        public void seek(int pos) { this.pos = pos; }
        public void close() { /*noop*/ }
    }
    
    public static class File extends Seekable {
        private final RandomAccessFile raf;
        
        public File(String fileName) throws IOException { this(fileName,false); }
        public File(String fileName, boolean writable) throws IOException { this(new java.io.File(fileName),writable,false); }    
        
        public File(java.io.File file, boolean writable, boolean truncate) throws IOException {
            String mode = writable ? "rw" : "r";
            raf = truncate ? Platform.truncatedRandomAccessFile(file,mode) : new RandomAccessFile(file,mode);
        }
        
        public int read(byte[] buf, int offset, int length) throws IOException { return raf.read(buf,offset,length); }
        public int write(byte[] buf, int offset, int length) throws IOException { raf.write(buf,offset,length); return length; }
        public void seek(int pos) throws IOException{ raf.seek(pos); }
        public int pos()  throws IOException { return (int) raf.getFilePointer(); }
        public int length() throws IOException { return (int)raf.length(); }
        public void close() throws IOException { raf.close(); }
    }
    
    public static class InputStream extends Seekable {
        private byte[] buffer = new byte[4096];
        private int bytesRead = 0;
        private boolean eof = false;
        private int pos;
        private java.io.InputStream is;
        
        public InputStream(java.io.InputStream is) { this.is = is; }
        
        public int read(byte[] outbuf, int off, int len) throws IOException {
            if(pos >= bytesRead && !eof) readTo(pos + 1);
            len = Math.min(len,bytesRead-pos);
            if(len <= 0) return -1;
            System.arraycopy(buffer,pos,outbuf,off,len);
            pos += len;
            return len;
        }
        
        private void readTo(int target) throws IOException {
            if(target >= buffer.length) {
                byte[] buf2 = new byte[Math.max(buffer.length+Math.min(buffer.length,65536),target)];
                System.arraycopy(buffer,0,buf2,0,bytesRead);
                buffer = buf2;
            }
            while(bytesRead < target) {
                int n = is.read(buffer,bytesRead,buffer.length-bytesRead);
                if(n == -1) {
                    eof = true;
                    break;
                }
                bytesRead += n;
            }
        }
        
        public int length() throws IOException {
            while(!eof) readTo(bytesRead+4096);
            return bytesRead;
        }
        
        public int write(byte[] buf, int off, int len) throws IOException { throw new IOException("read-only"); }
        public void seek(int pos) { this.pos = pos; }
        public int pos() { return pos; }
        public void close() throws IOException { is.close(); }
    }
    
}

package org.xwt.mips.util;

import java.io.IOException;

public class SeekableByteArray implements SeekableData {
    protected byte[] data;
    protected int pos;
    private final boolean writable;
    
    public SeekableByteArray(byte[] data, boolean writable) {
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

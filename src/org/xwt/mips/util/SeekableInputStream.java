package org.xwt.mips.util;

import java.io.*;

public class SeekableInputStream implements SeekableData {
    private byte[] buffer = new byte[4096];
    private int bytesRead = 0;
    private boolean eof = false;
    private int pos;
    private InputStream is;
    
    public SeekableInputStream(InputStream is) { this.is = is; }
    
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

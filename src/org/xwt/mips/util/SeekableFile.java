package org.xwt.mips.util;

import java.io.*;

public class SeekableFile implements SeekableData {
    private final RandomAccessFile raf;
    
    public SeekableFile(String fileName) throws IOException { this(fileName,false); }
    public SeekableFile(String fileName, boolean writable) throws IOException { this(new File(fileName),writable); }    
    
    public SeekableFile(File file, boolean writable) throws IOException {
        raf = new RandomAccessFile(file,writable ? "rw" : "r");
    }
    
    // NOTE: RandomAccessFile.setLength() is a Java2 function
    public void setLength(int n) throws IOException { raf.setLength(n); }
    
    public int read(byte[] buf, int offset, int length) throws IOException { return raf.read(buf,offset,length); }
    public int write(byte[] buf, int offset, int length) throws IOException { raf.write(buf,offset,length); return length; }
    public void seek(int pos) throws IOException{ raf.seek(pos); }
    public int pos()  throws IOException { return (int) raf.getFilePointer(); }
    public int length() throws IOException { return (int)raf.length(); }
    public void close() throws IOException { raf.close(); }
}

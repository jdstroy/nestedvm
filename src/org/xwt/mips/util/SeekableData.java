package org.xwt.mips.util;

import java.io.IOException;

public interface SeekableData { 
    public int read(byte[] buf, int offset, int length) throws IOException;
    public int write(byte[] buf, int offset, int length) throws IOException;
    public int length() throws IOException;
    public void seek(int pos) throws IOException;
    public void close() throws IOException;
    public int pos() throws IOException;
}

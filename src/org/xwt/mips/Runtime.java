// Copyright 2003 Brian Alliet
// Based on org.xwt.imp.MIPS by Adam Megacz
// Portions Copyright 2003 Adam Megacz

package org.xwt.mips;

import org.xwt.mips.util.*;
import java.io.*;
import java.util.Arrays;

// FEATURE: Look over the public API, make sure we're exposing a bare minimum
// (we might make this an interface in the future)

public abstract class Runtime implements UsermodeConstants,Registers {
    /** Pages are 4k in size */
    protected final int PAGE_SIZE;
    protected final int PAGE_WORDS;
    protected final int PAGE_SHIFT;
    protected final int TOTAL_PAGES;
    /** This is the upper limit of the pages allocated by the sbrk() syscall. */
    protected final int BRK_LIMIT;
    protected final int STACK_BOTTOM;

    /** This is the maximum size of command line arguments */
    public final static int ARGS_MAX = 1024*1024;
    
    /** True if we allow empty pages (_emptyPage) to exist in memory.
       Empty pages are pages which are allocated by the program but do not contain any
       data yet (they are all 0s). If empty pages are allowed subclasses must always
       access main memory with the memRead and memWrite functions */
    private final boolean allowEmptyPages;
    /** the "empty page" */
    private final static int[] _emptyPage = new int[0];
    
    protected final static boolean isEmptyPage(int[] page) { return page == _emptyPage; }
    
    /** Returns a new empty page (_emptyPage is empty pages are enabled or a new zero'd page) */
    private final int[] emptyPage() { return allowEmptyPages ? _emptyPage : new int[PAGE_WORDS]; }
    
    /** Readable main memory pages */
    protected final int[][] readPages;
    /** Writable main memory pages.
        If the page is writable writePages[x] == readPages[x]; if not writePages[x] == null. */
    protected final int[][] writePages;
    
    /** The current break between the heap and unallocated memory */
    protected int brkAddr;
        
    /** The program's entry point */
    protected int entryPoint;

    /** The location of the _user_info block (or 0 is there is none) */
    protected int userInfoBase;
    protected int userInfoSize;
    
    /** The location of the global pointer */
    protected int gp;
    
    /** When the process started */
    private long startTime;
    
    /** State constant: There is no program loaded in memory */
    public final static int UNINITIALIZED = 0; 
    /**  Text/Data loaded in memory  */
    public final static int INITIALIZED = 1;
    /** Program is executing instructions */
    public final static int RUNNING = 2;
    /** Prgram has been started but is paused */
    public final static int PAUSED = 3;
    /** Program is executing a callJava() method */
    public final static int CALLJAVA = 4;
    /** Program has exited (it cannot currently be restarted) */
    public final static int DONE = 5;
        
    /** The current state (UNINITIALIZED, INITIALIZED, RUNNING, PAUSED, or DONE) */
    protected int state = UNINITIALIZED;
    /** @see Runtime#state state */
    public final int getState() { return state; }
    
    /** The exit status if the process (only valid if state==DONE) 
        @see Runtime#state */
    protected int exitStatus;
    public ExecutionException exitException;
    
    /** Maximum number of open file descriptors */
    final static int OPEN_MAX = 256;
    /** Table containing all open file descriptors. (Entries are null if the fd is not in use */
    FD[] fds  = new FD[OPEN_MAX];
        
    /** Temporary buffer for read/write operations */
    private byte[] _byteBuf = null;
    /** Max size of temporary buffer
        @see Runtime#_byteBuf */
    private final static int MAX_CHUNK = 15*1024*1024;
        
    /** Subclasses should actually execute program in this method. They should continue 
        executing until state != RUNNING. Only syscall() can modify state. It is safe 
        to only check the state attribute after a call to syscall() */
    protected abstract void _execute() throws ExecutionException;
    
    /** Subclasses should return the address of the symbol <i>symbol</i> or -1 it it doesn't exits in this method 
        This method is only required if the call() function is used */
    protected int lookupSymbol(String symbol) { return -1; }
    
    /** Subclasses should returns a CPUState object representing the cpu state */
    protected abstract CPUState getCPUState();
    
    /** Subclasses should set the CPUState to the state held in <i>state</i> */
    protected abstract void setCPUState(CPUState state);

    static void checkPageSize(int pageSize, int totalPages) throws IllegalArgumentException {
        if(pageSize < 256) throw new IllegalArgumentException("pageSize too small");
        if((pageSize&(pageSize-1)) != 0) throw new IllegalArgumentException("pageSize must be a power of two");
        if((totalPages&(totalPages-1)) != 0) throw new IllegalArgumentException("totalPages must be a power of two");
        if(totalPages != 1 && totalPages < 256) throw new IllegalArgumentException("totalPages too small");
        if(totalPages * pageSize < 4*1024*1024) throw new IllegalArgumentException("total memory too small (" + totalPages + "*" + pageSize + ")");
    }
    
    protected Runtime(int pageSize, int totalPages, boolean allowEmptyPages) {
        this.allowEmptyPages = allowEmptyPages;
        
        checkPageSize(pageSize,totalPages);
        
        PAGE_SIZE = pageSize;
        PAGE_WORDS = pageSize>>>2;
        int pageShift = 0;
        while(pageSize>>>pageShift != 1) pageShift++;
        PAGE_SHIFT = pageShift;
        
        TOTAL_PAGES = totalPages;
        
        readPages = new int[TOTAL_PAGES][];
        writePages = new int[TOTAL_PAGES][];
        
        if(TOTAL_PAGES == 1) {
            readPages[0] = writePages[0] = new int[PAGE_WORDS];
            BRK_LIMIT = STACK_BOTTOM = 0;
        } else {
            int stackPages = max(TOTAL_PAGES>>>8,(1024*1024)>>>PAGE_SHIFT);
            STACK_BOTTOM = (TOTAL_PAGES - stackPages) * PAGE_SIZE;
            // leave some unmapped pages between the stack and the heap
            BRK_LIMIT = STACK_BOTTOM - 4*PAGE_SIZE;
        
            for(int i=0;i<stackPages;i++)
                readPages[TOTAL_PAGES-1-i] = writePages[TOTAL_PAGES-1-i] = emptyPage();
        }
        
        addFD(new StdinFD(System.in));
        addFD(new StdoutFD(System.out));
        addFD(new StdoutFD(System.err));
    }
    
    /** Copy everything from <i>src</i> to <i>addr</i> initializing uninitialized pages if required. 
       Newly initalized pages will be marked read-only if <i>ro</i> is set */
    protected final void initPages(int[] src, int addr, boolean ro) {
        for(int i=0;i<src.length;) {
            int page = addr >>> PAGE_SHIFT;
            int start = (addr&(PAGE_SIZE-1))>>2;
            int elements = min(PAGE_WORDS-start,src.length-i);
            if(readPages[page]==null) {
                initPage(page,ro);
            } else if(!ro) {
                if(writePages[page] == null) writePages[page] = readPages[page];
            }
            System.arraycopy(src,i,readPages[page],start,elements);
            i += elements;
            addr += elements*4;
        }
    }
    
    /** Initialize <i>words</i> of pages starting at <i>addr</i> to 0 */
    protected final void clearPages(int addr, int words) {
        for(int i=0;i<words;) {
            int page = addr >>> PAGE_SHIFT;
            int start = (addr&(PAGE_SIZE-1))>>2;
            int elements = min(PAGE_WORDS-start,words-i);
            if(readPages[page]==null) {
                readPages[page] = writePages[page] = emptyPage();
            } else {
                if(writePages[page] == null) writePages[page] = readPages[page];
                for(int j=start;j<start+elements;j++) writePages[page][j] = 0;
            }
            i += elements;
            addr += elements*4;
        }
    }
    
    /** Copies <i>length</i> bytes from the processes memory space starting at
        <i>addr</i> INTO a java byte array <i>a</i> */
    public final void copyin(int addr, byte[] buf, int count) throws ReadFaultException {
        int x=0;
        if((addr&3)!=0) {
            int word = memRead(addr&~3);
            switch(addr&3) {
                case 1: buf[x++] = (byte)((word>>>16)&0xff); if(--count==0) break;
                case 2: buf[x++] = (byte)((word>>> 8)&0xff); if(--count==0) break;
                case 3: buf[x++] = (byte)((word>>> 0)&0xff); if(--count==0) break;
            }
            addr = (addr&~3)+4;
        }
        if((count&~3) != 0) {
            int c = count>>>2;
            int a = addr>>>2;
            while(c != 0) {
                int[] page = readPages[a >>> (PAGE_SHIFT-2)];
                if(page == null) throw new ReadFaultException(a<<2);
                int index = a&(PAGE_WORDS-1);
                int n = min(c,PAGE_WORDS-index);
                if(page != _emptyPage) {
                    for(int i=0;i<n;i++,x+=4) {
                        int word = page[index+i];
                        buf[x+0] = (byte)((word>>>24)&0xff); buf[x+1] = (byte)((word>>>16)&0xff);
                        buf[x+2] = (byte)((word>>> 8)&0xff); buf[x+3] = (byte)((word>>> 0)&0xff);                        
                    }
                }
                a += n; c -=n;
            }
            addr = a<<2; count &=3;
        }
        if(count != 0) {
            int word = memRead(addr);
            switch(count) {
                case 3: buf[x+2] = (byte)((word>>>8)&0xff);
                case 2: buf[x+1] = (byte)((word>>>16)&0xff);
                case 1: buf[x+0] = (byte)((word>>>24)&0xff);
            }
        }
    }
    
    /** Copies <i>length</i> bytes OUT OF the java array <i>a</i> into the processes memory
        space at <i>addr</i> */
    public final void copyout(byte[] buf, int addr, int count) throws FaultException {
        int x=0;
        if((addr&3)!=0) {
            int word = memRead(addr&~3);
            switch(addr&3) {
                case 1: word = (word&0xff00ffff)|((buf[x++]&0xff)<<16); if(--count==0) break;
                case 2: word = (word&0xffff00ff)|((buf[x++]&0xff)<< 8); if(--count==0) break;
                case 3: word = (word&0xffffff00)|((buf[x++]&0xff)<< 0); if(--count==0) break;
            }
            memWrite(addr&~3,word);
            addr += x;
        }
        if((count&~3) != 0) {
            int c = count>>>2;
            int a = addr>>>2;
            while(c != 0) {
                int[] page = writePages[a >>> (PAGE_SHIFT-2)];
                if(page == null) throw new WriteFaultException(a<<2);
                if(page == _emptyPage) page = initPage(a >>> (PAGE_SHIFT-2));
                int index = a&(PAGE_WORDS-1);
                int n = min(c,PAGE_WORDS-index);
                for(int i=0;i<n;i++,x+=4)
                    page[index+i] = ((buf[x+0]&0xff)<<24)|((buf[x+1]&0xff)<<16)|((buf[x+2]&0xff)<<8)|((buf[x+3]&0xff)<<0);
                a += n; c -=n;
            }
            addr = a<<2; count&=3;
        }
        if(count != 0) {
            int word = memRead(addr);
            switch(count) {
                case 1: word = (word&0x00ffffff)|((buf[x+0]&0xff)<<24); break;
                case 2: word = (word&0x0000ffff)|((buf[x+0]&0xff)<<24)|((buf[x+1]&0xff)<<16); break;
                case 3: word = (word&0x000000ff)|((buf[x+0]&0xff)<<24)|((buf[x+1]&0xff)<<16)|((buf[x+2]&0xff)<<8); break;
            }
            memWrite(addr,word);
        }
    }
    
    public final void memcpy(int dst, int src, int count) throws FaultException {
        if((dst&3) == 0 && (src&3)==0) {
            if((count&~3) != 0) {
                int c = count>>2;
                int s = src>>>2;
                int d = dst>>>2;
                while(c != 0) {
                    int[] srcPage = readPages[s>>>(PAGE_SHIFT-2)];
                    if(srcPage == null) throw new ReadFaultException(s<<2);
                    int[] dstPage = writePages[d>>>(PAGE_SHIFT-2)];
                    if(dstPage == null) throw new WriteFaultException(d<<2);
                    int srcIndex = (s&(PAGE_WORDS-1));
                    int dstIndex = (d&(PAGE_WORDS-1));
                    int n = min(c,PAGE_WORDS-max(srcIndex,dstIndex));
                    if(srcPage != _emptyPage) {
                        if(dstPage == _emptyPage) dstPage = initPage(d>>>(PAGE_SHIFT-2));
                        System.arraycopy(srcPage,srcIndex,dstPage,dstIndex,n);
                    } else if(srcPage == _emptyPage && dstPage != _emptyPage) {
                        Arrays.fill(dstPage,dstIndex,dstIndex+n,0);
                    }
                    s += n; d += n; c -= n;
                }
                src = s<<2; dst = d<<2; count&=3;
            }
            if(count != 0) {
                int word1 = memRead(src);
                int word2 = memRead(dst);
                switch(count) {
                    case 1: memWrite(dst,(word1&0xff000000)|(word2&0x00ffffff)); break;
                    case 2: memWrite(dst,(word1&0xffff0000)|(word2&0x0000ffff)); break;
                    case 3: memWrite(dst,(word1&0xffffff00)|(word2&0x000000ff)); break;
                }
            }
        } else {
            while(count > 0) {
                int n = min(count,MAX_CHUNK);
                byte[] buf = byteBuf(n);
                copyin(src,buf,n);
                copyout(buf,dst,n);
                count -= n; src += n; dst += n;
            }
        }
    }
    
    public final void memset(int addr, int ch, int count) throws FaultException {
        int fourBytes = ((ch&0xff)<<24)|((ch&0xff)<<16)|((ch&0xff)<<8)|((ch&0xff)<<0);
        if((addr&3)!=0) {
            int word = memRead(addr&~3);
            switch(addr&3) {
                case 1: word = (word&0xff00ffff)|((ch&0xff)<<16); if(--count==0) break;
                case 2: word = (word&0xffff00ff)|((ch&0xff)<< 8); if(--count==0) break;
                case 3: word = (word&0xffffff00)|((ch&0xff)<< 0); if(--count==0) break;
            }
            memWrite(addr&~3,word);
            addr = (addr&~3)+4;
        }
        if((count&~3) != 0) {
            int c = count>>2;
            int a = addr>>>2;
            while(c != 0) {
                int[] page = readPages[a>>>(PAGE_SHIFT-2)];
                if(page == null) throw new WriteFaultException(a<<2);
                int index = (a&(PAGE_WORDS-1));
                int n = min(c,PAGE_WORDS-index);
                if(page != _emptyPage || ch != 0) {
                    if(page == _emptyPage) page = initPage(a>>>(PAGE_SHIFT-2));
                    Arrays.fill(page,index,index+n,fourBytes);
                }
                a += n; c -= n;
            }
            addr = a<<2; count&=3;
        }
        if(count != 0) {
            int word = memRead(addr);
            switch(count) {
                case 1: word = (word&0x00ffffff)|(fourBytes&0xff000000); break;
                case 2: word = (word&0x0000ffff)|(fourBytes&0xffff0000); break;
                case 3: word = (word&0x000000ff)|(fourBytes&0xffffff00); break;
            }
            memWrite(addr,word);
        }
    }
    
    /** Read a word from the processes memory at <i>addr</i> */
    public final int memRead(int addr) throws ReadFaultException  {
        if((addr & 3) != 0) throw new ReadFaultException(addr);
        return unsafeMemRead(addr);
    }
       
    protected final int unsafeMemRead(int addr) throws ReadFaultException {
        int page = addr >>> PAGE_SHIFT;
        int entry = (addr >>> 2) & (PAGE_WORDS-1);
        try {
            return readPages[page][entry];
        } catch(ArrayIndexOutOfBoundsException e) {
            if(page < 0) throw e; // should never happen
            if(page >= readPages.length) throw new ReadFaultException(addr);
            if(readPages[page] != _emptyPage) throw e; // should never happen
            initPage(page);
            return 0;
        } catch(NullPointerException e) {
            throw new ReadFaultException(addr);
        }
    }
    
    /** Writes a word to the processes memory at <i>addr</i> */
    public final void memWrite(int addr, int value) throws WriteFaultException  {
        if((addr & 3) != 0) throw new WriteFaultException(addr);
        unsafeMemWrite(addr,value);
    }
    
    protected final void unsafeMemWrite(int addr, int value) throws WriteFaultException {
        int page = addr >>> PAGE_SHIFT;
        int entry = (addr>>>2)&(PAGE_WORDS-1);
        try {
            writePages[page][entry] = value;
        } catch(ArrayIndexOutOfBoundsException e) {
            if(page < 0) throw e;// should never happen
            if(page >= writePages.length) throw new WriteFaultException(addr);
            if(readPages[page] != _emptyPage) throw e; // should never happen
            initPage(page);
            writePages[page][entry] = value;
        } catch(NullPointerException e) {
            throw new WriteFaultException(addr);
        }
    }
    
    /** Created a new non-empty writable page at page number <i>page</i> */
    private final int[] initPage(int page) { return initPage(page,false); }
    /** Created a new non-empty page at page number <i>page</i>. If <i>ro</i> is set the page will be read-only */
    private final int[] initPage(int page, boolean ro) {
        int[] buf = new int[PAGE_WORDS];
        writePages[page] = ro ? null : buf;
        readPages[page] = buf;
        return buf;
    }
    
    /** Returns the exit status of the process. (only valid if state == DONE) 
        @see Runtime#state */
    public final int exitStatus() {
        if(state != DONE) throw new IllegalStateException("exitStatus() called in an inappropriate state");
        return exitStatus;
    }
        
    private int addStringArray(String[] strings, int topAddr) {
        int count = strings.length;
        int total = 0; /* null last table entry  */
        for(int i=0;i<count;i++) total += strings[i].length() + 1;
        if(total >= ARGS_MAX) throw new IllegalArgumentException("arguments/environ too big");
        total += (count+1)*4;
        int start = (topAddr - total)&~3;
        int addr = start + (count+1)*4;
        int[] table = new int[count+1];
        try {
            for(int i=0;i<count;i++) {
                byte[] a = getBytes(strings[i]);
                table[i] = addr;
                copyout(a,addr,a.length);
                memset(addr+a.length,0,1);
                addr += a.length + 1;
            }
            addr=start;
            for(int i=0;i<count+1;i++) {
                memWrite(addr,table[i]);
                addr += 4;
            }
        } catch(FaultException e) {
            // should never happen
            throw new Error(e.toString());
        }
        return start;
    }
    
    protected String[] createEnv(String[] extra) { if(extra == null) extra = new String[0]; return extra; }
    
    /** Sets word number <i>index</i> in the _user_info table to <i>word</i>
     * The user_info table is a chunk of memory in the program's memory defined by the
     * symbol "user_info". The compiler/interpreter automatically determine the size
     * and location of the user_info table from the ELF symbol table. setUserInfo and
     * getUserInfo are used to modify the words in the user_info table. */
    public void setUserInfo(int index, int word) {
        if(index < 0 || index >= userInfoSize/4) throw new IndexOutOfBoundsException("setUserInfo called with index >= " + (userInfoSize/4));
        try {
            memWrite(userInfoBase+index*4,word);
        } catch(FaultException e) { throw new Error("should never happen: " + e); }
    }
    
    /** Returns the word in the _user_info table entry <i>index</i>
        @see Runtime#setUserInfo(int,int) setUserInfo */
    public int getUserInfo(int index) {
        if(index < 0 || index >= userInfoSize/4) throw new IndexOutOfBoundsException("setUserInfo called with index >= " + (userInfoSize/4));
        try {
            return memRead(userInfoBase+index*4);
        } catch(FaultException e) { throw new Error("should never happen: " + e); }
    }
    
    /** Calls _execute() (subclass's execute()) and catches exceptions */
    private void __execute() {
        try {
            _execute();
        } catch(FaultException e) {
            e.printStackTrace();
            sys_exit(128+11); // SIGSEGV
            exitException = e;
        } catch(ExecutionException e) {
            e.printStackTrace();
            System.err.println(e);
            sys_exit(128+4); // SIGILL
            exitException = e;
        }
    }
    
    /** Executes the process until the PAUSE syscall is invoked or the process exits. Returns true if the process exited. */
    public final boolean execute()  {
        if(state != PAUSED) throw new IllegalStateException("execute() called in inappropriate state");
        if(startTime == 0) startTime = System.currentTimeMillis();
        state = RUNNING;
        __execute();
        if(state != PAUSED && state != DONE) throw new IllegalStateException("execute() ended up in an inappropriate state (" + state + ")");
        return state == DONE;
    }
    
    public final int run() { return run(null); }
    public final int run(String argv0, String[] rest) {
        String[] args = new String[rest.length+1];
        System.arraycopy(rest,0,args,1,rest.length);
        args[0] = argv0;
        return run(args);
    }
    public final int run(String[] args) { return run(args,null); }
    
    /** Runs the process until it exits and returns the exit status.
        If the process executes the PAUSE syscall execution will be paused for 500ms and a warning will be displayed */
    public final int run(String[] args, String[] env) {
        start(args,env);
        for(;;) {
            if(execute()) break;
            System.err.println("WARNING: Pause requested while executing run()");
            try { Thread.sleep(500); } catch(InterruptedException e) { /* noop */ }
        }
        return exitStatus();
    }

    public final void start() { start(null); }
    public final void start(String[] args) { start(args,null); }
    
    /** Initializes the process and prepairs it to be executed with execute() */
    public final void start(String[] args, String[] environ)  {
        int sp, argsAddr, envAddr;
        if(state != INITIALIZED) throw new IllegalStateException("start() called in inappropriate state");

        if(args == null) args = new String[]{getClass().getName()};
        
        sp = TOTAL_PAGES*PAGE_SIZE-512;
        sp = argsAddr = addStringArray(args,sp);
        sp = envAddr = addStringArray(createEnv(environ),sp);
        sp &= ~15;
        
        CPUState cpuState = new CPUState();
        cpuState.r[A0] = argsAddr;
        cpuState.r[A1] = envAddr;
        cpuState.r[SP] = sp;
        cpuState.r[RA] = 0xdeadbeef;
        cpuState.r[GP] = gp;
        cpuState.pc = entryPoint;
        setCPUState(cpuState);
                
        state = PAUSED;
        
        _start();
    }
    
    /** Hook for subclasses to do their own startup */
    protected void _start() { /* noop */ }
    
    public final int call(String sym) throws CallException { return call(sym,0,0,0,0,0,0,0); }
    public final int call(String sym, int a0) throws CallException  { return call(sym,a0,0,0,0,0,0,0); }
    public final int call(String sym, int a0, int a1) throws CallException  { return call(sym,a0,a1,0,0,0,0,0); }
    public final int call(String sym, int a0, int a1, int a2) throws CallException  { return call(sym,a0,a1,a2,0,0,0,0); }
    public final int call(String sym, int a0, int a1, int a2, int a3) throws CallException  { return call(sym,a0,a1,a2,a3,0,0,0); }
    public final int call(String sym, int a0, int a1, int a2, int a3, int a4) throws CallException  { return call(sym,a0,a1,a2,a3,a4,0,0); }
    public final int call(String sym, int a0, int a1, int a2, int a3, int a4, int a5) throws CallException  { return call(sym,a0,a1,a2,a3,a4,a5,0); }
    
    /** Calls a function in the process with the given arguments */
    public final int call(String sym, int a0, int a1, int a2, int a3, int a4, int a5, int a6) throws CallException {
        int func = lookupSymbol(sym);
        if(func == -1) throw new CallException(sym + " not found");
        int helper = lookupSymbol("_call_helper");
        if(helper == -1) throw new CallException("_call_helper not found");
        return call(helper,func,a0,a1,a2,a3,a4,a5,a6);
    }
    
    /** Executes the code at <i>addr</i> in the process setting A0-A3 and S0-S3 to the given arguments
        and returns the contents of V1 when the the pause syscall is invoked */
    public final int call(int addr, int a0, int a1, int a2, int a3, int s0, int s1, int s2, int s3) {
        if(state != PAUSED && state != CALLJAVA) throw new IllegalStateException("call() called in inappropriate state");
        int oldState = state;
        CPUState saved = getCPUState();
        CPUState cpustate = new CPUState();
        cpustate.r[SP] = saved.r[SP]&~15;
        cpustate.r[RA] = 0xdeadbeef;
        cpustate.r[A0] = a0;
        cpustate.r[A1] = a1;
        cpustate.r[A2] = a2;
        cpustate.r[A3] = a3;
        cpustate.r[S0] = s0;
        cpustate.r[S1] = s1;
        cpustate.r[S2] = s2;
        cpustate.r[S3] = s3;
        cpustate.r[GP] = gp;
        cpustate.pc = addr;
        
        state = RUNNING;

        setCPUState(cpustate);
        __execute();
        cpustate = getCPUState();
        setCPUState(saved);

        if(state != PAUSED)
            System.out.println("WARNING: Process exit()ed while servicing a call() request");
        else
            state = oldState;
        
        return cpustate.r[V1];
    }
    
    // FEATURE: This is ugly - we should have some kind of way  to specify a callback rather than requiring subclassing
    protected int callJava(int a, int b, int c, int d) {
        System.err.println("WARNING: Default implementation of callJava() called with args " + toHex(a) + "," + toHex(b) + "," + toHex(c) + "," + toHex(d));
        return 0;
    }
    
    /** Determines if the process can access <i>fileName</i>. The default implementation simply logs 
        the request and allows it */
    protected boolean allowFileAccess(String fileName, boolean write) {
        //System.err.println("Allowing " + (write?"write":"read-only") + " access to " + fileName);
        return true;
    }
    
    /** Allocated an entry in the FileDescriptor table for <i>fd</i> and returns the number.
        Returns -1 if the table is full. This can be used by subclasses to use custom file
        descriptors */
    public int addFD(FD fd) {
        int i;
        for(i=0;i<OPEN_MAX;i++) if(fds[i] == null) break;
        if(i==OPEN_MAX) return -1;
        fds[i] = fd;
        return i;
    }

    /** Closes file descriptor <i>fdn</i> and removes it from the file descriptor table */
    public boolean closeFD(int fdn) {
        if(fdn < 0 || fdn >= OPEN_MAX) return false;
        if(fds[fdn] == null) return false;
        fds[fdn].close();
        fds[fdn] = null;        
        return true;
    }

    // FEATURE: These should be pulled in from UsermodeConstants but fcntl.h is hard to parse
    public static final int RD_ONLY = 0;
    public static final int WR_ONLY = 1;
    public static final int RDWR = 2;
    
    public static final int O_CREAT = 0x0200;
    public static final int O_EXCL = 0x0800;
    public static final int O_APPEND = 0x0008;
    public static final int O_TRUNC = 0x0400;
    public static final int O_NONBLOCK = 0x4000;
    
    // FEATURE: Lots of duplicate code between this and UnixRuntime.HostFS.open()
    protected FD open(String path, int flags, int mode) throws IOException {
        final File f = new File(path);
        // NOTE: createNewFile is a Java2 function
        if((flags & (O_EXCL|O_CREAT)) == (O_EXCL|O_CREAT))
            if(!f.createNewFile()) throw new ErrnoException(EEXIST);
        if(!f.exists() && (flags&O_CREAT) == 0) return null;
        if(f.isDirectory()) return null;
        final SeekableFile sf = new SeekableFile(path,mode!=RD_ONLY);
        if((flags&O_TRUNC)!=0) sf.setLength(0);
        return new SeekableFD(sf,flags) {
            protected FStat _fstat() { return new HostFStat(f) {
                public int size() {
                    try { return sf.length(); } catch(IOException e) { return 0; }
                }
            };}
        };
    }
    
    /** The open syscall */
    private int sys_open(int addr, int flags, int mode) {
        if((flags & O_NONBLOCK) != 0) {
            System.err.println("WARNING: O_NONBLOCK not supported");
            return -EOPNOTSUPP;
        }

        try {
            FD fd = open(cstring(addr),flags,mode);
            if(fd == null) return -ENOENT;
            int fdn = addFD(fd);
            if(fdn == -1) {
                fd.close();
                return -ENFILE;
            }
            return fdn;
        }
        catch(ErrnoException e) { return -e.errno; }
        catch(FileNotFoundException e) {
            if(e.getMessage() != null && e.getMessage().indexOf("Permission denied") >= 0) return -EACCES;
            return -ENOENT;
        }
        catch(IOException e) { return -EIO; }
        catch(FaultException e) { return -EFAULT; }
    }

    /** The write syscall */
    private int sys_write(int fdn, int addr, int count) {
        count = Math.min(count,MAX_CHUNK);
        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null || !fds[fdn].writable()) return -EBADFD;
        try {
            byte[] buf = byteBuf(count);
            copyin(addr,buf,count);
            return fds[fdn].write(buf,0,count);
        } catch(FaultException e) {
            System.err.println(e);
            return -EFAULT;
        } catch(IOException e) {
            // FEATURE: We should support signals and send a SIGPIPE
            if(e.getMessage().equals("Pipe closed")) return sys_exit(128+13);
            System.err.println(e);
            return -EIO;
        }
    }

    /** The read syscall */
    private int sys_read(int fdn, int addr, int count) {
        count = Math.min(count,MAX_CHUNK);
        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null || !fds[fdn].readable()) return -EBADFD;
        try {
            byte[] buf = byteBuf(count);
            int n = fds[fdn].read(buf,0,count);
            copyout(buf,addr,n);
            return n;
        } catch(FaultException e) {
            System.err.println(e);
            return -EFAULT;
        } catch(IOException e) {
            System.err.println(e);
            return -EIO;
        }
    }
    
    /** The close syscall */
    private int sys_close(int fdn) {
        return closeFD(fdn) ? 0 : -EBADFD;
    }

    
    /** The seek syscall */
    private int sys_lseek(int fdn, int offset, int whence) {
        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null) return -EBADFD;
        if(whence != SEEK_SET && whence !=  SEEK_CUR && whence !=  SEEK_END) return -EINVAL;
        try {
            int n = fds[fdn].seek(offset,whence);
            return n < 0 ? -ESPIPE : n;
        } catch(IOException e) {
            return -ESPIPE;
        }
    }
    
    /** The stat/fstat syscall helper */
    int stat(FStat fs, int addr) {
        try {
            memWrite(addr+0,(fs.dev()<<16)|(fs.inode()&0xffff)); // st_dev (top 16), // st_ino (bottom 16)
            memWrite(addr+4,((fs.type()&0xf000))|(fs.mode()&0xfff)); // st_mode
            memWrite(addr+8,1<<16); // st_nlink (top 16) // st_uid (bottom 16)
            memWrite(addr+12,0); // st_gid (top 16) // st_rdev (bottom 16)
            memWrite(addr+16,fs.size()); // st_size
            memWrite(addr+20,fs.atime()); // st_atime
            // memWrite(addr+24,0) // st_spare1
            memWrite(addr+28,fs.mtime()); // st_mtime
            // memWrite(addr+32,0) // st_spare2
            memWrite(addr+36,fs.ctime()); // st_ctime
            // memWrite(addr+40,0) // st_spare3
            memWrite(addr+44,fs.blksize()); // st_bklsize;
            memWrite(addr+48,fs.blocks()); // st_blocks
            // memWrite(addr+52,0) // st_spare4[0]
            // memWrite(addr+56,0) // st_spare4[1]
        } catch(FaultException e) {
            System.err.println(e);
            return -EFAULT;
        }
        return 0;
    }
    
    /** The fstat syscall */
    private int sys_fstat(int fdn, int addr) {
        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null) return -EBADFD;
        return stat(fds[fdn].fstat(),addr);
    }
    
    /*
    struct timeval {
    long tv_sec;
    long tv_usec;
    };
    */
    private int sys_gettimeofday(int timevalAddr, int timezoneAddr) {
        long now = System.currentTimeMillis();
        int tv_sec = (int)(now / 1000);
        int tv_usec = (int)((now%1000)*1000);
        try {
            memWrite(timevalAddr+0,tv_sec);
            memWrite(timevalAddr+4,tv_usec);
            return 0;
        } catch(FaultException e) {
            return -EFAULT;
        }
    }
    
    private int sys_sleep(int sec) {
        if(sec < 0) sec = Integer.MAX_VALUE;
        try {
            Thread.sleep((long)sec*1000);
            return 0;
        } catch(InterruptedException e) {
            return -1;
        }
    }
    
    /*
      #define _CLOCKS_PER_SEC_ 1000
      #define    _CLOCK_T_    unsigned long
    struct tms {
      clock_t   tms_utime;
      clock_t   tms_stime;
      clock_t   tms_cutime;    
      clock_t   tms_cstime;
    };*/
   
    private int sys_times(int tms) {
        long now = System.currentTimeMillis();
        int userTime = (int)((now - startTime)/16);
        int sysTime = (int)((now - startTime)/16);
        
        try {
            if(tms!=0) {
                memWrite(tms+0,userTime);
                memWrite(tms+4,sysTime);
                memWrite(tms+8,userTime);
                memWrite(tms+12,sysTime);
            }
        } catch(FaultException e) {
            return -EFAULT;
        }
        return (int)now;
    }
    
    private int sys_sysconf(int n) {
        switch(n) {
            case _SC_CLK_TCK: return 1000;
            default:
                System.err.println("WARNING: Attempted to use unknown sysconf key: " + n);
                return -EINVAL;
        }
    }
    
    /** The sbrk syscall. This can also be used by subclasses to allocate memory.
        <i>incr</i> is how much to increase the break by */
    public int sbrk(int incr) {
        if(incr < 0) return -ENOMEM;
        if(incr==0) return brkAddr;
        incr = (incr+3)&~3;
        int oldBrk = brkAddr;
        int newBrk = oldBrk + incr;
        if(TOTAL_PAGES == 1) {
            CPUState state = getCPUState();
            if(newBrk >= state.r[SP] - 65536) {
                System.err.println("WARNING: brk too close to stack pointer");
                return -ENOMEM;
            }
        } else if(newBrk >= BRK_LIMIT) {
            System.err.println("WARNING: Hit BRK_LIMIT");
            return -ENOMEM;
        }
        if(TOTAL_PAGES != 1) {
            try {
                for(int i=(oldBrk+PAGE_SIZE-1)>>>PAGE_SHIFT;i<((newBrk+PAGE_SIZE-1)>>>PAGE_SHIFT);i++)
                    readPages[i] = writePages[i] = emptyPage();
            } catch(OutOfMemoryError e) {
                System.err.println("WARNING: Caught OOM Exception in sbrk: " + e);
                return -ENOMEM;
            }
        }
        brkAddr = newBrk;
        return oldBrk;
    }

    /** The getpid syscall */
    private int sys_getpid() { return getPid(); }
    protected int getPid() { return 1; }
    
    private int sys_calljava(int a, int b, int c, int d) {
        if(state != RUNNING) throw new IllegalStateException("wound up calling sys_calljava while not in RUNNING");
        state = CALLJAVA;
        int ret = callJava(a,b,c,d);
        state = RUNNING;
        return ret;
    }
        
    private int sys_pause() {
        state = PAUSED;
        return 0;
    }
    
    private int sys_getpagesize() { return TOTAL_PAGES == 1 ? 4096 : PAGE_SIZE; }
    
    private int sys_isatty(int fdn) {
        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null) return -EBADFD;
        return fds[fdn].isatty() ? 1 : 0;
    }

    
    /** Hook for subclasses to do something when the process exits (MUST set state = DONE) */
    protected void _exit() { state = DONE; }
    private int sys_exit(int status) {
        exitStatus = status;
        for(int i=0;i<fds.length;i++) if(fds[i] != null) sys_close(i);
        _exit();
        return 0;
    }
       
    private int sys_fcntl(int fdn, int cmd, int arg) {
        final int F_DUPFD = 0;
        final int F_GETFL = 3;
        int i;
            
        if(fdn < 0 || fdn >= OPEN_MAX) return -EBADFD;
        if(fds[fdn] == null) return -EBADFD;
        FD fd = fds[fdn];
        
        switch(cmd) {
            case F_DUPFD:
                if(arg < 0 || arg >= OPEN_MAX) return -EINVAL;
                for(i=arg;i<OPEN_MAX;i++) if(fds[i]==null) break;
                if(i==OPEN_MAX) return -EMFILE;
                fds[i] = fd.dup();
                return 0;
            case F_GETFL:
                int flags = 0;
                if(fd.writable() && fd.readable())  flags = 2;
                else if(fd.writable()) flags = 1;
                return flags;
            default:
                System.err.println("WARNING: Unknown fcntl command: " + cmd);
                return -ENOSYS;
        }
    }
            
    /** The syscall dispatcher.
        The should be called by subclasses when the syscall instruction is invoked.
        <i>syscall</i> should be the contents of V0 and <i>a</i>, <i>b</i>, <i>c</i>, and <i>d</i> should be 
        the contenst of A0, A1, A2, and A3. The call MAY change the state
        @see Runtime#state state */
    protected int syscall(int syscall, int a, int b, int c, int d) {
        switch(syscall) {
            case SYS_null: return 0;
            case SYS_exit: return sys_exit(a);
            case SYS_pause: return sys_pause();
            case SYS_write: return sys_write(a,b,c);
            case SYS_fstat: return sys_fstat(a,b);
            case SYS_sbrk: return sbrk(a);
            case SYS_open: return sys_open(a,b,c);
            case SYS_close: return sys_close(a);
            case SYS_read: return sys_read(a,b,c);
            case SYS_lseek: return sys_lseek(a,b,c);
            case SYS_getpid: return sys_getpid();
            case SYS_calljava: return sys_calljava(a,b,c,d);
            case SYS_gettimeofday: return sys_gettimeofday(a,b);
            case SYS_sleep: return sys_sleep(a);
            case SYS_times: return sys_times(a);
            case SYS_getpagesize: return sys_getpagesize();
            case SYS_isatty: return sys_isatty(a);
            case SYS_fcntl: return sys_fcntl(a,b,c);
            case SYS_sysconf: return sys_sysconf(a);

            case SYS_kill:
            case SYS_fork:
            case SYS_pipe:
            case SYS_dup2:
            case SYS_waitpid:
            case SYS_stat:
            case SYS_mkdir:
            case SYS_getcwd:
            case SYS_chdir:
                System.err.println("Attempted to use a UnixRuntime syscall in Runtime (" + syscall + ")");
                return -ENOSYS;
            default:
                System.err.println("Attempted to use unknown syscall: " + syscall);
                return -ENOSYS;
        }
    }
    
    public int xmalloc(int size) { int p=malloc(size); if(p==0) throw new RuntimeException("malloc() failed"); return p; }
    public int xrealloc(int addr,int newsize) { int p=realloc(addr,newsize); if(p==0) throw new RuntimeException("realloc() failed"); return p; }
    public int realloc(int addr, int newsize) { try { return call("realloc",addr,newsize); } catch(CallException e) { return 0; } }
    public int malloc(int size) { try { return call("malloc",size); } catch(CallException e) { return 0; } }
    public void free(int p) { try { if(p!=0) call("free",p); } catch(CallException e) { /*noop*/ } }
    
    /** Helper function to create a cstring in main memory */
    public int strdup(String s) {
        byte[] a;
        if(s == null) s = "(null)";
        byte[] a2 = getBytes(s);
        a = new byte[a2.length+1];
        System.arraycopy(a2,0,a,0,a2.length);
        int addr = malloc(a.length);
        if(addr == 0) return 0;
        try {
            copyout(a,addr,a.length);
        } catch(FaultException e) {
            free(addr);
            return 0;
        }
        return addr;
    }
    
    /** Helper function to read a cstring from main memory */
    public String cstring(int addr) throws ReadFaultException {
        StringBuffer sb = new StringBuffer();
        for(;;) {
            int word = memRead(addr&~3);
            switch(addr&3) {
                case 0: if(((word>>>24)&0xff)==0) return sb.toString(); sb.append((char)((word>>>24)&0xff)); addr++;
                case 1: if(((word>>>16)&0xff)==0) return sb.toString(); sb.append((char)((word>>>16)&0xff)); addr++;
                case 2: if(((word>>> 8)&0xff)==0) return sb.toString(); sb.append((char)((word>>> 8)&0xff)); addr++;
                case 3: if(((word>>> 0)&0xff)==0) return sb.toString(); sb.append((char)((word>>> 0)&0xff)); addr++;
            }
        }
    }
    
    /** File Descriptor class */
    public static abstract class FD {
        private int refCount = 1;
    
        /** returns true if the fd is readable */
        public boolean readable() { return false; }
        /** returns true if the fd is writable */
        public boolean writable() { return false; }
        
        /** Read some bytes. Should return the number of bytes read, 0 on EOF, or throw an IOException on error */
        public int read(byte[] a, int off, int length) throws IOException { throw new IOException("no definition"); }
        /** Write. Should return the number of bytes written or throw an IOException on error */
        public int write(byte[] a, int off, int length) throws IOException { throw new IOException("no definition"); }

        /** Seek in the filedescriptor. Whence is SEEK_SET, SEEK_CUR, or SEEK_END. Should return -1 on error or the new position. */
        public int seek(int n, int whence)  throws IOException  { return -1; }
        
        /** Should return true if this is a tty */
        public boolean isatty() { return false; }
        
        private FStat cachedFStat = null;
        public final FStat fstat() {
            if(cachedFStat == null) cachedFStat = _fstat(); 
            return cachedFStat;
        }
        
        protected abstract FStat _fstat();
        
        /** Closes the fd */
        public final void close() { if(--refCount==0) _close(); }
        protected void _close() { /* noop*/ }
        
        FD dup() { refCount++; return this; }
    }
        
    /** FileDescriptor class for normal files */
    public abstract static class SeekableFD extends FD {
        private final int flags;
        private final SeekableData data;
        public boolean readable() { return (flags&3) != WR_ONLY; }
        public boolean writable() { return (flags&3) != RD_ONLY; }
        
        SeekableFD(SeekableData data, int flags) { this.data = data; this.flags = flags; }
        
        protected abstract FStat _fstat();

        public int seek(int n, int whence) throws IOException {
            switch(whence) {
                case SEEK_SET: break;
                case SEEK_CUR: n += data.pos(); break;
                case SEEK_END: n += data.length(); break;
                default: return -1;
            }
            data.seek(n);
            return n;
        }
        
        public int write(byte[] a, int off, int length) throws IOException {
            // NOTE: There is race condition here but we can't fix it in pure java
            if((flags&O_APPEND) != 0) seek(0,SEEK_END);
            return data.write(a,off,length);
        }
        
        public int read(byte[] a, int off, int length) throws IOException {
            int n = data.read(a,off,length);
            return n < 0 ? 0 : n;
        }
        
        protected void _close() { try { data.close(); } catch(IOException e) { /*ignore*/ } }        
    }
    
    public static class OutputStreamFD extends FD {
        private OutputStream os;
        public boolean writable() { return true; }
        public OutputStreamFD(OutputStream os) { this.os = os; }
        public int write(byte[] a, int off, int length) throws IOException { os.write(a,off,length); return length; }
        public void _close() { try { os.close(); } catch(IOException e) { /*ignore*/ }  }
        public FStat _fstat() { return new FStat(); }
    }
    
    public static class InputStreamFD extends FD {
        private InputStream is;
        public boolean readable() { return true; }
        public InputStreamFD(InputStream is) { this.is = is; }
        public int read(byte[] a, int off, int length) throws IOException { int n = is.read(a,off,length); return n < 0 ? 0 : n; }
        public void _close() { try { is.close(); } catch(IOException e) { /*ignore*/ } }
        public FStat _fstat() { return new FStat(); }
    }
    
    protected static class StdinFD extends InputStreamFD {
        public StdinFD(InputStream is) { super(is); }
        public void _close() { /* noop */ }
        public FStat _fstat() { return new FStat() { public int type() { return S_IFCHR; } }; }
        public boolean isatty() { return true; }
    }
    protected static class StdoutFD extends OutputStreamFD {
        public StdoutFD(OutputStream os) { super(os); }
        public void _close() { /* noop */ }
        public FStat _fstat() { return new FStat() { public int type() { return S_IFCHR; } }; }
        public boolean isatty() { return true; }
    }
    
    public static class FStat {
        public static final int S_IFIFO = 0010000;
        public static final int S_IFCHR = 0020000;
        public static final int S_IFDIR = 0040000;
        public static final int S_IFREG = 0100000;
        
        public int dev() { return -1; }
        // FEATURE: inode numbers are calculated inconsistently throught the runtime
        public int inode() { return hashCode() & 0xfffff; }
        public int mode() { return 0; }
        public int type() { return S_IFIFO; }
        public int nlink() { return 0; }
        public int uid() { return 0; }
        public int gid() { return 0; }
        public int size() { return 0; }
        public int atime() { return 0; }
        public int mtime() { return 0; }
        public int ctime() { return 0; }
        public int blksize() { return 512; }
        public int blocks() { return (size()+blksize()-1)/blksize(); }        
    }
    
    protected static class HostFStat extends FStat {
        private final File f;
        private final boolean executable; 
        public HostFStat(File f) {
            this.f = f;
            String name = f.getName();
            // FEATURE: This is ugly.. maybe we should do a file(1) type check
            executable = name.endsWith(".mips") || name.endsWith(".sh");
        }
        public int dev() { return 1; }
        public int inode() { return f.getName().hashCode() & 0xffff; }
        public int type() { return f.isDirectory() ? S_IFDIR : S_IFREG; }
        public int nlink() { return 1; }
        public int mode() {
            int mode = 0;
            boolean canread = f.canRead();
            if(canread && (executable || f.isDirectory())) mode |= 0111;
            if(canread) mode |= 0444;
            if(f.canWrite()) mode |= 0222;
            return mode;
        }
        public int size() { return (int) f.length(); }
        public int mtime() { return (int)(f.lastModified()/1000); }
    }
    
    // Exceptions
    public class ReadFaultException extends FaultException {
        public ReadFaultException(int addr) { super(addr); }
    }
    public class WriteFaultException extends FaultException {
        public WriteFaultException(int addr) { super(addr); }
    }
    public abstract class FaultException extends ExecutionException {
        public int addr;
        public FaultException(int addr) { super("fault at: " + toHex(addr)); this.addr = addr; }
    }
    public static class ExecutionException extends Exception {
        private String message = "(null)";
        private String location = "(unknown)";
        public ExecutionException() { /* noop */ }
        public ExecutionException(String s) { if(s != null) message = s; }
        void setLocation(String s) { location = s == null ? "(unknown)" : s; }
        public final String getMessage() { return message + " at " + location; }
    }
    public static class CallException extends Exception {
        public CallException(String s) { super(s); }
    }
    
    protected static class ErrnoException extends IOException {
        public int errno;
        public ErrnoException(int errno) { super("Errno: " + errno); this.errno = errno; }
    }
    
    // CPU State
    protected static class CPUState {
        public CPUState() { /* noop */ }
        /* GPRs */
        public int[] r = new int[32];
        /* Floating point regs */
        public int[] f = new int[32];
        public int hi, lo;
        public int fcsr;
        public int pc;
    }
    
    // Null pointer check helper function
    protected final void nullPointerCheck(int addr) throws ExecutionException {
        if(TOTAL_PAGES==1 ? addr < 65536 : (addr>>>PAGE_SHIFT) < 16)
            throw new ExecutionException("Attempted to dereference a null pointer " + toHex(addr));
    }
    
    // Utility functions
    private byte[] byteBuf(int size) {
        if(_byteBuf==null) _byteBuf = new byte[size];
        else if(_byteBuf.length < size)
            _byteBuf = new byte[min(max(_byteBuf.length*2,size),MAX_CHUNK)];
        return _byteBuf;
    }
    
    protected static String getSystemProperty(String key) {
        try {
            return System.getProperty(key);
        } catch(SecurityException e) {
            return null;
        }
    }
    
    /** Decode an packed string.. FEATURE: document this better */
    protected static final int[] decodeData(String s, int words) {
        if(s.length() % 8 != 0) throw new IllegalArgumentException("string length must be a multiple of 8");
        if((s.length() / 8) * 7 < words*4) throw new IllegalArgumentException("string isn't big enough");
        int[] buf = new int[words];
        int prev = 0, left=0;
        for(int i=0,n=0;n<words;i+=8) {
            long l = 0;
            for(int j=0;j<8;j++) { l <<= 7; l |= s.charAt(i+j) & 0x7f; }
            if(left > 0) buf[n++] = prev | (int)(l>>>(56-left));
            if(n < words) buf[n++] = (int) (l >>> (24-left));
            left = (left + 8) & 0x1f;
            prev = (int)(l << left);
        }
        return buf;
    }
    
    protected static byte[] getBytes(String s) {
        try {
            return s.getBytes("ISO-8859-1");
        } catch(UnsupportedEncodingException e) {
            return null; // should never happen
        }
    }
    
    protected final static String toHex(int n) { return "0x" + Long.toString(n & 0xffffffffL, 16); }
    protected final static int min(int a, int b) { return a < b ? a : b; }
    protected final static int max(int a, int b) { return a > b ? a : b; }
}

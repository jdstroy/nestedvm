package org.ibex.nestedvm;

import org.ibex.nestedvm.util.*;
import java.io.*;
import java.util.*;

// FIXME: Fix readdir in support_aux.c
// FIXME: Make plain old "mips-unknown-elf-gcc -o foo foo.c" work (modify spec file or whatever)

// FEATURE: Remove System.{out,err}.printlns and throw Errors where applicable

// FIXME: BusyBox's ASH doesn't like \r\n at the end of lines
// is ash just broken or are many apps like this? if so workaround in nestedvm

public abstract class UnixRuntime extends Runtime implements Cloneable {
    /** The pid of this "process" */
    private int pid;
    private UnixRuntime parent;
    public final int getPid() { return pid; }
    
    private static final GlobalState defaultGD = new GlobalState();
    private GlobalState gd = defaultGD;
    
    /** proceses' current working directory - absolute path WITHOUT leading slash
        "" = root, "bin" = /bin "usr/bin" = /usr/bin */
    private String cwd;
    
    /** The runtime that should be run next when in state == EXECED */
    private UnixRuntime execedRuntime;

    private Object children; // used only for synchronizatin
    private Vector activeChildren;
    private Vector exitedChildren;
    
    protected UnixRuntime(int pageSize, int totalPages) {
        super(pageSize,totalPages);
                
        // FEATURE: Do the proper mangling for non-unix hosts
        String userdir = getSystemProperty("user.dir");
        cwd = userdir != null && userdir.startsWith("/") && File.separatorChar == '/'  ? userdir.substring(1) : "";
    }
    
    // NOTE: getDisplayName() is a Java2 function
    private static String posixTZ() {
        StringBuffer sb = new StringBuffer();
        TimeZone zone = TimeZone.getDefault();
        int off = zone.getRawOffset() / 1000;
        sb.append(zone.getDisplayName(false,TimeZone.SHORT));
        if(off > 0) sb.append("-");
        else off = -off;
        sb.append(off/3600); off = off%3600;
        if(off > 0) sb.append(":").append(off/60); off=off%60;
        if(off > 0) sb.append(":").append(off);
        if(zone.useDaylightTime())
            sb.append(zone.getDisplayName(true,TimeZone.SHORT));
        return sb.toString();
    }
    
    private static boolean envHas(String key,String[] environ) {
        for(int i=0;i<environ.length;i++)
            if(environ[i]!=null && environ[i].startsWith(key + "=")) return true;
        return false;
    }
    
    String[] createEnv(String[] extra) {
        String[] defaults = new String[5];
        int n=0;
        if(extra == null) extra = new String[0];
        if(!envHas("USER",extra) && getSystemProperty("user.name") != null)
            defaults[n++] = "USER=" + getSystemProperty("user.name");
        if(!envHas("HOME",extra) && getSystemProperty("user.name") != null)
            defaults[n++] = "HOME=" + getSystemProperty("user.home");
        if(!envHas("SHELL",extra)) defaults[n++] = "SHELL=/bin/sh";
        if(!envHas("TERM",extra))  defaults[n++] = "TERM=vt100";
        if(!envHas("TZ",extra))    defaults[n++] = "TZ=" + posixTZ();
        String[] env = new String[extra.length+n];
        for(int i=0;i<n;i++) env[i] = defaults[i];
        for(int i=0;i<extra.length;i++) env[n++] = extra[i];
        return env;
    }
    
    private static class ProcessTableFullExn extends RuntimeException { }
    
    void _started() {
        UnixRuntime[] tasks = gd.tasks;
        synchronized(gd) {
            if(pid != 0) {
                if(tasks[pid] == null || tasks[pid].pid != pid) throw new Error("should never happen");
            } else {
                int newpid = -1;
                int nextPID = gd.nextPID;
            	    for(int i=nextPID;i<tasks.length;i++) if(tasks[i] == null) { newpid = i; break; }
            	    if(newpid == -1) for(int i=1;i<nextPID;i++) if(tasks[i] == null) { newpid = i; break; }
            	    if(newpid == -1) throw new ProcessTableFullExn();
            	    pid = newpid;
                gd.nextPID = newpid + 1;
            }
            tasks[pid] = this;
        }
    }
    
    int _syscall(int syscall, int a, int b, int c, int d) throws ErrnoException, FaultException {
        switch(syscall) {
            case SYS_kill: return sys_kill(a,b);
            case SYS_fork: return sys_fork();
            case SYS_pipe: return sys_pipe(a);
            case SYS_dup2: return sys_dup2(a,b);
            case SYS_waitpid: return sys_waitpid(a,b,c);
            case SYS_stat: return sys_stat(a,b);
            case SYS_lstat: return sys_lstat(a,b);
            case SYS_mkdir: return sys_mkdir(a,b);
            case SYS_getcwd: return sys_getcwd(a,b);
            case SYS_chdir: return sys_chdir(a);
            case SYS_exec: return sys_exec(a,b,c);

            default: return super._syscall(syscall,a,b,c,d);
        }
    }
    
    FD _open(String path, int flags, int mode) throws ErrnoException {
        return gd.open(this,normalizePath(path),flags,mode);
    }

    /** The kill syscall.
       SIGSTOP, SIGTSTO, SIGTTIN, and SIGTTOUT pause the process.
       SIGCONT, SIGCHLD, SIGIO, and SIGWINCH are ignored.
       Anything else terminates the process. */
    private int sys_kill(int pid, int signal) {
        // This will only be called by raise() in newlib to invoke the default handler
        // We don't have to worry about actually delivering the signal
        if(pid != pid) return -ESRCH;
        if(signal < 0 || signal >= 32) return -EINVAL;
        switch(signal) {
            case 0: return 0;
            case 17: // SIGSTOP
            case 18: // SIGTSTP
            case 21: // SIGTTIN
            case 22: // SIGTTOU
                state = PAUSED;
                break;
            case 19: // SIGCONT
            case 20: // SIGCHLD
            case 23: // SIGIO
            case 28: // SIGWINCH
                break;
            default:
                return syscall(SYS_exit,128+signal,0,0,0);
        }
        return 0;
    }

    private int sys_waitpid(int pid, int statusAddr, int options) throws FaultException, ErrnoException {
        System.err.println("PID: " + this.pid + " is waiting on " + pid);
        final int WNOHANG = 1;
        if((options & ~(WNOHANG)) != 0) return -EINVAL;
        if(pid == 0 || pid < -1) {
            System.err.println("WARNING: waitpid called with a pid of " + pid);
            return -ECHILD;
        }
        boolean blocking = (options&WNOHANG)==0;
        
        if(pid !=-1 && (pid <= 0 || pid >= gd.tasks.length)) return -ECHILD;
        if(children == null) return blocking ? -ECHILD : 0;
        
        UnixRuntime done = null;
        
        synchronized(children) {
            for(;;) {
                if(pid == -1) {
                    if(exitedChildren.size() > 0) done = (UnixRuntime)exitedChildren.remove(exitedChildren.size() - 1);
                } else if(pid > 0) {
                    UnixRuntime t = gd.tasks[pid];
                    if(t.parent != this) return -ECHILD;
                    if(t.state == EXITED) {
                        if(!exitedChildren.remove(t)) throw new Error("should never happen");
                        done = t;
                    }
                } else {
                    // process group stuff, EINVAL returned above
                	    throw new Error("should never happen");
                }
                if(done == null) {
                    if(!blocking) return 0;
                    try { children.wait(); } catch(InterruptedException e) {}
                    System.err.println("waitpid woke up: " + exitedChildren.size());
                } else {
                    gd.tasks[done.pid] = null;
                    break;
                }
            }
        }
        if(statusAddr!=0) memWrite(statusAddr,done.exitStatus()<<8);
        return done.pid;
    }
    
    
    void _exited() {
        if(children != null) synchronized(children) {
            for(Enumeration e = exitedChildren.elements(); e.hasMoreElements(); ) {
                UnixRuntime child = (UnixRuntime) e.nextElement();
                    gd.tasks[child.pid] = null;
            }
            exitedChildren.clear();
            for(Enumeration e = activeChildren.elements(); e.hasMoreElements(); ) {
                UnixRuntime child = (UnixRuntime) e.nextElement();
                child.parent = null;
            }
            activeChildren.clear();
        }
        
        UnixRuntime _parent = parent;
        if(_parent == null) {
            gd.tasks[pid] = null;
        } else {
            synchronized(_parent.children) {
                if(parent == null) {
                    gd.tasks[pid] = null;
                } else {
                    parent.activeChildren.remove(this);
                    parent.exitedChildren.add(this);
                    parent.children.notify();
                }
            }
        }
    }
    
    protected Object clone() throws CloneNotSupportedException {
    	    UnixRuntime r = (UnixRuntime) super.clone();
        r.pid = 0;
        r.parent = null;
        r.children = null;
        r.activeChildren = r.exitedChildren = null;
        return r;
    }

    private int sys_fork() {
        CPUState state = new CPUState();
        getCPUState(state);
        int sp = state.r[SP];
        final UnixRuntime r;
        
        try {
            r = (UnixRuntime) clone();
        } catch(Exception e) {
            e.printStackTrace();
            return -ENOMEM;
        }

        r.parent = this;

        try {
            r._started();
        } catch(ProcessTableFullExn e) {
        	    return -ENOMEM;
        }

        System.err.println("fork " + pid + " -> " + r.pid + " tasks[" + r.pid + "] = " + gd.tasks[r.pid]);
        if(children == null) {
            children = new Object();
            activeChildren = new Vector();
            exitedChildren = new Vector();
        }
        activeChildren.add(r);
        
        state.r[V0] = 0; // return 0 to child
        state.pc += 4; // skip over syscall instruction
        r.setCPUState(state);
        r.state = PAUSED;
        
        new ForkedProcess(r);
        
        return r.pid;
    }
    
    public static final class ForkedProcess extends Thread {
        private final UnixRuntime initial;
        public ForkedProcess(UnixRuntime initial) { this.initial = initial; start(); }
        public void run() { UnixRuntime.executeAndExec(initial); }
    }
    
    public static int runAndExec(UnixRuntime r, String argv0, String[] rest) { return runAndExec(r,concatArgv(argv0,rest)); }
    public static int runAndExec(UnixRuntime r, String[] argv) { r.start(argv); return executeAndExec(r); }
    
    public static int executeAndExec(UnixRuntime r) {
    	    for(;;) {
            for(;;) {
                if(r.execute()) break;
                System.err.println("WARNING: Pause requested while executing runAndExec()");
            }
            if(r.state != EXECED) return r.exitStatus();
            r = r.execedRuntime;
        }
    }
     
    private String[] readStringArray(int addr) throws ReadFaultException {
    	    int count = 0;
        for(int p=addr;memRead(p) != 0;p+=4) count++;
        String[] a = new String[count];
        for(int i=0,p=addr;i<count;i++,p+=4) a[i] = cstring(memRead(p));
        return a;
    }
    
    private int sys_exec(int cpath, int cargv, int cenvp) throws ErrnoException, FaultException {
    	    return exec(normalizePath(cstring(cpath)),readStringArray(cargv),readStringArray(cenvp));
    }
        
    private int exec(String normalizedPath, String[] argv, String[] envp) throws ErrnoException {
        if(argv.length == 0) argv = new String[]{""};

        Object o = gd.exec(this,normalizedPath);
        if(o == null) return -ENOENT;

        if(o instanceof Class) {
            Class c = (Class) o;
            try {
            	    return exec((UnixRuntime) c.newInstance(),argv,envp);
            } catch(Exception e) {
            	    e.printStackTrace();
                return -ENOEXEC;
            }
        } else {
            String[] command = (String[]) o;
            String[] newArgv = new String[argv.length + command[1] != null ? 2 : 1];
            int p = command[0].lastIndexOf('/');
            newArgv[0] = p == -1 ? command[0] : command[0].substring(p+1);
            p = 1;
            if(command[1] != null) newArgv[p++] = command[1];
            newArgv[p++] = "/" + normalizedPath;
            for(int i=1;i<argv.length;i++) newArgv[p++] = argv[i];
            return exec(command[0],newArgv,envp);
        }
    }
    
    private int exec(UnixRuntime r, String[] argv, String[] envp) {     
        
        System.err.println("Execing " + r);
        for(int i=0;i<OPEN_MAX;i++) if(closeOnExec[i]) closeFD(i);
        r.fds = fds;
        r.closeOnExec = closeOnExec;
        // make sure this doesn't get messed with these since we didn't copy them
        fds = null;
        closeOnExec = null;
        
        r.gd = gd;
        r.sm = sm;
        r.cwd = cwd;
        r.pid = pid;
        r.parent = parent;
        r.start(argv,envp);
                
        state = EXECED;
        execedRuntime = r;
        
        return 0;   
    }
    
    // FEATURE: Use custom PipeFD - be sure to support PIPE_BUF of data
    private int sys_pipe(int addr) throws FaultException {
        PipedOutputStream writerStream = new PipedOutputStream();
        PipedInputStream readerStream;
        try {
             readerStream = new PipedInputStream(writerStream);
        } catch(IOException e) {
            return -EIO;
        }
        FD reader = new InputStreamFD(readerStream);
        FD writer = new OutputStreamFD(writerStream);
        int fd1 = addFD(reader);
        if(fd1 < 0) return -ENFILE;
        int fd2 = addFD(writer);
        if(fd2 < 0) { closeFD(fd1); return -ENFILE; }
        try {
            memWrite(addr,fd1);
            memWrite(addr+4,fd2);
        } catch(FaultException e) {
            closeFD(fd1);
            closeFD(fd2);
            return -EFAULT;
        }
        return 0;
    }
    
    private int sys_dup2(int oldd, int newd) {
        if(oldd == newd) return 0;
        if(oldd < 0 || oldd >= OPEN_MAX) return -EBADFD;
        if(newd < 0 || newd >= OPEN_MAX) return -EBADFD;
        if(fds[oldd] == null) return -EBADFD;
        if(fds[newd] != null) fds[newd].close();
        fds[newd] = fds[oldd].dup();
        return 0;
    }
    
    private int sys_stat(int cstring, int addr) throws FaultException, ErrnoException {
        FStat s = gd.stat(this,normalizePath(cstring(cstring)));
        if(s == null) return -ENOENT;
        return stat(s,addr);
    }
    
    private int sys_lstat(int cstring, int addr) throws FaultException, ErrnoException {
        FStat s = gd.lstat(this,normalizePath(cstring(cstring)));
        if(s == null) return -ENOENT;
        return stat(s,addr);
    }
    
    private int sys_mkdir(int cstring, int mode) throws FaultException, ErrnoException {
        gd.mkdir(this,normalizePath(cstring(cstring)),mode);
        return 0;
    }
   
    
    private int sys_getcwd(int addr, int size) throws FaultException, ErrnoException {
        byte[] b = getBytes(cwd);
        if(size == 0) return -EINVAL;
        if(size < b.length+2) return -ERANGE;
        memset(addr,'/',1);
        copyout(b,addr+1,b.length);
        memset(addr+b.length+1,0,1);
        return addr;
    }
    
    private int sys_chdir(int addr) throws ErrnoException, FaultException {
        String path = normalizePath(cstring(addr));
        System.err.println("Chdir: " + cstring(addr) + " -> " + path + " pwd: " + cwd);
        if(gd.stat(this,path).type() != FStat.S_IFDIR) return -ENOTDIR;
        cwd = path;
        System.err.println("Now: [" + cwd + "]");
        return 0;
    }
    
    //  FEATURE: Run through the fork/wait stuff one more time
    public static class GlobalState {    
        protected static final int OPEN = 1;
        protected static final int STAT = 2;
        protected static final int LSTAT = 3;
        protected static final int MKDIR = 4;
        
        final UnixRuntime[] tasks;
        int nextPID = 1;
        
        private final MP[][] mps = new MP[128][];
        private FS root;
        
        public GlobalState() { this(255); }
        public GlobalState(int maxProcs) { this(maxProcs,true); }
        public GlobalState(int maxProcs, boolean defaultMounts) {
            tasks = new UnixRuntime[maxProcs+1];
            if(defaultMounts) {
            	    root = new HostFS();
                addMount("/dev",new DevFS());
            }
        }
        
        private static class MP {
            public MP(String path, FS fs) { this.path = path; this.fs = fs; }
            public String path;
            public FS fs;
            public int compareTo(Object o) {
                if(!(o instanceof MP)) return 1;
                return -path.compareTo(((MP)o).path);
            }
        }
        
        public synchronized FS getMount(String path) {
            if(!path.startsWith("/")) throw new IllegalArgumentException("Mount point doesn't start with a /");
            if(path.equals("/")) return root;
            path  = path.substring(1);
            int f = path.charAt(0) & 0x7f;
            for(int i=0;mps[f] != null && i < mps[f].length;i++)
                if(mps[f][i].path.equals(path)) return mps[f][i].fs;
            return null;
        }
        
        public synchronized void addMount(String path, FS fs) {
            if(getMount(path) != null) throw new IllegalArgumentException("mount point already exists");
            if(!path.startsWith("/")) throw new IllegalArgumentException("Mount point doesn't start with a /");
            if(path.equals("/")) { root = fs; return; }
            path = path.substring(1);
            int f = path.charAt(0) & 0x7f;
            int oldLength = mps[f] == null ? 0 : mps[f].length;
            MP[] newList = new MP[oldLength + 1];
            if(oldLength != 0) System.arraycopy(mps[f],0,newList,0,oldLength);
            newList[oldLength] = new MP(path,fs);
            Arrays.sort(newList);
            mps[f] = newList;
        }
        
        public synchronized void removeMount(String path) {
            if(getMount(path) == null) throw new IllegalArgumentException("mount point doesn't exist");
            if(!path.startsWith("/")) throw new IllegalArgumentException("Mount point doesn't start with a /");
            if(path.equals("/")) { root = null; return; }
            path = path.substring(1);
            int f = path.charAt(0) & 0x7f;
            MP[] oldList = mps[f];
            MP[] newList = new MP[oldList.length - 1];
            int p = 0;
            for(p=0;p<oldList.length;p++) if(oldList[p].path.equals(path)) break;
            if(p == oldList.length) throw new Error("should never happen");
            System.arraycopy(oldList,0,newList,0,p);
            System.arraycopy(oldList,0,newList,p,oldList.length-p-1);
            mps[f] = newList;
        }
        
        private Object fsop(int op, UnixRuntime r, String path, int arg1, int arg2) throws ErrnoException {
            int pl = path.length();
            if(pl != 0) {
            	    MP[] list = mps[path.charAt(0) & 0x7f];
                if(list != null) {
                    for(int i=0;i<list.length;i++) {
                    	    MP mp = list[i];
                    	    int mpl = mp.path.length();
                        if(path.startsWith(mp.path) && (pl == mpl || (pl < mpl && path.charAt(mpl) == '/')))
                        	    return dispatch(mp.fs,op,r,pl == mpl ? "" : path.substring(mpl+1),arg1,arg2);
                    }
                }
            }
            return dispatch(root,op,r,path,arg1,arg2);
        }
        
        private static Object dispatch(FS fs, int op, UnixRuntime r, String path, int arg1, int arg2) throws ErrnoException {
            switch(op) {
            	    case OPEN: return fs.open(r,path,arg1,arg2);
                case STAT: return fs.stat(r,path);
                case LSTAT: return fs.lstat(r,path);
                case MKDIR: fs.mkdir(r,path,arg1); return null;
                default: throw new Error("should never happen");
            }
        }
        
        public final FD open(UnixRuntime r, String path, int flags, int mode) throws ErrnoException { return (FD) fsop(OPEN,r,path,flags,mode); }
        public final FStat stat(UnixRuntime r, String path) throws ErrnoException { return (FStat) fsop(STAT,r,path,0,0); }
        public final FStat lstat(UnixRuntime r, String path) throws ErrnoException { return (FStat) fsop(LSTAT,r,path,0,0); }
        public final void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException { fsop(MKDIR,r,path,mode,0); }
        
        private Hashtable execCache = new Hashtable();
        private static class CacheEnt {
            public final long time;
            public final long size;
            public final Object o;
            public CacheEnt(long time, long size, Object o) { this.time = time; this.size = size; this.o = o; }
        }

        public synchronized Object exec(UnixRuntime r, String path) throws ErrnoException {
            FStat fstat = stat(r,path);
            if(fstat == null) return null;
            long mtime = fstat.mtime();
            long size = fstat.size();
            CacheEnt ent = (CacheEnt) execCache.get(path);
            if(ent != null) {
                System.err.println("Found cached entry for " + path);
            	    if(ent.time == mtime && ent.size == size) return ent.o;
                System.err.println("Cache was out of date");
                execCache.remove(path);
            }
            FD fd = open(r,path,RD_ONLY,0);
            if(fd == null) return null;
            Seekable s = fd.seekable();
            
            String[] command  = null;

            if(s == null) throw new ErrnoException(EACCES);
            byte[] buf = new byte[4096];
            
            try {
                int n = s.read(buf,0,buf.length);
                if(n == -1) throw new Error("zero length file");
                
                switch(buf[0]) {
                    case '\177': // possible ELF
                        if(n < 4 && s.tryReadFully(buf,n,4-n) != 4-n) throw new ErrnoException(ENOEXEC);
                        if(buf[1] != 'E' || buf[2] != 'L' || buf[3] != 'F') throw new ErrnoException(ENOEXEC);
                        break;
                    case '#':
                        if(n == 1) {
                        	    int n2 = s.read(buf,1,buf.length-1);
                            if(n2 == -1) throw new ErrnoException(ENOEXEC);
                            n += n2;
                        }
                        if(buf[1] != '!') throw new ErrnoException(ENOEXEC);
                        int p = 2;
                        n -= 2;
                        OUTER: for(;;) {
                        	    for(int i=p;i<p+n;i++) if(buf[i] == '\n') { p = i; break OUTER; }
                            p += n;
                            if(p == buf.length) break OUTER;
                            n = s.read(buf,p,buf.length-p);
                        }
                        command = new String[2];
                        int arg;
                        for(arg=2;arg<p;arg++) if(buf[arg] == ' ') break;
                        if(arg < p) {
                            int cmdEnd = arg;
                        	    while(arg < p && buf[arg] == ' ') arg++;
                            command[0] = new String(buf,2,cmdEnd);
                            command[1] = arg < p ? new String(buf,arg,p-arg) : null;
                        } else {
                        	    command[0] = new String(buf,2,p-2);
                        }
                        System.err.println("command[0]: " + command[0] + " command[1]: " + command[1]);
                        break;
                    default:
                        throw new ErrnoException(ENOEXEC);
                }
            } catch(IOException e) {
                fd.close();
            	    throw new ErrnoException(EIO);
            }
                        
            if(command == null) {
                // its an elf binary
                try {
                    s.seek(0);
                	    Class c = RuntimeCompiler.compile(s);
                    System.err.println("Compile succeeded: " + c);
                    ent = new CacheEnt(mtime,size,c);
                } catch(Compiler.Exn e) {
                    throw new ErrnoException(ENOEXEC);
                } catch(IOException e) {
                	    throw new ErrnoException(EIO);
                }
            } else {
            	    ent = new CacheEnt(mtime,size,command);
            }
            
            fd.close();
            
            execCache.put(path,ent);
            return ent.o;
        }
    }
        
    public abstract static class FS {
		// FIXME: inode stuff
        protected static FD directoryFD(String[] files, int hashCode)  {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            try {
            	    for(int i=0;i<files.length;i++) {
                	    	byte[] b = getBytes(files[i]);
                    int inode = (files[i].hashCode() ^ hashCode) & 0xfffff;
                    dos.writeInt(inode);
                    dos.writeInt(b.length);
                    dos.write(b,0,b.length);
                }
            } catch(IOException e) {
            	    return null;
            }
            final byte[] data = bos.toByteArray();
            return new SeekableFD(new Seekable.ByteArray(data,false),RD_ONLY) {
                protected FStat _fstat() { return  new FStat() {
                    public int length() { return data.length; }
                    public int type() { return S_IFDIR; }
                }; }
            };
        }

        public FStat lstat(UnixRuntime r, String path) throws ErrnoException { return stat(r,path); }

        // If this returns null it'll be truned into an ENOENT
        public abstract FD open(UnixRuntime r, String path, int flags, int mode) throws ErrnoException;
        // If this returns null it'll be turned into an ENOENT
        public abstract FStat stat(UnixRuntime r, String path) throws ErrnoException;
        public abstract void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException;
    }
        
    // FEATURE: chroot support in here
    private String normalizePath(String path) {
        boolean absolute = path.startsWith("/");
        int cwdl = cwd.length();
        // NOTE: This isn't just a fast path, it handles cases the code below doesn't
        if(!path.startsWith(".") && path.indexOf("./") == -1 && path.indexOf("//") == -1 && !path.endsWith("."))
            return absolute ? path.substring(1) : cwdl == 0 ? path : path.length() == 0 ? cwd : cwd + "/" + path;
        
        char[] in = new char[path.length()+1];
        char[] out = new char[in.length + (absolute ? -1 : cwd.length())];
        int inp=0, outp=0;
        
        if(absolute) {
            do { inp++; } while(in[inp] == '/');
        } else if(cwdl != 0) {
            	cwd.getChars(0,cwdl,out,0);
            	outp = cwdl;
        }
            
        path.getChars(0,path.length(),in,0);
        while(in[inp] != 0) {
            if(inp != 0) {
            	    if(in[inp] != '/') { out[outp++] = in[inp++]; continue; }
            	    while(in[inp] == '/') inp++;
            }
            if(in[inp] == '\0') continue;
            if(in[inp] != '.') { out[outp++] = '/'; out[outp++] = in[inp++]; continue; }
            if(in[inp+1] == '\0' || in[inp+1] == '/') { inp++; continue; }
            if(in[inp+1] == '.' && (in[inp+2] == '\0' || in[inp+2] == '/')) { // ..
                inp += 2;
                if(outp > 0) outp--;
                while(outp > 0 && out[outp] != '/') outp--;
                System.err.println("After ..: " + new String(out,0,outp));
                continue;
            }
            inp++;
            out[outp++] = '/';
            out[outp++] = '.';
        }
        if(outp > 0 && out[outp-1] == '/') outp--;
        //System.err.println("normalize: " + path + " -> " + new String(out,0,outp) + " (cwd: " + cwd + ")");
        return new String(out,0,outp);
    }
    
    FStat hostFStat(final File f) {
        boolean e = false;
        try {
            FileInputStream fis = new FileInputStream(f);
            switch(fis.read()) {
                case '\177': e = fis.read() == 'E' && fis.read() == 'L' && fis.read() == 'F'; break;
                case '#': e = fis.read() == '!';
            }
            fis.close();
        } catch(IOException e2) { } 
        return new HostFStat(f,e);
    }

    // FIXME: inode stuff
    FD hostFSDirFD(File f) { return FS.directoryFD(f.list(),f.hashCode()); }
    
    public static class HostFS extends FS {
        protected File root;
        public File getRoot() { return root; }
        
        private static File hostRootDir() {
            String cwd = getSystemProperty("user.dir");
            File f = new File(cwd != null ? cwd : ".");
            f = new File(f.getAbsolutePath());
            while(f.getParent() != null) f = new File(f.getParent());
            return f;
        }
        
        private File hostFile(String path) {
            char sep = File.separatorChar;
            if(sep != '/') {
                char buf[] = path.toCharArray();
                for(int i=0;i<buf.length;i++) {
                	    char c = buf[i];
                    if(c == '/') buf[i] = sep;
                    else if(c == sep) buf[i] = '/';
                }
                path = new String(buf);
            }
            return new File(root,path);
        }
        
        public HostFS() { this(hostRootDir()); }
        public HostFS(String root) { this(new File(root)); }
        public HostFS(File root) { this.root = root; }
        
        
        public FD open(UnixRuntime r, String path, int flags, int mode) throws ErrnoException {
            final File f = hostFile(path);
            return r.hostFSOpen(f,flags,mode);
        }
        
        public FStat stat(UnixRuntime r, String path) throws ErrnoException {
            File f = hostFile(path);
            if(r.sm != null && !r.sm.allowStat(f)) throw new ErrnoException(EACCES);
            if(!f.exists()) return null;
            return r.hostFStat(f);
        }
        
        public void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException {
            File f = hostFile(path);
            if(r.sm != null && !r.sm.allowWrite(f)) throw new ErrnoException(EACCES);
            if(f.exists() && f.isDirectory()) throw new ErrnoException(EEXIST);
            if(f.exists()) throw new ErrnoException(ENOTDIR);
            File parent = f.getParentFile();
            if(parent!=null && (!parent.exists() || !parent.isDirectory())) throw new ErrnoException(ENOTDIR);
            if(!f.mkdir()) throw new ErrnoException(EIO);            
        }
    }
        
    public static class DevFS extends FS {
        private static class DevFStat extends FStat {
            public int dev() { return 1; }
            public int mode() { return 0666; }
            public int type() { return S_IFCHR; }
            public int nlink() { return 1; }
        }
        private static FD devZeroFD = new FD() {
            public boolean readable() { return true; }
            public boolean writable() { return true; }
            public int read(byte[] a, int off, int length) { Arrays.fill(a,off,off+length,(byte)0); return length; }
            public int write(byte[] a, int off, int length) { return length; }
            public int seek(int n, int whence) { return 0; }
            public FStat _fstat() { return new DevFStat(); }
        };
        private static FD devNullFD = new FD() {
            public boolean readable() { return true; }
            public boolean writable() { return true; }
            public int read(byte[] a, int off, int length) { return 0; }
            public int write(byte[] a, int off, int length) { return length; }
            public int seek(int n, int whence) { return 0; }
            public FStat _fstat() { return new DevFStat(); }
        }; 
        
        public FD open(UnixRuntime r, String path, int mode, int flags) throws ErrnoException {
            if(path.equals("null")) return devNullFD;
            if(path.equals("zero")) return devZeroFD;
            if(path.startsWith("fd/")) {
                int n;
                try {
                    n = Integer.parseInt(path.substring(4));
                } catch(NumberFormatException e) {
                    return null;
                }
                if(n < 0 || n >= OPEN_MAX) return null;
                if(r.fds[n] == null) return null;
                return r.fds[n].dup();
            }
            if(path.equals("fd")) {
                int count=0;
                for(int i=0;i<OPEN_MAX;i++) if(r.fds[i] != null) count++; 
                String[] files = new String[count];
                count = 0;
                for(int i=0;i<OPEN_MAX;i++) if(r.fds[i] != null) files[count++] = Integer.toString(i);
                return directoryFD(files,hashCode());
            }
            if(path.equals("")) {
                String[] files = { "null", "zero", "fd" };
                return directoryFD(files,hashCode());
            }
            return null;
        }
        
        public FStat stat(UnixRuntime r,String path) throws ErrnoException {
            if(path.equals("null")) return devNullFD.fstat();
            if(path.equals("zero")) return devZeroFD.fstat();            
            if(path.startsWith("fd/")) {
                int n;
                try {
                    n = Integer.parseInt(path.substring(4));
                } catch(NumberFormatException e) {
                    return null;
                }
                if(n < 0 || n >= OPEN_MAX) return null;
                if(r.fds[n] == null) return null;
                return r.fds[n].fstat();
            }
            if(path.equals("fd")) return new FStat() { public int type() { return S_IFDIR; } public int mode() { return 0444; }};
            if(path.equals("")) return new FStat() { public int type() { return S_IFDIR; } public int mode() { return 0444; }};
            return null;
        }
        
        public void mkdir(UnixRuntime r, String path, int mode) throws ErrnoException { throw new ErrnoException(EACCES); }
    }
}

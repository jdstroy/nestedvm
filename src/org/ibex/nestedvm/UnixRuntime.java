package org.ibex.nestedvm;

import org.ibex.nestedvm.util.*;
import java.io.*;
import java.util.*;

// FEATURE: BusyBox's ASH doesn't like \r\n at the end of lines
// is ash just broken or are many apps like this? if so workaround in nestedvm

public abstract class UnixRuntime extends Runtime {
    /** The pid of this "process" */
    private int pid;
    private int ppid;
    protected int getPid() { return pid; }
    
    /** processes filesystem */
    private FS fs;
    public FS getFS() { return fs; }
    public void setFS(FS fs) {
        if(state >= RUNNING) throw new IllegalStateException("Can't change fs while process is running");
        this.fs = fs;
    }
    
    /** proceses' current working directory - absolute path WITHOUT leading slash
        "" = root, "bin" = /bin "usr/bin" = /usr/bin */
    private String cwd;
    
    /* Static stuff */
    // FEATURE: Most of this is O(n) or worse - fix it
    private final Object waitNotification = new Object();
    private final static int MAX_TASKS = 256;
    private final static UnixRuntime[] tasks = new UnixRuntime[MAX_TASKS];
    private static int addTask(UnixRuntime rt) {
        synchronized(tasks) {
            for(int i=1;i<MAX_TASKS;i++) {
                if(tasks[i] == null) {
                    tasks[i] = rt;
                    rt.pid = i;
                    return i;
                }
            }
            return -1;
        }
    }
    private static void removeTask(UnixRuntime rt) {
        synchronized(tasks) {
            for(int i=1;i<MAX_TASKS;i++)
                if(tasks[i] == rt) { tasks[i] = null; break; }
        }
    }
    
    public UnixRuntime(int pageSize, int totalPages, boolean allowEmptyPages) {
        super(pageSize,totalPages,allowEmptyPages);
        
        FS root = new HostFS();
        FS dev = new DevFS();
        MountPointFS mounts = new MountPointFS(root);
        mounts.add("/dev",dev);
        fs = mounts;
        
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
    
    protected String[] createEnv(String[] extra) {
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
    
    protected void _start() {
        if(addTask(this) < 0) throw new Error("Task list full");
    }
    
    protected void _exit() {
        synchronized(tasks) {
            if(ppid == 0) removeTask(this);
            for(int i=0;i<MAX_TASKS;i++) {
                if(tasks[i] != null && tasks[i].ppid == pid) {
                    if(tasks[i].state == DONE) removeTask(tasks[i]);
                    else tasks[i].ppid = 0;
                }
            }
            state = DONE;
            if(ppid != 0) synchronized(tasks[ppid].waitNotification) { tasks[ppid].waitNotification.notify(); }
        }
    }

    protected int syscall(int syscall, int a, int b, int c, int d) {
        switch(syscall) {
            case SYS_kill: return sys_kill(a,b);
            case SYS_fork: return sys_fork();
            case SYS_pipe: return sys_pipe(a);
            case SYS_dup2: return sys_dup2(a,b);
            case SYS_waitpid: return sys_waitpid(a,b,c);
            case SYS_stat: return sys_stat(a,b);
            case SYS_mkdir: return sys_mkdir(a,b);
            case SYS_getcwd: return sys_getcwd(a,b);
            case SYS_chdir: return sys_chdir(a);
            case SYS_execve: return sys_execve(a,b,c);

            default: return super.syscall(syscall,a,b,c,d);
        }
    }
    
    protected FD open(String path, int flags, int mode) throws IOException {
        return fs.open(normalizePath(path),flags,mode);
    }

    // FEATURE: Allow simple, broken signal delivery to other processes 
    // (check if a signal was delivered before and after syscalls)
    // FEATURE: Implement raise() in terms of call("raise",...) - kinda cheap, but it keeps the complexity in newlib
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
            default: {
                String msg = "Terminating on signal: " + signal + "\n";
                exitStatus = 1;
                state = DONE;
                if(fds[2]==null) {
                    System.out.print(msg);
                } else {
                    try {
                        byte[] b = getBytes(msg); 
                        fds[2].write(b,0,b.length);
                    }
                    catch(IOException e) { /* ignore */ }
                }
            }
        }
        return 0;
    }

    private int sys_waitpid(int pid, int statusAddr, int options) {
        final int WNOHANG = 1;
        if((options & ~(WNOHANG)) != 0) return -EINVAL;
        if(pid !=-1 && (pid <= 0 || pid >= MAX_TASKS)) return -ECHILD;
        for(;;) {
            synchronized(tasks) {
                UnixRuntime task = null;
                if(pid == -1) {
                    for(int i=0;i<MAX_TASKS;i++) {
                        if(tasks[i] != null && tasks[i].ppid == this.pid && tasks[i].state == DONE) {
                            task = tasks[i];
                            break;
                        }
                    }
                } else if(tasks[pid] != null && tasks[pid].ppid == this.pid && tasks[pid].state == DONE) {
                    task = tasks[pid];
                }
                
                if(task != null) {
                    removeTask(task);
                    try {
                        if(statusAddr!=0) memWrite(statusAddr,task.exitStatus()<<8);
                    } catch(FaultException e) {
                        return -EFAULT;
                    }

                    return task.pid;
                }
            }
            if((options&WNOHANG)!=0) return 0;
            synchronized(waitNotification) {
                try { waitNotification.wait(); } catch(InterruptedException e) { /* ignore */ }
            }
        }
    }
    
    // FEATURE: Make this cleaner
    // Great ugliness lies within.....
    private int sys_fork() {
        CPUState state = getCPUState();
        int sp = state.r[SP];
        final UnixRuntime r;
        try {
            r = (UnixRuntime) getClass().newInstance();
        } catch(Exception e) {
            System.err.println(e);
            return -ENOMEM;
        }
        int child_pid = addTask(r);
        if(child_pid < 0) return -ENOMEM;
        
        r.ppid = pid;
        r.brkAddr = brkAddr;
        r.fds = new FD[OPEN_MAX];
        for(int i=0;i<OPEN_MAX;i++) if(fds[i] != null) r.fds[i] = fds[i].dup();
        r.cwd = cwd;
        r.fs = fs;
        for(int i=0;i<TOTAL_PAGES;i++) {
            if(readPages[i] == null) continue;
            if(isEmptyPage(writePages[i])) {
                r.readPages[i] = r.writePages[i] = writePages[i];
            } else if(writePages[i] != null) {
                r.readPages[i] = r.writePages[i] = new int[PAGE_WORDS];
                if(STACK_BOTTOM == 0 || i*PAGE_SIZE < STACK_BOTTOM || i*PAGE_SIZE >= sp-PAGE_SIZE*2)
                    System.arraycopy(writePages[i],0,r.writePages[i],0,PAGE_WORDS);
            } else {
                r.readPages[i] = r.readPages[i];
            }
        }
        state.r[V0] = 0;
        state.pc += 4;
        r.setCPUState(state);
        r.state = PAUSED;
        
        new Thread() {
            public void run() {
                try {
                    while(!r.execute());
                } catch(Exception e) {
                    System.err.println("Forked process threw exception: ");
                    e.printStackTrace();
                }
            }
        }.start();
        
        return child_pid;        
    }
        
    private int sys_execve(int cstring, int argv, int envp) {
        /*
        try {
            String path = cstring(cstring);
            FStat stat = fs.stat(path);
            
            
        }
        catch(FaultException e) { return -EFAULT; }
        catch(FileNotFoundException e) {  return -ENOENT; }
        catch(IOException e) { return -EIO; }*/
        throw new Error("FIXME - exec() isn't finished");
    }
            
    private int sys_pipe(int addr) {
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
    
    private int sys_stat(int cstring, int addr) {
        try {
            String path = normalizePath(cstring(cstring));
            return stat(fs.stat(path),addr);
        }
        catch(ErrnoException e) { return -e.errno; }
        catch(FileNotFoundException e) {
            if(e.getMessage() != null && e.getMessage().indexOf("Permission denied") >= 0) return -EACCES;
            return -ENOENT;
        }
        catch(IOException e) { return -EIO; }
        catch(FaultException e) { return -EFAULT; }
    }
    
    
    private int sys_mkdir(int cstring, int mode) {
        try {
            fs.mkdir(normalizePath(cstring(cstring)));
            return 0;
        }
        catch(ErrnoException e) { return -e.errno; }
        catch(FileNotFoundException e) { return -ENOENT; }
        catch(IOException e) { return -EIO; }
        catch(FaultException e) { return -EFAULT; }
    }
   
    
    private int sys_getcwd(int addr, int size) {
        byte[] b = getBytes(cwd);
        if(size == 0) return -EINVAL;
        if(size < b.length+2) return -ERANGE;
        try {
            memset(addr,'/',1);
            copyout(b,addr+1,b.length);
            memset(addr+b.length+1,0,1);
            return addr;
        } catch(FaultException e) {
            return -EFAULT;
        }
    }
    
    private int sys_chdir(int addr) {
        try {
            String path = normalizePath(cstring(addr));
            System.err.println("Chdir: " + cstring(addr) + " -> " + path + " pwd: " + cwd);
            if(fs.stat(path).type() != FStat.S_IFDIR) return -ENOTDIR;
            cwd = path;
            System.err.println("Now: [" + cwd + "]");
            return 0;
        }
        catch(ErrnoException e) { return -e.errno; }
        catch(FileNotFoundException e) { return -ENOENT; }
        catch(IOException e) { return -EIO; }
        catch(FaultException e) { return -EFAULT; }
    }

    public void chdir(String dir) throws FileNotFoundException {
        if(state >= RUNNING) throw new IllegalStateException("Can't chdir while process is running");
        try {
            dir = normalizePath(dir);
            if(fs.stat(dir).type() != FStat.S_IFDIR) throw new FileNotFoundException();
        } catch(IOException e) {
            throw new FileNotFoundException();
        }
        cwd = dir;
    }
        
    public abstract static class FS {
        protected FD _open(String path, int flags, int mode) throws IOException { return null; }
        protected FStat _stat(String path) throws IOException { return null; }
        protected void _mkdir(String path) throws IOException { throw new ErrnoException(EROFS); }
        
        protected static final int OPEN = 1;
        protected static final int STAT = 2;
        protected static final int MKDIR = 3;
        
        protected Object op(int op, String path, int arg1, int arg2) throws IOException {
        		switch(op) {
        			case OPEN: return _open(path,arg1,arg2);
        			case STAT: return _stat(path);
        			case MKDIR: _mkdir(path); return null;
        			default: throw new IllegalArgumentException("Unknown FS OP");
        		}
        }
        
        public final FD open(String path, int flags, int mode) throws IOException { return (FD) op(OPEN,path,flags,mode); }
        public final FStat stat(String path) throws IOException { return (FStat) op(STAT,path,0,0); }
        public final void mkdir(String path) throws IOException { op(MKDIR,path,0,0); }
		
		// FIXME: inode stuff
        protected static FD directoryFD(String[] files, int hashCode) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            for(int i=0;i<files.length;i++) {
                byte[] b = getBytes(files[i]);
                int inode = (files[i].hashCode() ^ hashCode) & 0xfffff;
                dos.writeInt(inode);
                dos.writeInt(b.length);
                dos.write(b,0,b.length);
            }
            final byte[] data = bos.toByteArray();
            return new SeekableFD(new Seekable.ByteArray(data,false),RD_ONLY) {
                protected FStat _fstat() { return  new FStat() {
                    public int length() { return data.length; }
                    public int type() { return S_IFDIR; }
                }; }
            };
        }
    }
        
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
            out[outp++] = '.';
        }
        if(outp > 0 && out[outp-1] == '/') outp--;
        //System.err.println("normalize: " + path + " -> " + new String(out,0,outp) + " (cwd: " + cwd + ")");
        return new String(out,0,outp);
    }
    
    // FIXME: This is probably still buggy
    // FEATURE: Remove some of the "should never happen checks"
    /*protected static String cleanupPath(String p) throws ErrnoException {
        if(p.length() == 0) throw new ErrnoException(ENOENT);
        if(needsCleanup(p)) {
            char[] in = p.toCharArray();
            char[] out;
            int outp ;
            if(in[0] == '/') {
                out = new char[in.length];
                outp = 0;
            } else {
                out = new char[cwd.length() + in.length + 1];
                outp = cwd.length();
                for(int i=0;i<outp;i++) out[i] = cwd.charAt(i);
                if(outp == 0 || out[0] != '/') throw new Error("should never happen");
            }
            int inLength = in.length;
            int inp = 0;
            while(inp<inLength) {
                if(inp == 0 || in[inp] == '/') {
                    while(inp < inLength && in[inp] == '/') inp++;
                    if(inp == inLength) break;
                    if(in[inp] == '.') {
                        if(inp+1 == inLength) break;
                        if(in[inp+1] == '.' && (inp+2 == inLength || in[inp+2] == '/')) {
                            inp+=2;
                            if(outp == 0) continue;
                            do { outp--; } while(outp > 0 && out[outp] != '/');
                        } else if(in[inp+1] == '/') {
                            inp++;
                        } else {
                            out[outp++] = '/';
                        }
                    } else {
                        out[outp++] = '/';
                        out[outp++] = in[inp++];
                    }
                } else {
                    out[outp++] = in[inp++];
                }
            }
            if(outp == 0) out[outp++] = '/';
            return new String(out,0,outp);
        } else {
            if(p.startsWith("/")) return p;
            StringBuffer sb = new StringBuffer(cwd);
            if(!cwd.equals("/")) sb.append('/');
            return sb.append(p).toString();
        }
    }*/
    
    public static class MountPointFS extends FS {
        private static class MP {
            public MP(String path, FS fs) { this.path = path; this.fs = fs; }
            public String path;
            public FS fs;
            public int compareTo(Object o) {
                if(!(o instanceof MP)) return 1;
                return -path.compareTo(((MP)o).path);
            }
        }
        private final MP[][] mps = new MP[128][];
        private final FS root;
        public MountPointFS(FS root) { this.root = root; }
        
        private static String fixup(String path) {
            if(!path.startsWith("/")) throw new IllegalArgumentException("Mount point doesn't start with a /");
            path = path.substring(1);
            if(path.length() == 0) throw new IllegalArgumentException("Zero length mount point path");
            return path;
        }
        public FS get(String path) {
            path = fixup(path);
            int f = path.charAt(0) & 0x7f;
            for(int i=0;mps[f] != null && i < mps[f].length;i++)
                if(mps[f][i].path.equals(path)) return mps[f][i].fs;
            return null;
        }
        
        public void add(String path, FS fs) {
            if(get(path) != null) throw new IllegalArgumentException("mount point already exists");
            path = fixup(path);
            int f = path.charAt(0) & 0x7f;
            int oldLength = mps[f] == null ? 0 : mps[f].length;
            MP[] newList = new MP[oldLength + 1];
            if(oldLength != 0) System.arraycopy(mps[f],0,newList,0,oldLength);
            newList[oldLength] = new MP(path,fs);
            Arrays.sort(newList);
            mps[f] = newList;
        }
        
        public void remove(String path) {
            path = fixup(path);
            if(get(path) == null) throw new IllegalArgumentException("mount point doesn't exist");
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
        
        protected Object op(int op, String path, int arg1, int arg2) throws IOException {
            int pl = path.length();
            if(pl != 0) {
            	    MP[] list = mps[path.charAt(0) & 0x7f];
            	    if(list != null) for(int i=0;i<list.length;i++) {
            	    	    	MP mp = list[i];
            	    	    	int mpl = mp.path.length();
            	    	    	if(pl == mpl || (pl < mpl && path.charAt(mpl) == '/'))
            	    	    	return mp.fs.op(op,pl == mpl ? "" : path.substring(mpl+1),arg1,arg2);
                 }
            }
            return root.op(op,path,arg1,arg2);
        }
    }
    
    // FEATURE: Probably should make this more general - support mountpoints, etc
    /*public class UnixOverlayFS extends FS {
        private final FS root;
        private final FS dev = new DevFS();
        public UnixOverlayFS(FS root) {
            this.root = root;
        }
        private String devPath(String path) {
            if(path.startsWith("/dev")) {
                if(path.length() == 4) return "/";
                if(path.charAt(4) == '/') return path.substring(4);
            }
            return null;
        }
        public FD open(String path, int flags, int mode) throws IOException{
            String dp = devPath(path);
            return dp == null ? root.open(path,flags,mode) : dev.open(dp,flags,mode);
        }
        public FStat stat(String path) throws IOException {
            String dp = devPath(path);
            return dp == null ? root.stat(path) : dev.stat(dp);
        }
        public void mkdir(String path) throws IOException {
            String dp = devPath(path);
            if(dp == null) root.mkdir(path);
            else dev.mkdir(dp);
        }
    }*/
    
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
            if(File.separatorChar != '/') path = path.replace('/',File.separatorChar);
            return new File(root,path);
        }
        
        public HostFS() { this(hostRootDir()); }
        public HostFS(String root) { this(new File(root)); }
        public HostFS(File root) { this.root = root; }
        
        
        // FEATURE: This shares a lot with Runtime.open
        // NOTE: createNewFile is a Java2 function
        public FD _open(String path, int flags, int mode) throws IOException {
            final File f = hostFile(path);
            if(f.isDirectory()) {
                if((flags&3)!=RD_ONLY) throw new ErrnoException(EACCES);
                return directoryFD(f.list(),path.hashCode());
            }
            if((flags & (O_EXCL|O_CREAT)) == (O_EXCL|O_CREAT))
                if(!f.createNewFile()) throw new ErrnoException(EEXIST);
            if((flags&O_CREAT) == 0 && !f.exists())
                return null;
            final Seekable.File sf = new Seekable.File(f,(flags&3)!=RD_ONLY);
            if((flags&O_TRUNC)!=0) sf.setLength(0);
            return new SeekableFD(sf,mode) {
                protected FStat _fstat() { return new HostFStat(f) {
                    public int size() {
                        try { return sf.length(); } catch(IOException e) { return 0; }
                    }
                };}
            };
        }
        
        public FStat _stat(String path) throws FileNotFoundException {
            File f = hostFile(path);
            if(!f.exists()) throw new FileNotFoundException();
            return new HostFStat(f);
        }
        
        public void _mkdir(String path) throws IOException {
            File f = hostFile(path);
            if(f.exists() && f.isDirectory()) throw new ErrnoException(EEXIST);
            if(f.exists()) throw new ErrnoException(ENOTDIR);
            File parent = f.getParentFile();
            if(parent!=null && (!parent.exists() || !parent.isDirectory())) throw new ErrnoException(ENOTDIR);
            if(!f.mkdir()) throw new ErrnoException(EIO);            
        }
    }
        
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
    
    public class DevFS extends FS {
        public FD _open(String path, int mode, int flags) throws IOException {
            if(path.equals("null")) return devNullFD;
            if(path.equals("zero")) return devZeroFD;
            if(path.startsWith("fd/")) {
                int n;
                try {
                    n = Integer.parseInt(path.substring(4));
                } catch(NumberFormatException e) {
                    throw new FileNotFoundException();
                }
                if(n < 0 || n >= OPEN_MAX) throw new FileNotFoundException();
                if(fds[n] == null) throw new FileNotFoundException();
                return fds[n].dup();
            }
            if(path.equals("fd")) {
                int count=0;
                for(int i=0;i<OPEN_MAX;i++) if(fds[i] != null) count++; 
                String[] files = new String[count];
                count = 0;
                for(int i=0;i<OPEN_MAX;i++) if(fds[i] != null) files[count++] = Integer.toString(i);
                return directoryFD(files,hashCode());
            }
            if(path.equals("")) {
                String[] files = { "null", "zero", "fd" };
                return directoryFD(files,hashCode());
            }
            throw new FileNotFoundException();
        }
        
        public FStat _stat(String path) throws IOException {
            if(path.equals("null")) return devNullFD.fstat();
            if(path.equals("zero")) return devZeroFD.fstat();            
            if(path.startsWith("fd/")) {
                int n;
                try {
                    n = Integer.parseInt(path.substring(4));
                } catch(NumberFormatException e) {
                    throw new FileNotFoundException();
                }
                if(n < 0 || n >= OPEN_MAX) throw new FileNotFoundException();
                if(fds[n] == null) throw new FileNotFoundException();
                return fds[n].fstat();
            }
            if(path.equals("fd")) return new FStat() { public int type() { return S_IFDIR; } public int mode() { return 0444; }};
            if(path.equals("")) return new FStat() { public int type() { return S_IFDIR; } public int mode() { return 0444; }};
            throw new FileNotFoundException();
        }
        
        public void _mkdir(String path) throws IOException { throw new ErrnoException(EACCES); }
    }
}

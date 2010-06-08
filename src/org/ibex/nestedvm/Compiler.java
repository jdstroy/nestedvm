// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm;

import java.util.*;
import java.io.*;

import org.ibex.nestedvm.util.*;

public abstract class Compiler implements Registers {    
    /** The ELF binary being read */
    ELF elf;
    
    /** The name of the class beging generated */
    final String fullClassName;
    
    /** The name of the binary this class is begin generated from */
    String source = "unknown.mips.binary";
    public void setSource(String source) { this.source = source; }
        
    /** Thrown when the compilation fails for some reason */
    static class Exn extends Exception { public Exn(String s) { super(s); } }
    
    // Set this to true to enable fast memory access 
    // When this is enabled a Java RuntimeException will be thrown when a page fault occures. When it is disabled
    // a FaultException will be throw which is easier to catch and deal with, however. as the name implies, this is slower
    boolean fastMem = true;
    
    // This MUST be a power of two. If it is not horrible things will happen
    // NOTE: This value can be much higher without breaking the classfile 
    // specs (around 1024) but Hotstop seems to do much better with smaller
    // methods. 
    int maxInsnPerMethod = 128;
    
    // non-configurable
    int maxBytesPerMethod;
    int methodMask;
    int methodShift;
    void maxInsnPerMethodInit() throws Exn {
        if((maxInsnPerMethod&(maxInsnPerMethod-1)) != 0) throw new Exn("maxBytesPerMethod is not a power of two");
        maxBytesPerMethod = maxInsnPerMethod*4;
        methodMask = ~(maxBytesPerMethod-1);
        while(maxBytesPerMethod>>>methodShift != 1) methodShift++;
    }
    
    // True to try to determine which case statement are needed and only include them
    boolean pruneCases = true;
    
    boolean assumeTailCalls = true;
    
    // True to insert some code in the output to help diagnore compiler problems
    boolean debugCompiler = false;
    
    // True to print various statistics about the compilation
    boolean printStats = false;
    
    // True to generate runtime statistics that slow execution down significantly
    boolean runtimeStats = false;
    
    boolean supportCall = true;
    
    boolean nullPointerCheck = false;
    
    String runtimeClass = "org.ibex.nestedvm.Runtime";
    
    String hashClass = "java.util.Hashtable";
    
    boolean unixRuntime;
    
    boolean lessConstants;
    
    boolean singleFloat;
            
    int pageSize = 4096;
    int totalPages = 65536;
    int pageShift;
    boolean onePage;
    
    void pageSizeInit() throws Exn {
        if((pageSize&(pageSize-1)) != 0) throw new Exn("pageSize not a multiple of two");
        if((totalPages&(totalPages-1)) != 0) throw new Exn("totalPages not a multiple of two");
        while(pageSize>>>pageShift != 1) pageShift++;
    }
    
    /** A set of all addresses that can be jumped too (only available if pruneCases == true) */
    Hashtable jumpableAddresses;
    
    /** Some important symbols */
    ELF.Symbol userInfo, gp;
    
    private static void usage() {
        System.err.println("Usage: java Compiler [-outfile output.java] [-o options] [-dumpoptions] <classname> <binary.mips>");
        System.err.println("-o takes mount(8) like options and can be specified multiple times");
        System.err.println("Available options:");
        for(int i=0;i<options.length;i+=2)
            System.err.print(options[i] + ": " + wrapAndIndent(options[i+1],18-2-options[i].length(),18,62));
        System.exit(1);
    }
    
    public static void main(String[] args) throws IOException {
        String outfile = null;
        String outdir = null;
        String o = null;
        String className = null;
        String mipsBinaryFileName = null;
        String outformat = null;
        boolean dumpOptions = false;
        int arg = 0;
        while(args.length-arg > 0) {
            if(args[arg].equals("-outfile")) {
                arg++;
                if(arg==args.length) usage();
                outfile = args[arg];
            } else if(args[arg].equals("-d")) {
                arg++;
                if(arg==args.length) usage();
                outdir = args[arg];
            } else if(args[arg].equals("-outformat")) {
                arg++;
                if(arg==args.length) usage();
                outformat = args[arg];
            } else if(args[arg].equals("-o")) {
                arg++;
                if(arg==args.length) usage();
                if(o==null || o.length() == 0)
                    o = args[arg];
                else if(args[arg].length() != 0)
                    o += "," + args[arg];
            } else if(args[arg].equals("-dumpoptions")) {
                dumpOptions = true;
            } else if(className == null) {
                className = args[arg];
            } else if(mipsBinaryFileName == null) {
                mipsBinaryFileName = args[arg];
            } else {
                usage();
            }
            arg++;
        }
        if(className == null || mipsBinaryFileName == null) usage();
        
        Seekable mipsBinary = new Seekable.File(mipsBinaryFileName);
        
        Writer w = null;
        OutputStream os = null;
        Compiler comp = null;
        if(outformat == null || outformat.equals("class")) {
            if(outfile != null) {
                os = new FileOutputStream(outfile);
                comp = new ClassFileCompiler(mipsBinary,className,os);
            } else if(outdir != null) {
                File f = new File(outdir);
                if(!f.isDirectory()) {
                    System.err.println(outdir + " doesn't exist or is not a directory");
                    System.exit(1);
                }
                comp = new ClassFileCompiler(mipsBinary,className,f);
            } else {
                System.err.println("Refusing to write a classfile to stdout - use -outfile foo.class");
                System.exit(1);
            }
        } else if(outformat.equals("javasource") || outformat .equals("java")) {
            w = outfile == null ? new OutputStreamWriter(System.out): new FileWriter(outfile);
            comp = new JavaSourceCompiler(mipsBinary,className,w);
        } else {
            System.err.println("Unknown output format: " + outformat);
            System.exit(1);
        }
        
        comp.parseOptions(o);
        comp.setSource(mipsBinaryFileName);
        
        if(dumpOptions) {
            System.err.println("== Options ==");
            for(int i=0;i<options.length;i+=2)
                System.err.println(options[i] + ": " + comp.getOption(options[i]).get());
            System.err.println("== End Options ==");
        }
            
        try {
            comp.go();
        } catch(Exn e) {
            System.err.println("Compiler Error: " + e.getMessage());
            System.exit(1);
        } finally {
            if(w != null) w.close();
            if(os != null) os.close();
        }
    }
        
    public Compiler(Seekable binary, String fullClassName) throws IOException {
        this.fullClassName = fullClassName;
        elf = new ELF(binary);
        
        if(elf.header.type != ELF.ET_EXEC) throw new IOException("Binary is not an executable");
        if(elf.header.machine != ELF.EM_MIPS) throw new IOException("Binary is not for the MIPS I Architecture");
        if(elf.ident.data != ELF.ELFDATA2MSB) throw new IOException("Binary is not big endian");
    }

    abstract void _go() throws Exn, IOException;
    
    private boolean used;
    public void go() throws Exn, IOException {
        if(used) throw new RuntimeException("Compiler instances are good for one shot only");
        used = true;
        
        if(onePage && pageSize <= 4096) pageSize = 4*1024*1024;
        if(nullPointerCheck && !fastMem) throw new Exn("fastMem must be enabled for nullPointerCheck to be of any use");
        if(onePage && !fastMem) throw new Exn("fastMem must be enabled for onePage to be of any use");
        if(totalPages == 1 && !onePage) throw new Exn("totalPages == 1 and onePage is not set");
        if(onePage) totalPages = 1;

        maxInsnPerMethodInit();
        pageSizeInit();
        
        // Get a copy of the symbol table in the elf binary
        ELF.Symtab symtab = elf.getSymtab();
        if(symtab == null) throw new Exn("Binary has no symtab (did you strip it?)");
        ELF.Symbol sym;
        
        userInfo = symtab.getGlobalSymbol("user_info");
        gp = symtab.getGlobalSymbol("_gp");
        if(gp == null) throw new Exn("no _gp symbol (did you strip the binary?)");   
        
        if(pruneCases) {
            // Find all possible branches
            jumpableAddresses = new Hashtable();
            
            jumpableAddresses.put(new Integer(elf.header.entry),Boolean.TRUE);
            
            ELF.SHeader text = elf.sectionWithName(".text");
            if(text == null) throw new Exn("No .text segment");
            
            findBranchesInSymtab(symtab,jumpableAddresses);
            
            for(int i=0;i<elf.sheaders.length;i++) {
                ELF.SHeader sheader = elf.sheaders[i];
                String name = sheader.name;
                // if this section doesn't get loaded into our address space don't worry about it
                if(sheader.addr == 0x0) continue;
                if(name.equals(".data") || name.equals(".sdata") || name.equals(".rodata") || name.equals(".ctors") || name.equals(".dtors"))
                    findBranchesInData(new DataInputStream(sheader.getInputStream()),sheader.size,jumpableAddresses,text.addr,text.addr+text.size);
            }
            
            findBranchesInText(text.addr,new DataInputStream(text.getInputStream()),text.size,jumpableAddresses);            
        }

        if(unixRuntime && runtimeClass.startsWith("org.ibex.nestedvm.")) runtimeClass = "org.ibex.nestedvm.UnixRuntime";
        
        for(int i=0;i<elf.sheaders.length;i++) {
            String name = elf.sheaders[i].name;
            if((elf.sheaders[i].flags & ELF.SHF_ALLOC) !=0 && !(
                name.equals(".text")|| name.equals(".data") || name.equals(".sdata") || name.equals(".rodata") ||
                name.equals(".ctors") || name.equals(".dtors") || name.equals(".bss") || name.equals(".sbss")))
                    throw new Exn("Unknown section: " + name);
        }
        _go();
    }
    
    private void findBranchesInSymtab(ELF.Symtab symtab, Hashtable jumps) {
        ELF.Symbol[] symbols = symtab.symbols;
        int n=0;
        for(int i=0;i<symbols.length;i++) {
            ELF.Symbol s = symbols[i];
            if(s.type == ELF.Symbol.STT_FUNC) {
                if(jumps.put(new Integer(s.addr),Boolean.TRUE) == null) {
                    //System.err.println("Adding symbol from symtab: " + s.name + " at " + toHex(s.addr));
                    n++;
                }
            }
        }
        if(printStats) System.err.println("Found " + n + " additional possible branch targets in Symtab");
    }
    
    private void findBranchesInText(int base, DataInputStream dis, int size, Hashtable jumps) throws IOException {
        int count = size/4;
        int pc = base;
        int n=0;
        int[] lui_val = new int[32];
        int[] lui_pc = new int[32];
        //Interpreter inter = new Interpreter(source);
        
        for(int i=0;i<count;i++,pc+=4) {
            int insn = dis.readInt();
            int op = (insn >>> 26) & 0xff; 
            int rs = (insn >>> 21) & 0x1f;
            int rt = (insn >>> 16) & 0x1f;
            int signedImmediate = (insn << 16) >> 16;
            int unsignedImmediate = insn & 0xffff;
            int branchTarget = signedImmediate;
            int jumpTarget = (insn & 0x03ffffff);
            int subcode = insn & 0x3f;
            
            switch(op) {
                case 0:
                    switch(subcode) {
                        case 9: // JALR
                            if(jumps.put(new Integer(pc+8),Boolean.TRUE) == null) n++; // return address
                            break;
                        case 12: // SYSCALL
                            if(jumps.put(new Integer(pc+4),Boolean.TRUE) == null) n++; 
                            break;
                    }
                    break;
                case 1:
                    switch(rt) {
                        case 16: // BLTZAL
                        case 17: // BGTZAL
                            if(jumps.put(new Integer(pc+8),Boolean.TRUE) == null) n++; // return address
                            // fall through
                        case 0: // BLTZ
                        case 1: // BGEZ
                            if(jumps.put(new Integer(pc+branchTarget*4+4),Boolean.TRUE) == null) n++;
                            break;
                    }
                    break;
                case 3: // JAL
                    if(jumps.put(new Integer(pc+8),Boolean.TRUE) == null) n++; // return address
                    // fall through
                case 2: // J
                    if(jumps.put(new Integer((pc&0xf0000000)|(jumpTarget << 2)),Boolean.TRUE) == null) n++;
                    break;
                case 4: // BEQ
                case 5: // BNE
                case 6: // BLEZ
                case 7: // BGTZ
                    if(jumps.put(new Integer(pc+branchTarget*4+4),Boolean.TRUE) == null) n++;
                    break;
                case 9: { // ADDIU
                    if(pc - lui_pc[rs] <= 4*32) {
                        int t = (lui_val[rs]<<16)+signedImmediate;
                        if((t&3)==0 && t >= base && t < base+size) {
                            if(jumps.put(new Integer(t),Boolean.TRUE) == null) {
                                //System.err.println("Possible jump to " + toHex(t) + " (" + inter.sourceLine(t) + ") from " + toHex(pc) + " (" + inter.sourceLine(pc) + ")");
                                n++;
                            }
                        }
                        // we just blew it away
                        if(rt == rs) lui_pc[rs] = 0;
                    }
                    break;
                }
                case 15: { // LUI
                    lui_val[rt] = unsignedImmediate;
                    lui_pc[rt] = pc;
                    break;
                }
                    
                case 17: // FPU Instructions
                    switch(rs) {
                        case 8: // BC1F, BC1T
                            if(jumps.put(new Integer(pc+branchTarget*4+4),Boolean.TRUE) == null) n++;
                            break;
                    }
                    break;
            }
        }
        dis.close();
        if(printStats) System.err.println("Found " + n + " additional possible branch targets in Text segment");
    }
    
    private void findBranchesInData(DataInputStream dis, int size, Hashtable jumps, int textStart, int textEnd) throws IOException {
        int count = size/4;
        int n=0;
        for(int i=0;i<count;i++) {
            int word = dis.readInt();
            if((word&3)==0 && word >= textStart && word < textEnd) {
                if(jumps.put(new Integer(word),Boolean.TRUE) == null) {
                    //System.err.println("Added " + toHex(word) + " as possible branch target (fron data segment)");
                    n++;
                }
            }
        }
        dis.close();
        if(n>0 && printStats) System.err.println("Found " + n + " additional possible branch targets in Data segment");
    }
    
    // Helper functions for pretty output
    final static String toHex(int n) { return "0x" + Long.toString(n & 0xffffffffL, 16); }
    final static String toHex8(int n) {
        String s = Long.toString(n & 0xffffffffL, 16);
        StringBuffer sb = new StringBuffer("0x");
        for(int i=8-s.length();i>0;i--) sb.append('0');
        sb.append(s);
        return sb.toString();
    }

    final static String toOctal3(int n) {
        char[] buf = new char[3];
        for(int i=2;i>=0;i--) {
            buf[i] = (char) ('0' + (n & 7));
            n >>= 3;
        }
        return new String(buf);
    }

    // Option parsing
    private class Option {
        private java.lang.reflect.Field field;
        public Option(String name) throws NoSuchFieldException { field = name==null ? null : Compiler.class.getDeclaredField(name); }
        public void set(Object val) {
            if(field == null) return;
            try {
                /*field.setAccessible(true); NOT in JDK 1.1 */
                field.set(Compiler.this,val);
            } catch(IllegalAccessException e) {
                System.err.println(e);
            }
        }
        public Object get() {
            if(field == null) return null;
            try {
                /*field.setAccessible(true); NOT in JDK 1.1 */
                return field.get(Compiler.this);
            } catch(IllegalAccessException e) {
                System.err.println(e); return null;
            }
        }
        public Class getType() { return field == null ? null : field.getType(); }
    }
    
    private static String[] options = {
        "fastMem",          "Enable fast memory access - RuntimeExceptions will be thrown on faults",
        "nullPointerCheck", "Enables checking at runtime for null pointer accessses (slows things down a bit, only applicable with fastMem)",
        "maxInsnPerMethod", "Maximum number of MIPS instructions per java method (128 is optimal with Hotspot)",
        "pruneCases",       "Remove unnecessary case 0xAABCCDD blocks from methods - may break some weird code",
        "assumeTailCalls",  "Assume the JIT optimizes tail calls",
        "optimizedMemcpy",  "Use an optimized java version of memcpy where possible",
        "debugCompiler",    "Output information in the generated code for debugging the compiler - will slow down generated code significantly",
        "printStats",       "Output some useful statistics about the compilation",
        "runtimeStats",     "Keep track of some statistics at runtime in the generated code - will slow down generated code significantly",
        "supportCall",      "Keep a stripped down version of the symbol table in the generated code to support the call() method",
        "runtimeClass",     "Full classname of the Runtime class (default: Runtime) - use this is you put Runtime in a package",
        "hashClass",        "Full classname of a Hashtable class (default: java.util.HashMap) - this must support get() and put()",
        "unixRuntime",      "Use the UnixRuntime (has support for fork, wai, du, pipe, etc)",
        "pageSize",         "The page size (must be a power of two)",
        "totalPages",       "Total number of pages (total mem = pageSize*totalPages, must be a power of two)",
        "onePage",          "One page hack (FIXME: document this better)",
        "lessConstants",    "Use less constants at the cost of speed (FIXME: document this better)",
        "singleFloat",      "Support single precision (32-bit) FP ops only"
    };
        
    private Option getOption(String name) {
        name = name.toLowerCase();
        try {
            for(int i=0;i<options.length;i+=2)
                if(options[i].toLowerCase().equals(name))
                    return new Option(options[i]);
            return null;
        } catch(NoSuchFieldException e) {
            return null;
        }
    }
    
    public void parseOptions(String opts) {
        if(opts == null || opts.length() == 0) return;
        StringTokenizer st = new StringTokenizer(opts,",");
        while(st.hasMoreElements()) {
            String tok = st.nextToken();
            String key;
            String val;
            if(tok.indexOf("=") != -1) {
                key = tok.substring(0,tok.indexOf("="));
                val = tok.substring(tok.indexOf("=")+1);
            } else if(tok.startsWith("no")) {
                key = tok.substring(2);
                val = "false";
            } else {
                key = tok;
                val = "true";
            }
            Option opt = getOption(key);
            if(opt == null) {
                System.err.println("WARNING: No such option: " + key);
                continue;
            }
            
            if(opt.getType() == String.class)
                opt.set(val);
            else if(opt.getType() == Integer.TYPE)
                try {
                    opt.set(parseInt(val));
                } catch(NumberFormatException e) {
                    System.err.println("WARNING: " + val + " is not an integer");
                }
            else if(opt.getType() == Boolean.TYPE)
                opt.set(new Boolean(val.toLowerCase().equals("true")||val.toLowerCase().equals("yes")));
            else
                throw new Error("Unknown type: " + opt.getType());
        }
    }
        
    private static Integer parseInt(String s) {
        int mult = 1;
        s = s.toLowerCase();
        if(!s.startsWith("0x") && s.endsWith("m")) { s = s.substring(0,s.length()-1); mult = 1024*1024; }
        else if(!s.startsWith("0x") && s.endsWith("k")) { s = s.substring(0,s.length()-1); mult = 1024; }
        int n;
        if(s.length() > 2 && s.startsWith("0x")) n = Integer.parseInt(s.substring(2),16);
        else n = Integer.parseInt(s);
        return new Integer(n*mult);
    }
    
    private static String wrapAndIndent(String s, int firstindent, int indent, int width) {
        StringTokenizer st = new StringTokenizer(s," ");
        StringBuffer sb = new StringBuffer();
        for(int i=0;i<firstindent;i++)
            sb.append(' ');
        int sofar = 0;
        while(st.hasMoreTokens()) {
            String tok = st.nextToken();
            if(tok.length() + sofar + 1 > width && sofar > 0) {
                sb.append('\n');
                for(int i=0;i<indent;i++) sb.append(' ');
                sofar = 0;
            } else if(sofar > 0) {
                sb.append(' ');
                sofar++;
            }
            sb.append(tok);
            sofar += tok.length();
        }
        sb.append('\n');
        return sb.toString();
    }
    
    // This ugliness is to work around a gcj static linking bug (Bug 12908)
    // The best solution is to force gnu.java.locale.Calendar to be linked in but this'll do
    static String dateTime() {
        try {
            return new Date().toString();
        } catch(RuntimeException e) {
            return "<unknown>";
        }
    }
}


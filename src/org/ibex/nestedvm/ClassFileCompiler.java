// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm;

import java.io.*;

import org.ibex.nestedvm.util.*;
import org.ibex.classgen.*;

// FEATURE: Eliminate unnecessary use of SWAP
// FEATURE: Put regs in low (<=3) local vars, small classfile size

/* FEATURE: Span large binaries across several classfiles
 * We should be able to do this with no performance penalty
 * Every method in the inner classes is static and takes the main class as an arg
 * This makes them look just like methods in the main class because arg1 gets loaded into
 * local register 0
 */

/* FEATURE: smarter with local regs
 * Be even smarter with the use of local registers. We need to only load fields into
 * local regs when they are actually used and only write to fields if the regs could have
 * changed. This should allow us to put more regs in local vars. Right now putting all used 
 * regs local vars makes code like this slower.
 * 
 * void work(int a, int b) {
 *    if(a == b) {
 *       fast path
 *       return;
 *    }
 *    real work
 * }
 * Because all the regs used in "real work" are loaded/restored even for fast path
 */


public class ClassFileCompiler extends Compiler implements CGConst  {
    private static final boolean OPTIMIZE_CP = true;
    
    /** The stream to write the compiled output to */
    private OutputStream os;
    private File outDir;
    private PrintStream warn = System.err;

    private final Type.Class me;
    
    private ClassFile cg;
    private MethodGen clinit, init;
    
    public ClassFileCompiler(String path, String className, OutputStream os) throws IOException { this(new Seekable.File(path),className,os); }
    public ClassFileCompiler(Seekable binary, String className, OutputStream os) throws IOException {
        this(binary,className);
        if(os == null) throw new NullPointerException();
        this.os = os;
    }
    public ClassFileCompiler(Seekable binary, String className, File outDir) throws IOException {
        this(binary,className);
        if(outDir == null) throw new NullPointerException();
        this.outDir = outDir;
    }
    private ClassFileCompiler(Seekable binary, String className) throws IOException {
        super(binary,className);
        me = Type.Class.instance(fullClassName);
    }
    
    public void setWarnWriter(PrintStream warn) { this.warn = warn; }
        
    protected void _go() throws Exn, IOException {
        try {
            __go();
        } catch(ClassFile.Exn e) {
            e.printStackTrace(warn);
            throw new Exn("Class generation exception: " + e.toString());
        }
    }
        
    private void __go() throws Exn, IOException {
        if(!pruneCases) throw new Exn("-o prunecases MUST be enabled for ClassFileCompiler");

        // Class
        Type.Class superClass = Type.Class.instance(runtimeClass);
        cg = new ClassFile(me,superClass,PUBLIC|FINAL|SUPER);
        if(source != null) cg.setSourceFile(source);
        
        // Fields
        cg.addField("pc",Type.INT,PRIVATE);
        cg.addField("hi",Type.INT,PRIVATE);
        cg.addField("lo",Type.INT,PRIVATE);
        cg.addField("fcsr",Type.INT,PRIVATE);
        for(int i=1;i<32;i++) cg.addField("r" + i,Type.INT,PRIVATE);
        for(int i=0;i<32;i++) cg.addField("f" + i,singleFloat ? Type.FLOAT : Type.INT,PRIVATE);

        // <clinit>
        clinit = cg.addMethod("<clinit>",Type.VOID,Type.NO_ARGS,PRIVATE|STATIC);

        // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.UnixRuntime.<init>

        // <init>
        init = cg.addMethod("<init>",Type.VOID,Type.NO_ARGS,PUBLIC);        
        init.add(ALOAD_0);
        init.add(LDC,pageSize);
        init.add(LDC,totalPages);
        init.add(INVOKESPECIAL,me.method("<init>",Type.VOID,new Type[]{Type.INT,Type.INT}));
        init.add(RETURN);

        // <init>(Z)
        init = cg.addMethod("<init>",Type.VOID,new Type[]{Type.BOOLEAN},PUBLIC);        
        init.add(ALOAD_0);
        init.add(LDC,pageSize);
        init.add(LDC,totalPages);
        init.add(ILOAD_1);
        init.add(INVOKESPECIAL,me.method("<init>",Type.VOID,new Type[]{Type.INT,Type.INT,Type.BOOLEAN}));
        init.add(RETURN);
        
        // <init>(II)
        init = cg.addMethod("<init>",Type.VOID,new Type[]{Type.INT,Type.INT},PUBLIC);
        init.add(ALOAD_0);
        init.add(ILOAD_1);
        init.add(ILOAD_2);
        init.add(ICONST_0);
        init.add(INVOKESPECIAL,me.method("<init>",Type.VOID,new Type[]{Type.INT,Type.INT,Type.BOOLEAN}));
        init.add(RETURN);
        
        // <init>(IIZ)
        init = cg.addMethod("<init>",Type.VOID,new Type[]{Type.INT,Type.INT,Type.BOOLEAN},PUBLIC);
        init.add(ALOAD_0);
        init.add(ILOAD_1);
        init.add(ILOAD_2);
        init.add(ILOAD_3);
        init.add(INVOKESPECIAL,superClass.method("<init>",Type.VOID,new Type[]{Type.INT,Type.INT,Type.BOOLEAN}));
        
        if(onePage) {
            cg.addField("page",Type.INT.makeArray(),PRIVATE|FINAL);
            init.add(ALOAD_0);
            init.add(DUP);
            init.add(GETFIELD,me.field("readPages",Type.INT.makeArray(2)));
            init.add(LDC,0);
            init.add(AALOAD);
            init.add(PUTFIELD,me.field("page",Type.INT.makeArray()));
        }
        
        if(supportCall)
            cg.addField("symbols",Type.Class.instance(hashClass),PRIVATE|STATIC|FINAL);
        
        int highestAddr = 0;
        
        for(int i=0;i<elf.sheaders.length;i++) {
            ELF.SHeader sheader = elf.sheaders[i];
            String name = sheader.name;
            // if this section doesn't get loaded into our address space don't worry about it
            if(sheader.addr == 0x0) continue;
            
            highestAddr = Math.max(highestAddr, sheader.addr + sheader.size);
            
            if(name.equals(".text"))
                emitText(sheader.addr, new DataInputStream(sheader.getInputStream()),sheader.size);
            else if(name.equals(".data") || name.equals(".sdata") || name.equals(".rodata") || name.equals(".ctors") || name.equals(".dtors"))
                emitData(sheader.addr, new DataInputStream(sheader.getInputStream()), sheader.size,name.equals(".rodata")); 
            else if(name.equals(".bss") || name.equals(".sbss"))                
                emitBSS(sheader.addr,sheader.size);
            else
                throw new Exn("Unknown segment: " + name);
        }
        
        // Finish init
        init.add(RETURN);
        
        // Finish clinit
        if(supportCall) {
            Type.Class hash = Type.Class.instance(hashClass);
            clinit.add(NEW,hash);
            clinit.add(DUP);
            clinit.add(DUP);
            clinit.add(INVOKESPECIAL,hash.method("<init>",Type.VOID,Type.NO_ARGS));
            clinit.add(PUTSTATIC,me.field("symbols",hash));
            ELF.Symbol[] symbols = elf.getSymtab().symbols;
            for(int i=0;i<symbols.length;i++) {
                ELF.Symbol s = symbols[i];
                if(s.type == ELF.Symbol.STT_FUNC && s.binding == ELF.Symbol.STB_GLOBAL && (s.name.equals("_call_helper") || !s.name.startsWith("_"))) {
                    clinit.add(DUP);
                    clinit.add(LDC,s.name);
                    clinit.add(NEW,Type.INTEGER_OBJECT);
                    clinit.add(DUP);
                    clinit.add(LDC,s.addr);
                    clinit.add(INVOKESPECIAL,Type.INTEGER_OBJECT.method("<init>",Type.VOID,new Type[]{Type.INT}));
                    clinit.add(INVOKEVIRTUAL,hash.method("put",Type.OBJECT,new Type[]{Type.OBJECT,Type.OBJECT}));
                    clinit.add(POP);
                }
            }
            clinit.add(POP);
        }
        
        clinit.add(RETURN);
        
        ELF.SHeader text = elf.sectionWithName(".text");
        
        // Trampoline
        MethodGen tramp = cg.addMethod("trampoline",Type.VOID,Type.NO_ARGS,PRIVATE);
        
        int start = tramp.size();
        tramp.add(ALOAD_0);
        tramp.add(GETFIELD,me.field("state",Type.INT));
        tramp.add(IFEQ,tramp.size()+2);
        tramp.add(RETURN);
        
        tramp.add(ALOAD_0);
        tramp.add(ALOAD_0);
        tramp.add(GETFIELD,me.field("pc",Type.INT));
        tramp.add(LDC,methodShift);
        tramp.add(IUSHR);
        
        int beg = text.addr >>> methodShift;
        int end = ((text.addr + text.size + maxBytesPerMethod - 1) >>> methodShift);

        MethodGen.Switch.Table tsi = new MethodGen.Switch.Table(beg,end-1);
        tramp.add(TABLESWITCH,tsi);
        for(int n=beg;n<end;n++) {
            tsi.setTargetForVal(n,tramp.size());
            tramp.add(INVOKESPECIAL,me.method("run_"+toHex(n<<methodShift),Type.VOID,Type.NO_ARGS));
            tramp.add(GOTO,start);
        }
        tsi.setDefaultTarget(tramp.size());
        
        tramp.add(POP);
        tramp.add(NEW,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException"));
        tramp.add(DUP);
        tramp.add(NEW, Type.STRINGBUFFER);
        tramp.add(DUP);
        tramp.add(LDC,"Jumped to invalid address in trampoline (r2: ");
        tramp.add(INVOKESPECIAL,Type.STRINGBUFFER.method("<init>",Type.VOID,new Type[]{Type.STRING}));
        tramp.add(ALOAD_0);
        tramp.add(GETFIELD, me.field("r2",Type.INT));
        tramp.add(INVOKEVIRTUAL,Type.STRINGBUFFER.method("append",Type.STRINGBUFFER,new Type[]{Type.INT}));
        tramp.add(LDC," pc: ");
        tramp.add(INVOKEVIRTUAL,Type.STRINGBUFFER.method("append",Type.STRINGBUFFER,new Type[]{Type.STRING}));
        tramp.add(ALOAD_0);
        tramp.add(GETFIELD, me.field("pc",Type.INT));        
        tramp.add(INVOKEVIRTUAL,Type.STRINGBUFFER.method("append",Type.STRINGBUFFER,new Type[]{Type.INT}));
        tramp.add(LDC,")");
        tramp.add(INVOKEVIRTUAL,Type.STRINGBUFFER.method("append",Type.STRINGBUFFER,new Type[]{Type.STRING}));
        tramp.add(INVOKEVIRTUAL,Type.STRINGBUFFER.method("toString",Type.STRING,Type.NO_ARGS));
        // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime$ExecutionException.<init>
        tramp.add(INVOKESPECIAL,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException").method("<init>",Type.VOID,new Type[]{Type.STRING}));
        tramp.add(ATHROW);
        
        addConstReturnMethod("gp",gp.addr);
        addConstReturnMethod("entryPoint",elf.header.entry);
        addConstReturnMethod("heapStart",highestAddr);
                
        if(userInfo != null) {
            addConstReturnMethod("userInfoBase",userInfo.addr);
            addConstReturnMethod("userInfoSize",userInfo.size);
        }
        
        if(supportCall) {
            Type.Class hashClassType = Type.Class.instance(hashClass);
            MethodGen ls = cg.addMethod("lookupSymbol",Type.INT,new Type[]{Type.STRING},PROTECTED);
            ls.add(GETSTATIC,me.field("symbols",hashClassType));
            ls.add(ALOAD_1);
            ls.add(INVOKEVIRTUAL,hashClassType.method("get",Type.OBJECT,new Type[]{Type.OBJECT}));
            ls.add(DUP);
            int b = ls.add(IFNULL);
            ls.add(CHECKCAST,Type.INTEGER_OBJECT);
            ls.add(INVOKEVIRTUAL,Type.INTEGER_OBJECT.method("intValue",Type.INT,Type.NO_ARGS));
            ls.add(IRETURN);
            ls.setArg(b,ls.size());
            ls.add(POP);
            ls.add(ICONST_M1);
            ls.add(IRETURN);
        }
        
        // Kind of a hack, referencing dup() gets us all the fields for free
        // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime$CPUState.dup
        Type.Class cpuStateType = Type.Class.instance("org.ibex.nestedvm.Runtime$CPUState");
        MethodGen setCPUState = cg.addMethod("setCPUState",Type.VOID,new Type[]{cpuStateType},PROTECTED);
        MethodGen getCPUState = cg.addMethod("getCPUState",Type.VOID,new Type[]{cpuStateType},PROTECTED);
        
        setCPUState.add(ALOAD_1);
        getCPUState.add(ALOAD_1);
        setCPUState.add(GETFIELD,cpuStateType.field("r",Type.INT.makeArray()));
        getCPUState.add(GETFIELD,cpuStateType.field("r",Type.INT.makeArray()));
        setCPUState.add(ASTORE_2);
        getCPUState.add(ASTORE_2);
        
        for(int i=1;i<32;i++) {
            setCPUState.add(ALOAD_0);
            setCPUState.add(ALOAD_2);
            setCPUState.add(LDC,i);
            setCPUState.add(IALOAD);
            setCPUState.add(PUTFIELD,me.field("r"+i,Type.INT));
            
            getCPUState.add(ALOAD_2);
            getCPUState.add(LDC,i);
            getCPUState.add(ALOAD_0);
            getCPUState.add(GETFIELD,me.field("r"+i,Type.INT));
            getCPUState.add(IASTORE);
        }
        
        setCPUState.add(ALOAD_1);
        getCPUState.add(ALOAD_1);
        setCPUState.add(GETFIELD,cpuStateType.field("f",Type.INT.makeArray()));
        getCPUState.add(GETFIELD,cpuStateType.field("f",Type.INT.makeArray()));
        setCPUState.add(ASTORE_2);
        getCPUState.add(ASTORE_2);
        
        for(int i=0;i<32;i++) {
            setCPUState.add(ALOAD_0);
            setCPUState.add(ALOAD_2);
            setCPUState.add(LDC,i);
            setCPUState.add(IALOAD);
            if(singleFloat) setCPUState.add(INVOKESTATIC,Type.FLOAT_OBJECT.method("intBitsToFloat",Type.FLOAT,new Type[]{Type.INT}));
            setCPUState.add(PUTFIELD,me.field("f"+i,singleFloat ? Type.FLOAT : Type.INT));
            
            getCPUState.add(ALOAD_2);
            getCPUState.add(LDC,i);
            getCPUState.add(ALOAD_0);
            getCPUState.add(GETFIELD,me.field("f"+i,singleFloat ? Type.FLOAT: Type.INT));
            if(singleFloat) getCPUState.add(INVOKESTATIC,Type.FLOAT_OBJECT.method("floatToIntBits",Type.INT,new Type[]{Type.FLOAT}));
            getCPUState.add(IASTORE);            
        }
        
        String[] each = new String[] { "hi","lo","fcsr","pc" };
        for(int i=0;i<each.length;i++) {
            setCPUState.add(ALOAD_0);
            setCPUState.add(ALOAD_1);
            setCPUState.add(GETFIELD,cpuStateType.field(each[i],Type.INT));
            setCPUState.add(PUTFIELD,me.field(each[i],Type.INT));

            getCPUState.add(ALOAD_1);
            getCPUState.add(ALOAD_0);
            getCPUState.add(GETFIELD,me.field(each[i],Type.INT));
            getCPUState.add(PUTFIELD,cpuStateType.field(each[i],Type.INT));
        }
        setCPUState.add(RETURN);
        getCPUState.add(RETURN);
        

        MethodGen execute = cg.addMethod("_execute",Type.VOID,Type.NO_ARGS,PROTECTED);
        int tryStart = execute.size();
        execute.add(ALOAD_0);
        execute.add(INVOKESPECIAL,me.method("trampoline",Type.VOID,Type.NO_ARGS));
        int tryEnd = execute.size();
        execute.add(RETURN);
        
        int catchInsn = execute.size();
        execute.add(ASTORE_1);
        execute.add(NEW, Type.Class.instance("org.ibex.nestedvm.Runtime$FaultException"));
        execute.add(DUP);
        execute.add(ALOAD_1);
        // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime$FaultException.<init>
        execute.add(INVOKESPECIAL,Type.Class.instance("org.ibex.nestedvm.Runtime$FaultException").method("<init>",Type.VOID,new Type[]{Type.Class.instance("java.lang.RuntimeException")}));
        execute.add(ATHROW);
        
        execute.addExceptionHandler(tryStart,tryEnd,catchInsn,Type.Class.instance("java.lang.RuntimeException"));
        execute.addThrow(Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException"));

        MethodGen main = cg.addMethod("main",Type.VOID,new Type[]{Type.STRING.makeArray()},STATIC|PUBLIC);
        main.add(NEW,me);
        main.add(DUP);
        main.add(INVOKESPECIAL,me.method("<init>",Type.VOID,Type.NO_ARGS));
        main.add(LDC,fullClassName);
        main.add(ALOAD_0);
        if(unixRuntime) {
            Type.Class ur = Type.Class.instance("org.ibex.nestedvm.UnixRuntime");
            // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.runAndExec
            main.add(INVOKESTATIC,ur.method("runAndExec",Type.INT,new Type[]{ur,Type.STRING,Type.STRING.makeArray()}));
        } else {
            // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.run
            main.add(INVOKEVIRTUAL,me.method("run",Type.INT,new Type[]{Type.STRING,Type.STRING.makeArray()}));
        }
        main.add(INVOKESTATIC,Type.Class.instance("java.lang.System").method("exit",Type.VOID,new Type[]{Type.INT}));
        main.add(RETURN);
        
        if(outDir != null) {
            if(!outDir.isDirectory()) throw new IOException("" + outDir + " isn't a directory");
            cg.dump(outDir);
        } else {
            cg.dump(os);
        }
    }
    
    private void addConstReturnMethod(String name, int val) {
        MethodGen  m = cg.addMethod(name,Type.INT,Type.NO_ARGS,PROTECTED);
        m.add(LDC,val);
        m.add(IRETURN);
    }
        
    private static int initDataCount;
    private void emitData(int addr, DataInputStream dis, int size, boolean readOnly) throws Exn,IOException {
        if((addr&3)!=0 || (size&3)!=0) throw new Exn("Data section on weird boundaries");
        int last = addr + size;
        while(addr < last) {
            int segSize = Math.min(size,28000); // must be a multiple of 56
            StringBuffer sb = new StringBuffer();
            for(int i=0;i<segSize;i+=7) {
                long l = 0;
                for(int j=0;j<7;j++) {
                    l <<= 8;
                    byte b = (i+j < size) ? dis.readByte() : 1;
                    l |= (b & 0xffL);
                }
                for(int j=0;j<8;j++)
                    sb.append((char) ((l>>>(7*(7-j)))&0x7f));
            }
            String fieldname =  "_data" + (++initDataCount);
            cg.addField(fieldname,Type.INT.makeArray(),PRIVATE|STATIC|FINAL);
            
            clinit.add(LDC,sb.toString());
            clinit.add(LDC,segSize/4);
            // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.decodeData
            clinit.add(INVOKESTATIC,Type.Class.instance("org.ibex.nestedvm.Runtime").method("decodeData",Type.INT.makeArray(),new Type[]{Type.STRING,Type.INT}));
            clinit.add(PUTSTATIC,me.field(fieldname,Type.INT.makeArray()));
            init.add(ALOAD_0);
            init.add(GETSTATIC,me.field(fieldname,Type.INT.makeArray()));
            init.add(LDC,addr);
            init.add(LDC,readOnly ? 1 : 0);
            // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.initPages
            init.add(INVOKEVIRTUAL,me.method("initPages",Type.VOID,new Type[]{Type.INT.makeArray(),Type.INT,Type.BOOLEAN}));
            
            addr += segSize;
            size -= segSize;
        }
        dis.close();
    }
    
    private void emitBSS(int addr, int size) throws Exn {
        if((addr&3)!=0) throw new Exn("BSS section on weird boundaries");
        size = (size+3)&~3;
        int count = size/4;
        
        init.add(ALOAD_0);
        init.add(LDC,addr);
        init.add(LDC,count);
        // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.clearPages
        init.add(INVOKEVIRTUAL,me.method("clearPages",Type.VOID,new Type[]{Type.INT,Type.INT}));
    }
    
    // Method state info
    private int startOfMethod = 0; // the start of this method (not necessarily the first instruction)
    private int endOfMethod = 0; // the maximum end of this method (could end before it is full)
    
    private MethodGen.PhantomTarget returnTarget; // where to jump when exiting the method
    private MethodGen.PhantomTarget defaultTarget; // the default switch target (throws exn)
    private MethodGen.PhantomTarget[] insnTargets; // the targets for each jumpable instruction
    private MethodGen mg; // the method itself
    
    private boolean jumpable(int addr) { return jumpableAddresses.get(new Integer(addr)) != null; }
    
    private static final int UNREACHABLE = 1;
    private static final int SKIP_NEXT = 2;
    
    private boolean textDone; // a text segment was already processed
    private void emitText(int addr, DataInputStream dis, int size) throws Exn,IOException {
        if(textDone) throw new Exn("Multiple text segments");
        textDone = true;
        
        if((addr&3)!=0 || (size&3)!=0) throw new Exn("Section on weird boundaries");
        int count = size/4;
        int insn,nextInsn=-1;
        
        boolean skipNext = true;
        boolean unreachable = false;
        
        for(int i=0;i<count;i++,addr+=4) {
            insn = skipNext ? dis.readInt() : nextInsn;
            nextInsn = (i == count-1) ? -1 : dis.readInt();
            if(addr >= endOfMethod) { endMethod(addr,unreachable); startMethod(addr); }
            if(insnTargets[i%maxInsnPerMethod] != null) {
                insnTargets[i%maxInsnPerMethod].setTarget(mg.size());
                unreachable = false;
            } else if(unreachable) {
                continue;
            }
            try {
                int ret = emitInstruction(addr,insn,nextInsn);
                unreachable =  (ret & UNREACHABLE) != 0;
                skipNext = (ret & SKIP_NEXT) != 0;
            } catch(Exn e) {
                e.printStackTrace(warn);
                warn.println("Exception at " + toHex(addr));
                throw e;                
            } catch(RuntimeException e) {
                warn.println("Exception at " + toHex(addr));
                throw e;
            }
            if(skipNext) { addr+=4; i++; }
        }
        endMethod(0,unreachable);
        dis.close();
    }
    
    private void startMethod(int first) {
        startOfMethod = first & methodMask;
        endOfMethod = startOfMethod + maxBytesPerMethod;
        
        mg = cg.addMethod("run_" + toHex(startOfMethod),Type.VOID,Type.NO_ARGS,PRIVATE|FINAL);
        if(onePage) {
            mg.add(ALOAD_0);
            mg.add(GETFIELD,me.field("page",Type.INT.makeArray()));
            mg.add(ASTORE_2);
        } else {
            mg.add(ALOAD_0);
            mg.add(GETFIELD,me.field("readPages",Type.INT.makeArray(2)));
            mg.add(ASTORE_2);
            mg.add(ALOAD_0);
            mg.add(GETFIELD,me.field("writePages",Type.INT.makeArray(2)));
            mg.add(ASTORE_3);
        }
        
        returnTarget = new MethodGen.PhantomTarget();
        insnTargets = new MethodGen.PhantomTarget[maxBytesPerMethod/4];
        
        int[] buf = new int[maxBytesPerMethod/4];
        Object[] targetBuf = new Object[maxBytesPerMethod/4];
        int n = 0;
        for(int addr=first;addr<endOfMethod;addr+=4) {
            if(jumpable(addr)) {
                targetBuf[n] = insnTargets[(addr-startOfMethod)/4] = new MethodGen.PhantomTarget();
                buf[n] = addr;
                n++;
            }
        }

        MethodGen.Switch.Lookup lsi = new MethodGen.Switch.Lookup(n);
        System.arraycopy(buf,0,lsi.vals,0,n);
        System.arraycopy(targetBuf,0,lsi.targets,0,n);
        lsi.setDefaultTarget(defaultTarget = new MethodGen.PhantomTarget());
        
        fixupRegsStart();
        
        mg.add(ALOAD_0);
        mg.add(GETFIELD,me.field("pc",Type.INT));
        mg.add(LOOKUPSWITCH,lsi);
    }
    
    private void endMethod(int firstAddrOfNext,boolean unreachable) {
        if(startOfMethod == 0) return;
        
        if(!unreachable) {
            preSetPC();
            mg.add(LDC,firstAddrOfNext);
            setPC();
            // mark the start of the next method as jumpable
            jumpableAddresses.put(new Integer(firstAddrOfNext),Boolean.TRUE);
        }
        
        returnTarget.setTarget(mg.size());
        
        fixupRegsEnd();
        
        mg.add(RETURN);
        
        defaultTarget.setTarget(mg.size());
        
        if(debugCompiler) {
            mg.add(NEW,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException"));
            mg.add(DUP);
            mg.add(NEW,Type.STRINGBUFFER);
            mg.add(DUP);
            mg.add(LDC,"Jumped to invalid address: ");
            mg.add(INVOKESPECIAL,Type.STRINGBUFFER.method("<init>",Type.VOID,new Type[]{Type.STRING}));
            mg.add(ALOAD_0);
            mg.add(GETFIELD,me.field("pc",Type.INT));
            mg.add(INVOKEVIRTUAL,Type.STRINGBUFFER.method("append",Type.STRINGBUFFER,new Type[]{Type.INT}));
            mg.add(INVOKEVIRTUAL,Type.STRINGBUFFER.method("toString",Type.STRING,Type.NO_ARGS));
            mg.add(INVOKESPECIAL,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException").method("<init>",Type.VOID,new Type[]{Type.STRING}));
            mg.add(ATHROW);
        } else {
            mg.add(NEW,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException"));
            mg.add(DUP);
            mg.add(LDC,"Jumped to invalid address");
            mg.add(INVOKESPECIAL,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException").method("<init>",Type.VOID,new Type[]{Type.STRING}));
            mg.add(ATHROW);
        }
        
        endOfMethod = startOfMethod = 0;
    }


    private void leaveMethod() {
        mg.add(GOTO,returnTarget);
    }
    
    private void link(int mypc) {
        preSetReg(R+RA);
        if(lessConstants){
            int ref = (mypc+8 + 32768) & ~65535;
            int diff = (mypc+8) - ref;
            if(diff < -32768 || diff > 32767) throw new Error("should never happen " + diff);
            mg.add(LDC,ref);
            mg.add(LDC,diff);
            mg.add(IADD);
        } else {
            mg.add(LDC,mypc+8);
        }
        setReg();
    }

    private void branch(int pc, int target) {
        if((pc&methodMask) == (target&methodMask)) {
            mg.add(GOTO,insnTargets[(target-startOfMethod)/4]);
        } else {
            preSetPC();
            mg.add(LDC,target);
            setPC();
            leaveMethod();
        }
    }
    
    // This assumes everything needed by ifInsn is already on the stack
    private int doIfInstruction(byte op, int pc, int target, int nextInsn) throws Exn {
        emitInstruction(-1,nextInsn,-1); // delay slot
        if((target&methodMask) == (pc&methodMask)) {
            mg.add(op,insnTargets[(target-startOfMethod)/4]);
        } else {
            int h = mg.add(MethodGen.negate(op));
            branch(pc,target);
            mg.setArg(h,mg.size());
        }
        if(!jumpable(pc+4)) return SKIP_NEXT; // done - skip it
        
        //System.err.println("Delay slot is jumpable - This code is untested + " + toHex(nextInsn));
        if(pc+4==endOfMethod) {
            // the delay slot is at the start of the next method
            jumpableAddresses.put(new Integer(pc+8),Boolean.TRUE); // make the 2nd insn of the next method jumpable
            branch(pc,pc+8); // jump over it
            //System.err.println("delay slot: " + toHex(pc+8)); */
            //unreachable = true;
            //return false; // we still need to output it
            return UNREACHABLE;
        } else {
            //System.err.println("jumped over delay slot: " + toHex(pc+4));
            // add another copy and jump over
            
            int b = mg.add(GOTO);
            insnTargets[(pc+4-startOfMethod)/4].setTarget(mg.size());
            emitInstruction(-1,nextInsn,01); // delay slot
            mg.setArg(b,mg.size());
            
            return SKIP_NEXT;
        }
    }
    
    private static final Float POINT_5_F = new Float(0.5f);
    private static final Double POINT_5_D = new Double(0.5f);
    private static final Long FFFFFFFF = new Long(0xffffffffL);
    
    private int emitInstruction(int pc, int insn, int nextInsn) throws Exn {
        MethodGen mg = this.mg; // smaller bytecode
        if(insn == -1) throw new Exn("insn is -1");
        
        int ret = 0;
        
        int op = (insn >>> 26) & 0xff;                 // bits 26-31
        int rs = (insn >>> 21) & 0x1f;                 // bits 21-25
        int rt = (insn >>> 16) & 0x1f;                 // bits 16-20 
        int ft = (insn >>> 16) & 0x1f;
        int rd = (insn >>> 11) & 0x1f;                 // bits 11-15
        int fs = (insn >>> 11) & 0x1f;
        int shamt = (insn >>> 6) & 0x1f;               // bits 6-10
        int fd = (insn >>> 6) & 0x1f;
        int subcode = insn & 0x3f;                     // bits 0-5 
        int breakCode = (insn >>> 6) & 0xfffff;         // bits 6-20
    
        int jumpTarget = (insn & 0x03ffffff);          // bits 0-25
        int unsignedImmediate = insn & 0xffff;
        int signedImmediate = (insn << 16) >> 16;
        int branchTarget = signedImmediate;
    
        // temporaries
        int b1,b2;
        
        switch(op) {
        case 0: {
            switch(subcode) {
            case 0: // SLL
                if(insn == 0) break; 
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                mg.add(LDC,shamt);
                mg.add(ISHL);
                setReg();
                break;
            case 2: // SRL
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                mg.add(LDC,shamt);
                mg.add(IUSHR);
                setReg();
                break;
            case 3: // SRA
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                mg.add(LDC,shamt);
                mg.add(ISHR);
                setReg();
                break;
            case 4: // SLLV
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushRegWZ(R+rs);
                mg.add(ISHL);
                setReg();
                break;
            case 6: // SRLV
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushRegWZ(R+rs);
                mg.add(IUSHR);
                setReg();
                break;
            case 7: // SRAV
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushRegWZ(R+rs);
                mg.add(ISHR);
                setReg();
                break;
            case 8: // JR
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                emitInstruction(-1,nextInsn,-1);
                preSetPC();
                pushRegWZ(R+rs);
                setPC();
                leaveMethod();
                ret |= UNREACHABLE;
                break;
            case 9: // JALR
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                emitInstruction(-1,nextInsn,-1);
                link(pc);
                preSetPC();
                pushRegWZ(R+rs);
                setPC();
                leaveMethod();
                ret |= UNREACHABLE;
                break;
            case 12: // SYSCALL
                preSetPC();
                mg.add(LDC,pc);
                setPC();
                
                // FEATURE: This is actually broken, but it happens to work for our code
                // a func could theoretically jump back to here from a future point
                restoreChangedRegs();
                
                preSetReg(R+V0);
                mg.add(ALOAD_0);
                pushRegZ(R+V0);
                pushRegZ(R+A0);
                pushRegZ(R+A1);
                pushRegZ(R+A2);
                pushRegZ(R+A3);
                pushRegZ(R+T0);
                pushRegZ(R+T1);
                // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.syscall
                mg.add(INVOKEVIRTUAL,me.method("syscall",Type.INT,new Type[]{Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT}));
                setReg();
                
                mg.add(ALOAD_0);
                mg.add(GETFIELD,me.field("state",Type.INT));
                b1 = mg.add(IFEQ);
                preSetPC();
                mg.add(LDC,pc+4);
                setPC();
                leaveMethod();
                mg.setArg(b1,mg.size());
                break;
            case 13: // BREAK
                mg.add(NEW,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException"));
                mg.add(DUP);
                mg.add(LDC,"BREAK Code " + toHex(breakCode));
                mg.add(INVOKESPECIAL,Type.Class.instance("org.ibex.nestedvm.Runtime$ExecutionException").method("<init>",Type.VOID,new Type[]{Type.STRING}));
                mg.add(ATHROW);
                ret |= UNREACHABLE;
                break;
            case 16: // MFHI
                preSetReg(R+rd);
                pushReg(HI);
                setReg();
                break;
            case 17: // MTHI
                preSetReg(HI);
                pushRegZ(R+rs);
                setReg();
                break;
            case 18: // MFLO
                preSetReg(R+rd);
                pushReg(LO);
                setReg();
                break;
            case 19: // MTLO
                preSetReg(LO);
                pushRegZ(R+rs);
                setReg();
                break;
            case 24: // MULT
                pushRegWZ(R+rs);
                mg.add(I2L);
                pushRegWZ(R+rt);
                mg.add(I2L);
                mg.add(LMUL);
                mg.add(DUP2);
                
                mg.add(L2I);
                if(preSetReg(LO))
                    mg.add(SWAP); //a(InstructionConstants.SWAP);
                setReg();
                
                mg.add(LDC,32);
                mg.add(LUSHR);
                mg.add(L2I);
                if(preSetReg(HI))
                    mg.add(SWAP); //a(InstructionConstants.SWAP);
                setReg();
                
                break;
            case 25: // MULTU
                pushRegWZ(R+rs);
                mg.add(I2L);
                mg.add(LDC,FFFFFFFF);
                mg.add(LAND);
                pushRegWZ(R+rt);
                mg.add(I2L);
                mg.add(LDC,FFFFFFFF);
                mg.add(LAND);
                mg.add(LMUL);
                mg.add(DUP2);
                
                mg.add(L2I);
                if(preSetReg(LO))
                    mg.add(SWAP);
                setReg();
                
                mg.add(LDC,32);
                mg.add(LUSHR);
                mg.add(L2I);
                if(preSetReg(HI))
                    mg.add(SWAP);
                setReg();
                
                break;
            case 26: // DIV
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                mg.add(DUP2);
                
                mg.add(IDIV);
                if(preSetReg(LO))
                    mg.add(SWAP);
                setReg();
                
                mg.add(IREM);
                if(preSetReg(HI))
                    mg.add(SWAP);
                setReg();
                
                break;
            case 27: { // DIVU
                pushRegWZ(R+rt);
                mg.add(DUP);
                setTmp();
                b1 = mg.add(IFEQ);
                
                pushRegWZ(R+rs);
                mg.add(I2L);
                mg.add(LDC,FFFFFFFF);
                mg.add(LAND);
                mg.add(DUP2);
                pushTmp();
                mg.add(I2L);
                mg.add(LDC,FFFFFFFF);
                
                mg.add(LAND);
                mg.add(DUP2_X2);
                mg.add(LDIV);
                
                mg.add(L2I);
                if(preSetReg(LO))
                    mg.add(SWAP);
                setReg();
                
                mg.add(LREM);
                mg.add(L2I);
                if(preSetReg(HI))
                    mg.add(SWAP);
                setReg();
                
                mg.setArg(b1,mg.size());
                
                break;
            }
            case 32: // ADD
                throw new Exn("ADD (add with oveflow trap) not suported");
            case 33: // ADDU
                preSetReg(R+rd);
                if(rt != 0 && rs != 0) {
                    pushReg(R+rs);
                    pushReg(R+rt);
                    mg.add(IADD);
                } else if(rs != 0) {
                    pushReg(R+rs);
                } else {
                    pushRegZ(R+rt);
                }
                setReg();
                break;
            case 34: // SUB
                throw new Exn("SUB (add with oveflow trap) not suported");
            case 35: // SUBU
                preSetReg(R+rd);
                if(rt != 0 && rs != 0) {
                    pushReg(R+rs);
                    pushReg(R+rt);
                    mg.add(ISUB);
                } else if(rt != 0) {
                    pushReg(R+rt);
                    mg.add(INEG);
                } else {
                    pushRegZ(R+rs);
                }
                setReg();                
                break;
            case 36: // AND
                preSetReg(R+rd);
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                mg.add(IAND);
                setReg();
                break;
            case 37: // OR
                preSetReg(R+rd);
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                mg.add(IOR);
                setReg();
                break;
            case 38: // XOR
                preSetReg(R+rd);
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                mg.add(IXOR);
                setReg();
                break;
            case 39: // NOR
                preSetReg(R+rd);
                if(rs != 0 || rt != 0) {
                    if(rs != 0 && rt != 0) {
                        pushReg(R+rs);
                        pushReg(R+rt);
                        mg.add(IOR);
                    } else if(rs != 0) {
                        pushReg(R+rs);
                    } else {
                        pushReg(R+rt);
                    }
                    mg.add(ICONST_M1);
                    mg.add(IXOR);
                } else {
                    mg.add(LDC,-1);
                }
                setReg();
                break;
            case 42: // SLT
                preSetReg(R+rd);
                if(rs != rt) {
                    pushRegZ(R+rs);
                    pushRegZ(R+rt);
                    b1 = mg.add(IF_ICMPLT);
                    mg.add(ICONST_0);
                    b2 = mg.add(GOTO);
                    mg.setArg(b1,mg.add(ICONST_1));
                    mg.setArg(b2,mg.size());
                } else {
                    mg.add(LDC,0);
                }
                setReg();
                break;
            case 43: // SLTU
                preSetReg(R+rd);
                if(rs != rt) {
                    if(rs != 0) {
                        pushReg(R+rs);
                        mg.add(I2L);
                        mg.add(LDC,FFFFFFFF);
                        mg.add(LAND);
                        pushReg(R+rt);
                        mg.add(I2L);
                        mg.add(LDC,FFFFFFFF);
                        mg.add(LAND);
                        mg.add(LCMP);
                        b1 = mg.add(IFLT);
                    } else {
                        pushReg(R+rt);
                        b1 = mg.add(IFNE);
                    }
                    mg.add(ICONST_0);
                    b2 = mg.add(GOTO);
                    mg.setArg(b1,mg.add(ICONST_1));
                    mg.setArg(b2,mg.size());
                } else {
                    mg.add(LDC,0);
                }
                setReg();
                break;
            default:
                throw new Exn("Illegal instruction 0/" + subcode);
            }
            break;
        }
        case 1: {
            switch(rt) {
            case 0: // BLTZ
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                pushRegWZ(R+rs);
                return doIfInstruction(IFLT,pc,pc+branchTarget*4+4,nextInsn);
            case 1: // BGEZ
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                pushRegWZ(R+rs);
                return doIfInstruction(IFGE,pc,pc+branchTarget*4+4,nextInsn);
            case 16: // BLTZAL
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                pushRegWZ(R+rs);
                b1 = mg.add(IFGE);
                emitInstruction(-1,nextInsn,-1);
                link(pc);
                branch(pc,pc+branchTarget*4+4);
                mg.setArg(b1,mg.size());
                break;
            case 17: // BGEZAL
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                b1 = -1;
                if(rs != 0) { // r0 is always >= 0
                    pushRegWZ(R+rs);
                    b1 = mg.add(IFLT);
                }
                emitInstruction(-1,nextInsn,-1);
                link(pc);
                branch(pc,pc+branchTarget*4+4);
                if(b1 != -1) mg.setArg(b1,mg.size());
                if(b1 == -1) ret |= UNREACHABLE;
                break;
            default:
                throw new Exn("Illegal Instruction 1/" + rt);
            }
            break;
        }
        case 2: { // J
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            emitInstruction(-1,nextInsn,-1);
            branch(pc,(pc&0xf0000000)|(jumpTarget << 2));
            ret |= UNREACHABLE;
            break;
        }
        case 3: { // JAL
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            int target = (pc&0xf0000000)|(jumpTarget << 2);
            emitInstruction(-1,nextInsn,-1);
            link(pc);
            branch(pc, target);
            ret |= UNREACHABLE;
            break;
        }
        case 4: // BEQ
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            if(rs == rt) {
                emitInstruction(-1,nextInsn,-1);
                branch(pc,pc+branchTarget*4+4);
                ret |= UNREACHABLE;
            } else if(rs == 0 || rt == 0) {
                pushReg(rt == 0 ? R+rs : R+rt);
                return doIfInstruction(IFEQ,pc,pc+branchTarget*4+4,nextInsn);
            } else {
                pushReg(R+rs);
                pushReg(R+rt);
                return doIfInstruction(IF_ICMPEQ,pc,pc+branchTarget*4+4,nextInsn);
            }
            break;
        case 5: // BNE       
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            pushRegWZ(R+rs);
            if(rt == 0) {
                return doIfInstruction(IFNE,pc,pc+branchTarget*4+4,nextInsn);
            } else {
                pushReg(R+rt);
                return doIfInstruction(IF_ICMPNE,pc,pc+branchTarget*4+4,nextInsn);
            }
        case 6: //BLEZ
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            pushRegWZ(R+rs);
            return doIfInstruction(IFLE,pc,pc+branchTarget*4+4,nextInsn);
        case 7: //BGTZ
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            pushRegWZ(R+rs);
            return doIfInstruction(IFGT,pc,pc+branchTarget*4+4,nextInsn);
        case 8: // ADDI
            throw new Exn("ADDI (add immediate with oveflow trap) not suported");
        case 9: // ADDIU
            if(rs != 0 && signedImmediate != 0 && rs == rt && doLocal(rt) && signedImmediate >= -32768 && signedImmediate <= 32767) {
                // HACK: This should be a little cleaner
                regLocalWritten[rt] = true;
                mg.add(IINC, new MethodGen.Pair(getLocalForReg(rt),signedImmediate));
            } else {
                preSetReg(R+rt);
                addiu(rs,signedImmediate);
                setReg();
            }
            break;
        case 10: // SLTI
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            mg.add(LDC,signedImmediate);
            b1 = mg.add(IF_ICMPLT);
            mg.add(ICONST_0);
            b2 = mg.add(GOTO);
            mg.setArg(b1,mg.add(ICONST_1));
            mg.setArg(b2,mg.size());
            setReg();
            break;
        case 11: // SLTIU
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            mg.add(I2L);
            mg.add(LDC,FFFFFFFF);
            mg.add(LAND);
            // Yes, this is correct, you have to sign extend the immediate then do an UNSIGNED comparison
            mg.add(LDC,new Long(signedImmediate&0xffffffffL));
            mg.add(LCMP);
            
            b1 = mg.add(IFLT);
            mg.add(ICONST_0);
            b2 = mg.add(GOTO);
            mg.setArg(b1,mg.add(ICONST_1));
            mg.setArg(b2,mg.size());            
            setReg();
            break;            
        case 12: // ANDI
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            mg.add(LDC,unsignedImmediate);
            mg.add(IAND);
            setReg();
            break;
        case 13: // ORI
            preSetReg(R+rt);
            if(rs != 0 && unsignedImmediate != 0) {
                pushReg(R+rs);
                mg.add(LDC,unsignedImmediate);
                mg.add(IOR);
            } else if(rs != 0){
                pushReg(R+rs);
            } else {
                mg.add(LDC,unsignedImmediate);
            }
            setReg();
            break;
        case 14: // XORI
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            mg.add(LDC,unsignedImmediate);
            mg.add(IXOR);
            setReg();
            break;
        case 15: // LUI
            preSetReg(R+rt);
            mg.add(LDC,unsignedImmediate << 16);
            setReg();
            break;
        case 16:
            throw new Exn("TLB/Exception support not implemented");
        case 17: { // FPU
            switch(rs) {
            case 0: // MFC.1
                preSetReg(R+rt);
                pushReg(F+rd);
                setReg();
                break;
            case 2: // CFC.1
                if(fs != 31) throw new Exn("FCR " + fs + " unavailable");
                preSetReg(R+rt);
                pushReg(FCSR);
                setReg();
                break;
            case 4: // MTC.1
                preSetReg(F+rd);
                if(rt != 0) pushReg(R+rt);
                else mg.add(ICONST_0);
                setReg();
                break;
            case 6: // CTC.1
                if(fs != 31) throw new Exn("FCR " + fs + " unavailable");
                preSetReg(FCSR);
                pushReg(R+rt);
                setReg();
                break;
            case 8: {// BC1F, BC1T
                pushReg(FCSR);
                mg.add(LDC,0x800000);
                mg.add(IAND);
                return doIfInstruction(((insn>>>16)&1) == 0 ? IFEQ : IFNE,pc,pc+branchTarget*4+4,nextInsn);
            }
            case 16:
            case 17: 
            { // Single/Double math
                boolean d = rs == 17;
                switch(subcode) {
                case 0: // ADD.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    mg.add(d ? DADD : FADD);
                    setDouble(d);
                    break;
                case 1: // SUB.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    mg.add(d ? DSUB : FSUB);
                    setDouble(d);
                    break;
                case 2: // MUL.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    mg.add(d ? DMUL : FMUL);
                    setDouble(d);                    
                    break;
                case 3: // DIV.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    mg.add(d ? DDIV : FDIV);
                    setDouble(d);                    
                    break;
                case 5: // ABS.X
                    preSetDouble(F+fd,d);
                    // NOTE: We can't use fneg/dneg here since they'll turn +0.0 into -0.0
                    
                    pushDouble(F+fs,d);
                    mg.add(d ? DUP2 : DUP);
                    mg.add(d ? DCONST_0 : FCONST_0);
                    mg.add(d ? DCMPG : FCMPG);
                    
                    b1 = mg.add(IFGT);
                    mg.add(d ? DCONST_0 : FCONST_0);
                    if(d) {
                        mg.add(DUP2_X2);
                        mg.add(POP2);
                    } else {
                        mg.add(SWAP);
                    }
                    mg.add(d ? DSUB : FSUB);
                    
                    mg.setArg(b1,mg.size());
                    setDouble(d);
                    
                    break;
                case 6: // MOV.X
                    preSetReg(F+fd);
                    pushReg(F+fs);
                    setReg();
                
                    if(d) {
                        preSetReg(F+fd+1);
                        pushReg(F+fs+1);
                        setReg();
                    }
                    break;
                case 7: // NEG.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    mg.add(d ? DNEG : FNEG);
                    setDouble(d);
                    break;
                case 32: // CVT.S.X
                    preSetFloat(F+fd);
                    pushDouble(F+fs,d);
                    if(d) mg.add(D2F);
                    setFloat();
                    break;
                case 33: // CVT.D.X
                    preSetDouble(F+fd);
                    pushDouble(F+fs,d);
                    if(!d) mg.add(F2D);
                    setDouble();
                    break;
                case 36: { // CVT.W.D
                    MethodGen.Switch.Table tsi = new MethodGen.Switch.Table(0,3);
                    preSetReg(F+fd);
                    pushDouble(F+fs,d);
                    pushReg(FCSR);
                    mg.add(ICONST_3);
                    mg.add(IAND);
                    mg.add(TABLESWITCH,tsi);
                    
                    // Round towards plus infinity
                    tsi.setTarget(2,mg.size());
                    if(!d) mg.add(F2D); // Ugh.. java.lang.Math doesn't have a float ceil/floor
                    mg.add(INVOKESTATIC,Type.Class.instance("java.lang.Math").method("ceil",Type.DOUBLE,new Type[]{Type.DOUBLE}));
                    if(!d) mg.add(D2F);
                    b1 = mg.add(GOTO);
                    
                    // Round to nearest
                    tsi.setTarget(0,mg.size());
                    mg.add(LDC,d ? (Object)POINT_5_D : (Object)POINT_5_F);
                    mg.add(d ? DADD : FADD);
                    // fall through
                    
                    // Round towards minus infinity
                    tsi.setTarget(3,mg.size());
                    if(!d) mg.add(F2D);
                    mg.add(INVOKESTATIC,Type.Class.instance("java.lang.Math").method("floor",Type.DOUBLE,new Type[]{Type.DOUBLE}));
                    if(!d) mg.add(D2F);
                    
                    tsi.setTarget(1,mg.size());
                    tsi.setDefaultTarget(mg.size());
                    mg.setArg(b1,mg.size());
                    
                    mg.add(d ? D2I : F2I);
                    setReg();
                    
                    break;
                }
                case 50: // C.EQ.D
                case 60: // C.LT.D
                case 62: // C.LE.D
                    preSetReg(FCSR);
                    pushReg(FCSR);
                    mg.add(LDC,~0x800000);
                    mg.add(IAND);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    mg.add(d ? DCMPG : FCMPG);
                    switch(subcode) {
                        case 50: b1 = mg.add(IFNE); break;
                        case 60: b1 = mg.add(IFGE); break;
                        case 62: b1 = mg.add(IFGT); break;
                        default: b1 = -1;
                    }
                    mg.add(LDC,0x800000);
                    mg.add(IOR);
                    mg.setArg(b1,mg.size());
                    setReg();
                    break;
                default: throw new Exn("Invalid Instruction 17/" + rs + "/" + subcode);
                }
                break;
            }
            case 20: { // Integer
                switch(subcode) {
                case 32: // CVT.S.W
                    preSetFloat(F+fd);
                    pushReg(F+fs);
                    mg.add(I2F);
                    setFloat();
                    break;
                case 33: // CVT.D.W
                    preSetDouble(F+fd);
                    pushReg(F+fs);
                    mg.add(I2D);
                    setDouble();
                    break;
                default: throw new Exn("Invalid Instruction 17/" + rs + "/" + subcode);
                }
                break; 
            }
            default:
                throw new Exn("Invalid Instruction 17/" + rs);
            }
            break;
        }
        case 18: case 19:
            throw new Exn("coprocessor 2 and 3 instructions not available");
        case 32: { // LB
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp();
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(I2B);
            setReg();
            break; 
        }
        case 33: { // LH
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp();
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_2);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(I2S);
            setReg();            
            break; 
        }
        case 34: { // LWL;
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp(); // addr
            
            pushRegWZ(R+rt);
            mg.add(LDC,0x00ffffff);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(IAND);
            
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(ISHL);
            mg.add(IOR);
            
            setReg();
            
            break;
            
        }
        case 35: // LW
            preSetReg(R+rt);
            memRead(R+rs,signedImmediate);
            setReg();
            break;
        case 36: { // LBU
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp();
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(LDC,0xff);
            mg.add(IAND);
            setReg();
            break; 
        }
        case 37: { // LHU
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp();
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_2);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            
            // chars are unsigend so this works
            mg.add(I2C);
            setReg();
            break; 
        }
        case 38: { // LWR            
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp(); // addr
            
            pushRegWZ(R+rt);
            mg.add(LDC,0xffffff00);
            pushTmp();
            
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(ISHL);
            mg.add(IAND);
            
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(IOR);
            
            
            setReg();
            break;
        }
        case 40: { // SB            
            addiu(R+rs,signedImmediate);
            setTmp(); // addr
            
            preMemRead(true);
            pushTmp();
            memRead(true);
            
            mg.add(LDC,0xff000000);
            pushTmp();
            
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(IAND);
            
            if(rt != 0) {
                pushReg(R+rt);
                mg.add(LDC,0xff);
                mg.add(IAND);
            } else {
                mg.add(LDC,0);
            }
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(ISHL);
            mg.add(IOR);
            
            memWrite();
            
            break;
        }
        case 41: { // SH    
            addiu(R+rs,signedImmediate);
            setTmp();
            
            preMemRead(true);
            pushTmp();
            memRead(true);
                       
            mg.add(LDC,0xffff);
            pushTmp();
            
            mg.add(ICONST_2);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(ISHL);
            mg.add(IAND);
            
            if(rt != 0) {
                pushReg(R+rt);
                mg.add(LDC,0xffff);
                mg.add(IAND);
            } else {
                mg.add(LDC,0);
            }
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_2);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(ISHL);
            mg.add(IOR);
            
            memWrite();
            
            break;            
        }
        case 42: { // SWL
            addiu(R+rs,signedImmediate);
            setTmp();
            
            preMemRead(true);            
            pushTmp();
            memRead(true);
            
            mg.add(LDC,0xffffff00);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(ISHL);
            mg.add(IAND);
            
            pushRegWZ(R+rt);
            pushTmp();
            
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(IOR);
            
            memWrite();
            break;
            
        }
        case 43: // SW
            preMemWrite1();
            preMemWrite2(R+rs,signedImmediate);
            pushRegZ(R+rt);
            memWrite();
            break;
        case 46: { // SWR
            addiu(R+rs,signedImmediate);
            setTmp();
            
            preMemRead(true);
            pushTmp();
            memRead(true);
            
            mg.add(LDC,0x00ffffff);
            pushTmp();
            
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(IUSHR);
            mg.add(IAND);
            
            pushRegWZ(R+rt);
            pushTmp();
            
            mg.add(ICONST_M1);
            mg.add(IXOR);
            mg.add(ICONST_3);
            mg.add(IAND);
            mg.add(ICONST_3);
            mg.add(ISHL);
            mg.add(ISHL);
            mg.add(IOR);
                    
            memWrite();
            break;
        }
        // This need to be atomic if we ever support threads (see SWC0/SC)
        case 48: // LWC0/LL
            preSetReg(R+rt);
            memRead(R+rs,signedImmediate);
            setReg();
            break;
            
        case 49: // LWC1
            preSetReg(F+rt);
            memRead(R+rs,signedImmediate);
            setReg();
            break;
        
        /* This needs to fail (set rt to 0) if the memory location was modified
         * between the LL and SC if we ever support threads.
         */
        case 56: // SWC0/SC
            preSetReg(R+rt);
            preMemWrite1();
            preMemWrite2(R+rs,signedImmediate);
            pushReg(R+rt);
            memWrite();
            mg.add(LDC,1);
            setReg();
            break;
            
        case 57: // SWC1
            preMemWrite1();
            preMemWrite2(R+rs,signedImmediate);
            pushReg(F+rt);
            memWrite();
            break;
        default:
            throw new Exn("Invalid Instruction: " + op + " at " + toHex(pc));
        }
        return ret; 
    }
    
    // Helper functions for emitText
    
    private static final int R = 0;
    private static final int F = 32;
    private static final int HI = 64;
    private static final int LO = 65;
    private static final int FCSR = 66;
    private static final int REG_COUNT=67;
    private static final String[] regField = {
            "r0","r1","r2","r3","r4","r5","r6","r7",
            "r8","r9","r10","r11","r12","r13","r14","r15",
            "r16","r17","r18","r19","r20","r21","r22","r23",
            "r24","r25","r26","r27","r28","r29","r30","r31",
            "f0","f1","f2","f3","f4","f5","f6","f7",
            "f8","f9","f10","f11","f12","f13","f14","f15",
            "f16","f17","f18","f19","f20","f21","f22","f23",
            "f24","f25","f26","f27","f28","f29","f30","f31",
            "hi","lo","fcsr"
    };
    private static final int MAX_LOCALS = 4; // doLocal can return true for this many regs
    private static final int LOAD_LENGTH = 3; // number of instructions needed to load a field to a reg
    
    // Local register state info
    private int[] regLocalMapping = new int[REG_COUNT];  
    private boolean[] regLocalWritten = new boolean[REG_COUNT];
    private int nextAvailLocal;
    private int loadsStart;
    
    private boolean doLocal(int reg) {
        return reg == R+2 || reg == R+3 || reg == R+4 || reg == R+29;
    }
    
    private int getLocalForReg(int reg) {
        if(regLocalMapping[reg] != 0) return regLocalMapping[reg];
        regLocalMapping[reg] = nextAvailLocal++;
        return regLocalMapping[reg];
    }
    
    private void fixupRegsStart() {
        for(int i=0;i<REG_COUNT;i++) {
            regLocalMapping[i] = 0;
            regLocalWritten[i] = false;
        }
        nextAvailLocal = onePage ? 4 : 5;
        loadsStart = mg.size();
        for(int i=0;i<MAX_LOCALS*LOAD_LENGTH;i++)
            mg.add(NOP);
    }
    
    private void fixupRegsEnd() {
        int p = loadsStart;
        for(int i=0;i<REG_COUNT;i++) {
            if(regLocalMapping[i] == 0) continue;
            mg.set(p++,ALOAD_0);
            mg.set(p++,GETFIELD,me.field(regField[i],Type.INT));
            mg.set(p++,ISTORE,regLocalMapping[i]);
            
            if(regLocalWritten[i]) {
                mg.add(ALOAD_0);
                mg.add(ILOAD,regLocalMapping[i]);
                mg.add(PUTFIELD,me.field(regField[i],Type.INT));
            }
        }
    }
        
    private void restoreChangedRegs() {
        for(int i=0;i<REG_COUNT;i++) {
            if(regLocalWritten[i]) {
                mg.add(ALOAD_0);
                mg.add(ILOAD,regLocalMapping[i]);
                mg.add(PUTFIELD,me.field(regField[i],Type.INT));
            }
        }
    }
            
    private int pushRegWZ(int reg) {
        if(reg == R+0) {
            warn.println("Warning: Pushing r0!");
            new Exception().printStackTrace(warn);
        }
        return pushRegZ(reg);
    }
    
    private int pushRegZ(int reg) {
        if(reg == R+0) return mg.add(ICONST_0);
        else return pushReg(reg);
    }
    
    
    private int pushReg(int reg) {
        int h = mg.size();
        if(doLocal(reg)) {
            mg.add(ILOAD,getLocalForReg(reg));
        } else if(reg >= F+0 && reg <= F+31 && singleFloat) {
            mg.add(ALOAD_0);
            mg.add(GETFIELD,me.field(regField[reg],Type.FLOAT));
            mg.add(INVOKESTATIC,Type.FLOAT_OBJECT.method("floatToIntBits",Type.INT,new Type[]{Type.FLOAT}));
        } else {
            mg.add(ALOAD_0);
            mg.add(GETFIELD,me.field(regField[reg],Type.INT));
        }
        return h;
    }
    
    private int preSetRegStackPos;
    private int[] preSetRegStack = new int[8];
    
    // This can push ONE or ZERO words to the stack. If it pushed one it returns true
    private boolean preSetReg(int reg) {
        preSetRegStack[preSetRegStackPos] = reg;
        preSetRegStackPos++;
        if(doLocal(reg)) {
            return false;
        } else {
            mg.add(ALOAD_0);
            return true;
        }
    }
    
    private int setReg() {
        if(preSetRegStackPos==0) throw new RuntimeException("didn't do preSetReg");
        preSetRegStackPos--;
        int reg = preSetRegStack[preSetRegStackPos];
        int h = mg.size();
        if(doLocal(reg)) {
            mg.add(ISTORE,getLocalForReg(reg));
            regLocalWritten[reg] = true;
        } else if(reg >= F+0 && reg <= F+31 && singleFloat) {
            mg.add(INVOKESTATIC,Type.FLOAT_OBJECT.method("intBitsToFloat",Type.FLOAT,new Type[]{Type.INT}));
            mg.add(PUTFIELD,me.field(regField[reg],Type.FLOAT));            
        } else {
            mg.add(PUTFIELD,me.field(regField[reg],Type.INT));
        }
        return h;
    }
    
    private int preSetPC() { return mg.add(ALOAD_0); }
    private int setPC() {
        return mg.add(PUTFIELD,me.field("pc",Type.INT));
    }
    
    //unused - private InstructionHandle pushDouble(int reg) throws CompilationException { return pushDouble(reg,true); }
    private int pushFloat(int reg) throws Exn { return pushDouble(reg,false); }
    private int pushDouble(int reg, boolean d) throws Exn {
        if(reg < F || reg >= F+32) throw new IllegalArgumentException(""+reg);
        int h = mg.size();
        if(d) {
            if(singleFloat) throw new Exn("Double operations not supported when singleFloat is enabled");
            if(reg == F+31) throw new Exn("Tried to use a double in f31");
            pushReg(reg+1);
            mg.add(I2L);
            mg.add(LDC,32);
            mg.add(LSHL);
            pushReg(reg);
            mg.add(I2L);
            mg.add(LDC,FFFFFFFF);
            mg.add(LAND);
            mg.add(LOR);
            mg.add(INVOKESTATIC,Type.DOUBLE_OBJECT.method("longBitsToDouble",Type.DOUBLE,new Type[]{Type.LONG}));
        } else if(singleFloat) {
            mg.add(ALOAD_0);
            mg.add(GETFIELD,me.field(regField[reg],Type.FLOAT));
        } else {
            pushReg(reg);
            mg.add(INVOKESTATIC,Type.Class.instance("java.lang.Float").method("intBitsToFloat",Type.FLOAT,new Type[]{Type.INT}));
        }
        return h;
    }
    
    private void preSetFloat(int reg) { preSetDouble(reg,false); }
    private void preSetDouble(int reg) { preSetDouble(reg,true); }
    private void preSetDouble(int reg, boolean d) { preSetReg(reg); }
    
    private int setFloat() throws Exn { return setDouble(false); }
    private int setDouble() throws Exn { return setDouble(true); }
    private int setDouble(boolean d) throws Exn {
        int reg = preSetRegStack[preSetRegStackPos-1];
        if(reg < F || reg >= F+32) throw new IllegalArgumentException(""+reg);
        int h = mg.size();
        if(d) {
            if(singleFloat) throw new Exn("Double operations not supported when singleFloat is enabled");
            if(reg == F+31) throw new Exn("Tried to use a double in f31");
            mg.add(INVOKESTATIC,Type.DOUBLE_OBJECT.method("doubleToLongBits",Type.LONG,new Type[]{Type.DOUBLE}));
            mg.add(DUP2);
            mg.add(LDC,32);
            mg.add(LUSHR);
            mg.add(L2I);
            if(preSetReg(reg+1))
                mg.add(SWAP);
            setReg();
            mg.add(L2I);
            setReg(); // preSetReg was already done for this by preSetDouble
        } else if(singleFloat) {
            // HACK: Clean this up
            preSetRegStackPos--;
            mg.add(PUTFIELD,me.field(regField[reg],Type.FLOAT));
        } else {
            //h = a(fac.createInvoke("java.lang.Float","floatToRawIntBits",Type.INT,new Type[]{Type.FLOAT},INVOKESTATIC));
            mg.add(INVOKESTATIC,Type.FLOAT_OBJECT.method("floatToRawIntBits",Type.INT,new Type[]{Type.FLOAT}));
            setReg();   
        }
        return h;
    }
    
    private void pushTmp() { mg.add(ILOAD_1); }
    private void setTmp() { mg.add(ISTORE_1); }
    
    private void addiu(int reg, int offset) {
        if(reg != R+0 && offset != 0) {
            pushReg(reg);
            mg.add(LDC,offset);
            mg.add(IADD);
        } else if(reg != R+0) {
            pushReg(reg);
        } else {
            mg.add(LDC,offset);
        }        
    }
    private int memWriteStage;
    private void preMemWrite1() {
        if(memWriteStage!=0) throw new Error("pending preMemWrite1/2");
        memWriteStage=1;
        if(onePage)
            mg.add(ALOAD_2);
        else if(fastMem)
            mg.add(ALOAD,3);
        else
            mg.add(ALOAD_0);
    }
    
    private void preMemWrite2(int reg, int offset) {
        addiu(reg,offset);
        preMemWrite2();
    }
    
    private void preMemWrite2() { preMemWrite2(false); }
    private void preMemWrite2(boolean addrInTmp) {
        if(memWriteStage!=1) throw new Error("pending preMemWrite2 or no preMemWrite1");
        memWriteStage=2;
        
        if(nullPointerCheck) {
            mg.add(DUP);
            mg.add(ALOAD_0);
            mg.add(SWAP);
            // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.nullPointerCheck
            mg.add(INVOKEVIRTUAL,me.method("nullPointerCheck",Type.VOID,new Type[]{Type.INT}));
        }
        
        if(onePage) {
            mg.add(ICONST_2);
            mg.add(IUSHR);
        } else if(fastMem) {
            if(!addrInTmp)
                mg.add(DUP_X1);
            mg.add(LDC,pageShift);
            mg.add(IUSHR);
            mg.add(AALOAD);
            if(addrInTmp)
                pushTmp();
            else
                mg.add(SWAP);
            mg.add(ICONST_2);
            mg.add(IUSHR);
            mg.add(LDC,(pageSize>>2)-1);
            mg.add(IAND);            
        }
    }
    
    // pops an address and value off the stack, sets *addr to value
    private void memWrite() {
        if(memWriteStage!=2) throw new Error("didn't do preMemWrite1 or preMemWrite2");
        memWriteStage=0;
                
        if(onePage) {
            mg.add(IASTORE);
        } else if(fastMem) {
            mg.add(IASTORE);
        } else {
            // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.unsafeMemWrite
            mg.add(INVOKEVIRTUAL,me.method("unsafeMemWrite",Type.VOID,new Type[]{Type.INT,Type.INT}));
        }
        
    }
    
    // reads the word at r[reg]+offset
    private void memRead(int reg, int offset) {
        preMemRead();
        addiu(reg,offset);
        memRead();
    }
    
    private boolean didPreMemRead;
    private boolean preMemReadDoPreWrite;
    
    private void preMemRead() { preMemRead(false); }
    private void preMemRead(boolean preWrite) {
        if(didPreMemRead) throw new Error("pending preMemRead");
        didPreMemRead = true;
        preMemReadDoPreWrite = preWrite;
        if(onePage)
            mg.add(ALOAD_2);
        else if(fastMem)
            mg.add(ALOAD,preWrite ? 3 : 2);
        else
            mg.add(ALOAD_0);
    }
    // memRead pops an address off the stack, reads the value at that addr, and pushed the value
    // preMemRead MUST be called BEFORE the addresses is pushed
    private void memRead() { memRead(false); }
    
    private void memRead(boolean addrInTmp) {
        if(!didPreMemRead) throw new Error("didn't do preMemRead");
        didPreMemRead = false;
        if(preMemReadDoPreWrite)
            memWriteStage=2; 
            
        if(nullPointerCheck) {
            mg.add(DUP);
            mg.add(ALOAD_0);
            mg.add(SWAP);
            mg.add(INVOKEVIRTUAL,me.method("nullPointerCheck",Type.VOID,new Type[]{Type.INT}));
        }
        
        if(onePage) {
            mg.add(ICONST_2);
            mg.add(IUSHR);
            if(preMemReadDoPreWrite)
                mg.add(DUP2);
            mg.add(IALOAD);
        } else if(fastMem) {
            if(!addrInTmp)
                mg.add(DUP_X1);
            mg.add(LDC,pageShift);
            mg.add(IUSHR);
            mg.add(AALOAD);
            if(addrInTmp)
                pushTmp();
            else
                mg.add(SWAP);
            mg.add(ICONST_2);
            mg.add(IUSHR);
            mg.add(LDC,(pageSize>>2)-1);
            mg.add(IAND);
            if(preMemReadDoPreWrite)
                mg.add(DUP2);
            mg.add(IALOAD);
            
        } else {
            if(preMemReadDoPreWrite)
                mg.add(DUP2);
            // GCCLASS_HINT: org.ibex.nestedvm.RuntimeCompiler.compile org.ibex.nestedvm.Runtime.unsafeMemRead
            mg.add(INVOKEVIRTUAL,me.method("unsafeMemRead",Type.INT,new Type[]{Type.INT}));
        }
    }
    
    
    // This might come in handy for something else
    /*private boolean touchesReg(int insn, int reg) {
        if((reg < R+0 || reg >= R+32) && reg != FCSR) throw new IllegalArgumentException(""+reg);
        if(reg == R+0) return false; // r0 is never modified
        int op = (insn >>> 26) & 0xff;                 // bits 26-31
        int subcode = insn & 0x3f;                     // bits 0-5 
        int rd = (insn >>> 11) & 0x1f;                 // bits 11-15
        int rt = (insn >>> 16) & 0x1f;                 // bits 16-20 
        int rs = (insn >>> 21) & 0x1f;                 // bits 21-25
        
        switch(op) {
        case 0:
            if(subcode >= 0 && subcode <= 7) return reg == R+rd; // Shift ops
            if(subcode >= 32 && subcode <= 43) return reg == R+rd; // Other math ops 
            if(subcode >= 24 && subcode <= 27) return reg == HI || reg == LO; // MULT/DIV
            break;
        case 13: return false; // BREAK
        case 17:
            switch(rs) {
                case 0: return reg == R+rt; // MFC.1
                case 2: return reg == R+rt; // CFC.1
                case 4: return false; // MTC.1
                case 6: return false; // CTC.1
                case 16: // Single 
                case 17: // Double
                    if(subcode == 50 || subcode == 60 || subcode == 62) return reg == FCSR;
                    return false; // everything else just touches f0-f31
                case 20: return false; // Integer - just touches f0-f31
            }
            break;
        default:
            if(op >= 8 && op <= 15) return reg == R+rt; // XXXI instructions
            if(op >= 40 && op <= 46) return false; // Memory WRITE ops
            if(op == 49) return reg == F+rt; // LWC1
            if(op == 57) return false; // SWC1
            break;
        }
        warn.println("Unknown instruction in touchesReg()- assuming it modifies all regs " + op + " " + subcode);
        new Exception().fillInStackTrace().printStackTrace(warn);
        return true;
    }*/
}

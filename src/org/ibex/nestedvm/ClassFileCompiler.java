package org.ibex.nestedvm;

import java.io.*;
import java.util.Hashtable;

import org.ibex.nestedvm.util.*;

import org.apache.bcel.generic.*;

// FEATURE: Use BCEL to do peephole optimization
// FEATURE: Special mode to support single-precision only - regs are floats not ints

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


public class ClassFileCompiler extends Compiler implements org.apache.bcel.Constants  {    
    /** The stream to write the compiled output to */
    private OutputStream os;
    private PrintStream warn = System.err;
    
    private ClassGen cl;
    private ConstantPoolGen cp;
    private InstructionList clinitExtras = new InstructionList();
    private InstructionList initExtras = new InstructionList();
    private InstructionFactory fac; 
    
    // Handy wrappers around the BCEL functions
    private InstructionList insnList;
    private void selectMethod(MethodGen m) { insnList = m.getInstructionList(); }
    private void selectList(InstructionList l) { insnList = l; }
    private InstructionHandle a(Instruction i) { return insnList.append(i); }
    private BranchHandle a(BranchInstruction i) { return insnList.append(i); }
    private InstructionHandle a(InstructionList l) { return insnList.append(l); }
    private InstructionHandle a(CompoundInstruction c) { return insnList.append(c); }
    
    // This works around a bug in InstructionList
    // FEATURE: fix this in bcel, send them a patch, etc
    private static class FixedInstructionList extends InstructionList {
        public void move(InstructionHandle start, InstructionHandle end, InstructionHandle target) {
            InstructionHandle extra = target != null && target == getEnd() ? append(InstructionConstants.NOP) : null;
            super.move(start,end,target);
            if(extra != null) try { delete(extra); } catch (TargetLostException e) { /* won't happen */ }
        }
    }
    
    private MethodGen newMethod(int flags, Type ret, Type[] args, String name) {
        return new MethodGen(flags,ret,args,null,name,fullClassName,new FixedInstructionList(),cp);
    }
    
    public ClassFileCompiler(String path, String className, OutputStream os) throws IOException { this(new Seekable.File(path),className,os); }
    public ClassFileCompiler(Seekable binary, String className, OutputStream os) throws IOException {
        super(binary,className);
        this.os = os;
    }
    
    public void setWarnWriter(PrintStream warn) { this.warn = warn; }
        
    protected void _go() throws Exn, IOException {
        if(lessConstants) throw new Exn("ClassFileCompiler doesn't support -o lessconstants");
        if(!pruneCases) throw new Exn("-o prunecases MUST be enabled for ClassFileCompiler");

        // Class
        cl = new ClassGen(fullClassName,runtimeClass,source,ACC_SUPER|ACC_PUBLIC|ACC_FINAL,null);
        cp = cl.getConstantPool();
        fac = new InstructionFactory(cl,cp);
        
        // Fields
        cl.addField(new FieldGen(ACC_PRIVATE,Type.INT,"pc",cp).getField());
        for(int i=1;i<32;i++)
            cl.addField(new FieldGen(ACC_PRIVATE,Type.INT,"r"+i,cp).getField());
        for(int i=0;i<32;i++)
            cl.addField(new FieldGen(ACC_PRIVATE,Type.INT,"f"+i,cp).getField());

        cl.addField(new FieldGen(ACC_PRIVATE,Type.INT,"hi",cp).getField());
        cl.addField(new FieldGen(ACC_PRIVATE,Type.INT,"lo",cp).getField());
        cl.addField(new FieldGen(ACC_PRIVATE,Type.INT,"fcsr",cp).getField());
        
        if(onePage) {
            cl.addField(new FieldGen(ACC_PRIVATE|ACC_FINAL,new ArrayType(Type.INT,1),"page",cp).getField());

            selectList(initExtras);
            a(InstructionConstants.ALOAD_0);
            a(InstructionConstants.DUP);
            a(fac.createFieldAccess(fullClassName,"readPages",new ArrayType(Type.INT, 2), GETFIELD));
            pushConst(0);
            a(InstructionConstants.AALOAD);
            a(fac.createFieldAccess(fullClassName,"page",new ArrayType(Type.INT,1), PUTFIELD));
        }
        
        if(supportCall)
            cl.addField(new FieldGen(ACC_PRIVATE|ACC_STATIC|ACC_FINAL,Type.getType("L"+hashClass.replace('.','/')+";"),"symbols",cp).getField()); 
        
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
        
        ELF.SHeader text = elf.sectionWithName(".text");
        
        // Trampoline
        MethodGen tramp = newMethod(ACC_PRIVATE,Type.VOID, Type.NO_ARGS, "trampoline");
        tramp.addException("org.ibex.nestedvm.Runtime$ExecutionException");
        selectMethod(tramp);
        
        InstructionHandle start = a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"state",Type.INT, GETFIELD));
        pushConst(Runtime.RUNNING);
        BranchInstruction stateCheck =  InstructionFactory.createBranchInstruction(IF_ICMPNE,null);
        a(stateCheck);
        a(InstructionConstants.ALOAD_0);
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"pc",Type.INT, GETFIELD));
        pushConst(methodShift);
        a(InstructionConstants.IUSHR);
        
        int beg = text.addr >>> methodShift;
        int end = ((text.addr + text.size + maxBytesPerMethod - 1) >>> methodShift);

        // This data is redundant but BCEL wants it
        int[] matches = new int[end-beg];
        for(int i=beg;i<end;i++)  matches[i-beg] = i;
        TABLESWITCH ts = new TABLESWITCH(matches,new InstructionHandle[matches.length],null);
        a(ts);
        for(int n=beg;n<end;n++){
            InstructionHandle h = a(fac.createInvoke(fullClassName,"run_"+toHex(n<<methodShift),Type.VOID,Type.NO_ARGS,INVOKESPECIAL));
            a(InstructionFactory.createBranchInstruction(GOTO,start));
            ts.setTarget(n-beg,h);
        }
        
        ts.setTarget(a(InstructionConstants.POP)); // default case
        a(fac.createNew("org.ibex.nestedvm.Runtime$ExecutionException"));
        a(InstructionConstants.DUP);
        a(fac.createNew("java.lang.StringBuffer"));
        a(InstructionConstants.DUP);
        a(new PUSH(cp,"Jumped to invalid address in trampoline (r2: "));
        a(fac.createInvoke("java.lang.StringBuffer","<init>",Type.VOID,new Type[]{Type.STRING},INVOKESPECIAL));
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"r2",Type.INT, GETFIELD));
        a(fac.createInvoke("java.lang.StringBuffer","append",Type.STRINGBUFFER,new Type[]{Type.INT},INVOKEVIRTUAL));
        a(new PUSH(cp," pc:"));
        a(fac.createInvoke("java.lang.StringBuffer","append",Type.STRINGBUFFER,new Type[]{Type.STRING},INVOKEVIRTUAL));
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"pc",Type.INT, GETFIELD));
        a(fac.createInvoke("java.lang.StringBuffer","append",Type.STRINGBUFFER,new Type[]{Type.INT},INVOKEVIRTUAL));
        a(new PUSH(cp,')'));
        a(fac.createInvoke("java.lang.StringBuffer","append",Type.STRINGBUFFER,new Type[]{Type.CHAR},INVOKEVIRTUAL));
        a(fac.createInvoke("java.lang.StringBuffer","toString",Type.STRING,Type.NO_ARGS,INVOKEVIRTUAL));
        a(fac.createInvoke("org.ibex.nestedvm.Runtime$ExecutionException","<init>",Type.VOID,new Type[]{Type.STRING},INVOKESPECIAL));
        a(InstructionConstants.ATHROW);
        
        stateCheck.setTarget(a(InstructionConstants.RETURN));
        
        tramp.setMaxStack();
        tramp.setMaxLocals();
        try {
            cl.addMethod(tramp.getMethod());
        } catch(ClassGenException e) {
            e.printStackTrace(warn);
            throw new Exn("Generation of the trampoline method failed. Try increasing maxInsnPerMethod");
        }
        
        addConstReturnMethod("gp",gp.addr);
        addConstReturnMethod("entryPoint",elf.header.entry);
        addConstReturnMethod("heapStart",highestAddr);
                
        if(userInfo != null) {
            addConstReturnMethod("userInfoBase",userInfo.addr);
            addConstReturnMethod("userInfoSize",userInfo.size);
        }
        
        // FEATURE: Allow specification of memory size at runtime (numpages)
        // Constructor
        MethodGen init = newMethod(ACC_PUBLIC,Type.VOID, Type.NO_ARGS, "<init>");
        selectMethod(init);
        a(InstructionConstants.ALOAD_0);
        pushConst(pageSize);
        pushConst(totalPages);
        a(fac.createInvoke(runtimeClass,"<init>",Type.VOID,new Type[]{Type.INT,Type.INT},INVOKESPECIAL));
        
        a(initExtras);
        
        a(InstructionConstants.RETURN);
        
        init.setMaxLocals();
        init.setMaxStack();
        cl.addMethod(init.getMethod());
        
        
        MethodGen clinit = newMethod(ACC_PRIVATE|ACC_STATIC,Type.VOID, Type.NO_ARGS, "<clinit>");
        selectMethod(clinit);
        a(clinitExtras);
        
        if(supportCall) {
            a(fac.createNew(hashClass));
            a(InstructionConstants.DUP);
            a(InstructionConstants.DUP);
            a(fac.createInvoke(hashClass,"<init>",Type.VOID,Type.NO_ARGS,INVOKESPECIAL));
            a(fac.createFieldAccess(fullClassName,"symbols",Type.getType("L"+hashClass.replace('.','/')+";"), PUTSTATIC));
            ELF.Symbol[] symbols = elf.getSymtab().symbols;
            for(int i=0;i<symbols.length;i++) {
                ELF.Symbol s = symbols[i];
                if(s.type == ELF.Symbol.STT_FUNC && s.binding == ELF.Symbol.STB_GLOBAL && (s.name.equals("_call_helper") || !s.name.startsWith("_"))) {
                    a(InstructionConstants.DUP);
                    a(new PUSH(cp,s.name));
                    a(fac.createNew("java.lang.Integer"));
                    a(InstructionConstants.DUP);
                    a(new PUSH(cp,s.addr));
                    a(fac.createInvoke("java.lang.Integer","<init>",Type.VOID,new Type[]{Type.INT},INVOKESPECIAL));
                    a(fac.createInvoke(hashClass,"put",Type.OBJECT,new Type[]{Type.OBJECT,Type.OBJECT},INVOKEVIRTUAL));
                    a(InstructionConstants.POP);
                }
            }
            a(InstructionConstants.POP);
        }
        
        a(InstructionConstants.RETURN);
        clinit.setMaxLocals();
        clinit.setMaxStack();
        cl.addMethod(clinit.getMethod());
        
        if(supportCall) {
            MethodGen lookupSymbol = newMethod(ACC_PROTECTED,Type.INT,new Type[]{Type.STRING},"lookupSymbol");
            selectMethod(lookupSymbol);
            a(fac.createFieldAccess(fullClassName,"symbols",Type.getType("L"+hashClass.replace('.','/')+";"), GETSTATIC));
            a(InstructionConstants.ALOAD_1);
            a(fac.createInvoke(hashClass,"get",Type.OBJECT,new Type[]{Type.OBJECT},INVOKEVIRTUAL));
            a(InstructionConstants.DUP);
            BranchHandle bh = a(InstructionFactory.createBranchInstruction(IFNULL,null));
            a(fac.createCheckCast(new ObjectType("java.lang.Integer")));
            a(fac.createInvoke("java.lang.Integer","intValue",Type.INT,Type.NO_ARGS,INVOKEVIRTUAL));
            a(InstructionConstants.IRETURN);
            bh.setTarget(a(InstructionConstants.POP));
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IRETURN);
            lookupSymbol.setMaxLocals();
            lookupSymbol.setMaxStack();
            cl.addMethod(lookupSymbol.getMethod());
        }
        
        MethodGen setCPUState = newMethod(ACC_PROTECTED,Type.VOID,new Type[]{Type.getType("Lorg/ibex/nestedvm/Runtime$CPUState;")},"setCPUState");
        selectMethod(setCPUState);
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","r",new ArrayType(Type.INT,1),GETFIELD));
        a(InstructionConstants.ASTORE_2);
        for(int i=1;i<32;i++) {
            a(InstructionConstants.ALOAD_0);
            a(InstructionConstants.ALOAD_2);
            pushConst(i);
            a(InstructionConstants.IALOAD);
            a(fac.createFieldAccess(fullClassName,"r"+i,Type.INT, PUTFIELD));
        }
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","f",new ArrayType(Type.INT,1),GETFIELD));
        a(InstructionConstants.ASTORE_2);
        for(int i=0;i<32;i++) {
            a(InstructionConstants.ALOAD_0);
            a(InstructionConstants.ALOAD_2);
            pushConst(i);
            a(InstructionConstants.IALOAD);
            a(fac.createFieldAccess(fullClassName,"f"+i,Type.INT, PUTFIELD));
        }
        a(InstructionConstants.ALOAD_0);
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","hi",Type.INT,GETFIELD));
        a(fac.createFieldAccess(fullClassName,"hi",Type.INT, PUTFIELD));
        a(InstructionConstants.ALOAD_0);
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","lo",Type.INT,GETFIELD));
        a(fac.createFieldAccess(fullClassName,"lo",Type.INT, PUTFIELD));
        a(InstructionConstants.ALOAD_0);
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","fcsr",Type.INT,GETFIELD));
        a(fac.createFieldAccess(fullClassName,"fcsr",Type.INT, PUTFIELD));
        a(InstructionConstants.ALOAD_0);
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","pc",Type.INT,GETFIELD));
        a(fac.createFieldAccess(fullClassName,"pc",Type.INT, PUTFIELD));
        
        a(InstructionConstants.RETURN);
        setCPUState.setMaxLocals();
        setCPUState.setMaxStack();
        cl.addMethod(setCPUState.getMethod());
        
        MethodGen getCPUState = newMethod(ACC_PROTECTED,Type.VOID,new Type[]{Type.getType("Lorg/ibex/nestedvm/Runtime$CPUState;")},"getCPUState");
        selectMethod(getCPUState);
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","r",new ArrayType(Type.INT,1),GETFIELD));
        a(InstructionConstants.ASTORE_2);
        for(int i=1;i<32;i++) {
            a(InstructionConstants.ALOAD_2);
            pushConst(i);
            a(InstructionConstants.ALOAD_0);
            a(fac.createFieldAccess(fullClassName,"r"+i,Type.INT, GETFIELD));
            a(InstructionConstants.IASTORE);
        }
        
        a(InstructionConstants.ALOAD_1);
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","f",new ArrayType(Type.INT,1),GETFIELD));
        a(InstructionConstants.ASTORE_2);
        for(int i=0;i<32;i++) {
            a(InstructionConstants.ALOAD_2);
            pushConst(i);
            a(InstructionConstants.ALOAD_0);
            a(fac.createFieldAccess(fullClassName,"f"+i,Type.INT, GETFIELD));
            a(InstructionConstants.IASTORE);
        }
        a(InstructionConstants.ALOAD_1);
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"hi",Type.INT, GETFIELD));
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","hi",Type.INT,PUTFIELD));
        a(InstructionConstants.ALOAD_1);
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"lo",Type.INT, GETFIELD));
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","lo",Type.INT,PUTFIELD));
        a(InstructionConstants.ALOAD_1);
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"fcsr",Type.INT, GETFIELD));
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","fcsr",Type.INT,PUTFIELD));
        a(InstructionConstants.ALOAD_1);
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"pc",Type.INT, GETFIELD));
        a(fac.createFieldAccess("org.ibex.nestedvm.Runtime$CPUState","pc",Type.INT,PUTFIELD));
        
        a(InstructionConstants.RETURN);
        getCPUState.setMaxLocals();
        getCPUState.setMaxStack();
        cl.addMethod(getCPUState.getMethod());


        MethodGen execute = newMethod(ACC_PROTECTED,Type.VOID,Type.NO_ARGS,"_execute");
        selectMethod(execute);
        InstructionHandle tryStart = a(InstructionConstants.ALOAD_0);
        InstructionHandle tryEnd = a(fac.createInvoke(fullClassName,"trampoline",Type.VOID,Type.NO_ARGS,INVOKESPECIAL));
        a(InstructionConstants.RETURN);
        
        InstructionHandle catchInsn = a(InstructionConstants.ASTORE_1);
        a(fac.createNew("org.ibex.nestedvm.Runtime$FaultException"));
        a(InstructionConstants.DUP);
        a(InstructionConstants.ALOAD_1);
        a(fac.createInvoke("org.ibex.nestedvm.Runtime$FaultException","<init>",Type.VOID,new Type[]{new ObjectType("java.lang.RuntimeException")},INVOKESPECIAL));
        a(InstructionConstants.ATHROW);
        
        execute.addExceptionHandler(tryStart,tryEnd,catchInsn,new ObjectType("java.lang.RuntimeException"));
        execute.setMaxLocals();
        execute.setMaxStack();
        cl.addMethod(execute.getMethod());
        
        
        MethodGen main = newMethod(ACC_STATIC|ACC_PUBLIC,Type.VOID,new Type[]{new ArrayType(Type.STRING,1)},"main");
        selectMethod(main);
        a(fac.createNew(fullClassName));
        a(InstructionConstants.DUP);
        a(fac.createInvoke(fullClassName,"<init>",Type.VOID,Type.NO_ARGS,INVOKESPECIAL));
        a(new PUSH(cp,fullClassName));
        a(InstructionConstants.ALOAD_0);
        if(unixRuntime)
            a(fac.createInvoke("org.ibex.nestedvm.UnixRuntime","runAndExec",Type.INT,
                new Type[]{Type.getType("Lorg/ibex/nestedvm/UnixRuntime;"),Type.STRING,new ArrayType(Type.STRING,1)},
                INVOKESTATIC));
        else
            a(fac.createInvoke(fullClassName,"run",Type.INT,new Type[]{Type.STRING,new ArrayType(Type.STRING,1)},INVOKEVIRTUAL));
        a(fac.createInvoke("java.lang.System","exit",Type.VOID,new Type[]{Type.INT},INVOKESTATIC));
        a(InstructionConstants.RETURN);
        main.setMaxLocals();
        main.setMaxStack();
        cl.addMethod(main.getMethod());
        
        if(printStats)
            System.out.println("Constant Pool Size: " + cp.getSize());
        cl.getJavaClass().dump(os);
    }
    
    private void addConstReturnMethod(String name, int val) {
        MethodGen method = newMethod(ACC_PROTECTED,Type.INT, Type.NO_ARGS,name);
        selectMethod(method);
        pushConst(val);
        a(InstructionConstants.IRETURN);
        method.setMaxLocals();
        method.setMaxStack();
        cl.addMethod(method.getMethod());
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
            cl.addField(new FieldGen(ACC_PRIVATE|ACC_STATIC|ACC_FINAL,new ArrayType(Type.INT,1),fieldname,cp).getField());
            
            selectList(clinitExtras);
            a(new PUSH(cp,sb.toString()));
            a(new PUSH(cp,segSize/4));
            a(fac.createInvoke("org.ibex.nestedvm.Runtime","decodeData",new ArrayType(Type.INT,1),new Type[]{Type.STRING,Type.INT},INVOKESTATIC));
            a(fac.createPutStatic(fullClassName,fieldname,new ArrayType(Type.INT,1)));

            selectList(initExtras);
            a(InstructionConstants.ALOAD_0);
            a(fac.createGetStatic(fullClassName,fieldname,new ArrayType(Type.INT,1)));
            a(new PUSH(cp,addr));
            a(new PUSH(cp,readOnly));
            a(fac.createInvoke(fullClassName,"initPages",Type.VOID,new Type[]{new ArrayType(Type.INT,1),Type.INT,Type.BOOLEAN},INVOKEVIRTUAL));
            
            addr += segSize;
            size -= segSize;
        }
        dis.close();
    }
    
    private void emitBSS(int addr, int size) throws Exn {
        if((addr&3)!=0) throw new Exn("BSS section on weird boundaries");
        size = (size+3)&~3;
        int count = size/4;
        selectList(initExtras);
        a(InstructionConstants.ALOAD_0);
        a(new PUSH(cp,addr));
        a(new PUSH(cp,count));
        a(fac.createInvoke(fullClassName,"clearPages",Type.VOID,new Type[]{Type.INT,Type.INT},INVOKEVIRTUAL));
    }
    
    // method state info
    private boolean textDone;
    private int startOfMethod = 0;
    private int endOfMethod = 0;
    private boolean unreachable = false;
    private InstructionHandle[] jumpHandles;
    private InstructionHandle defaultHandle;
    private InstructionHandle returnHandle;
    private InstructionHandle realStart;
    private MethodGen curMethod;
    
    private boolean jumpable(int addr) { return jumpableAddresses.contains(new Integer(addr)); }
    
    private void emitText(int addr, DataInputStream dis, int size) throws Exn,IOException {
        if(textDone) throw new Exn("Multiple text segments");
        textDone = true;
        
        if((addr&3)!=0 || (size&3)!=0) throw new Exn("Section on weird boundaries");
        int count = size/4;
        int insn,nextInsn=-1;
        boolean skipNext = true;
        
        for(int i=0;i<count;i++,addr+=4) {
            insn = skipNext ? dis.readInt() : nextInsn;
            nextInsn = (i == count-1) ? -1 : dis.readInt();
            if(addr >= endOfMethod) { endMethod(addr); startMethod(addr); }
            if(jumpHandles[(addr-startOfMethod)/4] != null) {
                // Move the fake jump target to the current location
                insnList.move(jumpHandles[(addr-startOfMethod)/4],insnList.getEnd());
                unreachable = false;
            } else if(unreachable) {
                continue;
            }
            try {
                skipNext = emitInstruction(addr,insn,nextInsn);
            } catch(RuntimeException e) {
                warn.println("Exception at " + toHex(addr));
                throw e;
            }
            if(skipNext) { addr+=4; i++; }
        }
        endMethod(0);
        dis.close();
    }
    
    private void startMethod(int first) {
        startOfMethod = first & methodMask;
        endOfMethod = startOfMethod + maxBytesPerMethod;
        curMethod = newMethod(ACC_PRIVATE,Type.VOID,Type.NO_ARGS,"run_" + toHex(startOfMethod));
        selectMethod(curMethod);
        
        int[] buf = new int[maxBytesPerMethod/4];
        jumpHandles = new InstructionHandle[maxBytesPerMethod/4];
        int n=0;
        for(int addr=first;addr<endOfMethod;addr+=4) {
            if(jumpable(addr)) {
                buf[n++] = addr;
                // append NOPs for GOTO jumps (these will be moved to the correct location later)
                jumpHandles[(addr-startOfMethod)/4] = a(InstructionConstants.NOP);
            }
        }
        
        // append NOP for default case (throw exn) (this will be moved later)
        defaultHandle = a(InstructionConstants.NOP);
        returnHandle = a(InstructionConstants.NOP);
        
        int[] matches = new int[n];
        System.arraycopy(buf,0,matches,0,n);
        InstructionHandle[] targets = new InstructionHandle[n];
        for(int i=0;i<matches.length;i++)
            targets[i] = jumpHandles[(matches[i]-startOfMethod)/4];
        
        
        // First instruction of the actual method - everything above this should be removed
        // before we get to the end
        realStart = a(InstructionConstants.NOP);
        
        if(onePage) {
            a(InstructionConstants.ALOAD_0);
            a(fac.createFieldAccess(fullClassName,"page",new ArrayType(Type.INT,1), GETFIELD));
            a(InstructionConstants.ASTORE_2);
        } else {
            a(InstructionConstants.ALOAD_0);
            a(fac.createFieldAccess(fullClassName,"readPages",new ArrayType(Type.INT,2), GETFIELD));
            a(InstructionConstants.ASTORE_2);
            a(InstructionConstants.ALOAD_0);
            a(fac.createFieldAccess(fullClassName,"writePages",new ArrayType(Type.INT,2), GETFIELD));
            a(InstructionFactory.createStore(Type.OBJECT,3));
        }
        
        LOOKUPSWITCH initialSwitch = new LOOKUPSWITCH(matches,targets,defaultHandle);
        a(InstructionConstants.ALOAD_0);
        a(fac.createFieldAccess(fullClassName,"pc",Type.INT, GETFIELD));
        a(initialSwitch);     
    }
    
    private void endMethod(int firstAddrOfNext) {
        if(startOfMethod == 0) return;
        
        if(!unreachable) {
            preSetPC();
            pushConst(firstAddrOfNext);
            setPC();
            // mark the start of the next method as jumpable
            jumpableAddresses.add(new Integer(firstAddrOfNext));
        }
        
        insnList.move(returnHandle,insnList.getEnd());
        fixupRegs();
        a(InstructionConstants.RETURN);
        
        // move the default jump target (lookupswitch) to before the throw
        insnList.move(defaultHandle,insnList.getEnd());
        if(debugCompiler) {
            a(fac.createNew("org.ibex.nestedvm.Runtime$ExecutionException"));
            a(InstructionConstants.DUP);
            a(fac.createNew("java.lang.StringBuffer"));
            a(InstructionConstants.DUP);
            a(new PUSH(cp,"Jumped to invalid address: "));
            a(fac.createInvoke("java.lang.StringBuffer","<init>",Type.VOID,new Type[]{Type.STRING},INVOKESPECIAL));
            a(InstructionConstants.ALOAD_0);
            a(fac.createFieldAccess(fullClassName,"pc",Type.INT, GETFIELD));
            a(fac.createInvoke("java.lang.StringBuffer","append",Type.STRINGBUFFER,new Type[]{Type.INT},INVOKEVIRTUAL));
            a(fac.createInvoke("java.lang.StringBuffer","toString",Type.STRING,Type.NO_ARGS,INVOKEVIRTUAL));
            a(fac.createInvoke("org.ibex.nestedvm.Runtime$ExecutionException","<init>",Type.VOID,new Type[]{Type.STRING},INVOKESPECIAL));
            a(InstructionConstants.ATHROW);
        } else {
            a(fac.createNew("org.ibex.nestedvm.Runtime$ExecutionException"));
            a(InstructionConstants.DUP);
            a(new PUSH(cp,"Jumped to invalid address"));
            a(fac.createInvoke("org.ibex.nestedvm.Runtime$ExecutionException","<init>",Type.VOID,new Type[]{Type.STRING},INVOKESPECIAL));
            a(InstructionConstants.ATHROW);
        }

        if(insnList.getStart() != realStart) {
            System.err.println(insnList);
            throw new Error("A jumpHandle wasn't moved into place");
        }
        
        curMethod.removeNOPs();
        curMethod.setMaxLocals();
        curMethod.setMaxStack();
        
        cl.addMethod(curMethod.getMethod());
        
        endOfMethod = startOfMethod = 0;
    }


    private void leaveMethod() {
        a(InstructionFactory.createBranchInstruction(GOTO,returnHandle));
    }

    private void branch(int pc, int target) {
        if((pc&methodMask) == (target&methodMask)) {
            a(InstructionFactory.createBranchInstruction(GOTO,jumpHandles[(target-startOfMethod)/4]));
        } else {
            preSetPC();
            pushConst(target);
            setPC();
            leaveMethod();
        }
    }
    
    // This assumes everything needed by ifInsn is already on the stack
    private boolean doIfInstruction(short op, int pc, int target, int nextInsn) throws Exn {
        emitInstruction(-1,nextInsn,-1); // delay slot
        BranchHandle h;
        IfInstruction ifInsn = (IfInstruction) InstructionFactory.createBranchInstruction(op,null);
        if((target&methodMask) == (pc&methodMask)) {
            h = a(ifInsn);
            h.setTarget(jumpHandles[(target-startOfMethod)/4]);
        } else {
            h = a(ifInsn.negate());
            branch(pc,target);
            h.setTarget(a(InstructionConstants.NOP));
        }
        if(!jumpable(pc+4)) return true; // done - skip it
        
        //System.err.println("Delay slot is jumpable - This code is untested + " + toHex(nextInsn));
        if(pc+4==endOfMethod) {
            // the delay slot is at the start of the next method
            jumpableAddresses.add(new Integer(pc+8)); // make the 2nd insn of the next method jumpable
            branch(pc,pc+8); // jump over it
            //System.err.println("delay slot: " + toHex(pc+8));
            unreachable = true;
            return false; // we still need to output it
        } else {
            //System.err.println("jumped over delay slot: " + toHex(pc+4));
            // add another copy and jump over
            h = a(InstructionFactory.createBranchInstruction(GOTO,null));
            insnList.move(jumpHandles[(pc+4-startOfMethod)/4],insnList.getEnd());
            emitInstruction(-1,nextInsn,-1); // delay slot
            h.setTarget(a(InstructionConstants.NOP));
            return true;
        }
    }
    
    private boolean emitInstruction(int pc, int insn, int nextInsn) throws Exn {
        if(insn == -1) throw new Exn("insn is -1");
        
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
        BranchHandle b1,b2;
        
        switch(op) {
        case 0: {
            switch(subcode) {
            case 0: // SLL
                if(insn == 0) break; 
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushConst(shamt);
                a(InstructionConstants.ISHL);
                setReg();
                break;
            case 2: // SRL
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushConst(shamt);
                a(InstructionConstants.IUSHR);
                setReg();
                break;
            case 3: // SRA
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushConst(shamt);
                a(InstructionConstants.ISHR);        
                setReg();
                break;
            case 4: // SLLV
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushRegWZ(R+rs);
                a(InstructionConstants.ISHL);
                setReg();
                break;
            case 6: // SRLV
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushRegWZ(R+rs);
                a(InstructionConstants.IUSHR);
                setReg();
                break;
            case 7: // SRAV
                preSetReg(R+rd);
                pushRegWZ(R+rt);
                pushRegWZ(R+rs);
                a(InstructionConstants.ISHR);
                setReg();
                break;
            case 8: // JR
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                emitInstruction(-1,nextInsn,-1);
                preSetPC();
                pushRegWZ(R+rs);
                setPC();
                leaveMethod();
                unreachable = true;
                break;
            case 9: // JALR
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                emitInstruction(-1,nextInsn,-1);
                preSetPC();
                pushRegWZ(R+rs);
                setPC();
                
                preSetReg(R+RA);
                pushConst(pc+8);
                setReg();
                leaveMethod();
                unreachable = true;
                break;
            case 12: // SYSCALL
                preSetPC();
                pushConst(pc);
                setPC();
                
                restoreChangedRegs();
                
                preSetReg(R+V0);
                a(InstructionConstants.ALOAD_0);
                pushRegZ(R+V0);
                pushRegZ(R+A0);
                pushRegZ(R+A1);
                pushRegZ(R+A2);
                pushRegZ(R+A3);
                pushRegZ(R+T0);
                pushRegZ(R+T1);
                a(fac.createInvoke(fullClassName,"syscall",Type.INT,new Type[]{Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT,Type.INT},INVOKEVIRTUAL));
                setReg();
                
                a(InstructionConstants.ALOAD_0);
                a(fac.createFieldAccess(fullClassName,"state",Type.INT, GETFIELD));
                pushConst(Runtime.RUNNING);
                b1 = a(InstructionFactory.createBranchInstruction(IF_ICMPEQ,null));
                preSetPC();
                pushConst(pc+4);
                setPC();
                leaveMethod();
                b1.setTarget(a(InstructionConstants.NOP));
                
                break;
            case 13: // BREAK
                a(fac.createNew("org.ibex.nestedvm.Runtime$ExecutionException"));
                a(InstructionConstants.DUP);
                a(new PUSH(cp,"BREAK Code " + toHex(breakCode)));
                a(fac.createInvoke("org.ibex.nestedvm.Runtime$ExecutionException","<init>",Type.VOID,new Type[]{Type.STRING},INVOKESPECIAL));
                a(InstructionConstants.ATHROW);
                unreachable = true;
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
                a(InstructionConstants.I2L);
                pushRegWZ(R+rt);
                a(InstructionConstants.I2L);
                a(InstructionConstants.LMUL);
                a(InstructionConstants.DUP2);
                
                a(InstructionConstants.L2I);
                if(preSetReg(LO))
                    a(InstructionConstants.SWAP);
                setReg();
                
                pushConst(32);
                a(InstructionConstants.LUSHR);
                a(InstructionConstants.L2I);
                if(preSetReg(HI))
                    a(InstructionConstants.SWAP);
                setReg();
                
                break;
            case 25: // MULTU
                pushRegWZ(R+rs);
                a(InstructionConstants.I2L);
                pushConst(0xffffffffL);
                a(InstructionConstants.LAND);
                pushRegWZ(R+rt);
                a(InstructionConstants.I2L);
                pushConst(0xffffffffL);
                a(InstructionConstants.LAND);
                a(InstructionConstants.LMUL);
                a(InstructionConstants.DUP2);
                
                a(InstructionConstants.L2I);
                if(preSetReg(LO))
                    a(InstructionConstants.SWAP);
                setReg();
                
                pushConst(32);
                a(InstructionConstants.LUSHR);
                a(InstructionConstants.L2I);
                if(preSetReg(HI))
                    a(InstructionConstants.SWAP);
                setReg();
                
                break;
            case 26: // DIV
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                a(InstructionConstants.DUP2);
                
                a(InstructionConstants.IDIV);
                if(preSetReg(LO))
                    a(InstructionConstants.SWAP);
                setReg();
                
                a(InstructionConstants.IREM);
                if(preSetReg(HI))
                    a(InstructionConstants.SWAP);
                setReg();
                
                break;
            case 27: { // DIVU
                pushRegWZ(R+rt);
                a(InstructionConstants.DUP);
                setTmp();
                b1 = a(InstructionFactory.createBranchInstruction(IFEQ,null));
                
                pushRegWZ(R+rs);
                a(InstructionConstants.I2L);
                pushConst(0xffffffffL);
                a(InstructionConstants.LAND);
                a(InstructionConstants.DUP2);
                pushTmp();
                a(InstructionConstants.I2L);
                pushConst(0xffffffffL);
                
                a(InstructionConstants.LAND);
                a(InstructionConstants.DUP2_X2);
                a(InstructionConstants.LDIV);
                
                a(InstructionConstants.L2I);
                if(preSetReg(LO))
                    a(InstructionConstants.SWAP);
                setReg();
                
                a(InstructionConstants.LREM);
                a(InstructionConstants.L2I);
                if(preSetReg(HI))
                    a(InstructionConstants.SWAP);
                setReg();
                
                b1.setTarget(a(InstructionConstants.NOP));
                
                break;
            }
            case 32: // ADD
                throw new Exn("ADD (add with oveflow trap) not suported");
            case 33: // ADDU
                preSetReg(R+rd);
                if(rt != 0 && rs != 0) {
                    pushReg(R+rs);
                    pushReg(R+rt);
                    a(InstructionConstants.IADD);
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
                    a(InstructionConstants.ISUB);
                } else if(rt != 0) {
                    pushReg(R+rt);
                    a(InstructionConstants.INEG);
                } else {
                    pushRegZ(R+rs);
                }
                setReg();                
                break;
            case 36: // AND
                preSetReg(R+rd);
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                a(InstructionConstants.IAND);
                setReg();
                break;
            case 37: // OR
                preSetReg(R+rd);
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                a(InstructionConstants.IOR);
                setReg();
                break;
            case 38: // XOR
                preSetReg(R+rd);
                pushRegWZ(R+rs);
                pushRegWZ(R+rt);
                a(InstructionConstants.IXOR);
                setReg();
                break;
            case 39: // NOR
                preSetReg(R+rd);
                if(rs != 0 || rt != 0) {
                    if(rs != 0 && rt != 0) {
                        pushReg(R+rs);
                        pushReg(R+rt);
                        a(InstructionConstants.IOR);
                    } else if(rs != 0) {
                        pushReg(R+rs);
                    } else {
                        pushReg(R+rt);
                    }
                    a(InstructionConstants.ICONST_M1);
                    a(InstructionConstants.IXOR);
                } else {
                    pushConst(-1);
                }
                setReg();
                break;
            case 42: // SLT
                preSetReg(R+rd);
                if(rs != rt) {
                    pushRegZ(R+rs);
                    pushRegZ(R+rt);
                    b1 = a(InstructionFactory.createBranchInstruction(IF_ICMPLT,null));
                    a(InstructionConstants.ICONST_0);
                    b2 = a(InstructionFactory.createBranchInstruction(GOTO,null));
                    b1.setTarget(a(InstructionConstants.ICONST_1));
                    b2.setTarget(a(InstructionConstants.NOP));
                } else {
                    pushConst(0);
                }
                setReg();
                break;
            case 43: // SLTU
                preSetReg(R+rd);
                if(rs != rt) {
                    if(rs != 0) {
                        pushReg(R+rs);
                        a(InstructionConstants.I2L);
                        pushConst(0xffffffffL);
                        a(InstructionConstants.LAND);
                        pushReg(R+rt);
                        a(InstructionConstants.I2L);
                        pushConst(0xffffffffL);
                        a(InstructionConstants.LAND);
                        a(InstructionConstants.LCMP);
                        b1 = a(InstructionFactory.createBranchInstruction(IFLT,null));
                    } else {
                        pushReg(R+rt);
                        b1 = a(InstructionFactory.createBranchInstruction(IFNE,null));
                    }
                    a(InstructionConstants.ICONST_0);
                    b2 = a(InstructionFactory.createBranchInstruction(GOTO,null));
                    b1.setTarget(a(InstructionConstants.ICONST_1));
                    b2.setTarget(a(InstructionConstants.NOP));
                } else {
                    pushConst(0);
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
                b1 = a(InstructionFactory.createBranchInstruction(IFGE,null));
                emitInstruction(-1,nextInsn,-1);
                preSetReg(R+RA);
                pushConst(pc+8);
                setReg();
                branch(pc,pc+branchTarget*4+4);
                b1.setTarget(a(InstructionConstants.NOP));
                break;
            case 17: // BGEZAL
                if(pc == -1) throw new Exn("pc modifying insn in delay slot");
                b1 = null;
                if(rs != 0) { // r0 is always >= 0
                    pushRegWZ(R+rs);
                    b1 = a(InstructionFactory.createBranchInstruction(IFLT,null));
                }
                emitInstruction(-1,nextInsn,-1);
                preSetReg(R+RA);
                pushConst(pc+8);
                setReg();
                branch(pc,pc+branchTarget*4+4);
                if(b1 != null) b1.setTarget(a(InstructionConstants.NOP));
                if(b1 == null) unreachable = true;
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
            unreachable = true;
            break;
        }
        case 3: { // JAL
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            int target = (pc&0xf0000000)|(jumpTarget << 2);
            emitInstruction(-1,nextInsn,-1);
            preSetReg(R+RA);
            pushConst(pc+8);
            setReg();
            branch(pc, target);
            unreachable = true;
            break;
        }
        case 4: // BEQ
            if(pc == -1) throw new Exn("pc modifying insn in delay slot");
            if(rs == rt) {
                emitInstruction(-1,nextInsn,-1);
                branch(pc,pc+branchTarget*4+4);
                unreachable = true;
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
            preSetReg(R+rt);
            addiu(rs,signedImmediate);
            setReg();            
            break;
        case 10: // SLTI
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            pushConst(signedImmediate);
            b1 = a(InstructionFactory.createBranchInstruction(IF_ICMPLT,null));
            a(InstructionConstants.ICONST_0);
            b2 = a(InstructionFactory.createBranchInstruction(GOTO,null));
            b1.setTarget(a(InstructionConstants.ICONST_1));
            b2.setTarget(a(InstructionConstants.NOP));
            setReg();
            break;
        case 11: // SLTIU
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            a(InstructionConstants.I2L);
            pushConst(0xffffffffL);
            a(InstructionConstants.LAND);
            // Yes, this is correct, you have to sign extend the immediate then do an UNSIGNED comparison
            pushConst(signedImmediate&0xffffffffL);
            a(InstructionConstants.LCMP);
            
            b1 = a(InstructionFactory.createBranchInstruction(IFLT,null));
            a(InstructionConstants.ICONST_0);
            b2 = a(InstructionFactory.createBranchInstruction(GOTO,null));
            b1.setTarget(a(InstructionConstants.ICONST_1));
            b2.setTarget(a(InstructionConstants.NOP));
            
            setReg();
            break;            
        case 12: // ANDI
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            pushConst(unsignedImmediate);
            a(InstructionConstants.IAND);
            setReg();
            break;
        case 13: // ORI
            preSetReg(R+rt);
            if(rs != 0 && unsignedImmediate != 0) {
                pushReg(R+rs);
                pushConst(unsignedImmediate);
                a(InstructionConstants.IOR);
            } else if(rs != 0){
                pushReg(R+rs);
            } else {
                pushConst(unsignedImmediate);
            }
            setReg();
            break;
        case 14: // XORI
            preSetReg(R+rt);
            pushRegWZ(R+rs);
            pushConst(unsignedImmediate);
            a(InstructionConstants.IXOR);
            setReg();
            break;
        case 15: // LUI
            preSetReg(R+rt);
            pushConst(unsignedImmediate << 16);
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
                if(rt != 0)
                    pushReg(R+rt);
                else
                    pushConst(0);
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
                pushConst(0x800000);
                a(InstructionConstants.IAND);
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
                    a(d ? InstructionConstants.DADD : InstructionConstants.FADD);
                    setDouble(d);
                    break;
                case 1: // SUB.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    a(d ? InstructionConstants.DSUB : InstructionConstants.FSUB);
                    setDouble(d);
                    break;
                case 2: // MUL.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    a(d ? InstructionConstants.DMUL : InstructionConstants.FMUL);
                    setDouble(d);                    
                    break;
                case 3: // DIV.X
                    preSetDouble(F+fd,d);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    a(d ? InstructionConstants.DDIV : InstructionConstants.FDIV);
                    setDouble(d);                    
                    break;
                case 5: // ABS.X
                    preSetDouble(F+fd,d);
                    // NOTE: We can't use fneg/dneg here since they'll turn +0.0 into -0.0
                    
                    pushDouble(F+fs,d);
                    a(d ? InstructionConstants.DUP2 : InstructionConstants.DUP);
                    a(d ? InstructionConstants.DCONST_0 : InstructionConstants.FCONST_0);
                    a(d ? InstructionConstants.DCMPG : InstructionConstants.FCMPG);
                    
                    b1 = a(InstructionFactory.createBranchInstruction(IFGT,null));
                    a(d ? InstructionConstants.DCONST_0 : InstructionConstants.FCONST_0);
                    a(d ? InstructionConstants.DSUB : InstructionConstants.FSUB);
                    
                    b1.setTarget(setDouble(d));
                    
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
                    a(d ? InstructionConstants.DNEG : InstructionConstants.FNEG);
                    setDouble(d);
                    break;
                case 32: // CVT.S.X
                    preSetFloat(F+fd);
                    pushDouble(F+fs,d);
                    if(d) a(InstructionConstants.D2F);
                    setFloat();
                    break;
                case 33: // CVT.D.X
                    preSetDouble(F+fd);
                    pushDouble(F+fs,d);
                    if(!d) a(InstructionConstants.F2D);
                    setDouble();
                    break;
                case 36: { // CVT.W.D
                    int[] matches = new int[4];
                    for(int i=0;i<4;i++) matches[i] = i;
                    
                    TABLESWITCH ts = new  TABLESWITCH(matches,new InstructionHandle[4], null);
                    
                    preSetReg(F+fd);
                    pushDouble(F+fs,d);
                    pushReg(FCSR);
                    a(InstructionConstants.ICONST_3);
                    a(InstructionConstants.IAND);
                    a(ts);
                    
                    // Round towards plus infinity
                    ts.setTarget(2,a(InstructionConstants.NOP));
                    if(!d) a(InstructionConstants.F2D); // Ugh.. java.lang.Math doesn't have a float ceil/floor
                    a(fac.createInvoke("java.lang.Math","ceil",Type.DOUBLE,new Type[]{Type.DOUBLE},INVOKESTATIC));
                    if(!d) a(InstructionConstants.D2F);
                    b1 = a(InstructionFactory.createBranchInstruction(GOTO,null));
                    
                    // Round to nearest
                    ts.setTarget(0,d ? pushConst(0.5d) : pushConst(0.5f));
                    a(d ? InstructionConstants.DADD : InstructionConstants.FADD);
                    // fall through
                    
                    // Round towards minus infinity
                    ts.setTarget(3,a(InstructionConstants.NOP));
                    if(!d) a(InstructionConstants.F2D);
                    a(fac.createInvoke("java.lang.Math","floor",Type.DOUBLE,new Type[]{Type.DOUBLE},INVOKESTATIC));
                    if(!d) a(InstructionConstants.D2F);
                    
                    InstructionHandle h = a(d ? InstructionConstants.D2I : InstructionConstants.F2I);
                    setReg();
                    
                    ts.setTarget(1,h);
                    ts.setTarget(h);
                    b1.setTarget(h);
                                        
                    break;
                }
                case 50: // C.EQ.D
                case 60: // C.LT.D
                case 62: // C.LE.D
                    preSetReg(FCSR);
                    pushReg(FCSR);
                    pushConst(~0x800000);
                    a(InstructionConstants.IAND);
                    pushDouble(F+fs,d);
                    pushDouble(F+ft,d);
                    a(d ? InstructionConstants.DCMPG : InstructionConstants.FCMPG);
                    switch(subcode) {
                        case 50: b1 = a(InstructionFactory.createBranchInstruction(IFEQ,null)); break;
                        case 60: b1 = a(InstructionFactory.createBranchInstruction(IFLT,null)); break;
                        case 62: b1 = a(InstructionFactory.createBranchInstruction(IFLE,null)); break;
                        default: b1 = null;
                    }
                    // FIXME: We probably don't need to pushConst(0x00000)
                    pushConst(0x000000);
                    b2 = a(InstructionFactory.createBranchInstruction(GOTO,null));
                    b1.setTarget(pushConst(0x800000));
                    b2.setTarget(a(InstructionConstants.IOR));
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
                    a(InstructionConstants.I2F);
                    setFloat();
                    break;
                case 33: // CVT.D.W
                    preSetDouble(F+fd);
                    pushReg(F+fs);
                    a(InstructionConstants.I2D);
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
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.I2B);
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
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.I2S);
            setReg();            
            break; 
        }
        case 34: { // LWL;
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp(); // addr
            
            pushRegWZ(R+rt);
            pushConst(0x00ffffff);
            pushTmp();
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.IAND);
            
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IOR);
            
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
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            pushConst(0xff);
            a(InstructionConstants.IAND);
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
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            
            // chars are unsigend so this works
            a(InstructionConstants.I2C);
            setReg();
            break; 
        }
        case 38: { // LWR            
            preSetReg(R+rt);
            addiu(R+rs,signedImmediate);
            setTmp(); // addr
            
            pushRegWZ(R+rt);
            pushConst(0xffffff00);
            pushTmp();
            
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IAND);
            
            preMemRead();
            pushTmp();
            memRead(true);
            pushTmp();
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.IOR);
            
            
            setReg();
            break;
        }
        case 40: { // SB            
            addiu(R+rs,signedImmediate);
            setTmp(); // addr
            
            // FEATURE: DO the preMemRead(true) thing for the rest of the S* instructions
            preMemRead(true);
            pushTmp();
            memRead(true);
            
            pushConst(0xff000000);
            pushTmp();
            
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.IAND);
            
            if(rt != 0) {
                pushReg(R+rt);
                pushConst(0xff);
                a(InstructionConstants.IAND);
            } else {
                pushConst(0);
            }
            pushTmp();
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IOR);
            
            memWrite();
            
            break;
        }
        case 41: { // SH    
            preMemWrite1();
            
            addiu(R+rs,signedImmediate);
            
            a(InstructionConstants.DUP);
            setTmp(); // addr
            
            preMemWrite2(true);
            
            preMemRead();
            pushTmp();
            memRead(true);
            
            pushConst(0xffff);
            pushTmp();
            
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IAND);
            
            if(rt != 0) {
                pushReg(R+rt);
                pushConst(0xffff);
                a(InstructionConstants.IAND);
            } else {
                pushConst(0);
            }
            pushTmp();
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IOR);
            
            memWrite();
            
            break;            
        }
        case 42: { // SWL
            preMemWrite1();
            
            addiu(R+rs,signedImmediate);
            a(InstructionConstants.DUP);
            setTmp(); // addr

            preMemWrite2(true);
            
            preMemRead();
            pushTmp();
            memRead(true);
            
            pushConst(0xffffff00);
            pushTmp();
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IAND);
            
            pushRegWZ(R+rt);
            pushTmp();
            
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.IOR);
            
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
            preMemWrite1();
            
            addiu(R+rs,signedImmediate);
            a(InstructionConstants.DUP);
            setTmp(); // addr
            
            preMemWrite2(true);
            
            preMemRead();
            pushTmp();
            memRead(true);
            
            pushConst(0x00ffffff);
            pushTmp();
            
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.IAND);
            
            pushRegWZ(R+rt);
            pushTmp();
            
            a(InstructionConstants.ICONST_M1);
            a(InstructionConstants.IXOR);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.IAND);
            a(InstructionConstants.ICONST_3);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.ISHL);
            a(InstructionConstants.IOR);
                    
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
         * between the LL and SC if we every support threads.
         */
        case 56: // SWC0/SC
            preSetReg(R+rt);
            preMemWrite1();
            preMemWrite2(R+rs,signedImmediate);
            pushReg(R+rt);
            memWrite();
            pushConst(1);
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
        return false; 
    }
    
    // Helper functions for emitText
    
    private static final int R = 0;
    private static final int F = 32;
    private static final int HI = 64;
    private static final int LO = 65;
    private static final int FCSR = 66;
    private static final int REG_COUNT=67;
        
    private int[] regLocalMapping = new int[REG_COUNT];  
    private int[] regLocalReadCount = new int[REG_COUNT];
    private int[] regLocalWriteCount = new int[REG_COUNT];
    private int nextAvailLocal; 
    
    private int getLocalForReg(int reg) {
        if(regLocalMapping[reg] != 0) return regLocalMapping[reg];
        if(nextAvailLocal == 0) nextAvailLocal = onePage ? 3 : 4;
        regLocalMapping[reg] = nextAvailLocal++;
        return regLocalMapping[reg];
    }
    
    private void fixupRegs() {
        InstructionHandle prev = realStart;
        for(int i=0;i<REG_COUNT;i++) {
            if(regLocalMapping[i] == 0) continue; 
            
            prev = insnList.append(prev,InstructionConstants.ALOAD_0);
            prev = insnList.append(prev,fac.createFieldAccess(fullClassName,regField(i),Type.INT, GETFIELD));
            prev = insnList.append(prev,InstructionFactory.createStore(Type.INT,regLocalMapping[i]));
            
            if(regLocalWriteCount[i] > 0) {
                a(InstructionConstants.ALOAD_0);
                a(InstructionFactory.createLoad(Type.INT,regLocalMapping[i]));
                a(fac.createFieldAccess(fullClassName,regField(i),Type.INT, PUTFIELD));
            }
            
            regLocalMapping[i] = regLocalReadCount[i] = regLocalWriteCount[i] = 0;
        }
        nextAvailLocal = 0;
    }
    
    private void restoreChangedRegs() {
        for(int i=0;i<REG_COUNT;i++) {
            if(regLocalWriteCount[i] > 0) {
                a(InstructionConstants.ALOAD_0);
                a(InstructionFactory.createLoad(Type.INT,regLocalMapping[i]));
                a(fac.createFieldAccess(fullClassName,regField(i),Type.INT, PUTFIELD));                
            }
        }
    }
    
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
    
    private static String regField(int reg) {
        return regField[reg];
                                   
        /*String field;
        switch(reg) {
            case HI: field = "hi"; break;
            case LO: field = "lo"; break;
            case FCSR: field = "fcsr"; break;
            default:
                if(reg > R && reg < R+32) regFieldR[reg-R];
                else if(reg >= F && reg < F+32) return regFieldF[
                else throw new IllegalArgumentException(""+reg);
        }
        return field;*/
    }
    
    private boolean doLocal(int reg) {
        //return false;
        return reg == R+2 || reg == R+3 || reg == R+4 || reg == R+29;
    }
    
    private InstructionHandle pushRegWZ(int reg) {
        if(reg == R+0) {
            warn.println("Warning: Pushing r0!");
            new Exception().printStackTrace(warn);
        }
        return pushRegZ(reg);
    }
    
    private InstructionHandle pushRegZ(int reg) {
        if(reg == R+0) return pushConst(0);
        else return pushReg(reg);
    }
    
    
    private InstructionHandle pushReg(int reg) {
        InstructionHandle h;
        if(doLocal(reg)) {
            regLocalReadCount[reg]++;
            h = a(InstructionFactory.createLoad(Type.INT,getLocalForReg(reg)));
        } else {
            h = a(InstructionConstants.ALOAD_0);
            a(fac.createFieldAccess(fullClassName,regField(reg),Type.INT, GETFIELD));
        }
        return h;
    }
    
    private int preSetRegStackPos;
    private int[] preSetRegStack = new int[8];
    
    // This can push ONE or ZERO words to the stack. If it pushed one it returns true
    private boolean preSetReg(int reg) {
        regField(reg); // just to check for validity
        preSetRegStack[preSetRegStackPos] = reg;
        preSetRegStackPos++;
        if(doLocal(reg)) {
            return false;
        } else {
            a(InstructionConstants.ALOAD_0);
            return true;
        }
    }
    
    private InstructionHandle setReg() {
        if(preSetRegStackPos==0) throw new RuntimeException("didn't do preSetReg");
        preSetRegStackPos--;
        int reg = preSetRegStack[preSetRegStackPos];
        InstructionHandle h;
        if(doLocal(reg)) {
            h = a(InstructionFactory.createStore(Type.INT,getLocalForReg(reg)));
            regLocalWriteCount[reg]++;
        } else {
            h = a(fac.createFieldAccess(fullClassName,regField(reg),Type.INT, PUTFIELD));
        }
        return h;
    }
    
    private InstructionHandle preSetPC() { return a(InstructionConstants.ALOAD_0); }
    private InstructionHandle setPC() { return a(fac.createFieldAccess(fullClassName,"pc",Type.INT, PUTFIELD)); }
    
    //unused - private InstructionHandle pushFloat(int reg) throws CompilationException { return pushDouble(reg,false); }
    //unused - private InstructionHandle pushDouble(int reg) throws CompilationException { return pushDouble(reg,true); }
    private InstructionHandle pushDouble(int reg, boolean d) throws Exn {
        if(reg < F || reg >= F+32) throw new IllegalArgumentException(""+reg);
        InstructionHandle h;
        if(d) {
            if(reg == F+31) throw new Exn("Tried to use a double in f31");
            h = pushReg(reg+1);
            a(InstructionConstants.I2L);
            pushConst(32);
            a(InstructionConstants.LSHL);
            pushReg(reg);
            a(InstructionConstants.I2L);
            pushConst(0xffffffffL);
            a(InstructionConstants.LAND);
            a(InstructionConstants.LOR);
            //p("invokestatic java/lang/Double/longBitsToDouble(J)D");
            a(fac.createInvoke("java.lang.Double","longBitsToDouble",Type.DOUBLE,new Type[]{Type.LONG},INVOKESTATIC));
        } else {
            h = pushReg(reg);
            a(fac.createInvoke("java.lang.Float","intBitsToFloat",Type.FLOAT,new Type[]{Type.INT},INVOKESTATIC));
        }
        return h;
    }
    
    private void preSetFloat(int reg) { preSetDouble(reg,false); }
    private void preSetDouble(int reg) { preSetDouble(reg,true); }
    private void preSetDouble(int reg, boolean d) { preSetReg(reg); }
    
    private InstructionHandle setFloat() throws Exn { return setDouble(false); }
    private InstructionHandle setDouble() throws Exn { return setDouble(true); }
    private InstructionHandle setDouble(boolean d) throws Exn {
        int reg = preSetRegStack[preSetRegStackPos-1];
        if(reg < F || reg >= F+32) throw new IllegalArgumentException(""+reg);
        //p("invokestatic java/lang/Double/doubleToLongBits(D)J");
        InstructionHandle h;
        if(d) {
            if(reg == F+31) throw new Exn("Tried to use a double in f31");
            h = a(fac.createInvoke("java.lang.Double","doubleToLongBits",Type.LONG,new Type[]{Type.DOUBLE},INVOKESTATIC));
            a(InstructionConstants.DUP2);
            pushConst(32);
            a(InstructionConstants.LUSHR);
            a(InstructionConstants.L2I);
            if(preSetReg(reg+1))
                a(InstructionConstants.SWAP);
            setReg();
            a(InstructionConstants.L2I);
            setReg(); // preSetReg was already done for this by preSetDouble
        } else {
            h = a(fac.createInvoke("java.lang.Float","floatToRawIntBits",Type.INT,new Type[]{Type.FLOAT},INVOKESTATIC));
            setReg();   
        }
        return h;
    }
        
    private Hashtable intCache = new Hashtable();
    
    private InstructionHandle pushConst(int n) {
        if(n >= -1 && n <= 5) {
            switch(n) {
                case -1: return a(InstructionConstants.ICONST_M1);
                case 0: return a(InstructionConstants.ICONST_0);
                case 1: return a(InstructionConstants.ICONST_1);
                case 2: return a(InstructionConstants.ICONST_2);
                case 3: return a(InstructionConstants.ICONST_3);
                case 4: return a(InstructionConstants.ICONST_4);
                case 5: return a(InstructionConstants.ICONST_5);
                default: return null;
            }
        } else if(n >= -128 && n <= 127) {
            return a(new BIPUSH((byte) n));
        } else if(n >= -32768 && n <= 32767) {
            return a(new SIPUSH((short) n));
        } else {
            return a(new PUSH(cp,n));
        }
    }
    
    private InstructionHandle pushConst(long l) { return a(new PUSH(cp,l)); }
    private InstructionHandle pushConst(float f) { return a(new PUSH(cp,f)); }
    private InstructionHandle pushConst(double d) { return a(new PUSH(cp,d)); }
    
    private void pushTmp() { a(InstructionConstants.ILOAD_1); }
    private void setTmp() { a(InstructionConstants.ISTORE_1); }
    
    private void addiu(int reg, int offset) {
        if(reg != R+0 && offset != 0) {
            pushReg(reg);
            pushConst(offset);
            a(InstructionConstants.IADD);
        } else if(reg != R+0) {
            pushReg(reg);
        } else {
            pushConst(offset);
        }        
    }
    private int memWriteStage;
    private void preMemWrite1() {
        if(memWriteStage!=0) throw new Error("pending preMemWrite1/2");
        memWriteStage=1;
        if(onePage)
            a(InstructionConstants.ALOAD_2);
        else if(fastMem)
            a(InstructionFactory.createLoad(Type.OBJECT,3));
        else
            a(InstructionConstants.ALOAD_0);
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
            a(InstructionConstants.DUP);
            a(InstructionConstants.ALOAD_0);
            a(InstructionConstants.SWAP);
            a(fac.createInvoke(fullClassName,"nullPointerCheck",Type.VOID,new Type[]{Type.INT},INVOKEVIRTUAL));
        }
        
        if(onePage) {
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IUSHR);
        } else if(fastMem) {
            if(!addrInTmp)
                a(InstructionConstants.DUP_X1);
            pushConst(pageShift);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.AALOAD);
            if(addrInTmp)
                pushTmp();
            else
                a(InstructionConstants.SWAP);
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IUSHR);
            pushConst((pageSize>>2)-1);
            a(InstructionConstants.IAND);            
        }
    }
    
    // pops an address and value off the stack, sets *addr to value
    private void memWrite() {
        if(memWriteStage!=2) throw new Error("didn't do preMemWrite1 or preMemWrite2");
        memWriteStage=0;
                
        if(onePage) {
            a(InstructionConstants.IASTORE);
        } else if(fastMem) {
            a(InstructionConstants.IASTORE);
        } else {
            a(fac.createInvoke(fullClassName,"unsafeMemWrite",Type.VOID,new Type[]{Type.INT,Type.INT},INVOKEVIRTUAL));
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
            a(InstructionConstants.ALOAD_2);
        else if(fastMem)
            a(InstructionFactory.createLoad(Type.OBJECT,preWrite ? 3 : 2));
        else
            a(InstructionConstants.ALOAD_0);
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
            a(InstructionConstants.DUP);
            a(InstructionConstants.ALOAD_0);
            a(InstructionConstants.SWAP);
            a(fac.createInvoke(fullClassName,"nullPointerCheck",Type.VOID,new Type[]{Type.INT},INVOKEVIRTUAL));
        }
        
        if(onePage) {
            // p(target + "= page[(" + addr + ")>>>2];");
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IUSHR);
            if(preMemReadDoPreWrite)
                a(InstructionConstants.DUP2);
            a(InstructionConstants.IALOAD);
        } else if(fastMem) {
            //p(target  + " = readPages[("+addr+")>>>"+pageShift+"][(("+addr+")>>>2)&"+toHex((pageSize>>2)-1)+"];");
            
            if(!addrInTmp)
                a(InstructionConstants.DUP_X1);
            pushConst(pageShift);
            a(InstructionConstants.IUSHR);
            a(InstructionConstants.AALOAD);
            if(addrInTmp)
                pushTmp();
            else
                a(InstructionConstants.SWAP);
            a(InstructionConstants.ICONST_2);
            a(InstructionConstants.IUSHR);
            pushConst((pageSize>>2)-1);
            a(InstructionConstants.IAND);
            if(preMemReadDoPreWrite)
                a(InstructionConstants.DUP2);
            a(InstructionConstants.IALOAD);
            
        } else {
            if(preMemReadDoPreWrite)
                a(InstructionConstants.DUP2);
            a(fac.createInvoke(fullClassName,"unsafeMemWrite",Type.INT,new Type[]{Type.INT},INVOKEVIRTUAL));
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

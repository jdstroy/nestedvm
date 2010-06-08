// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package tests;

import org.ibex.nestedvm.Runtime;
import java.io.*;

public class MSPack {
    private String[] fileNames;
    private int[] lengths;
    private byte[][] data;
        
    public static class MSPackException extends IOException { public MSPackException(String s) { super(s); } }
    
    public MSPack(InputStream cabIS) throws IOException {
        byte[] cab = InputStreamToByteArray.convert(cabIS);        
        try {
            //Interpreter vm = new Interpreter("mspack.mips");
        		Runtime vm;
        		try {
        			 vm = (Runtime) Class.forName("tests.MSPackHelper").newInstance();
        		} catch(Exception e) {
        			throw new MSPackException("couldn't instansiate MSPackHelper");
        		}
            int cabAddr = vm.sbrk(cab.length);
            if(cabAddr < 0) throw new MSPackException("sbrk failed");
        
            vm.copyout(cab,cabAddr,cab.length);
        
            vm.setUserInfo(0,cabAddr);
            vm.setUserInfo(1,cab.length);
        
            int status = vm.run(new String[]{ "mspack.mips"} );
            if(status != 0) throw new MSPackException("mspack.mips failed (" + status + ")");
            
            /*static struct {
                char *filename;
                char *data;
                int length;
            } output_table[MAX_MEMBERS+1]; */

            int filesTable = vm.getUserInfo(2);
            int count=0;
            while(vm.memRead(filesTable+count*12) != 0) count++;
            
            fileNames = new String[count];
            data = new byte[count][];
            lengths = new int[count];
            
            for(int i=0,addr=filesTable;i<count;i++,addr+=12) {
                int length = vm.memRead(addr+8);
                data[i] = new byte[length];
                lengths[i] = length;
                fileNames[i] = vm.cstring(vm.memRead(addr));
                System.out.println("" + fileNames[i]);
                vm.copyin(vm.memRead(addr+4),data[i],length);
            }
        } catch(Runtime.ExecutionException e) {
            e.printStackTrace();
            throw new MSPackException("mspack.mips crashed");
        }
    }
    
    public String[] getFileNames() { return fileNames; }
    public int[] getLengths() { return lengths; }
    public InputStream getInputStream(int index) { return new ByteArrayInputStream(data[index]); }
    public InputStream getInputStream(String fileName) {
        for(int i=0;i<fileNames.length;i++) {
            if(fileName.equalsIgnoreCase(fileNames[i])) return getInputStream(i);
        }
        return null;
    }
    
    public static void main(String[] args) throws IOException {
        MSPack pack = new MSPack(new FileInputStream(args[0]));
        String[] files = pack.getFileNames();
        for(int i=0;i<files.length;i++)
            System.out.println(i + ": " + files[i] + ": " + pack.getLengths()[i]);
        System.out.println("Writing " + files[files.length-1]);
        InputStream is = pack.getInputStream(files.length-1);
        OutputStream os = new FileOutputStream(files[files.length-1]);
        int n;
        byte[] buf = new byte[4096];
        while((n = is.read(buf)) != -1) os.write(buf,0,n);
        os.close();
        is.close();
    }
    private static class InputStreamToByteArray {

        /** scratch space for isToByteArray() */
        private static byte[] workspace = new byte[16 * 1024];
        /** Trivial method to completely read an InputStream */
        public static synchronized byte[] convert(InputStream is) throws IOException {
            int pos = 0;
            while (true) {
                int numread = is.read(workspace, pos, workspace.length - pos);
                if (numread == -1) break;
                else if (pos + numread < workspace.length) pos += numread;
                else {
                    pos += numread;
                    byte[] temp = new byte[workspace.length * 2];
                    System.arraycopy(workspace, 0, temp, 0, workspace.length);
                    workspace = temp;
                }
            }
            byte[] ret = new byte[pos];
            System.arraycopy(workspace, 0, ret, 0, pos);
            return ret;
        }
    }
}


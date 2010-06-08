// Copyright 2000-2005 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package tests;

import org.ibex.nestedvm.Runtime;
import org.ibex.nestedvm.Interpreter;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.awt.event.*;
import java.io.*;

public class FreeTypeDemo {
    private JFrame frame;
    private static final int OURWIDTH=640;
    private static final int OURHEIGHT=256;
    private static final int BASELINE=160;
    private byte[] render = new byte[OURWIDTH*OURHEIGHT];
    private int size = 72;
    private StringBuffer sb = new StringBuffer();
    private View view;
    private Image image;
    
    private Runnable renderThread;
    private String theText;
    private boolean renderNeeded;
    
    private String name;
    private Runtime rt;
    
    int renderAddr;
    int stringAddr;
    int stringSize;
    
    public static void main(String[] argv) throws Exception {
        new FreeTypeDemo(argv);
    }
    
    public FreeTypeDemo(String[] argv) throws Exception {
        if(argv.length >= 2 && argv[1].startsWith("int")) {
            name = "Interpreter";
            rt = new Interpreter("build/FreeTypeDemoHelper.mips");
        } else {
            rt = (Runtime) Class.forName("tests.FreeTypeDemoHelper").newInstance();
            name = "Compiler";
        }
        
        rt.start(new String[]{ "freetype.mips"});
        if(rt.execute()) throw new Error("freetype.mips exited");

        byte[] font = InputStreamToByteArray.convert(new FileInputStream(argv[0]));
        int fontAddr = rt.malloc(font.length);
        if(fontAddr == 0) throw new Error("malloc() failed");
        rt.copyout(font,fontAddr,font.length);
        
        rt.setUserInfo(0,fontAddr);
        rt.setUserInfo(1,font.length);

        renderAddr = rt.malloc(OURWIDTH*OURHEIGHT);
        if(renderAddr == 0) throw new Error("malloc() failed");
        
        if(rt.execute()) throw new Error("freetype.mips exited (" + rt.getUserInfo(1) +")");

        createImage();
        
        frame = new JFrame("FreeTypeDemo - " + name);
        frame.setSize(OURWIDTH,OURHEIGHT);
        view = new View();
        frame.getContentPane().add(view,BorderLayout.CENTER);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.show();
        view.requestFocus();
        renderThread = new Runnable() {
            public void run() {
                try {
                    for(;;) {
                         synchronized(this) { while(!renderNeeded) wait(); renderNeeded = false; }
                        renderText(theText==null ? "" : theText);
                    }
                } catch(Exception e) { throw new Error(e); }
            }
        };
        new Thread(renderThread).start();
        keyPress('\n');
    }
   
    private static ColorModel cmodel = new DirectColorModel(8, 0xff,0xff,0xff);
    private void createImage() {
        for(int i=0;i<OURHEIGHT;i++)
            for(int j=0;j<OURWIDTH;j++)
                render[i*OURWIDTH+j] = (byte)((~(render[i*OURWIDTH+j]&0xff))&0xff);
        image = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(OURWIDTH, OURHEIGHT, cmodel, render, 0, OURWIDTH));
        MediaTracker mediatracker = new MediaTracker(new Canvas());
        mediatracker.addImage(image, 1);
        try { mediatracker.waitForAll(); } catch (InterruptedException e) { }
        mediatracker.removeImage(image);
    }
    private void renderText(String s) {
        try {
            byte[] b = (s+"\0").getBytes("UTF-16BE");
            if(stringSize < b.length) {
                System.err.println("reallocing the string space");
                if(stringAddr != 0) rt.free(stringAddr);
                stringAddr = rt.malloc(b.length*2);
                if(stringAddr == 0) throw new Error("malloc failed");
                stringSize = b.length*2;
            }
            rt.copyout(b,stringAddr,b.length);
            long start = System.currentTimeMillis();
            if(rt.call("render",new int[]{stringAddr,size,renderAddr,OURWIDTH,OURHEIGHT,BASELINE})==0)
                throw new Error("render() failed");
            System.out.println(name + ": Render of: " + s + " took " + (System.currentTimeMillis()-start) + " ms");
            rt.copyin(renderAddr,render,render.length);
            createImage();
            view.repaint();
        } catch(Exception e) {
            throw new Error(e);
        }
    }
    
    private void keyPress(char c) {
        if(c == '\n' || c == '\r') {
            sb.setLength(0);
            theText = "Press any key";
        } else if(c == '+' || c == '-') {
            size += (c=='+'?1:-1) * 8;
            System.out.println("New size: " + size);
        } else {
            sb.append(c);
            theText = sb.toString();
        }
        synchronized(renderThread) { renderNeeded = true; renderThread.notify(); }
    }
                
    public class View extends JComponent {
        public void paintComponent(Graphics g) {
            g.drawImage(image,0,0,OURWIDTH,OURHEIGHT,0,0,OURWIDTH,OURHEIGHT,null);
        }
        public View() {
            addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    keyPress(e.getKeyChar());
                }
            });
            setPreferredSize(new Dimension(OURWIDTH,OURHEIGHT));
        }
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


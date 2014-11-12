// Copyright 2014 the Contributors, as shown in the revision logs.
// Licensed under the Apache License 2.0 ("the License").
// You may not use this file except in compliance with the License.

package org.ibex.nestedvm.util;

import org.ibex.nestedvm.Runtime;
import org.ibex.nestedvm.UnixRuntime;

import java.io.InputStream;
import java.io.OutputStream;

public class UnixRuntimeJavaBridge {
    public static boolean setStdin(UnixRuntime runtime, InputStream in) {
        int fd = runtime.addFD(new Runtime.InputOutputStreamFD(in));
        return runtime.sys_dup2(fd, 0) == 0;
    }

    public static boolean setStdout(UnixRuntime runtime, OutputStream out) {
        int fd = runtime.addFD(new Runtime.InputOutputStreamFD(out));
        return runtime.sys_dup2(fd, 1) == 0;
    }

    public static boolean setStderr(UnixRuntime runtime, OutputStream out) {
        int fd = runtime.addFD(new Runtime.InputOutputStreamFD(out));
        return runtime.sys_dup2(fd, 2) == 0;
    }
}

#include <string.h>
#include <sys/stat.h>
#include <sys/dirent.h>
#include <sys/types.h>
#include <utime.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include <signal.h>

int _syscall_set_errno(struct _reent *ptr, int err) {
    ptr->_errno = -err;
    return -1;
}

extern int _stat_r(struct _reent *, const char *, struct stat *);
int _access_r(struct _reent *ptr, const char *pathname, int mode) {
    struct stat statbuf;
    if(_stat_r(ptr,pathname,&statbuf) < 0) return -1;
    return 0;
}

/* NestedVM doesn't, and probably never will, support this security related stuff */
uid_t getuid() { return 0; }
gid_t getgid() { return 0; }
uid_t geteuid() { return 0; }
gid_t getegid() { return 0; }
int getgroups(int gidsetlen, gid_t *gidset) {
    if(gidsetlen) *gidset = 0;
    return 1;
}
mode_t umask(mode_t new) { return 0022; }
int _chmod_r(struct _reent *ptr, const char *f, mode_t mode) { return 0; }
int _fchmod_r(struct _reent *ptr, int fd, mode_t mode) { return 0; }
int _chown_r(struct _reent *ptr, const char *f, uid_t uid, gid_t gid) { return 0; }
int _fchown_r(struct _reent *ptr, int fd, uid_t uid, gid_t gid) { return 0; }

#define REENT_WRAPPER0R(f,rt) \
    extern rt _##f##_r(struct _reent *ptr); \
    rt f() { return _##f##_r(_REENT); }
#define REENT_WRAPPER0(f) REENT_WRAPPER0R(f,int)

#define REENT_WRAPPER1R(f,rt,t1) \
    extern rt _##f##_r(struct _reent *ptr, t1 a); \
    rt f(t1 a) { return _##f##_r(_REENT,a); }
#define REENT_WRAPPER1(f,t1) REENT_WRAPPER1R(f,int,t1)

#define REENT_WRAPPER2R(f,rt,t1,t2) \
    extern rt _##f##_r(struct _reent *ptr, t1 a, t2 b); \
    rt f(t1 a, t2 b) { return _##f##_r(_REENT,a,b); }
#define REENT_WRAPPER2(f,t1,t2) REENT_WRAPPER2R(f,int,t1,t2)

#define REENT_WRAPPER3R(f,rt,t1,t2,t3) \
    extern rt _##f##_r(struct _reent *ptr, t1 a, t2 b, t3 c); \
    rt f(t1 a, t2 b, t3 c) { return _##f##_r(_REENT,a,b,c); }
#define REENT_WRAPPER3(f,t1,t2,t3) REENT_WRAPPER3R(f,int,t1,t2,t3)

#define REENT_WRAPPER4R(f,rt,t1,t2,t3,t4) \
extern rt _##f##_r(struct _reent *ptr, t1 a, t2 b, t3 c, t4 d); \
rt f(t1 a, t2 b, t3 c, t4 d) { return _##f##_r(_REENT,a,b,c,d); }
#define REENT_WRAPPER4(f,t1,t2,t3,t4) REENT_WRAPPER4R(f,int,t1,t2,t3,t4)

REENT_WRAPPER2(mkdir,const char *,mode_t)
REENT_WRAPPER2(access,const char *,int)
REENT_WRAPPER1(rmdir,const char *)
REENT_WRAPPER1R(sysconf,long,int)
REENT_WRAPPER1(chdir,const char*)
REENT_WRAPPER2(utime,const char *,const struct utimbuf *)
REENT_WRAPPER1(pipe,int *)
REENT_WRAPPER2(dup2,int,int)
REENT_WRAPPER3(waitpid,pid_t,int *,int)
REENT_WRAPPER2R(getcwd,char *,char *,size_t)
REENT_WRAPPER2R(_getcwd,char *,char *,size_t)
REENT_WRAPPER2(symlink,const char *,const char *)
REENT_WRAPPER3(readlink,const char *, char *,int)
REENT_WRAPPER3(chown,const char *,uid_t,gid_t)
REENT_WRAPPER3(fchown,int,uid_t,gid_t)
REENT_WRAPPER3(lchown,const char *,uid_t,gid_t)
REENT_WRAPPER2(chmod,const char *,mode_t)
REENT_WRAPPER2(fchmod,int,mode_t)
REENT_WRAPPER2(lstat,const char *,struct stat *)
REENT_WRAPPER4(getdents,int, char *, size_t,long *)
REENT_WRAPPER1(dup,int)
REENT_WRAPPER2R(pathconf,long,const char *,int)
REENT_WRAPPER0(vfork)
REENT_WRAPPER1(chroot,const char *)
REENT_WRAPPER3(mknod,const char *,mode_t,dev_t)
REENT_WRAPPER2(ftruncate,int,off_t)
REENT_WRAPPER1(usleep,unsigned int)
REENT_WRAPPER2(mkfifo,const char *, mode_t)
REENT_WRAPPER2(_open_socket,const char *,int)
REENT_WRAPPER1(_listen_socket,int)
REENT_WRAPPER1(_accept,int)

extern int __execve_r(struct _reent *ptr, const char *path, char *const argv[], char *const envp[]);
int _execve(const char *path, char *const argv[], char *const envp[]) {
    return __execve_r(_REENT,path,argv,envp);
}

char *_getcwd_r(struct _reent *ptr, char *buf, size_t size) {
    if(buf != NULL) {
        buf = __getcwd_r(ptr,buf,size);
        return (long)buf == -1 ? NULL : buf;
    }
    
    size = 256;
    for(;;) {
        buf = malloc(size);
        char *ret = __getcwd_r(ptr,buf,size);
        if((long)ret != -1) return ret;
        free(buf);
        size *= 2;
        if(ptr->_errno != ERANGE) return NULL;
    }
}

pid_t _wait_r(struct _reent *ptr, int *status) {
    return _waitpid_r(ptr,-1,status,0);
}

long _pathconf_r(struct _reent *ptr,const char *path, int name) {
    switch(name) {
        default:
            fprintf(stderr,"WARNING: pathconf: Unknown \"name\": %d\n",name);
            ptr->_errno = EINVAL;
            return -1;
    }
}

void sync() {
    /* do nothing*/
}

int fsync(int fd) {
    /* do nothing */
    return 0;
}

char *ttyname(int fd) {
    return isatty(fd) ? "/dev/console" : NULL;
}

int sigaction(int sig, const struct sigaction *act, struct sigaction *oact) {
    _sig_func_ptr old;
    _sig_func_ptr new;
    if(act) {
        if(act->sa_flags || act->sa_mask != ~((sigset_t)0)) { errno = EINVAL; return -1; }
        old = signal(sig,act->sa_handler);
    } else if(oact) {
        old = signal(sig,SIG_DFL);
        signal(sig,old);
    }
    if(oact) {
        oact->sa_handler = old;
        oact->sa_mask = 0;
        oact->sa_mask = ~((sigset_t)0);
    }
    return 0;
}

int sigfillset(sigset_t *set) {
    *set = ~((sigset_t)0);
    return 0;
}

int sigemptyset(sigset_t *set) {
    *set = (sigset_t) 0;
    return 0;
}

DIR *opendir(const char *path) {
    struct stat sb;
    int fd;
    DIR *dir;
    
    fd = open(path,O_RDONLY);
    if(fd < 0) return NULL;
    
    if(fstat(fd,&sb) < 0 || !S_ISDIR(sb.st_mode)) {
        close(fd);
        errno = ENOTDIR;
        return NULL;
    }
    
    dir = malloc(sizeof(*dir));
    if(dir == NULL) {
        close(fd);
        errno = ENOMEM;
        return NULL;
    }
    dir->dd_fd = fd;
    dir->dd_buf = malloc(sizeof(struct dirent));
    dir->dd_size = sizeof(struct dirent);
    if(dir->dd_buf == NULL) {
        close(fd);
        free(dir);
        return NULL;
    }
    dir->dd_loc = 0;
    dir->dd_len = 0;
    return dir;
}

struct dirent *readdir(DIR *dir) {
    struct dirent *dp;
    errno = 0;
    if(dir->dd_loc == 0 || dir->dd_loc == dir->dd_len) {
        dir->dd_len = getdents(dir->dd_fd,dir->dd_buf,dir->dd_size,NULL);
        dir->dd_loc = 0;
        if(dir->dd_len <= 0) { dir->dd_len = 0; return NULL; }
    }
    dp = (struct dirent*) (dir->dd_buf + dir->dd_loc);
    if(dp->d_reclen == 0 || dp->d_reclen > dir->dd_len - dir->dd_loc) return NULL;
    dir->dd_loc += dp->d_reclen;
    return dp;
}

int closedir(DIR *dir) {
    int fd = dir->dd_fd;
    free(dir->dd_buf);
    free(dir);
    return close(fd);
}

/*
 * Other People's Code 
 */

/* FreeBSD's dirname/basename */

/* FIXME: Put these in a header */

/*
 * Copyright (c) 1997 Todd C. Miller <Todd.Miller@courtesan.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL
 * THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

char *
dirname(path)
const char *path;
{
    static char bname[MAXPATHLEN];
    register const char *endp;
    
    /* Empty or NULL string gets treated as "." */
    if (path == NULL || *path == '\0') {
        (void)strcpy(bname, ".");
        return(bname);
    }
    
    /* Strip trailing slashes */
    endp = path + strlen(path) - 1;
    while (endp > path && *endp == '/')
        endp--;
    
    /* Find the start of the dir */
    while (endp > path && *endp != '/')
        endp--;
    
    /* Either the dir is "/" or there are no slashes */
    if (endp == path) {
        (void)strcpy(bname, *endp == '/' ? "/" : ".");
        return(bname);
    } else {
        do {
            endp--;
        } while (endp > path && *endp == '/');
    }
    
    if (endp - path + 2 > sizeof(bname)) {
        errno = ENAMETOOLONG;
        return(NULL);
    }
    (void)strncpy(bname, path, endp - path + 1);
    bname[endp - path + 1] = '\0';
    return(bname);
}

char *
basename(path)
const char *path;
{
    static char bname[MAXPATHLEN];
    register const char *endp, *startp;
    
    /* Empty or NULL string gets treated as "." */
    if (path == NULL || *path == '\0') {
        (void)strcpy(bname, ".");
        return(bname);
    }
    
    /* Strip trailing slashes */
    endp = path + strlen(path) - 1;
    while (endp > path && *endp == '/')
        endp--;
    
    /* All slashes becomes "/" */
    if (endp == path && *endp == '/') {
        (void)strcpy(bname, "/");
        return(bname);
    }
    
    /* Find the start of the base */
    startp = endp;
    while (startp > path && *(startp - 1) != '/')
        startp--;
    
    if (endp - startp + 2 > sizeof(bname)) {
        errno = ENAMETOOLONG;
        return(NULL);
    }
    (void)strncpy(bname, startp, endp - startp + 1);
    bname[endp - startp + 1] = '\0';
    return(bname);
}


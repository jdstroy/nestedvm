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
#include <sys/sysctl.h>
#include <sys/utsname.h>
#include <nestedvm/sockets.h>
#include <paths.h>

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

#define REENT_WRAPPER5R(f,rt,t1,t2,t3,t4,t5) \
extern rt _##f##_r(struct _reent *ptr, t1 a, t2 b, t3 c, t4 d, t5 e); \
rt f(t1 a, t2 b, t3 c, t4 d, t5 e) { return _##f##_r(_REENT,a,b,c,d,e); }
#define REENT_WRAPPER5(f,t1,t2,t3,t4,t5) REENT_WRAPPER5R(f,int,t1,t2,t3,t4,t5)

#define REENT_WRAPPER6R(f,rt,t1,t2,t3,t4,t5,t6) \
extern rt _##f##_r(struct _reent *ptr, t1 a, t2 b, t3 c, t4 d, t5 e, t6 f); \
rt f(t1 a, t2 b, t3 c, t4 d, t5 e, t6 f) { return _##f##_r(_REENT,a,b,c,d,e,f); }
#define REENT_WRAPPER6(f,t1,t2,t3,t4,t5,t6) REENT_WRAPPER6R(f,int,t1,t2,t3,t4,t5,t6)

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
REENT_WRAPPER3(klogctl,int,char*,int)
REENT_WRAPPER2R(realpath,char *,const char *,char *)
REENT_WRAPPER6(_sysctl,int *,int, void *, size_t*, void *, size_t)
REENT_WRAPPER6(sysctl,int *, int, void *, size_t*, void *, size_t)
REENT_WRAPPER2(getpriority,int,int)
REENT_WRAPPER3(setpriority,int,int,int)
REENT_WRAPPER3(connect,int,const struct sockaddr *,socklen_t)
REENT_WRAPPER3(socket,int,int,int)
REENT_WRAPPER3(_resolve_hostname,const char *,char*,size_t*)
REENT_WRAPPER3(accept,int,struct sockaddr *,socklen_t*)
REENT_WRAPPER5(getsockopt,int,int,int,void*,socklen_t*)
REENT_WRAPPER5(setsockopt,int,int,int,const void*,socklen_t)
REENT_WRAPPER3(bind,int,const struct sockaddr *,socklen_t)
REENT_WRAPPER2(listen,int,int)
REENT_WRAPPER2(shutdown,int,int)

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

int _sysctl_r(struct _reent *ptr, int *name, int namelen, void *oldp, size_t *oldlen, void *newp, size_t newlen) {
    if(name[0] != CTL_USER) return _sysctl(name,namelen,oldp,oldlen,newp,newlen);
    if(newp != NULL) { ptr->_errno = EPERM; return -1; }
    if(namelen != 2) { ptr->_errno = EINVAL; return -1; }
    
    switch(name[1]) {
        default:
            fprintf(stderr,"WARNING: sysctl: Unknown name: %d\n",name[1]);
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
 * Networking/Socket stuff
 */

/* This should really be part of the newlib _reent structure */
int h_errno;

char *inet_ntoa(struct in_addr in) {
    static char buf[18];
    const unsigned char *p = (void*) &in;
    snprintf(buf,sizeof(buf),"%u.%u.%u.%u",p[0],p[1],p[2],p[3]);
    return buf;
}

struct servent *getservbyname(const char *name,const char *proto) {
    return NULL;
}

static const char *h_errlist[] = { "No Error","Unknown host", "Host name lookup failure","Unknown server error","No address associated with name" };

const char *hstrerror(int err) {
    if(err < 0 || err > 4) return "Unknown Error";
    return h_errlist[err];
}

void herror(const char *string) {
    fprintf(stderr,"%s: %s\n",string,hstrerror(h_errno));
}

extern int _resolve_hostname(const char *, char *buf, size_t *size);

struct hostent *gethostbyname(const char *hostname) {
#define MAX_ADDRS 256
    static struct hostent hostent;
    static char saved_hostname[128];
    static char *addr_list[MAX_ADDRS+1];
    static char addr_list_buf[MAX_ADDRS*sizeof(struct in_addr)];
    static char *aliases[1];
    
    unsigned char buf[MAX_ADDRS*sizeof(struct in_addr)];
    size_t size = sizeof(buf);
    int err,i,n=0;
    
    err = _resolve_hostname(hostname,buf,&size);
    if(err != 0) { h_errno = err; return NULL; }
    
    memcpy(addr_list_buf,buf,size);
    for(i=0;i<size;i += sizeof(struct in_addr)) addr_list[n++] = &addr_list_buf[i];
    addr_list[n] = NULL;
    strncpy(saved_hostname,hostname,sizeof(saved_hostname));
    aliases[0] = NULL;
    
    hostent.h_name = saved_hostname;
    hostent.h_aliases = aliases;
    hostent.h_addrtype = AF_INET;
    hostent.h_length = sizeof(struct in_addr);
    hostent.h_addr_list = addr_list;
    
    return &hostent;
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

/* FreeBSD's uname */
int
uname(name)
struct utsname *name;
{
	int mib[2], rval;
	size_t len;
	char *p;
	int oerrno;
    
	rval = 0;
    
	mib[0] = CTL_KERN;
	mib[1] = KERN_OSTYPE;
	len = sizeof(name->sysname);
	oerrno = errno;
	if (sysctl(mib, 2, &name->sysname, &len, NULL, 0) == -1) {
		if(errno == ENOMEM)
			errno = oerrno;
		else
			rval = -1;
	}
	name->sysname[sizeof(name->sysname) - 1] = '\0';
    
	mib[0] = CTL_KERN;
	mib[1] = KERN_HOSTNAME;
	len = sizeof(name->nodename);
	oerrno = errno;
	if (sysctl(mib, 2, &name->nodename, &len, NULL, 0) == -1) {
		if(errno == ENOMEM)
			errno = oerrno;
		else
			rval = -1;
	}
	name->nodename[sizeof(name->nodename) - 1] = '\0';
    
	mib[0] = CTL_KERN;
	mib[1] = KERN_OSRELEASE;
	len = sizeof(name->release);
	oerrno = errno;
	if (sysctl(mib, 2, &name->release, &len, NULL, 0) == -1) {
		if(errno == ENOMEM)
			errno = oerrno;
		else
			rval = -1;
	}
	name->release[sizeof(name->release) - 1] = '\0';
    
	/* The version may have newlines in it, turn them into spaces. */
	mib[0] = CTL_KERN;
	mib[1] = KERN_VERSION;
	len = sizeof(name->version);
	oerrno = errno;
	if (sysctl(mib, 2, &name->version, &len, NULL, 0) == -1) {
		if (errno == ENOMEM)
			errno = oerrno;
		else
			rval = -1;
	}
	name->version[sizeof(name->version) - 1] = '\0';
	for (p = name->version; len--; ++p) {
		if (*p == '\n' || *p == '\t') {
			if (len > 1)
				*p = ' ';
			else
				*p = '\0';
		}
	}
    
	mib[0] = CTL_HW;
	mib[1] = HW_MACHINE;
	len = sizeof(name->machine);
	oerrno = errno;
	if (sysctl(mib, 2, &name->machine, &len, NULL, 0) == -1) {
		if (errno == ENOMEM)
			errno = oerrno;
		else
			rval = -1;
	}
	name->machine[sizeof(name->machine) - 1] = '\0';
	return (rval);
}

/* FreeBSD's daemon() - modified for nestedvm */
int
daemon(nochdir, noclose)
int nochdir, noclose;
{
	int fd;
    
	switch (fork()) {
        case -1:
            return (-1);
        case 0:
            break;
        default:
            _exit(0);
	}
    
	/*if (setsid() == -1)
		return (-1);*/
    
	if (!nochdir)
		(void)chdir("/");
    
	if (!noclose && (fd = open(_PATH_DEVNULL, O_RDWR, 0)) != -1) {
		(void)dup2(fd, STDIN_FILENO);
		(void)dup2(fd, STDOUT_FILENO);
		(void)dup2(fd, STDERR_FILENO);
		if (fd > 2)
			(void)close(fd);
	}
	return (0);
}

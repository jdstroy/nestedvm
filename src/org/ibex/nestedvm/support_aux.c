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
#include <paths.h>
#include <ctype.h>
#include <pwd.h>
#include <grp.h>
#include <stdarg.h>

#include <nestedvm/socket.h>

int _syscall_set_errno(struct _reent *ptr, int err) {
    ptr->_errno = -err;
    return -1;
}

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
REENT_WRAPPER1R(usleep,unsigned int,unsigned int)
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
REENT_WRAPPER3(_resolve_ip,int,char*,size_t)
REENT_WRAPPER3(accept,int,struct sockaddr *,socklen_t*)
REENT_WRAPPER5(getsockopt,int,int,int,void*,socklen_t*)
REENT_WRAPPER5(setsockopt,int,int,int,const void*,socklen_t)
REENT_WRAPPER3(bind,int,const struct sockaddr *,socklen_t)
REENT_WRAPPER2(listen,int,int)
REENT_WRAPPER2(shutdown,int,int)
REENT_WRAPPER6(sendto,int,const void*,size_t,int,const struct sockaddr*,socklen_t)
REENT_WRAPPER6(recvfrom,int,void*,size_t,int,struct sockaddr*,socklen_t*)
REENT_WRAPPER5(select,int,fd_set*,fd_set*,fd_set*,struct timeval*)
REENT_WRAPPER4(send,int,const void*,size_t,int)
REENT_WRAPPER4(recv,int,void*,size_t,int)
REENT_WRAPPER2(getgroups,int,gid_t*)
REENT_WRAPPER3(getsockname,int,struct sockaddr*,int*)
REENT_WRAPPER3(getpeername,int,struct sockaddr*,int*)
REENT_WRAPPER1(setuid,uid_t)
REENT_WRAPPER1(seteuid,uid_t)
REENT_WRAPPER1(setgid,gid_t)
REENT_WRAPPER1(setegid,gid_t)
REENT_WRAPPER2(setgroups,int,const gid_t *)
REENT_WRAPPER0R(setsid,pid_t)
REENT_WRAPPER1(fsync,int)

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

int sync() {
    /* do nothing*/
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

extern int _resolve_ip(int addr, char *buf, size_t size);

struct hostent *gethostbyaddr(const char *addr, int len, int type) {
    static struct hostent hostent;
    static char name[128];
    static char *aliases[1];
    static char *addr_list[1];
    static char addr_list_buf[4];
    int err,i;
    
    if(type != AF_INET || len != 4) return NULL;
    memcpy(&i,addr,4);
    memcpy(addr_list_buf,addr,4);
    err = _resolve_ip(i,name,sizeof(name));
    if(err != 0) { h_errno = err; return NULL; }
    
    hostent.h_name = name;
    hostent.h_aliases = aliases;
    aliases[0] = NULL;
    hostent.h_addrtype = AF_INET;
    hostent.h_length = sizeof(struct in_addr);
    hostent.h_addr_list = addr_list;
    addr_list[0] = addr_list_buf;
    
    return &hostent;
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

static struct passwd pw_passwd;
static struct group gr_group;
static FILE *passwd_fp;
static FILE *group_fp;
static char pw_name[1024];
static char pw_password[1024];
static char pw_gecos[1024];
static char pw_dir[1024];
static char pw_shell[1024];
static char gr_name[1024];
static char gr_passwd[1024];
static char *gr_mem[1];

static int gr_parse_body(const char *buf) {
    if(sscanf(buf,"%[^:]:%[^:]:%hu",gr_name,gr_passwd,&gr_group.gr_gid) < 3) return -1;
    gr_group.gr_name = gr_name;
    gr_group.gr_passwd = gr_passwd;
    gr_group.gr_mem = gr_mem;
    gr_mem[0] = NULL;
    return 0;
}

static int pw_parse_body(const char *buf) {
    int pos;
    if(sscanf(buf,"%[^:]:%[^:]:%d:%d:%[^:]:%[^:]:%s\n",pw_name,pw_password,&pw_passwd.pw_uid,&pw_passwd.pw_gid,pw_gecos,pw_dir,pw_shell) < 7) return -1;
    pw_passwd.pw_name = pw_name;
    pw_passwd.pw_passwd = pw_password;
    pw_passwd.pw_gecos = pw_gecos;
    pw_passwd.pw_dir = pw_dir;
    pw_passwd.pw_shell = pw_shell;
    pw_passwd.pw_comment = "";
    return 0;
}

struct group *getgrnam(const char *name) {
    FILE *fp;
    char buf[1024];
    
    if((fp=fopen("/etc/group","r"))==NULL) return NULL;
    while(fgets(buf,sizeof(buf),fp)) {
        if(buf[0] == '#') continue;
        if(gr_parse_body(buf) < 0) {
            fclose(fp);
            return NULL;
        }
        if(strcmp(name,gr_name)==0) {
            fclose(fp);
            return &gr_group;
        }
    }
    fclose(fp);
    return NULL;
}

struct group *getgrgid(gid_t gid) {
    FILE *fp;
    char buf[1024];
    
    if((fp=fopen("/etc/group","r"))==NULL) return NULL;
    while(fgets(buf,sizeof(buf),fp)) {
        if(buf[0] == '#') continue;
        if(gr_parse_body(buf) < 0) {
            fclose(fp);
            return NULL;
        }
        if(gid == gr_group.gr_gid) {
            fclose(fp);
            return &gr_group;
        }
    }
    fclose(fp);
    return NULL;
}
    
struct group *getgrent() {
    char buf[1024];
    if(group_fp == NULL) return NULL;
    if(fgets(buf,sizeof(buf),group_fp) == NULL) return NULL;
    if(buf[0] == '#') return getgrent();
    if(gr_parse_body(buf) < 0) return NULL;
    return &gr_group;
}

void setgrent() { 
    if(group_fp != NULL) fclose(group_fp);
    group_fp = fopen("/etc/group","r");
}

void endgrent() {
    if(group_fp != NULL) fclose(group_fp);
    group_fp = NULL;
}

struct passwd *getpwnam(const char *name) {
    FILE *fp;
    char buf[1024];
    
    if((fp=fopen("/etc/passwd","r"))==NULL) return NULL;
    while(fgets(buf,sizeof(buf),fp)) {
        if(buf[0] == '#') continue;
        if(pw_parse_body(buf) < 0) {
            fclose(fp);
            return NULL;
        }
        if(strcmp(name,pw_name)==0) {
            fclose(fp);
            return &pw_passwd;
        }
    }
    fclose(fp);
    return NULL;
}

struct passwd *getpwuid(uid_t uid) {
    FILE *fp;
    char buf[1024];
    
    if((fp=fopen("/etc/passwd","r"))==NULL) return NULL;
    while(fgets(buf,sizeof(buf),fp)) {
        if(buf[0] == '#') continue;
        if(pw_parse_body(buf) < 0) {
            fclose(fp);
            return NULL;
        }
        if(uid == pw_passwd.pw_uid) {
            fclose(fp);
            return &pw_passwd;
        }
    }
    fclose(fp);
    return NULL;
}

struct passwd *getpwent() {
    char buf[1024];
    if(passwd_fp == NULL) return NULL;
    if(fgets(buf,sizeof(buf),passwd_fp) == NULL) return NULL;
    if(buf[0] == '#') return getpwent();
    if(pw_parse_body(buf) < 0) return NULL;
    return &pw_passwd;
}

void setpwent() { 
    if(passwd_fp != NULL) fclose(passwd_fp);
    passwd_fp = fopen("/etc/group","r");
}

void endpwent() {
    if(passwd_fp != NULL) fclose(passwd_fp);
    passwd_fp = NULL;
}

char *getpass(const char *prompt) {
    static char buf[1024];
    int len = 0;
    fputs(prompt,stderr);
    fflush(stdout);
    if(fgets(buf,sizeof(buf),stdin)!=NULL) {
        len = strlen(buf);
        if(buf[len-1] == '\n') len--;
    }
    fputc('\n',stderr);
    buf[len] = '\0';
    return buf;
}

/* Argh... newlib's asprintf is totally broken... */
int vasprintf(char **ret, const char *fmt, va_list ap) {
    int n;
    char *p;
    *ret = malloc(128); /* just guess for now */
    if(!*ret) return -1;
    n = vsnprintf(*ret,128,fmt,ap);
    if(n < 128) {
        return n;
    } else {
        p = realloc(*ret,n+1);
        if(!p) { free(*ret); return -1; }
        return vsprintf(*ret = p,fmt,ap);
    }
}

// FIXME: This needs to be in a header
char *getlogin() {
    return getenv("USER");
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

/* FreeBSD's gethostname */
int
gethostname(name, namelen)
char *name;
int namelen;
{
    int mib[2];
    size_t size;
    
    mib[0] = CTL_KERN;
    mib[1] = KERN_HOSTNAME;
    size = namelen;
    if (sysctl(mib, 2, name, &size, NULL, 0) == -1)
        return (-1);
    return (0);
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
    
	if (setsid() == -1)
		return (-1);
    
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

/* FreeBSD's inet_addr/inet_aton */

/* 
* Check whether "cp" is a valid ASCII representation
 * of an Internet address and convert to a binary address.
 * Returns 1 if the address is valid, 0 if not.
 * This replaces inet_addr, the return value from which
 * cannot distinguish between failure and a local broadcast address.
 */
int
inet_aton(cp, addr)
register const char *cp;
struct in_addr *addr;
{
	u_long parts[4];
	in_addr_t val;
	char *c;
	char *endptr;
	int gotend, n;
    
	c = (char *)cp;
	n = 0;
	/*
	 * Run through the string, grabbing numbers until
	 * the end of the string, or some error
	 */
	gotend = 0;
	while (!gotend) {
		errno = 0;
		val = strtoul(c, &endptr, 0);
        
		if (errno == ERANGE)	/* Fail completely if it overflowed. */
			return (0);
		
		/* 
            * If the whole string is invalid, endptr will equal
		 * c.. this way we can make sure someone hasn't
		 * gone '.12' or something which would get past
		 * the next check.
		 */
		if (endptr == c)
			return (0);
		parts[n] = val;
		c = endptr;
        
		/* Check the next character past the previous number's end */
		switch (*c) {
            case '.' :
                /* Make sure we only do 3 dots .. */
                if (n == 3)	/* Whoops. Quit. */
                    return (0);
                n++;
                c++;
                break;
                
            case '\0':
                gotend = 1;
                break;
                
            default:
                if (isspace((unsigned char)*c)) {
                    gotend = 1;
                    break;
                } else
                    return (0);	/* Invalid character, so fail */
		}
        
	}
    
	/*
	 * Concoct the address according to
	 * the number of parts specified.
	 */
    
	switch (n) {
        case 0:				/* a -- 32 bits */
            /*
             * Nothing is necessary here.  Overflow checking was
             * already done in strtoul().
             */
            break;
        case 1:				/* a.b -- 8.24 bits */
            if (val > 0xffffff || parts[0] > 0xff)
                return (0);
            val |= parts[0] << 24;
            break;
            
        case 2:				/* a.b.c -- 8.8.16 bits */
            if (val > 0xffff || parts[0] > 0xff || parts[1] > 0xff)
                return (0);
            val |= (parts[0] << 24) | (parts[1] << 16);
                break;
                
            case 3:				/* a.b.c.d -- 8.8.8.8 bits */
                if (val > 0xff || parts[0] > 0xff || parts[1] > 0xff ||
                    parts[2] > 0xff)
                    return (0);
                val |= (parts[0] << 24) | (parts[1] << 16) | (parts[2] << 8);
                    break;
	}
    
	if (addr != NULL)
		addr->s_addr = htonl(val);
	return (1);
}

/*
 * ASCII internet address interpretation routine.
 * The value returned is in network order.
 */
in_addr_t		/* XXX should be struct in_addr :( */
inet_addr(cp)
register const char *cp;
{
	struct in_addr val;
    
	if (inet_aton(cp, &val))
		return (val.s_addr);
	return (INADDR_NONE);
}

int
getgrouplist(uname, agroup, groups, grpcnt)
const char *uname;
gid_t agroup;
register gid_t *groups;
int *grpcnt;
{
	register struct group *grp;
	register int i, ngroups;
	int ret, maxgroups;
    
	ret = 0;
	ngroups = 0;
	maxgroups = *grpcnt;
	/*
	 * When installing primary group, duplicate it;
	 * the first element of groups is the effective gid
	 * and will be overwritten when a setgid file is executed.
	 */
	groups[ngroups++] = agroup;
	if (maxgroups > 1)
		groups[ngroups++] = agroup;
	/*
	 * Scan the group file to find additional groups.
	 */
	setgrent();
	while ((grp = getgrent())) {
		for (i = 0; i < ngroups; i++) {
			if (grp->gr_gid == groups[i])
				goto skip;
		}
		for (i = 0; grp->gr_mem[i]; i++) {
			if (!strcmp(grp->gr_mem[i], uname)) {
				if (ngroups >= maxgroups) {
					ret = -1;
					break;
				}
				groups[ngroups++] = grp->gr_gid;
				break;
			}
		}
skip: ;
	}
	endgrent();
	*grpcnt = ngroups;
	return (ret);
}

int
initgroups(uname, agroup)
const char *uname;
gid_t agroup;
{
    gid_t groups[32], ngroups;
    
    ngroups = 32;
    getgrouplist(uname, agroup, groups, &ngroups);
    return (setgroups(ngroups, groups));
}

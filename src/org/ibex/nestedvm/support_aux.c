#include <sys/stat.h>
#include <sys/dirent.h>
#include <sys/types.h>
#include <utime.h>
#include <errno.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>

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
REENT_WRAPPER2(chmod,const char *,mode_t)
REENT_WRAPPER2(fchmod,int,mode_t)
REENT_WRAPPER2(lstat,const char *,struct stat *)
REENT_WRAPPER4(getdents,int, char *, size_t,long *)
REENT_WRAPPER1(dup,int)

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

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
REENT_WRAPPER2(symlink,const char *,const char *)
REENT_WRAPPER3(readlink,const char *, char *,int)
REENT_WRAPPER3(chown,const char *,uid_t,gid_t)
REENT_WRAPPER3(fchown,int,uid_t,gid_t)
REENT_WRAPPER2(chmod,const char *,mode_t)
REENT_WRAPPER2(fchmod,int,mode_t)
REENT_WRAPPER2(lstat,const char *,struct stat *)

extern int __execve_r(struct _reent *ptr, const char *path, char *const argv[], char *const envp[]);
int _execve(const char *path, char *const argv[], char *const envp[]) {
    return __execve_r(_REENT,path,argv,envp);
}

static int read_fully(int fd, void *buf, size_t size) {
    int n;
    while(size) {
        n = read(fd,buf,size);
        if(n <= 0) return -1;
        size -= n;
        buf += n;
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
    //dir->dd_pos = 0;
    return dir;
}

int readdir_r(DIR *dir,struct dirent *entry, struct dirent **result) {
    struct {
        int inode;
        int name_len;
    } h;
    if(dir->dd_fd < 0) return -1;
again:
    if(read_fully(dir->dd_fd,&h,sizeof(h)) < 0) goto fail;
    if(h.name_len < 0 || h.name_len >= sizeof(entry->d_name)-1) goto fail;
    
    entry->d_ino = h.inode;
    if(read_fully(dir->dd_fd,entry->d_name,h.name_len) < 0) goto fail;
    
    entry->d_name[h.name_len] = '\0';
    //dir->dd_pos += h.name_len + 8;
    
    if(result) *result = entry;
    return 0;
fail:
    if(result) *result = NULL; 
    return -1;    
}

// FIXME: Rewrite all this dirent stuff in terms of a getdirentries syscall
static struct dirent static_dir_ent;

struct dirent *readdir(DIR *dir) { return readdir_r(dir,&static_dir_ent,NULL) == 0 ? &static_dir_ent : NULL; }

int closedir(DIR *dir) {
    close(dir->dd_fd);
    free(dir);
    return 0;
}

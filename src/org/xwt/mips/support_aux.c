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

extern int _stat_r(struct _reent *ptr, const char *path, struct stat *sb);
int _lstat_r(struct _reent *ptr, const char *path, struct stat *sb) {
    return _stat_r(ptr,path,sb);
}

uid_t getuid() { return 0; }
gid_t getgid() { return 0; }
uid_t geteuid() { return 0; }
gid_t getegid() { return 0; }
int getgroups(int gidsetlen, gid_t *gidset) {
    if(gidsetlen) *gidset = 0;
    return 1;
}
mode_t umask(mode_t new) { return 0022; }

static int syscall_nosys(struct _reent *ptr) {
    ptr->_errno = ENOSYS;
    return -1;
}

int _access_r(struct _reent *ptr, const char *pathname, int mode) {
    struct stat statbuf;
    if(_stat_r(ptr,pathname,&statbuf) < 0) return -1;
    return 0;
}

/* FIXME: These should be in newlib */
int access(const char *pathname, int mode) { return _access_r(_REENT,pathname,mode); }
extern int _rmdir_r(struct _reent *ptr, const char *pathname);
int rmdir(const char *pathname) { return _rmdir_r(_REENT,pathname); }
extern long _sysconf_r(struct _reent *ptr, int n);
long sysconf(int n) { return _sysconf_r(_REENT,n); }

#define SYSCALL_NOSYS_R(name) int _##name##_r(struct _reent *ptr) { return syscall_nosys(ptr); }

SYSCALL_NOSYS_R(link)
SYSCALL_NOSYS_R(symlink)
SYSCALL_NOSYS_R(readlink)
SYSCALL_NOSYS_R(chown)
SYSCALL_NOSYS_R(fchown)
SYSCALL_NOSYS_R(chmod)
SYSCALL_NOSYS_R(fchmod)

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
    dir->dd_pos = 0;
    return dir;
}

static int readdir_r(DIR *dir,struct dirent *entry, struct dirent **result) {
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
    dir->dd_pos += h.name_len + 8;
    
    if(result) *result = entry;
    return 0;
fail:
    if(result) *result = NULL; 
    return -1;    
}

struct dirent *readdir(DIR *dir) { return readdir_r(dir,&dir->ent,NULL) == 0 ? &dir->ent : NULL; }

int closedir(DIR *dir) {
    close(dir->dd_fd);
    free(dir);
    return 0;
}

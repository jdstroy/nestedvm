#include "syscalls.h"

#define zero $0
#define v0 $2
#define v1 $3
#define a0 $4
#define a1 $5
#define a2 $6
#define a3 $7
#define t0 $8
#define t1 $9
#define ra $31

/* We intentionally don't take advantage of delay slots because
   the compiler removes them anyway */

.set noreorder;

#define SYSCALL(name) SYSCALL2(name,SYS_##name)
#define SYSCALL2(name,number)  \
    .section .text.name,"ax",@progbits; \
    .align 2;       \
    .globl name;    \
    .ent name;      \
name:               \
    li v0, number;  \
    syscall;        \
    j ra;           \
    nop;            \
    .end name;

#define SYSCALL_R(name) SYSCALL_R2(_##name##_r,SYS_##name)
#define SYSCALL_R2(name,number) \
    .section .text.name,"ax",@progbits; \
    .align 2;                  \
    .globl name;               \
    .ent name;                 \
name:                          \
    li v0, number;             \
    move t0, a0;               \
    move a0, a1;               \
    move a1, a2;               \
    move a2, a3;               \
    syscall;                   \
    addu t1,v0,255;            \
    sltu t1,t1,255;            \
    bne t1,zero,$L##name##_errno;\
    nop;                       \
    j ra;                      \
    nop;                       \
$L##name##_errno:              \
    move a0, t0;               \
    move a1, v0;               \
    j _syscall_set_errno;      \
    nop;                       \
    .end name;


    .align   2
    .globl   _call_helper
    .ent     _call_helper
_call_helper:
    subu $sp,$sp,32
    
    /* addr */
    move $2,$4
    
    /* args 1-4 */
    move $4,$5 
    move $5,$6
    move $6,$7
    move $7,$16
    
    /* args 5 and 6 */
    sw $17,16($sp)
    sw $18,20($sp)
    
    /* call the func */
    jal $31,$2
    nop
    
    move $3,$2
    li $2,SYS_pause
    syscall
    
    /* shouldn't get here */
    li $2,SYS_exit
    li $3,1
    syscall
    
    .end _call_helper

SYSCALL2(_exit,SYS_exit)
SYSCALL2(_pause,SYS_pause)
SYSCALL_R(open)
SYSCALL_R(close)
SYSCALL_R(read)
SYSCALL_R(write)
SYSCALL_R(sbrk)
SYSCALL_R(fstat)
SYSCALL_R(lseek)
SYSCALL_R(kill)
SYSCALL_R(getpid)
SYSCALL2(_call_java,SYS_calljava)
SYSCALL_R(stat)
SYSCALL_R(gettimeofday)
SYSCALL(sleep)
SYSCALL_R(times)
SYSCALL_R(mkdir)
SYSCALL(getpagesize)
SYSCALL_R(unlink)
SYSCALL_R(utime)
SYSCALL_R(chdir)
SYSCALL_R(pipe)
SYSCALL_R(dup2)
SYSCALL_R(fork)
SYSCALL_R(waitpid)
SYSCALL_R2(__getcwd_r,SYS_getcwd)
SYSCALL_R2(__execve_r,SYS_exec)
SYSCALL_R(fcntl)
SYSCALL_R(rmdir)
SYSCALL_R(sysconf)
SYSCALL_R(readlink)
SYSCALL_R(lstat)
SYSCALL_R(symlink)
SYSCALL_R(link)
SYSCALL_R(getdents)
SYSCALL(memcpy)
SYSCALL(memset)
SYSCALL_R(dup)
SYSCALL_R(vfork)
SYSCALL_R(chroot)
SYSCALL_R(mknod)
SYSCALL_R(lchown)
SYSCALL_R(ftruncate)
SYSCALL_R(usleep)
SYSCALL(getppid)
SYSCALL_R(mkfifo)
SYSCALL_R2(__open_socket_r,SYS_opensocket)
SYSCALL_R2(__listen_socket_r,SYS_listensocket)
SYSCALL_R2(__accept_r,SYS_accept)

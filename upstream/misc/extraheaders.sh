
#!/bin/sh -e

mkdir -p arpa netinet sys nestedvm net

for f in arpa/inet.h netdb.h netinet/in.h sys/socket.h net/if.h; do
    test -f $f || echo "#include <nestedvm/socket.h>" > $f
done

for f in getopt.h; do
    test -f $f || echo "#include <unistd.h>" > $f
done

cat <<__EOF__ > sys/ioctl.h

__EOF__

cat <<__EOF__ > sys/klog.h
#ifndef __SYS_KLOG_H
#define __SYS_KLOG_H

int klogctl(int cmd, char *buf, int buflen);

#endif
__EOF__

cat <<__EOF__ > sys/utsname.h
#ifndef __SYS_UTSNAME_H
#define __SYS_UTSNAME_H

#define SYS_NMLN 32

struct utsname {
    char sysname[SYS_NMLN];
    char nodename[SYS_NMLN];
    char release[SYS_NMLN];
    char version[SYS_NMLN];
    char machine[SYS_NMLN];
};

int uname(struct utsname *);

#endif
__EOF__

cat <<__EOF__ > sys/sysctl.h
/*
 * Copyright (c) 1989, 1993
 *      The Regents of the University of California.  All rights reserved.
 *
 * This code is derived from software contributed to Berkeley by
 * Mike Karels at Berkeley Software Design, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *      This product includes software developed by the University of
 *      California, Berkeley and its contributors.
 * 4. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
            * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 *      @(#)sysctl.h    8.1 (Berkeley) 6/2/93
 * $FreeBSD: src/sys/sys/sysctl.h,v 1.81.2.10 2003/05/01 22:48:09 trhodes Exp $
 */

#ifndef __SYS_SYSCTL_H
#define __SYS_SYSCTL_H

#define CTL_MAXNAME     12


/*
 * Top-level identifiers
 */
#define	CTL_UNSPEC	0		/* unused */
#define	CTL_KERN	1		/* "high kernel": proc, limits */
#define	CTL_VM		2		/* virtual memory */
#define	CTL_VFS		3		/* file system, mount type is next */
#define	CTL_NET		4		/* network, see socket.h */
#define	CTL_DEBUG	5		/* debugging parameters */
#define	CTL_HW		6		/* generic cpu/io */
#define	CTL_MACHDEP	7		/* machine dependent */
#define	CTL_USER	8		/* user-level */
#define	CTL_P1003_1B	9		/* POSIX 1003.1B */
#define	CTL_MAXID	10		/* number of valid top-level ids */

#define CTL_NAMES { \
	{ 0, 0 }, \
{ "kern", CTLTYPE_NODE }, \
{ "vm", CTLTYPE_NODE }, \
{ "vfs", CTLTYPE_NODE }, \
{ "net", CTLTYPE_NODE }, \
{ "debug", CTLTYPE_NODE }, \
{ "hw", CTLTYPE_NODE }, \
{ "machdep", CTLTYPE_NODE }, \
{ "user", CTLTYPE_NODE }, \
{ "p1003_1b", CTLTYPE_NODE }, \
}

/*
 * CTL_KERN identifiers
 */
#define	KERN_OSTYPE	 	 1	/* string: system version */
#define	KERN_OSRELEASE	 	 2	/* string: system release */
#define	KERN_OSREV	 	 3	/* int: system revision */
#define	KERN_VERSION	 	 4	/* string: compile time info */
#define	KERN_MAXVNODES	 	 5	/* int: max vnodes */
#define	KERN_MAXPROC	 	 6	/* int: max processes */
#define	KERN_MAXFILES	 	 7	/* int: max open files */
#define	KERN_ARGMAX	 	 8	/* int: max arguments to exec */
#define	KERN_SECURELVL	 	 9	/* int: system security level */
#define	KERN_HOSTNAME		10	/* string: hostname */
#define	KERN_HOSTID		11	/* int: host identifier */
#define	KERN_CLOCKRATE		12	/* struct: struct clockrate */
#define	KERN_VNODE		13	/* struct: vnode structures */
#define	KERN_PROC		14	/* struct: process entries */
#define	KERN_FILE		15	/* struct: file entries */
#define	KERN_PROF		16	/* node: kernel profiling info */
#define	KERN_POSIX1		17	/* int: POSIX.1 version */
#define	KERN_NGROUPS		18	/* int: # of supplemental group ids */
#define	KERN_JOB_CONTROL	19	/* int: is job control available */
#define	KERN_SAVED_IDS		20	/* int: saved set-user/group-ID */
#define	KERN_BOOTTIME		21	/* struct: time kernel was booted */
#define KERN_NISDOMAINNAME	22	/* string: YP domain name */
#define KERN_UPDATEINTERVAL	23	/* int: update process sleep time */
#define KERN_OSRELDATE		24	/* int: OS release date */
#define KERN_NTP_PLL		25	/* node: NTP PLL control */
#define	KERN_BOOTFILE		26	/* string: name of booted kernel */
#define	KERN_MAXFILESPERPROC	27	/* int: max open files per proc */
#define	KERN_MAXPROCPERUID 	28	/* int: max processes per uid */
#define KERN_DUMPDEV		29	/* dev_t: device to dump on */
#define	KERN_IPC		30	/* node: anything related to IPC */
#define	KERN_DUMMY		31	/* unused */
#define	KERN_PS_STRINGS		32	/* int: address of PS_STRINGS */
#define	KERN_USRSTACK		33	/* int: address of USRSTACK */
#define	KERN_LOGSIGEXIT		34	/* int: do we log sigexit procs? */
#define KERN_MAXID		35      /* number of valid kern ids */

#define CTL_KERN_NAMES { \
	{ 0, 0 }, \
	{ "ostype", CTLTYPE_STRING }, \
	{ "osrelease", CTLTYPE_STRING }, \
	{ "osrevision", CTLTYPE_INT }, \
	{ "version", CTLTYPE_STRING }, \
	{ "maxvnodes", CTLTYPE_INT }, \
	{ "maxproc", CTLTYPE_INT }, \
	{ "maxfiles", CTLTYPE_INT }, \
	{ "argmax", CTLTYPE_INT }, \
	{ "securelevel", CTLTYPE_INT }, \
	{ "hostname", CTLTYPE_STRING }, \
	{ "hostid", CTLTYPE_UINT }, \
	{ "clockrate", CTLTYPE_STRUCT }, \
	{ "vnode", CTLTYPE_STRUCT }, \
	{ "proc", CTLTYPE_STRUCT }, \
	{ "file", CTLTYPE_STRUCT }, \
	{ "profiling", CTLTYPE_NODE }, \
	{ "posix1version", CTLTYPE_INT }, \
	{ "ngroups", CTLTYPE_INT }, \
	{ "job_control", CTLTYPE_INT }, \
	{ "saved_ids", CTLTYPE_INT }, \
	{ "boottime", CTLTYPE_STRUCT }, \
	{ "nisdomainname", CTLTYPE_STRING }, \
	{ "update", CTLTYPE_INT }, \
	{ "osreldate", CTLTYPE_INT }, \
	{ "ntp_pll", CTLTYPE_NODE }, \
	{ "bootfile", CTLTYPE_STRING }, \
	{ "maxfilesperproc", CTLTYPE_INT }, \
	{ "maxprocperuid", CTLTYPE_INT }, \
	{ "dumpdev", CTLTYPE_STRUCT }, /* we lie; don't print as int */ \
	{ "ipc", CTLTYPE_NODE }, \
	{ "dummy", CTLTYPE_INT }, \
	{ "ps_strings", CTLTYPE_INT }, \
	{ "usrstack", CTLTYPE_INT }, \
	{ "logsigexit", CTLTYPE_INT }, \
}

/*
 * CTL_VFS identifiers
 */
#define CTL_VFS_NAMES { \
	{ "vfsconf", CTLTYPE_STRUCT }, \
}

/*
 * KERN_PROC subtypes
 */
#define KERN_PROC_ALL		0	/* everything */
#define	KERN_PROC_PID		1	/* by process id */
#define	KERN_PROC_PGRP		2	/* by process group id */
#define	KERN_PROC_SESSION	3	/* by session of pid */
#define	KERN_PROC_TTY		4	/* by controlling tty */
#define	KERN_PROC_UID		5	/* by effective uid */
#define	KERN_PROC_RUID		6	/* by real uid */
#define	KERN_PROC_ARGS		7	/* get/set arguments/proctitle */

/*
 * KERN_IPC identifiers
 */
#define KIPC_MAXSOCKBUF		1	/* int: max size of a socket buffer */
#define	KIPC_SOCKBUF_WASTE	2	/* int: wastage factor in sockbuf */
#define	KIPC_SOMAXCONN		3	/* int: max length of connection q */
#define	KIPC_MAX_LINKHDR	4	/* int: max length of link header */
#define	KIPC_MAX_PROTOHDR	5	/* int: max length of network header */
#define	KIPC_MAX_HDR		6	/* int: max total length of headers */
#define	KIPC_MAX_DATALEN	7	/* int: max length of data? */
#define	KIPC_MBSTAT		8	/* struct: mbuf usage statistics */
#define	KIPC_NMBCLUSTERS	9	/* int: maximum mbuf clusters */

/*
 * CTL_HW identifiers
 */
#define	HW_MACHINE	 1		/* string: machine class */
#define	HW_MODEL	 2		/* string: specific machine model */
#define	HW_NCPU		 3		/* int: number of cpus */
#define	HW_BYTEORDER	 4		/* int: machine byte order */
#define	HW_PHYSMEM	 5		/* int: total memory */
#define	HW_USERMEM	 6		/* int: non-kernel memory */
#define	HW_PAGESIZE	 7		/* int: software page size */
#define	HW_DISKNAMES	 8		/* strings: disk drive names */
#define	HW_DISKSTATS	 9		/* struct: diskstats[] */
#define HW_FLOATINGPT	10		/* int: has HW floating point? */
#define HW_MACHINE_ARCH	11		/* string: machine architecture */
#define	HW_MAXID	12		/* number of valid hw ids */

#define CTL_HW_NAMES { \
	{ 0, 0 }, \
	{ "machine", CTLTYPE_STRING }, \
	{ "model", CTLTYPE_STRING }, \
	{ "ncpu", CTLTYPE_INT }, \
	{ "byteorder", CTLTYPE_INT }, \
	{ "physmem", CTLTYPE_UINT }, \
	{ "usermem", CTLTYPE_UINT }, \
	{ "pagesize", CTLTYPE_INT }, \
	{ "disknames", CTLTYPE_STRUCT }, \
	{ "diskstats", CTLTYPE_STRUCT }, \
	{ "floatingpoint", CTLTYPE_INT }, \
}

/*
 * CTL_USER definitions
 */
#define	USER_CS_PATH		 1	/* string: _CS_PATH */
#define	USER_BC_BASE_MAX	 2	/* int: BC_BASE_MAX */
#define	USER_BC_DIM_MAX		 3	/* int: BC_DIM_MAX */
#define	USER_BC_SCALE_MAX	 4	/* int: BC_SCALE_MAX */
#define	USER_BC_STRING_MAX	 5	/* int: BC_STRING_MAX */
#define	USER_COLL_WEIGHTS_MAX	 6	/* int: COLL_WEIGHTS_MAX */
#define	USER_EXPR_NEST_MAX	 7	/* int: EXPR_NEST_MAX */
#define	USER_LINE_MAX		 8	/* int: LINE_MAX */
#define	USER_RE_DUP_MAX		 9	/* int: RE_DUP_MAX */
#define	USER_POSIX2_VERSION	10	/* int: POSIX2_VERSION */
#define	USER_POSIX2_C_BIND	11	/* int: POSIX2_C_BIND */
#define	USER_POSIX2_C_DEV	12	/* int: POSIX2_C_DEV */
#define	USER_POSIX2_CHAR_TERM	13	/* int: POSIX2_CHAR_TERM */
#define	USER_POSIX2_FORT_DEV	14	/* int: POSIX2_FORT_DEV */
#define	USER_POSIX2_FORT_RUN	15	/* int: POSIX2_FORT_RUN */
#define	USER_POSIX2_LOCALEDEF	16	/* int: POSIX2_LOCALEDEF */
#define	USER_POSIX2_SW_DEV	17	/* int: POSIX2_SW_DEV */
#define	USER_POSIX2_UPE		18	/* int: POSIX2_UPE */
#define	USER_STREAM_MAX		19	/* int: POSIX2_STREAM_MAX */
#define	USER_TZNAME_MAX		20	/* int: POSIX2_TZNAME_MAX */
#define	USER_MAXID		21	/* number of valid user ids */

#define	CTL_USER_NAMES { \
	{ 0, 0 }, \
	{ "cs_path", CTLTYPE_STRING }, \
	{ "bc_base_max", CTLTYPE_INT }, \
	{ "bc_dim_max", CTLTYPE_INT }, \
	{ "bc_scale_max", CTLTYPE_INT }, \
	{ "bc_string_max", CTLTYPE_INT }, \
	{ "coll_weights_max", CTLTYPE_INT }, \
	{ "expr_nest_max", CTLTYPE_INT }, \
	{ "line_max", CTLTYPE_INT }, \
	{ "re_dup_max", CTLTYPE_INT }, \
	{ "posix2_version", CTLTYPE_INT }, \
	{ "posix2_c_bind", CTLTYPE_INT }, \
	{ "posix2_c_dev", CTLTYPE_INT }, \
	{ "posix2_char_term", CTLTYPE_INT }, \
	{ "posix2_fort_dev", CTLTYPE_INT }, \
	{ "posix2_fort_run", CTLTYPE_INT }, \
	{ "posix2_localedef", CTLTYPE_INT }, \
	{ "posix2_sw_dev", CTLTYPE_INT }, \
	{ "posix2_upe", CTLTYPE_INT }, \
	{ "stream_max", CTLTYPE_INT }, \
	{ "tzname_max", CTLTYPE_INT }, \
}

#define CTL_P1003_1B_ASYNCHRONOUS_IO		1	/* boolean */
#define CTL_P1003_1B_MAPPED_FILES		2	/* boolean */
#define CTL_P1003_1B_MEMLOCK			3	/* boolean */
#define CTL_P1003_1B_MEMLOCK_RANGE		4	/* boolean */
#define CTL_P1003_1B_MEMORY_PROTECTION		5	/* boolean */
#define CTL_P1003_1B_MESSAGE_PASSING		6	/* boolean */
#define CTL_P1003_1B_PRIORITIZED_IO		7	/* boolean */
#define CTL_P1003_1B_PRIORITY_SCHEDULING	8	/* boolean */
#define CTL_P1003_1B_REALTIME_SIGNALS		9	/* boolean */
#define CTL_P1003_1B_SEMAPHORES			10	/* boolean */
#define CTL_P1003_1B_FSYNC			11	/* boolean */
#define CTL_P1003_1B_SHARED_MEMORY_OBJECTS	12	/* boolean */
#define CTL_P1003_1B_SYNCHRONIZED_IO		13	/* boolean */
#define CTL_P1003_1B_TIMERS			14	/* boolean */
#define CTL_P1003_1B_AIO_LISTIO_MAX		15	/* int */
#define CTL_P1003_1B_AIO_MAX			16	/* int */
#define CTL_P1003_1B_AIO_PRIO_DELTA_MAX		17	/* int */
#define CTL_P1003_1B_DELAYTIMER_MAX		18	/* int */
#define CTL_P1003_1B_MQ_OPEN_MAX		19	/* int */
#define CTL_P1003_1B_PAGESIZE			20	/* int */
#define CTL_P1003_1B_RTSIG_MAX			21	/* int */
#define CTL_P1003_1B_SEM_NSEMS_MAX		22	/* int */
#define CTL_P1003_1B_SEM_VALUE_MAX		23	/* int */
#define CTL_P1003_1B_SIGQUEUE_MAX		24	/* int */
#define CTL_P1003_1B_TIMER_MAX			25	/* int */

#define CTL_P1003_1B_MAXID		26

#define	CTL_P1003_1B_NAMES { \
	{ 0, 0 }, \
	{ "asynchronous_io", CTLTYPE_INT }, \
	{ "mapped_files", CTLTYPE_INT }, \
	{ "memlock", CTLTYPE_INT }, \
	{ "memlock_range", CTLTYPE_INT }, \
	{ "memory_protection", CTLTYPE_INT }, \
	{ "message_passing", CTLTYPE_INT }, \
	{ "prioritized_io", CTLTYPE_INT }, \
	{ "priority_scheduling", CTLTYPE_INT }, \
	{ "realtime_signals", CTLTYPE_INT }, \
	{ "semaphores", CTLTYPE_INT }, \
	{ "fsync", CTLTYPE_INT }, \
	{ "shared_memory_objects", CTLTYPE_INT }, \
	{ "synchronized_io", CTLTYPE_INT }, \
	{ "timers", CTLTYPE_INT }, \
	{ "aio_listio_max", CTLTYPE_INT }, \
	{ "aio_max", CTLTYPE_INT }, \
	{ "aio_prio_delta_max", CTLTYPE_INT }, \
	{ "delaytimer_max", CTLTYPE_INT }, \
	{ "mq_open_max", CTLTYPE_INT }, \
	{ "pagesize", CTLTYPE_INT }, \
	{ "rtsig_max", CTLTYPE_INT }, \
	{ "nsems_max", CTLTYPE_INT }, \
	{ "sem_value_max", CTLTYPE_INT }, \
	{ "sigqueue_max", CTLTYPE_INT }, \
	{ "timer_max", CTLTYPE_INT }, \
}

#endif
__EOF__

cat <<__EOF__ > nestedvm/socket.h
#ifndef __NESTEDVM_SOCKETS_H
#define __NESTEDVM_SOCKETS_H

#include <sys/types.h>
#include <sys/time.h>

static unsigned short htons(int x) { return x; }
static unsigned long htonl(int x) { return x; }
static unsigned short ntohs(int x) { return x; }
static unsigned long ntohl(int x) { return x; }

/* Note AF_UNIX isn't supported */
#define AF_UNIX 1
#define PF_UNIX AF_UNIX

#define AF_INET 2
#define PF_INET AF_INET

#define SOCK_STREAM 1
#define SOCK_DGRAM 2

#define HOST_NOT_FOUND  1
#define TRY_AGAIN       2
#define NO_RECOVERY     3
#define NO_DATA         4

#define SOL_SOCKET 0xffff

#define SO_REUSEADDR 0x0004
#define SO_KEEPALIVE 0x0008 
#define SO_BROADCAST 0x0020
#define SO_TYPE      0x1008

#define SHUT_RD 0
#define SHUT_WR 1
#define SHUT_RDWR 2

#define INADDR_ANY 0
#define INADDR_NONE -1
#define INADDR_LOOPBACK 0x7f000001
#define INADDR_BROADCAST 0xffffffff

typedef unsigned long in_addr_t;
typedef int socklen_t;

struct in_addr {
	in_addr_t s_addr;
};

struct sockaddr {
    u_char sa_len;
    u_char sa_family;
    char sa_data[6];
};

struct sockaddr_in {
	u_char	sin_len;
	u_char	sin_family;
	u_short	sin_port;
	struct	in_addr sin_addr;
};

struct	sockaddr_un {
	u_char	sun_len;
	u_char	sun_family;
	char	sun_path[256];
};

#define SUN_LEN(su) (sizeof(*(su)) - sizeof((su)->sun_path) + strlen((su)->sun_path))

struct  servent {
    char    *s_name;        /* official name of service */
    char    **s_aliases;    /* alias list */
    int     s_port;         /* port service resides at */
    char    *s_proto;       /* protocol to use */
};

struct servent *getservbyname(const char *name, const char *proto);

struct  hostent {
    char    *h_name;        /* official name of host */
    char    **h_aliases;    /* alias list */
    int     h_addrtype;     /* host address type */
    int     h_length;       /* length of address */
    char    **h_addr_list;  /* list of addresses from name server */
};
#define h_addr  h_addr_list[0]  /* address, for backward compatibility */

struct hostent *gethostbyname(const char *name);

int socket(int domain, int type, int proto);
int bind(int s, const struct sockaddr *addr, socklen_t addrlen);
int listen(int s, int backlog);
int accept(int s, struct sockaddr *addr, socklen_t *addrlen);
int shutdown(int s, int how);
int connect(int s, const struct sockaddr *name, socklen_t namelen);

char *inet_ntoa(struct in_addr in);
in_addr_t inet_addr(const char *cp);
int inet_aton(const char *cp, struct in_addr *addr);

int recvfrom(int s, void *buf, size_t len, int flags, struct sockaddr *from, socklen_t *fromlen);
int sendto(int s, const void *msg, size_t len, int flags, const struct sockaddr *to, socklen_t tolen);
int select(int n, fd_set *readfds, fd_set *writefds, fd_set *exceptfds, struct timeval *timeout);

int getsockopt(int s, int level, int name, void *val, socklen_t *len);
int setsockopt(int s, int level, int name, const void *val, socklen_t len);

extern int h_errno;

void herror(const char *);

#endif
__EOF__

#include <stdio.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/fcntl.h>
#include <sys/unistd.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/signal.h>
#include <time.h>
#include <dirent.h>
#include <wchar.h>
#include <math.h>

char *user_info[1024];

extern void _pause();
extern int _call_java(int a, int b, int c, int d);

void suckram();

int main(int argc, char **argv) {
    int i,n,fd;
    time_t now;
    DIR *dir;
    struct dirent *dent;
    char buf[1024];
    unsigned char ubuf[1024];
    unsigned short sbuf[1024];
    char *s;
    
    printf("Entered main()\n");
    
    if(argc > 1 && strcmp(argv[1],"calltest")==0)  {
        printf("pausing for call test\n");
        _pause();
        printf("unpaused from call test\n");
        
        for(i=1;i<=3;i++) {
            char *s = (char*)_call_java(i,0,0,0);
            printf("_call_java(%d,0,0,0) = \"%s\" (%d chars)\n",i,s,strlen(s));
            free(s);
        }
        fd = _call_java(4,0,0,0);
        if(fd != -1) {
            FILE *fp;
            fprintf(stderr,"fd: %i\n",fd);
            fp = fdopen(fd,"w");
            if(fp != NULL) {
                fprintf(fp,"It worked! fp is %p - Hello, Java!\n",fp);
                fclose(fp);
            } else {
                fprintf(stderr,"fdopen failed\n");
                close(fd);
            }
        } else {
            fprintf(stderr,"fd == -1\n");
        }
        
        printf("In main() in MIPS\n");
        _call_java(5,0,0,0);
        printf("Back in main() in MIPS\n");
    } else if(argc > 2 && strcmp(argv[1],"fdtest")==0)  {
        printf("opening %s\n",argv[2]);
        fd = open(argv[2],O_RDONLY);
        if(fd < 0) { perror("open"); exit(1); }
        
        printf("reading up to 64 bytes\n");
        n = read(fd,buf,64);
        if(n < 0) {perror("read"); exit(1); }
        printf("read %d bytes\n",n);
        for(i=0;i<n;i++) if(buf[i]=='\n' || buf[i]=='\r') { buf[i] = '\0'; break; }
        printf("Read \"%s\"...\n",n == 0 ? NULL : buf);
        
        printf("seeking back to pos 4...\n");
        if(lseek(fd,4,SEEK_SET) < 0) { perror("lseek"); exit(1); }
        
        printf("reading up to 64 bytes\n");
        n = read(fd,buf,64);
        if(n < 0) {perror("read"); exit(1); }
        printf("read %d bytes\n",n);
        for(i=0;i<n;i++) if(buf[i]=='\n' || buf[i]=='\r') { buf[i] = '\0'; break; }
        printf("Read \"%s\"...\n",n == 0 ? NULL : buf);

        printf("reading up to 64 bytes\n");
        n = read(fd,buf,64);
        if(n < 0) {perror("read"); exit(1); }
        printf("read %d bytes\n",n);
        for(i=0;i<n;i++) if(buf[i]=='\n' || buf[i]=='\r') { buf[i] = '\0'; break; }
        printf("Read \"%s\"...\n",n == 0 ? NULL : buf);
    } else if(argc > 1 && strcmp(argv[1],"fptest")==0)  {
        double d = 0.0;
        while(d != 10.0) {
            printf("d: %f\n",d);
            d += 2.5;
        }
    } else if(argc > 1 && strcmp(argv[1],"nullderef")==0) {
        volatile int *mem = 0;
        *mem = 1;
    } else if(argc > 2 && strcmp(argv[1],"crashme") == 0) {
        volatile int *mem = (int*) atoi(argv[2]);
        *mem = 1;
    } else { 
        printf("%d\n", 0xffffff);
        printf("%u\n", 0xffffffU);
        printf("%li\n",0xffffffL);
        printf("%lu\n",0xffffffUL);

        
        for(i=0;i<argc;i++)
            printf("argv[%d] = \"%s\"\n",i,argv[i]);
        for(i=0;user_info[i];i++)
            printf("user_info[%d] = \"%s\"\n",i,user_info[i]);
        
        printf("getenv(\"USER\") = \"%s\"\n",getenv("USER"));
        printf("getenv(\"HOME\") = \"%s\"\n",getenv("HOME"));
        printf("getenv(\"TZ\") = \"%s\"\n",getenv("TZ"));
    
        time(&now);
        tzset();
        printf("%s %s %d\n",tzname[0],tzname[1],(int)_timezone);
        
        printf("Running ctime\n");
        s = ctime(&now);
        printf("ctime returned: %p\n",s);
        printf("Current time: %s",s);
        
        printf("Trying to open /nonexistent\n");
        fd = open("/nonexistent",O_RDONLY);
        if(fd < 0) perror("open");
        else close(fd);
        
        printf("Tyring to mkdir .mkdirtest\n");
        if(mkdir(".mkdirtest",0700) < 0) perror("mkdir");
        
        printf("Trying to opendir .\n");
        dir = opendir(".");
        if(dir) {
            printf("Success!\n");
            while((dent=readdir(dir))!=NULL) {
                struct stat statbuf;
                stat(dent->d_name,&statbuf);
                printf("\t[%s] %lu %i %i\n",dent->d_name,dent->d_ino,statbuf.st_ino,statbuf.st_dev);
            }
            if(errno != 0) { fprintf(stderr,"readdir errno: %d\n",errno); perror("readdir"); }
            closedir(dir);
        } else {
            perror("opendir");
        }
                
    
#if 0
        printf("Sleeping...\n");
        sleep(1);
        printf("Done\n");
#endif
        
        fd = open("test.txt",O_RDONLY);
        if(fd != -1) {
            printf("Opened test.txt\n");
            n = read(fd,sbuf,sizeof(sbuf));
            printf("n: %d\n",n);
            if(n < 0) perror("read");
            ubuf[n] = '\0';
            printf("buf: %s\n",buf);
            for(i=0;i<n/2;i++) {
                printf("Char %d: [%x]\n",i,sbuf[i]);
            }
        }
        
        {
            static double f = 1.574;
            int n;
            printf("%e\n",f);
            f += 20.001;
            f *= -2.0;
            n = (int) f;
            printf("%el\n",f);
            printf("%d\n",n);
            printf("%e\n",f);
            printf("%e\n",fabs(f));
        }
    }
        
    {
        char buf[1024];
        memcpy(buf,"Hello, World",sizeof("Hello, World"));
        printf("%s\n",buf);
    }
    
    {
        
#define HOST_BITS_PER_WIDE_INT 64
#define HOST_WIDE_INT long long
        
        extern int ri(int n);
        int precision = ri(8);
        long long l;
        
        l = (precision - HOST_BITS_PER_WIDE_INT > 0
             ? -1 : ((HOST_WIDE_INT) 1 << (precision - 1)) - 1),
            (precision - HOST_BITS_PER_WIDE_INT - 1 > 0
             ? (((HOST_WIDE_INT) 1
                 << (precision - HOST_BITS_PER_WIDE_INT - 1))) - 1
             : 0);
        
        printf("%llX\n",l);
    }
    
    {
        double d = -2.34;
        d = fabs(d);
        printf("fabs(-2.24) = %g\n",d);
    }
        
    
    //printf("cwd: %s\n",getcwd(NULL,0));
    //printf("isatty(0): %d\n",isatty(0));
    //printf("exiting\n");
    return 0;
}

long long zero = 0;
int izero = 0;
long long rl(long long n) { return n + zero; }
int ri(int n) { return n + izero; }

void suckram() {
    int total = 0;
    fprintf(stderr,"Eating up all available memory\n");
    while(malloc(1024*1024) != NULL) total ++;
    fprintf(stderr,"Ate up %d megs\n",total);
}

__attribute__((constructor)) static void my_ctor()  { printf("Constructor!\n"); }
__attribute__((destructor)) static void my_dtor()  { printf("Destructor!\n"); }

int callme(int a1,int a2, int a3, int a4, int a5, int a6)  __attribute__((section(".text")));
int callme(int a1,int a2, int a3, int a4, int a5, int a6) {
    printf("You said: %d %d %d %d %d %d\n",a1,a2,a3,a4,a5,a6);
    return a1+a2+a3+a4+a5+a6;
}

void echo(const char *string, int count)  __attribute__((section(".text")));
void echo(const char *string, int count) {
    int i;
    for(i=0;i<count;i++)
        printf("%d: %s\n",i,string);
}

void backinmips()  __attribute__((section(".text")));
void backinmips() {
    fprintf(stderr,"In backinmips() in mips\n");
}

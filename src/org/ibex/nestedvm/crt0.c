#include <stddef.h>

extern int main(int argc, char **argv, char **envp);
extern void exit(int status);
extern int atexit(void (*f)());

/* For constuctors/destructors */
extern void _init();
extern void _fini();

extern char _gp[];
register char *gp asm("$28");
 
char **environ;
    
void _start(char **argv, char **environ_) {
    int argc;
    
    if(!gp) gp = _gp;
    
    environ = environ_;
    
    /* Call global constructors */
    _init();
    
    /* Register _fini() to be called on exit */
    atexit(_fini);
    
    /* Count the arguments */
    for(argc=0;argv[argc];argc++);
        
    /* Call main and exit */
    exit(main(argc,argv,environ));
}

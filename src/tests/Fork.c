#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/wait.h>

int main() {
    fprintf(stderr,"In the main process (pid: %d), about to fork\n",getpid());
    pid_t pid;
    int status;
    int i;
    
    pid = fork();
    switch(pid) {
        case -1: perror("fork"); break;
        case 0: 
            fprintf(stderr,"In the forked process (pid: %d), sleeping for 2 sec\n",getpid());
            sleep(2);
            fprintf(stderr,"Child done sleeping... exiting\n");
            _exit(0);
            break;
        default:
            fprintf(stderr,"In the main process (child is: %d) waiting for child\n",pid);
            if(waitpid(pid,&status,0) < 0)
                perror("waitpid");
            else
                fprintf(stderr,"Child process exited (status: %d)\n",status);
    }
 
    pid = fork();
    if(pid==0) {
        fprintf(stderr,"1st fork (pid: %d)\n",getpid());
        if(fork()==0) {
            fprintf(stderr,"2nd fork (pid: %d).. sleeping\n",getpid());
            sleep(5);
            fprintf(stderr,"2nd fork exiting\n");
            _exit(0);
        }
        fprintf(stderr,"1st fork (pid: %d) exiting\n",getpid());
        _exit(0);
    } else  {
        waitpid(pid,NULL,0);
        fprintf(stderr,"1st  fork terminated\n");
    }
    fprintf(stderr,"Sleeping for a bit\n");
    sleep(10);
    
    fprintf(stderr,"Next few pids should be sequential\n");
    for(i=0;i<10;i++) {
        if(fork() == 0) {
            fprintf(stderr,"I am a child %d\n",getpid());
            sleep(i%4 + 5);
            fprintf(stderr,"Child %d exiting\n",getpid());
            _exit(0);
        }
    }
    for(i=0;i<10;i++) fprintf(stderr,"Waited on %d\n",waitpid(-1,NULL,0));
    
    return 0;
}

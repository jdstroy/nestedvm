#include <stdio.h>
#include <string.h>

int main() {
    char buf[1024];
    char *p;
    printf("Hello! Welcome to EchoHelper.c\n");
    while(fgets(buf,sizeof(buf),stdin) != NULL) {
        for(p=buf;*p && *p!='\n' && *p!='\r';p++);
        *p = '\0';
        fprintf(stdout,"You said: %s\n",buf);
        fflush(stdout);
        fprintf(stderr,"They said: %s\n",buf);
        if(strcmp(buf,"exit")==0) break;
    }
    return 0;
}

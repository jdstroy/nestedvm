#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <sys/fcntl.h>

#include "mspack.h"

int main(int argc, char **argv) {
    struct mscab_decompressor *decomp;
    struct mscabd_cabinet *cab;
    struct mscabd_file *file;
    int i;
    
    if(argc < 2) {
        fprintf(stderr,"Usage: %s cab\n",argv[0]);
        exit(1);
    }
    
    
    decomp = mspack_create_cab_decompressor(NULL);
    if(!decomp) exit(1);
    
    for(i=1;i<argc;i++) {
        cab = decomp->search(decomp,argv[i]);
        if(!cab) exit(2);
    
        for(file = cab->files;file;file=file->next)
            decomp->extract(decomp,file,file->filename);
         
        decomp->close(decomp,cab);
    }
    mspack_destroy_cab_decompressor(decomp);

    return 0;
}

/*
UserInfo:
    On start:
        0: Addr of CAB/EXE
        1: Length of CAB/EXE
    On Edit:
        2: Addr of output_table array

Exit codes:
    0: Success
    1: Internal Error
    2: Invalid CAB
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include <sys/fcntl.h>

#include "mspack.h"

#define MAX(a,b) (((a)>(b))?(a):(b))
#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX_MEMBERS 64

char *xstrdup(const char *s) {
    char *ret = strdup(s);
    if(ret == NULL) exit(1);
    return ret;
}

typedef struct {
    char *addr;
    int pos;
    int size;
    int length;
    int writable;
} mem_buf_t;

static mem_buf_t *cab_mem_buf = NULL;

static void mem_buf_grow(mem_buf_t *buf,size_t newsize) {
    size_t new_len;
    char *p;
    if(buf->length < 0) exit(1); 
    if(newsize <= buf->length) return;
    new_len = MAX(buf->length ? buf->length*2 : 65536,newsize);
    p = realloc(buf->addr,new_len);
    if(p == NULL) exit(1);
    buf->addr = p;
    buf->length = new_len;
}

static struct {
    char *filename;
    mem_buf_t buf;
} write_buf_table[MAX_MEMBERS];

static struct {
    char *filename;
    char *data;
    int length;
} output_table[MAX_MEMBERS+1];

static struct mspack_file *my_open(struct mspack_system *sys, char *filename, int mode) {
    mem_buf_t *buf = NULL;
    int i;
    if(strcmp(filename,"/dev/cab")==0) {    
        if(mode != MSPACK_SYS_OPEN_READ) return NULL;
        buf = cab_mem_buf;
    } else {
        if(mode != MSPACK_SYS_OPEN_WRITE) return NULL;
        
        for(i=0;i<MAX_MEMBERS;i++) {
            if(write_buf_table[i].filename == NULL) {
                printf("%s in %d\n",filename,i);
                write_buf_table[i].filename = xstrdup(filename);
                buf = &write_buf_table[i].buf;
                buf->writable = 1;
                break;
            }
        }
    }
    
    return (struct mspack_file *) buf;
}

static void my_close(struct mspack_file *buf_) {
    mem_buf_t *buf = (mem_buf_t*) buf_;
    /* NO OP */
}

static int my_read(struct mspack_file *buf_, void *out, int count) {
    mem_buf_t *buf = (mem_buf_t*) buf_;
    count = MIN(buf->size - buf->pos, count);
    memcpy(out,buf->addr + buf->pos,count);
    buf->pos += count;
    return count;
}

static int my_write(struct mspack_file *buf_, void *in, int count) {
    mem_buf_t *buf = (mem_buf_t*) buf_;
    if(!buf->writable) return -1;
    if(buf->length < buf->pos + count) mem_buf_grow(buf,buf->pos + count);
    memcpy(buf->addr+buf->pos,in,count);
    buf->pos += count;
    buf->size = MAX(buf->size,buf->pos);
    return count;
}

static int my_seek(struct mspack_file *buf_, off_t off, int mode) {
    mem_buf_t *buf = (mem_buf_t*) buf_;
    int newpos;
    switch(mode) {
        case MSPACK_SYS_SEEK_START: newpos = off; break;
        case MSPACK_SYS_SEEK_CUR: newpos = buf->pos + off; break;
        case MSPACK_SYS_SEEK_END: newpos = buf->size - off; break;
        default: return -1;
    }
    if(newpos < 0) return -1;
    if(newpos > buf->size) {
        if(!buf->writable) return -1;
        if(newpos > buf->length)
            mem_buf_grow(buf,newpos);
    }
    buf->pos = newpos;
    return 0;
}

static off_t my_tell(struct mspack_file *buf_) {
    mem_buf_t *buf = (mem_buf_t*) buf_;
    return buf ? buf->pos : 0;
}

static void my_message(struct mspack_file *file, char *format, ...) {
  va_list ap;
  va_start(ap, format);
  vfprintf(stderr, format, ap);
  va_end(ap);
  fputc((int) '\n', stderr);
  fflush(stderr);
}

static void *my_alloc(struct mspack_system *sys, size_t size) { return malloc(size); }
static void my_free(void *p) { free(p); }
static void my_copy(void *src, void *dest, size_t bytes) { memcpy(dest, src, bytes); }

static struct mspack_system my_system =  {
    &my_open,
    &my_close,
    &my_read, 
    &my_write,
    &my_seek,
    &my_tell,
    &my_message,
    &my_alloc,
    &my_free,
    &my_copy,
    NULL
};

char *user_info[4];

int main(int argc, char **argv) {
    struct mscab_decompressor *decomp;
    struct mscabd_cabinet *cab;
    struct mscabd_file *file;
        mem_buf_t mem_buf;
        size_t size = (size_t)user_info[1];
        int i;
        
        mem_buf.addr = user_info[0];
        mem_buf.pos = mem_buf.writable = 0;
        mem_buf.length = -1;
        mem_buf.size = size;
        
        cab_mem_buf = &mem_buf;
                
    decomp = mspack_create_cab_decompressor(&my_system);
    if(!decomp) exit(1);
    
    cab = decomp->search(decomp,"/dev/cab");
    if(!cab) exit(2);
    
        for(file = cab->files;file;file=file->next)
            decomp->extract(decomp,file,file->filename);
        
    decomp->close(decomp,cab);
    mspack_destroy_cab_decompressor(decomp);
        
    printf("Success!\n");
    
        for(i=0;i<MAX_MEMBERS && write_buf_table[i].filename;i++) {
            output_table[i].filename = write_buf_table[i].filename;
            output_table[i].data = write_buf_table[i].buf.addr;
            output_table[i].length = write_buf_table[i].buf.size;
        }
        
        user_info[2] = (char*) output_table;
        
        /*
        if(output_table[0].filename) {
            printf("%s in 0\n",write_buf_table[0].filename);
            fp = fopen(output_table[0].filename,"wb");
            if(fp) {
                fwrite(output_table[0].data,1,output_table[0].length,fp);
                fclose(fp);
                printf("Wrote: %s\n",output_table[0].filename);
            }
        }
        */
        
    return 0;
}

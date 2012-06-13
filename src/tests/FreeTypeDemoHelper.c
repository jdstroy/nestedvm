// Copyright 2003 Adam Megacz, see the COPYING file for licensing [GPL]

#include <unistd.h>
#include <ft2build.h>
#include FT_FREETYPE_H

FT_Library  library;   /* handle to library     */
FT_Face     face;      /* handle to face object */

#define FT_Check(expr,err) do { \
    if((expr) != 0) { \
        errprint(#expr " failed\n"); \
            return err; \
    } \
} while(0)

#define max(a,b) ((a) > (b) ? (a) : (b))
#define min(a,b) ((a) < (b) ? (a) : (b))

static int errprint(const char *s) {
    int l = strlen(s);
    int n;
    while(l) {
        n = write(STDERR_FILENO,s,l);
        if(n < 0) return n;
        l -= n;
        s += n;
    }
    return 0;
}
        
void draw(FT_GlyphSlot glyph,int x, char *buf, int buf_width, int buf_height, int baseline) {
    int y = max(baseline - glyph->bitmap_top,0);
    int rows = glyph->bitmap.rows;
    int width = glyph->bitmap.width;
    int i,j;
    x = x + glyph->bitmap_left;
    if(x + width >= buf_width) return;
    if(y + rows >= buf_height) return;
    //if(buf == NULL) fprintf(stderr,"ABout to dereference %p\n",buf);
    for(i=0;i<rows;i++)
        for(j=0;j<width;j++)
            buf[(i+y)*buf_width+x+j] |= glyph->bitmap.buffer[i*width+j];
}

/* Prevent --gc-sections from blowing this away */
int render(short *s, int size, char *buf, int buf_width, int buf_height, int baseline)  __attribute__((section(".text")));
int render(short *s, int size, char *buf, int buf_width, int buf_height, int baseline) {
    int glyph_index;
    int x = 0;
    FT_Check(FT_Set_Pixel_Sizes(face,0,size),0);
    memset(buf,'\0',buf_width*buf_height);
    //fprintf(stderr,"Rendering %d pt %c... at %p (%dx%d)\n",size,*s,buf,buf_width,buf_height);
    while(*s) {
        glyph_index = FT_Get_Char_Index(face,*s);
        FT_Check(FT_Load_Glyph(face,glyph_index,FT_LOAD_DEFAULT),0);
        FT_Check(FT_Render_Glyph(face->glyph,FT_RENDER_MODE_NORMAL/*256color antialiased*/),0);
        draw(face->glyph,x,buf,buf_width,buf_height,baseline);
        x += face->glyph->advance.x/64;
        s++;
    }
    return 1;
}

char * user_info[2];
extern void _pause();

int main(int argc,char** argv) {
    char *fontdata;
    int fontsize;

    _pause();
    
    fontdata = user_info[0];
    fontsize = (int)user_info[1];
    
    //fprintf(stderr,"Initializng freetype with a %d byte font at %p\n", fontsize, fontdata);
    
    FT_Check(FT_Init_FreeType(&library),EXIT_FAILURE);
    FT_Check(FT_New_Memory_Face(library, fontdata,fontsize, 0, &face),EXIT_FAILURE);
    
    errprint("Freetype initialized\n");
    _pause();
    errprint("Unpaused\n");
    
    /* not reached */
    return EXIT_FAILURE;
}

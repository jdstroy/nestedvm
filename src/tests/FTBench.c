#include <stdio.h>
#include <freetype/freetype.h>
#include <fcntl.h>
#include <unistd.h>

#define FT_Check(expr) do { \
    if((expr) != 0) { \
        fprintf(stderr,#expr " failed\n"); \
        exit(EXIT_FAILURE); \
    } \
} while(0)

#define BMP_WIDTH 800
#define BMP_HEIGHT 600

static char buf[BMP_WIDTH*BMP_HEIGHT];

int main(int argc, char **argv) {
    char *ttf;
    char *out;
    FT_Library library;
    FT_Face face;
    FT_GlyphSlot glyph;
    int num_glyphs;
    int c;
    int glyph_index;
    int loc_x;
    int loc_y;
    int glyph_width;
    int glyph_height;
    int i,j;
    int fd;
    char *p;
    int n,count;
    char *glyph_buf;
    int pixel_size;
    
    if(argc < 3) {
        fprintf(stderr,"Usage: %s ttf bmp\n",argv[0]);
        exit(1);
    }
    
    ttf = argv[1];
    out = argv[2];
    
    memset(buf,'\377',BMP_WIDTH*BMP_HEIGHT);
    
    FT_Check(FT_Init_FreeType(&library));
    FT_Check(FT_New_Face(library,ttf,0,&face));
    
    loc_y = loc_x = 0;
    for(pixel_size=8;pixel_size<48;pixel_size+=4) {
        FT_Check(FT_Set_Pixel_Sizes(face,0,pixel_size));
        for(c=32;c<127;c++) {
            glyph_index = FT_Get_Char_Index(face,c);
            FT_Check(FT_Load_Glyph(face,glyph_index,FT_LOAD_DEFAULT));
            FT_Check(FT_Render_Glyph(face->glyph, ft_render_mode_normal));
            glyph = face->glyph;
            glyph_width = glyph->bitmap.width;
            glyph_height = glyph->bitmap.rows;
            glyph_buf = glyph->bitmap.buffer;
            if(loc_x + glyph_width + glyph->bitmap_left >= BMP_WIDTH) {
                loc_x = 0;
                loc_y += pixel_size;
                if(loc_y >= BMP_HEIGHT-pixel_size) goto done;
            }
            
            for(i=0;i<glyph_height;i++)
                for(j=0;j<glyph_width;j++)
                    buf[(loc_y+i)*BMP_WIDTH+loc_x+j] &= (~glyph_buf[i*glyph_width+j]);
            loc_x += face->glyph->advance.x/64;
        }
    }
done:
    
    if((fd = open(out,O_CREAT|O_WRONLY,0644)) < 0) {
        perror("open");
        exit(1);
    }
    p = buf;
    count = BMP_WIDTH*BMP_HEIGHT;
    
    while(count) {
        n = write(fd,p,count);
        if(n < 0) {
            perror("write");
            exit(1);
        }
        count -=n;
        p += n;
    }
    close(fd);
    
    return 0;
}


int precision = 7;
long long r;

#define HOST_BITS_PER_WIDE_INT 64
#define HOST_WIDE_INT long long

long long x = 123;

int dec(long long x){
    return x - 1;
}

int _start() {
    //long long r = (((HOST_WIDE_INT) 1 << (precision - 1)) - 1);
    //long long r = ((HOST_WIDE_INT) 1 << (precision)) - 1;
    long long r = x - 1;
    
    //r = dec(x);
    r >>= 32;
    
    return r;
}

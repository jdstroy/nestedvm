#include <string.h>

char *a = "bar";

volatile char buf[] = "Hello World";

long long l = -1614907703LL;

int _start() {
    return l >= -64;
}

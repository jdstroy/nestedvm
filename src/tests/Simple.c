#include <string.h>

char *a = "bar";

volatile char buf[] = "Hello World";

int _start() {
    return strlen("foo");
}

#include <math.h>

double d = -1.23;

int _start() {
	int n;
    d = fabs(d);
    n = (int)(d*100);
	return n;;
}

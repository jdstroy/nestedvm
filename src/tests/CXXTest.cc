#include <iostream>

using namespace std;

class Test {
public:
    Test();
    ~Test();
    void sayhi();
};

class Exn {
public:
    Exn() { }
};
    

Test test;

int main(int argc,char *argv[]) {
    printf("Name: %p\n",typeid(const char*).name());
    printf("Name: %s\n",typeid(const char*).name());
    printf("Is pointer: %d\n",typeid(const char*).__is_pointer_p ());
    printf("Name: %p\n",typeid(int).name());
    printf("Name: %s\n",typeid(int).name());
    printf("Is pointer: %d\n",typeid(int).__is_pointer_p ());
    
    try {
        test.sayhi();
    } catch(char *e) {
        printf("sayhi threw: %s\n",e);
    } catch(const char *e) {
        printf("sayhi threw: const char *:%s\n",e);
    } catch(int n) {
        printf("sayhi threw: %d\n",n);
    }
    return 0;
}

Test::Test() {
    cout << "Test's constructor" << endl;
}

Test::~Test() {
    cout << "Test's destructor" << endl;
}

void Test::sayhi() {
    static char exn[] = "Non-const!";
    cout << "Hello, World from Test" << endl;
    cout << "Now throwing an exception" << endl;
    throw "Hello, Exception Handling!";
    //throw exn;
}

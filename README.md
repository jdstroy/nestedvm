nestedvm
========

Clone of NestedVM repository.

Toolchain hint: Most folks have moved on to gcc-4.x.  NestedVM uses gcc-3.x, and requires gcc-3.x to build its toolchain.  Clang appears to work as a decent substitute (make CC=clang) - I used clang version 3.2 (tags/RELEASE\_32/final) / Target: x86\_64-pc-linux-gnu / Thread model: posix.

From the [original site](http://nestedvm.ibex.org/):

NestedVM provides binary translation for Java Bytecode. This is done by having GCC compile to a MIPS binary which is then translated to a Java class file. Hence any application written in C, C++, Fortran, or any other language supported by GCC can be run in 100% pure Java with no source changes.

NestedVM was created by [Brian Alliet](http://www.brianweb.net/) and [Adam Megacz](http://www.megacz.com/).

[David Crawshaw](http://www.zentus.com/) has also made significant contributions.

NestedVM is Open Source, released under the Apache 2.0 license. 


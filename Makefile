.SECONDARY:

# 
# What to build
#

# Java sources that are part of the compiler/interpreter
java_sources = $(wildcard src/org/ibex/nestedvm/*.java) $(wildcard src/org/ibex/nestedvm/util/*.java)

# C sources that are part of the compiler/interpreter
mips_sources = crt0.c support_aux.c
mips_asm_sources = support.s

mips2java_root = $(shell pwd)
build = $(mips2java_root)/build
tasks = upstream/tasks

#
# MIPS Settings (don't change these)
#
flags = -march=mips1
MIPS_CC = mips-unknown-elf-gcc
MIPS_CXX = mips-unknown-elf-g++

# Be VERY careful about changing any of these as they can break binary 
# compatibility and create hard to find bugs
mips_optflags = -O3 -g \
	-mmemcpy \
	-ffunction-sections -fdata-sections \
	-falign-functions=512 \
	-fno-rename-registers \
	-fno-schedule-insns \
	-fno-delayed-branch \
	-freduce-all-givs

MIPS_CFLAGS = $(mips_optflags) $(flags) -I. -Wall -Wno-unused -Werror
MIPS_LD = mips-unknown-elf-gcc
MIPS_LDFLAGS= $(flags) --static -Wl,--gc-sections
MIPS_STRIP = mips-unknown-elf-strip

# Java compiler/VM settings
JAVAC = javac
JAVA = java
ifeq ($(firstword $(JAVAC)),gcj)
	JAVAC_NODEBUG_FLAGS = -g0
else
	JAVAC_NODEBUG_FLAGS = -g:none
endif

bcel_jar = upstream/build/bcel-5.1/bcel-5.1.jar
classpath = build:$(bcel_jar)

GCJ = gcj
EXE_EXT = 

#####

java_classes = $(java_sources:src/%.java=build/%.class)
mips_objects = $(mips_sources:%.c=build/org/ibex/nestedvm/%.o) $(mips_asm_sources:%.s=build/org/ibex/nestedvm/%.o)

usr = $(mips2java_root)/upstream/install
PATH := $(usr)/bin:$(PATH)
export PATH

#
# General Build Stuff
#
all: $(java_classes) $(mips_objects)
ifdef NATIVE_MIPS2JAVA_COMPILER
all: build/mips2java$(EXE_EXT) $(mips_objects)
endif

$(tasks)/%:
	$(MAKE) -C upstream tasks/$* usr="$(usr)" MIPS_LDFLAGS="$(MIPS_LDFLAGS)" MIPS_CFLAGS="$(flags) $(mips_optflags)"

upstream_clean_%:
	$(MAKE) -C upstream clean_$* usr="$(usr)"

#
# Interpreter/Compiler/Runtime Java Compilation
#

# This works around a gcj -C bug
ifeq ($(firstword $(JAVAC)),gcj)
build/org/ibex/nestedvm/util/.Dummy.class:
	mkdir -p `dirname $@`
	touch $@
$(java_classes): build/org/ibex/nestedvm/util/.Dummy.class
endif

$(java_classes): $(java_sources) $(bcel_jar)
	$(JAVAC) -classpath $(classpath) -d build $(java_sources)

$(bcel_jar): upstream/tasks/extract_bcel
	@true

# GCJ Stuff
# FIXME: We're cramming more than we need into the binary here
build/mips2java$(EXE_EXT): $(java_sources) $(java_gen_sources)
	@mkdir -p `dirname $@`
	$(GCJ) -s -o $@ --main=org.ibex.nestedvm.Compiler $(java_sources) $(java_gen_sources)

#
# MIPS Binary compilation
#
build/%.o: src/%.c $(tasks)/full_toolchain
	@mkdir -p `dirname $@`
	$(MIPS_CC) $(MIPS_CFLAGS) $($(notdir $*)_CFLAGS) -c -o $@ $<

build/%.o: src/%.s $(tasks)/full_toolchain
	@mkdir -p `dirname $@`
	$(MIPS_CC) -x assembler-with-cpp -c -o $@ $<

%.s: %.c $(tasks)/full_toolchain
	$(MIPS_CC) $(MIPS_CFLAGS) $($(notdir $*)_CFLAGS) -c -S -o $@ $<

build/%.mips: build/%.o $(mips_objects)
	$(MIPS_LD) -o $@ $< $(MIPS_LDFLAGS) $($(notdir $*)_LDFLAGS)

build/%.mips: src/%.cc $(tasks)/full_toolchain $(mips_objects)
	@mkdir -p `dirname $@`
	$(MIPS_CXX) $(MIPS_CFLAGS) $($(notdir $*)_CFLAGS) $(MIPS_LDFLAGS) $($(notdir $*)_LDFLAGS) -o $@ $<

build/%.mips.stripped: build/%.mips
	cp $< $@
	$(MIPS_STRIP) -s $@

# MIPS Compiler generated class compilation
ifdef DO_JAVASOURCE

build/%.java: build/%.mips build/org/ibex/nestedvm/JavaSourceCompiler.class 
	$(JAVA) -cp $(classpath) org.ibex.nestedvm.Compiler -outformat javasource $(compiler_flags) $($(notdir $*)_COMPILERFLAGS) $(subst /,.,$*) $< > build/$*.java

build/%.class: build/%.java build/org/ibex/nestedvm/Runtime.class
	$(JAVAC) $(JAVAC_NODEBUG_FLAGS) -classpath build -d build $<
else

build/%.class: build/%.mips build/org/ibex/nestedvm/ClassFileCompiler.class
	$(JAVA) -cp $(classpath) org.ibex.nestedvm.Compiler -outformat class -outfile $@ $(compiler_flags) $($(notdir $*)_COMPILERFLAGS) $(subst /,.,$*) $<


endif

# General Java Class compilation
build/%.class: src/%.java
	$(JAVAC) -classpath build -d build $<

clean:
	rm -rf build/tests build/org/ibex/nestedvm *.jar build/mips2java$(EXE_EXT)

#
# env.sh
#
env.sh: Makefile $(tasks)/full_toolchain build/org/ibex/nestedvm/Compiler.class
	@rm -f "$@~"
	@echo 'PATH="$(mips2java_root)/build:$(usr)/bin:$$PATH"; export PATH' >> $@~
	@echo 'CC=mips-unknown-elf-gcc; export CC' >> $@~
	@echo 'CXX=mips-unknown-elf-g++; export CXX' >> $@~
	@echo 'AS=mips-unknown-elf-as; export AS' >> $@~
	@echo 'AR=mips-unknown-elf-ar; export AR' >> $@~
	@echo 'LD=mips-unknown-elf-ld; export LD' >> $@~
	@echo 'RANLIB=mips-unknown-elf-ranlib; export RANLIB' >> $@~
	@echo 'CFLAGS="$(mips_optflags)"; export CFLAGS' >> $@~
	@echo 'CXXFLAGS="$(mips_optflags)"; export CXXFLAGS' >> $@~
	@echo 'LDFLAGS="$(MIPS_LDFLAGS)"; export LDFLAGS' >> $@~
	@echo 'CLASSPATH=$(mips2java_root)/build:$(mips2java_root)/$(bcel_jar):.; export CLASSPATH' >> $@~
	@mv "$@~" "$@"
	@echo "$@ created successfully"

#
# Runtime.jar
#

runtime_classes = Runtime Registers UsermodeConstants util/Seekable

runtime.jar: $(runtime_classes:%=build/org/ibex/nestedvm/%.class)
	cd build && jar cf ../$@ \
		$(runtime_classes:%=org/ibex/nestedvm/%.class) \
		org/ibex/nestedvm/Runtime\$$*.class \
		org/ibex/nestedvm/util/Seekable\$$*.class
		
.manifest:
	echo -ne "Manifest-Version: 1.0\nMain-Class: org.ibex.nestedvm.RuntimeCompiler\n" > $@

nestedvm.jar: $(java_classes) .manifest
	cd build && jar cfm ../$@ ../.manifest $(java_classes:build/%.class=%*.class)

compact_runtime_compiler.jar: $(java_classes) .manifest $(tasks)/build_gcclass
	mkdir -p tmp/pruned
	java -cp upstream/build/gcclass/build:$(bcel_jar) com.brian_web.gcclass.GCClass \
		build tmp/pruned \
		org.ibex.nestedvm.RuntimeCompiler.main 'org.ibex.nestedvm.Runtime.decodeData' \
		'org.ibex.nestedvm.UnixRuntime.<init>' 'org.ibex.nestedvm.Runtime.initPages' \
		'org.ibex.nestedvm.Runtime.clearPages' 'org.ibex.nestedvm.Runtime.syscall' \
		'org.ibex.nestedvm.Runtime$$CPUState.dup'
	cd tmp/pruned && jar cfm ../../$@ ../../.manifest .

# This is only for Brian to use... don't mess with it
rebuild-constants: $(tasks)/build_newlib
	@mkdir -p `dirname $@`
	( \
		cat \
			src/org/ibex/nestedvm/syscalls.h \
			$(usr)/mips-unknown-elf/include/sys/{errno.h,unistd.h,syslimits.h}; \
		$(MIPS_CC) -E -dM $(usr)/mips-unknown-elf/include/sys/fcntl.h | awk '$$2 ~ /^[OF]_/ { print; }'; \
	) | ( \
		echo "// THIS FILE IS AUTOGENERATED! DO NOT EDIT!"; \
		echo "// run \"make rebuild-constants\" if it needs to be updated"; \
		echo ""; \
		echo "package org.ibex.nestedvm;"; \
		echo "public interface UsermodeConstants {"; \
		tr '\t' ' ' | sed -n ' \
			s/  */ /g; \
			s/ *# *define \([A-Z_][A-Za-z0-9_]*\) \([0-9][0-9x]*\)/    public static final int \1 = \2;/p'; \
		echo "}"; \
	) > src/org/ibex/nestedvm/UsermodeConstants.java
	
#
# Tests
# These are simply here for convenience. They aren't required 
# to build or run mips2java
#

build/tests/Env.class: build/org/ibex/nestedvm/Runtime.class build/org/ibex/nestedvm/Interpreter.class

# Generic Hello Worldish test
test: build/tests/Test.class
	$(JAVA) -cp build tests.Test "arg 1" "arg 2" "arg 3"
inttest: build/tests/Test.mips build/org/ibex/nestedvm/Interpreter.class
	$(JAVA) -cp build org.ibex.nestedvm.Interpreter build/tests/Test.mips "arg 1" "arg 2" "arg 3"
cxxtest: build/tests/CXXTest.class
	$(JAVA) -cp build tests.CXXTest

# CallTest
build/tests/CallTest.class: build/tests/Test.class
calltest: build/tests/CallTest.class
	$(JAVA) -cp build tests.CallTest `date|perl -pe 's/\D+/ /g;'` `id -u`

# FDTest
build/tests/FDTest.class: build/tests/Test.class
fdtest: build/tests/FDTest.class
	$(JAVA) -cp build tests.FDTest


# Simple
Simple_LDFLAGS = -nostdlib
simpletest: build/tests/Simple.class
	$(JAVA) -cp build tests.Simple

# Paranoia
Paranoia_CFLAGS = "-Wno-error"
Paranoia_LDFLAGS = -lm
paranoiatest: build/tests/Paranoia.class
	$(JAVA) -cp build tests.Paranoia
	
#
# Freetype Stuff
#
FreeType_CFLAGS = -Iupstream/build/freetype/include
FreeType_LDFLAGS =  -Lupstream/build/freetype/objs -lfreetype

FreeTypeDemoHelper_CFLAGS = $(FreeType_CFLAGS)
FreeTypeDemoHelper_LDFLAGS = $(FreeType_LDFLAGS)
build/tests/FreeTypeDemoHelper.o: $(mips_objects) $(tasks)/build_freetype
build/tests/FreeTypeDemoHelper.mips: 
build/tests/FreeTypeDemo.class: build/tests/FreeTypeDemoHelper.class

FTBench_CFLAGS =  $(FreeType_CFLAGS)
FTBench_LDFLAGS = $(FreeType_LDFLAGS)
build/tests/FTBench.o: $(tasks)/build_freetype

#
# MSPack Stuff
#
MSPackHelper_CFLAGS = -Iupstream/build/libmspack/mspack
MSPackHelper_LDFLAGS = -Lupstream/build/libmspack/mspack -lmspack
build/tests/MSPackHelper.o: $(mips_objects) $(tasks)/build_libmspack
build/tests/MSPack.class: build/tests/MSPackHelper.class

MSPackBench_CFLAGS = -Iupstream/build/libmspack/mspack
MSPackBench_LDFLAGS = -Lupstream/build/libmspack/mspack -lmspack
build/tests/MSPackBench.o: $(tasks)/build_libmspack

#
# Echo
#
build/tests/Echo.class: build/tests/EchoHelper.class

#
# Libjpeg
#
DJpeg_COMPILERFLAGS = -o onepage,pagesize=8m
build/tests/DJpeg.mips: $(mips_objects) $(tasks)/build_libjpeg
	@mkdir -p `dirname $@`
	cp upstream/build/libjpeg/djpeg $@

#
# Busybox
#
BusyBox_COMPILERFLAGS = -o unixruntime
build/tests/BusyBox.mips: $(mips_objects) $(tasks)/build_busybox
	@mkdir -p `dirname $@`
	cp upstream/build/busybox/busybox $@
	
busyboxtest: build/tests/BusyBox.class
	$(JAVA) -cp $(classpath) tests.BusyBox ash

#
# Boehm GC
#
build/tests/GCTest.mips: $(mips_objects) $(tasks)/build_boehmgc
	@mkdir -p `dirname $@`
	cp upstream/build/boehmgc/gctest $@

boehmgctest: build/tests/Env.class build/tests/GCTest.class
	$(JAVA) -cp build tests.Env GC_PRINT_STATS=1  tests.GCTest


#
# Speed tests
#

build/tests/SpeedTest.class: build/org/ibex/nestedvm/Runtime.class

tmp/thebride_1280.jpg:
	@mkdir -p tmp
	cd tmp && wget http://www.kill-bill.com/images/wallpaper/thebride_1280.jpg

oldspeedtest: build/tests/DJpeg.class tmp/thebride_1280.jpg
	bash -c "time $(JAVA) -cp build tests.DJpeg -targa -outfile tmp/thebride_1280.tga tmp/thebride_1280.jpg"
	@echo "e90f6b915aee2fc0d2eb9fc60ace6203  tmp/thebride_1280.tga" | md5sum -c && echo "MD5 is OK"

djpegspeedtest: build/tests/SpeedTest.class build/tests/DJpeg.class tmp/thebride_1280.jpg
	@echo "Running DJpeg test..."
	@$(JAVA) -cp build tests.SpeedTest tests.DJpeg 8 -targa -outfile tmp/thebride_1280.tga tmp/thebride_1280.jpg

mspackspeedtest: build/tests/SpeedTest.class build/tests/MSPackBench.class
	@if [ -e tmp/mspack/comic32.exe ]; then \
		echo "Running MSPackBench test..."; \
		cd tmp/mspack && $(JAVA) -cp ../../build tests.SpeedTest tests.MSPackBench 20 *32.exe; \
	else \
		echo "Run \"make check\" to get the MS True Type fonts for the MSPackBench test"; \
	fi

speedtest: build/tests/SpeedTest.class build/tests/DJpeg.class build/tests/FTBench.class tmp/thebride_1280.jpg build/tests/MSPackBench.class
	@echo "Running DJpeg test..."
	@$(JAVA) -cp build tests.SpeedTest tests.DJpeg 10 -targa -outfile tmp/thebride_1280.tga tmp/thebride_1280.jpg
	@if [ -e tmp/mspack/Comic.TTF ]; then \
		echo "Running FTBench test..."; \
		$(JAVA) -cp build tests.SpeedTest tests.FTBench 10 tmp/mspack/Comic.TTF tmp/mspack/Comic.TTF.render; \
	else \
		echo "Run \"make check\" to get Arial.TTF for the FTBench test"; \
	fi
	@if false && [ -e tmp/mspack/comic32.exe ]; then \
		echo "Running MSPackBench test..."; \
		cd tmp/mspack && $(JAVA) -cp ../../build tests.SpeedTest tests.MSPackBench 10 *32.exe; \
	else \
		echo "Run \"make check\" to get the MS True Type fonts for the MSPackBench test"; \
	fi

intspeed: build/tests/DJpeg.mips  build/org/ibex/nestedvm/Interpreter.class tmp/thebride_1280.jpg
	time $(JAVA) -cp build org.ibex.nestedvm.Interpreter build/tests/DJpeg.mips -targa  -outfile tmp/thebride_1280.tga tmp/thebride_1280.jpg
	@echo "e90f6b915aee2fc0d2eb9fc60ace6203  tmp/thebride_1280.tga" | md5sum -c && echo "MD5 is OK"

#
# Verification checks
#

check: $(patsubst %,build/tests/%.class, FTBench MSPackBench DJpeg GCTest) tmp/thebride_1280.jpg
	@/bin/bash ./src/tests/check.sh running_from_make

compiletests: $(patsubst %,build/tests/%.class,FTBench MSPackBench DJpeg Test FreeTypeDemoHelper MSPackHelper EchoHelper BusyBox GCTest Fork)
	@true


# IVME Paper
doc/nestedvm.ivme04.pdf: doc/nestedvm.ivme04.tex doc/acmconf.cls
	cd doc; pdflatex nestedvm.ivme04.tex && ./pst2pdf && pdflatex nestedvm.ivme04.tex

pdf: doc/nestedvm.ivme04.pdf
	open doc/nestedvm.ivme04.pdf

# Inspired by XWT's Makefile.upstream

version_gcc = 3.3
version_gpc = 20030830
version_texinputs = 1.0
url_gcc-core = http://mirrors.rcn.net/pub/sourceware/gcc/releases/gcc-$(version_gcc)/gcc-core-$(version_gcc).tar.gz
url_gcc-c++ = http://mirrors.rcn.net/pub/sourceware/gcc/releases/gcc-$(version_gcc)/gcc-g++-$(version_gcc).tar.gz
patches_gcc = gcc-fixes.patch gcc-fdata-sections-bss.patch
configure_gcc = --target=mips-unknown-elf --disable-threads --with-gnu-ld --with-gnu-as --with-newlib=yes --enable-sjlj-exceptions --enable-languages="c"
configure_gcc_step2 = $(configure_gcc) --enable-languages="c,c++"

version_gpc = 20030830
url_gpc = http://www.gnu-pascal.de/alpha/gpc-$(version_gpc).tar.gz
patches_gpc = $(patches_gcc) gpc-with-3.3.3.patch gpc-noutils.patch
configure_gpc = $(configure_gcc) --enable-languages="pascal"

version_binutils = 2.14
url_binutils = ftp://ftp.gnu.org/gnu/binutils/binutils-$(version_binutils).tar.gz
patches_binutils = binutils-no64.patch binutils-nocompilein.patch
configure_binutils = --target=mips-unknown-elf

version_newlib = 1.11.0
url_newlib = http://mirrors.rcn.net/pub/sourceware/newlib/newlib-$(version_newlib).tar.gz
patches_newlib = newlib-mips.patch newlib-tzset.patch newlib-malloc.patch newlib-vasprintf.patch newlib-nomemcpy.patch
configure_newlib = --enable-multilib --target=mips-unknown-elf

url_openbsdglob = http://www.brianweb.net/xwt/openbsdglob.tar.gz
url_regex = ftp://ftp.zoo.toronto.edu/pub/regex.shar

version_bcel = 5.1
url_bcel = http://mirrors.mix5.com/apache/jakarta/bcel/binaries/bcel-$(version_bcel).tar.gz

url_linpack = http://www.math.iastate.edu/burkardt/f_src/linpack_bench/linpack_bench.f

.SECONDARY: # This is broken in gmake < 3.79.1

upstream = $(shell pwd)
root = $(shell dirname "`pwd`")
usr = $(upstream)/install

PATH := $(usr)/bin:$(PATH)
export PATH

# Solaris' patch doesn't work, use gnu patch on solaris
PATCH = $(shell test `uname` = SunOS && echo gpatch || echo patch)

tasks/build_gcc: tasks/build_linker
tasks/build_newlib: tasks/build_gcc

cross_root := $(usr)/mips-unknown-elf
libc_a := $(cross_root)/lib/libc.a

tasks/build_extraheaders: $(upstream)/misc/extraheaders.sh tasks/build_newlib
	# Add extra nestedvm specific headers to include
	cd $(cross_root)/include && sh $<
	touch $@

tasks/build_libc: tasks/build_newlib tasks/build_extraheaders tasks/build_regex tasks/build_openbsdglob
	# HACK: Get the top level makefile to build the support stuff
	make -s -C $(root) build/org/ibex/nestedvm/{crt0.o,support.o,support_aux.o}

	# Add our support stuff to libc
	mips-unknown-elf-ar sr $(libc_a)  $(root)/build/org/ibex/nestedvm/{support.o,support_aux.o}
	rm -f $(cross_root)/lib/crt0.o
	cp $(root)/build/org/ibex/nestedvm/crt0.o $(cross_root)/lib/crt0.o

	touch $@

tasks/build_linker: tasks/build_binutils $(root)/src/org/ibex/nestedvm/linker.ld
	cp $(root)/src/org/ibex/nestedvm/linker.ld $(cross_root)/lib/ldscripts/nestedvm.ld
	for f in $(cross_root)/lib/ldscripts/elf32ebmip.*; do \
		rm -f "$$f"; \
		ln -s nestedvm.ld "$$f"; \
	done
	touch $@

clean_%:
	rm -rf "build/$(*)"*
	rm -f "tasks/build_$*" "tasks/patch_$*" "tasks/extract_$*"


gpc := $(usr)/bin/mips-unknown-elf-gpc
gpc_flags := --big-endian -w -lgpc -lc $(usr)/mips-unknown-elf/lib/support*.o
gpc_flags += -Wl,-T,$(usr)/mips-unknown-elf/lib/linker.ld

# FIXME ugly path hacks evil bad
nestedvm := java -cp $(usr)/../build/bcel/bcel-5.1.jar:$(usr)/../../build org.ibex.nestedvm.Compiler -outformat class

tasks/download_tex:  ; mkdir -p build/tex; touch $@
build/tex/tex.web:   ; curl -o $@ http://www.math.uni.wroc.pl/~hebisch/tex_p/tex.web
build/dtex/tangle.p: ; curl http://www.gnu-pascal.de/crystal/gpc/en/attachments/5593/tangle.p.gz | zcat > $@
tasks/extract_tex:     build/tex/tex.web build/tex/tangle.p; touch $@

build/tex/tangle.mips:  build/tex/tangle.p tasks/full_toolchain tasks/extract_tex tasks/build_gcc_step2
	cd build/tex; $(gpc) tangle.p $(gpc_flags) -o tangle.mips
build/tex/Tangle.class: build/tex/tangle.mips
	$(nestedvm) -o unixruntime -outfile $@ Tangle $<
build/tex/tex.p: build/tex/tex.web build/tex/tex.ch build/tex/Tangle.class
	rm -f build/tex/tex.p build/tex/tex.pool
	cd build/tex; java -cp .:$(usr)/../../build Tangle        \
			--gpc-rts=-nPascalfile:tex.p              \
			--gpc-rts=-npool:tex.pool                 \
			--gpc-rts=-nwebfile:tex.web               \
			--gpc-rts=-nchangefile:tex.ch
build/tex/tex.mips:  build/tex/tex.p tasks/full_toolchain tasks/build_gcc_step2
	cd build/tex; $(gpc) tex.p $(gpc_flags) -o tex.mips
tasks/build_tex: build/tex/tex.mips
	cd build/tex; $(nestedvm) -o unixruntime -outfile TeX.class TeX tex.mips
	touch $@ 
tasks/extract_texinputs: tasks/build_tex tasks/download_texinputs
	cd build/tex; tar xvzf ../../download/texinputs-1.0.tgz
	touch $@

tasks/extract_linpack: tasks/download_linpack ; touch $@
tasks/download_linpack:
	mkdir -p build/linpack
	cd build/linpack; wget -N http://www.math.iastate.edu/burkardt/f_src/linpack_bench/linpack_bench.f
	touch $@

tasks/build_linpack: tasks/full_toolchain tasks/extract_linpack
	cd build/linpack; $(g77) linpack_bench.f $(g77_flags) -o linpack.mips
	cd build/linpack; $(nestedvm) -o unixruntime -outfile Linpack.class Linpack linpack.mips
	touch $@

tasks/download_%:
	if [ -z "$(url_$*)" ]; then echo "No url for $*" >&2; false; fi
	mkdir -p download
	cd download && wget --passive-ftp -N $(url_$*)
	touch $@

tasks/download_gcc: tasks/download_gcc-core tasks/download_gcc-c++
	touch $@

tasks/extract_%: tasks/download_%
	mkdir -p build
	cd build && \
		gzip -dc  ../download/$*-$(version_$*).tar.gz | tar xf - && \
		rm -f $* && \
		ln -s $*-$(version_$*) $*
	touch $@

tasks/extract_gcc: tasks/download_gcc
	mkdir -p build
	cd build && gzip -dc ../download/gcc-core-$(version_gcc).tar.gz | tar xf -
	cd build && gzip -dc ../download/gcc-g++-$(version_gcc).tar.gz | tar xf -
	touch $@

tasks/patch_%: tasks/extract_%
	cd build/$*-$(version_$*) &&  \
	for p in $(patches_$*) end; do \
		[ "$$p" = "end" ] || $(PATCH) -p0 < ../../patches/$$p || exit 1; \
	done
	touch $@

tasks/build_%: tasks/patch_%
	mkdir -p $(usr)
	mkdir -p build/$*-obj && cd build/$*-obj && \
		../$*-$(version_$*)/configure --prefix=$(usr) $(configure_$*) && \
		$(MAKE) TARGET_CFLAGS="$(MIPS_CFLAGS)" && \
		$(MAKE) install
	touch $@

tasks/build_gcc_step2: tasks/patch_gcc tasks/build_libc
	for f in crt0.o support.o support_aux.o; do \
		rm -f "$(usr)/mips-unknown-elf/lib/$$f"; \
		ln -s "$(root)/build/org/ibex/nestedvm/$$f" "$(usr)/mips-unknown-elf/lib/$$f"; \
	done

	rm -f "$(usr)/mips-unknown-elf/lib/linker.ld"
	ln -s "$(root)/src/org/ibex/nestedvm/linker.ld" "$(usr)/mips-unknown-elf/lib/linker.ld"

	if [ ! -e "$(usr)/mips-unknown-elf/include/getopt.h" ]; then \
		echo '#include <unistd.h>' > "$(usr)/mips-unknown-elf/include/getopt.h"; \
	fi
	
	mips-unknown-elf-gcc -dumpspecs \
		| sed '/startfile:$$/{n;s/crt0%O%s/crt0%O%s support%O%s support_aux%O%s/;};' \
		| sed '/link_command:$$/{n;s/%{T\*}/%{T*} %{!T:-T linker.ld%s}/;};' \
		> "$(usr)/lib/gcc-lib/mips-unknown-elf/$(version_gcc)/specs"
		
	mkdir -p $(usr)
	mkdir -p build/gcc-obj && cd build/gcc-obj && \
		echo | ../gcc-$(version_gcc)/configure --prefix=$(usr) $(configure_gcc_step2) && \
		$(MAKE) TARGET_CFLAGS="$(MIPS_CFLAGS)" GCC_FOR_TARGET=$(usr)/install/bin/mips-unknown-elf-gcc && \
		$(MAKE) install
	touch $@

tasks/extract_gpc: tasks/download_gcc tasks/download_gpc
	mkdir -p build/gpc.extract
	cd build/gpc.extract && \
		gzip -dc ../../download/gcc-core-$(version_gcc).tar.gz | tar xf - && \
		gzip -dc ../../download/gpc-$(version_gpc).tar.gz      | tar xf - && \
		test ! -e gcc-$(version_gcc)/gcc/p && \
		mv gpc-$(version_gpc)/p gcc-$(version_gcc)/gcc/p
	mv build/gpc.extract/gcc-$(version_gcc) build/gpc-$(version_gpc)
	rm -rf build/gpc.extract
	touch $@

tasks/build_gpc: tasks/patch_gpc tasks/build_libc
	mkdir -p $(usr)
	# We need to preapply this patch because gpc's configury doesn't like 3.3.3
	cd build/gpc-$(version_gpc) && cat gcc/version.c | grep -q GPC || $(PATCH) -p0 < gcc/p/diffs/gcc-3.3.diff
	
	# echo | is to dismiss the gcc version warning
	mkdir -p build/gpc-obj && cd build/gpc-obj && \
		echo | ../gpc-$(version_gpc)/configure --prefix=$(usr) $(configure_gpc) && \
		$(MAKE) TARGET_CFLAGS="$(MIPS_CFLAGS)" && \
		cd gcc && $(MAKE) pascal.install
	
	touch $@

tasks/build_openbsdglob: tasks/download_openbsdglob tasks/build_newlib
	@mkdir -p $(usr)/mips-unknown-elf/{include,lib}
	cd build && gzip -dc ../download/openbsdglob.tar.gz | tar xf -
	cd build/openbsdglob && \
		mips-unknown-elf-gcc '-Dissetugid()=0' -DARG_MAX=65536 $(MIPS_CFLAGS) -I. -c -o glob.o glob.c && \
		mips-unknown-elf-gcc -O2 -Wall -Werror -I. -c -o fnmatch.o fnmatch.c && \
		mips-unknown-elf-ar cr libglob.a *.o && \
		mips-unknown-elf-ranlib libglob.a && \
		cp *.h $(usr)/mips-unknown-elf/include && \
		cp *.a $(usr)/mips-unknown-elf/lib
	touch $@

tasks/build_regex: tasks/download_regex tasks/build_newlib
	@mkdir -p $(usr)/mips-unknown-elf/{include,lib}
	mkdir -p build/regex build/regex/fake
	cd build/regex && \
		sh ../../download/regex.shar && \
		make CC=mips-unknown-elf-gcc CFLAGS="-I. $(MIPS_CFLAGS)" regcomp.o regexec.o regerror.o regfree.o && \
		mips-unknown-elf-ar cr libregex.a regcomp.o regexec.o regerror.o regfree.o && \
		mips-unknown-elf-ranlib libregex.a && \
		cp regex.h $(usr)/mips-unknown-elf/include && \
		cp libregex.a $(usr)/mips-unknown-elf/lib 
	touch $@


#
# Tests
# These are simply here for convenience. They aren't required 
# to build or run mips2java
#

version_libjpeg = 6b
url_libjpeg = http://www.ijg.org/files/jpegsrc.v$(version_libjpeg).tar.gz

version_libmspack = 20030726
url_libmspack = http://www.kyz.uklinux.net/downloads/libmspack-$(version_libmspack).tar.gz
patches_libmspack = libmspack.patch

version_freetype = 2.1.4
url_freetype = http://umn.dl.sourceforge.net/sourceforge/freetype/freetype-$(version_freetype).tar.gz
patches_freetype = freetype.patch ft-nostdio.patch

version_boehmgc = 6.3alpha2
url_boehmgc = http://www.hpl.hp.com/personal/Hans_Boehm/gc/gc_source/gc$(version_boehmgc).tar.gz
patches_boehmgc = boehm-gc.patch

version_busybox = 1.00-pre9
url_busybox = http://busybox.net/downloads/busybox-$(version_busybox).tar.gz
patches_busybox = busybox.patch

tasks/extract_libjpeg: tasks/download_libjpeg
	mkdir -p build
	cd build && \
		gzip -dc ../download/jpegsrc.v$(version_libjpeg).tar.gz | tar xf - && \
		mv jpeg-$(version_libjpeg) libjpeg-$(version_libjpeg) && \
		rm -f libjpeg && \
		ln -s libjpeg-$(version_libjpeg)  libjpeg
	touch $@    

tasks/build_libjpeg: tasks/patch_libjpeg tasks/build_libc
	cd build/libjpeg-$(version_libjpeg) && \
		./configure && \
		make CC="mips-unknown-elf-gcc" \
			AR="mips-unknown-elf-ar rc" \
			AR2="mips-unknown-elf-ranlib" \
			CFLAGS="$(MIPS_CFLAGS)" \
			LDFLAGS="$(MIPS_LDFLAGS)"
	touch $@    

tasks/extract_libmspack: tasks/download_libmspack tasks/build_libc
	mkdir -p build
	cd build && \
		gzip -dc ../download/libmspack-$(version_libmspack).tar.gz | tar xf - && \
		mv libmspack libmspack-$(version_libmspack) && \
		rm -f libmspack && \
		ln -s libmspack-$(version_libmspack)  libmspack
	touch $@    

tasks/build_libmspack: tasks/patch_libmspack tasks/build_libc
	cd build/libmspack-$(version_libmspack)/mspack && \
		make CC="mips-unknown-elf-gcc" \
			AR="mips-unknown-elf-ar" \
			RANLIB="mips-unknown-elf-ranlib" \
			OPTIM="$(MIPS_CFLAGS)"
	touch $@    

tasks/build_freetype: tasks/patch_freetype tasks/build_libc
	cd build/freetype-$(version_freetype) && \
		$(MAKE) setup ansi && \
		$(MAKE) CC="mips-unknown-elf-gcc" \
		    CFLAGS="-c $(MIPS_CFLAGS)" \
			AR="mips-unknown-elf-ar" \
			RANLIB="mips-unknown-elf-ranlib" && \
		mips-unknown-elf-ranlib objs/libfreetype.a
	touch $@    

tasks/extract_boehmgc: tasks/download_boehmgc
	mkdir -p build
	cd build && \
		rm -rf gc$(version_boehmgc) && \
		gzip -dc ../download/gc$(version_boehmgc).tar.gz | tar xf - && \
		rm -f boehmgc boehmgc-$(version_boehmgc) && \
		ln -s gc$(version_boehmgc)  boehmgc && \
		ln -s gc$(version_boehmgc)  boehmgc-$(version_boehmgc)
	touch $@

tasks/build_boehmgc: tasks/patch_boehmgc tasks/build_libc
	cd build/gc$(version_boehmgc) && \
		CC="mips-unknown-elf-gcc" CFLAGS="$(MIPS_CFLAGS)" LDFLAGS="$(MIPS_LDFLAGS)" \
		./configure --host=mips-unknown-elf --disable-shared --disable-threads && \
		make && \
		make gctest 
	touch $@

tasks/build_busybox: tasks/patch_busybox tasks/build_libc
	cd build && \
		rm -f busybox && \
		ln -s busybox-$(version_busybox) busybox
	cd build/busybox && $(MAKE) LDFLAGS="$(MIPS_LDFLAGS)" CFLAGS_EXTRA="$(MIPS_CFLAGS) -DNESTEDVM -g"
	touch $@

tasks/build_gcclass:
	cd build &&  darcs get --verbose http://darcs.brianweb.net/gcclass
	cd build/gcclass && make
	touch $@

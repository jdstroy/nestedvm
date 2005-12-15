#!/bin/sh -e

[ -z "$JAVA" ] && JAVA=java

MD5SUM=`which md5sum|grep -v '^no '`
[ -z "$MD5SUM" ] && MD5SUM=`which gmd5sum|grep -v '^no '`
if [ -z "$MD5SUM" ]; then
    echo "could not find an md5sum command"
    exit 1
fi

CLASSPATH="$(pwd)/build"; export CLASSPATH
if [ "$1" != "running_from_make" ]; then
	echo "Please don't run this scipt directly. Use make check" >&2
	exit 1
fi

INT="$2"

cd tmp

if [ ! -e .skipmspack ]; then

mkdir -p mspack
cd mspack
rm -f *.TTT *.inf FONTINST.EXE *.DLL *.TXT;
for f in \
    andale32.exe arial32.exe arialb32.exe comic32.exe courie32.exe georgi32.exe \
    impact32.exe times32.exe trebuc32.exe verdan32.exe webdin32.exe; \
do
	[ -e "$f" ] || wget "http://dist.xwt.org/corefonts/$f" || rm -f "$f"
	[ -e "$f" ] ||  exit 1
done

echo "Extracting MS Core Fonts using MSPackBench..."
$JAVA tests.MSPackBench *32.exe

cat <<EOF | $MD5SUM -c
663974c9fe3ba55b228724fd4d4e445f  AndaleMo.TTF
3e7043e8125f1c8998347310f2c315bc  AriBlk.TTF
f11c0317db527bdd80fa0afa04703441  Arial.TTF
34cd8fd9e4fae9f075d4c9a2c971d065  Arialbd.TTF
a2b3bcdb39097b6aed17a766652b92b2  Arialbi.TTF
25633f73d92a0646e733e50cf2cc3b07  Ariali.TTF
a50f9c96a76356e3d01013e0b042989f  Comic.TTF
81d64ec3675c4adc14e9ad2c5c8103a7  Comicbd.TTF
f4b306eed95aa7d274840533be635532  Georgia.TTF
c61b355a5811e56ed3d7cea5d67c900e  Georgiab.TTF
1e4e5d1975bdf4a5c648afbf8872fa13  Georgiai.TTF
e5d52bbfff45e1044381bacb7fc8e300  Georgiaz.TTF
8fc622c3a2e2d992ec059cca61e3dfc0  Impact.TTF
4f97f4d6ba74767259ccfb242ce0e3f7  Times.TTF
ed6e29caf3843142d739232aa8642158  Timesbd.TTF
6d2bd425ff00a79dd02e4c95f689861b  Timesbi.TTF
957dd4f17296522dead302ab4fcdfa8d  Timesi.TTF
055460df9ab3c8aadd3330bd30805f11  Trebucbd.ttf
3ba52ab1fa0cd726e7868e9c6673902c  Verdana.TTF
a2b4dc9afc18e76cfcaa0071fa7cd0da  Verdanab.TTF
24b3a293c865a2c265280f017fb24ba5  Verdanai.TTF
f7310c29df0070530c48a47f2dca9014  Verdanaz.TTF
1a56b45a66b07b4c576d5ead048ed992  Webdings.TTF
20f23317e90516cbb7d38bd53b3d1c5b  cour.ttf
7d94f95bf383769b51379d095139f2d7  courbd.ttf
da414c01f951b020bb09a4165d3fb5fa  courbi.ttf
167e27add66e9e8eb0d28a1235dd3bda  couri.ttf
70e7be8567bc05f771b59abd9d696407  trebuc.ttf
fb5d68cb58c6ad7e88249d65f6900740  trebucbi.ttf
8f308fe77b584e20b246aa1f8403d2e9  trebucit.ttf
663974c9fe3ba55b228724fd4d4e445f  AndaleMo.TTF
3e7043e8125f1c8998347310f2c315bc  AriBlk.TTF
f11c0317db527bdd80fa0afa04703441  Arial.TTF
34cd8fd9e4fae9f075d4c9a2c971d065  Arialbd.TTF
a2b3bcdb39097b6aed17a766652b92b2  Arialbi.TTF
25633f73d92a0646e733e50cf2cc3b07  Ariali.TTF
a50f9c96a76356e3d01013e0b042989f  Comic.TTF
81d64ec3675c4adc14e9ad2c5c8103a7  Comicbd.TTF
f4b306eed95aa7d274840533be635532  Georgia.TTF
c61b355a5811e56ed3d7cea5d67c900e  Georgiab.TTF
1e4e5d1975bdf4a5c648afbf8872fa13  Georgiai.TTF
e5d52bbfff45e1044381bacb7fc8e300  Georgiaz.TTF
8fc622c3a2e2d992ec059cca61e3dfc0  Impact.TTF
4f97f4d6ba74767259ccfb242ce0e3f7  Times.TTF
ed6e29caf3843142d739232aa8642158  Timesbd.TTF
6d2bd425ff00a79dd02e4c95f689861b  Timesbi.TTF
957dd4f17296522dead302ab4fcdfa8d  Timesi.TTF
055460df9ab3c8aadd3330bd30805f11  Trebucbd.ttf
3ba52ab1fa0cd726e7868e9c6673902c  Verdana.TTF
a2b4dc9afc18e76cfcaa0071fa7cd0da  Verdanab.TTF
24b3a293c865a2c265280f017fb24ba5  Verdanai.TTF
f7310c29df0070530c48a47f2dca9014  Verdanaz.TTF
1a56b45a66b07b4c576d5ead048ed992  Webdings.TTF
20f23317e90516cbb7d38bd53b3d1c5b  cour.ttf
7d94f95bf383769b51379d095139f2d7  courbd.ttf
da414c01f951b020bb09a4165d3fb5fa  courbi.ttf
167e27add66e9e8eb0d28a1235dd3bda  couri.ttf
70e7be8567bc05f771b59abd9d696407  trebuc.ttf
fb5d68cb58c6ad7e88249d65f6900740  trebucbi.ttf
8f308fe77b584e20b246aa1f8403d2e9  trebucit.ttf
EOF

echo "Core Fonts extracted successfully!"

cd ..

fi

if [ ! -e .skipdjpeg ]; then
echo "Decoding some jpegs with DJpeg..."

rm -f *.tga

[ -e banner.jpg ] || wget http://www.xwt.org/image/banner.jpg
[ -e banner.jpg ] || exit 1

$JAVA tests.DJpeg -targa -outfile thebride_1280.tga thebride_1280.jpg 
echo "e90f6b915aee2fc0d2eb9fc60ace6203  thebride_1280.tga" | $MD5SUM -c

$JAVA tests.DJpeg -targa -outfile banner.tga banner.jpg
echo "4c7cc29ae2094191a9b0308cf9a04fbd  banner.tga" | $MD5SUM -c

echo "JPEGs decoded successfully!"

fi

if [ ! -e .skipfreetype ]; then

cd mspack

echo "Rendering some fonts with FTBench..."
if ! [ -e Verdana.TTF -a -e Arial.TTF -a -e Comic.TTF ]; then
	echo "Can't find the corefonts - did the mspack test complete?"
	exit 1
fi

rm -f *.render

for f in Verdana.TTF Arial.TTF Comic.TTF; do
	$JAVA tests.FTBench "$f" "$f".render
done

cat <<EOF|$MD5SUM -c
e33b9db5a413af214b2524265af18026  Arial.TTF.render
61dee4f697a61ebc1b47decbed04b2da  Comic.TTF.render
d5a6d39a63e49c597ed860913e27d2bb  Verdana.TTF.render
EOF

echo "Fonts rendered successfully"
cd ..

fi

if [ ! -e .skipgc ]; then

echo "Running gctest from the boehm-gc library..."
$JAVA tests.GCTest

fi

if [ ! -e .busybox -a -e ../build/tests/BusyBox.class ]; then
	echo "Running busybox's md5sum command on some ttfs"
	$JAVA tests.BusyBox ash -c "md5sum mspack/*.ttf > md5.1"
	md5sum mspack/*.ttf > md5.2
	cmp md5.1 md5.2 && echo "The BusyBox md5sum command and sh work properly!"
fi

cat <<EOF
* * * * * * * * * * * * * * * * * * * * * 
* All tests completed with no failures  *
* * * * * * * * * * * * * * * * * * * * * 

EOF

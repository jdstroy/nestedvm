# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:500]
set xrange [1:7.4]
set data style boxes
set boxwidth 0.4
set xtics ("DJpeg" 1.8, "Freetype" 3.4, "Boehm-GC" 5.0, "LibMSPack" 6.6)
set xlabel "Application"
set ylabel "Size (kilobytes)"
set title "Size of MIPS Binary vs Java Bytecode"
set grid
#set label "59"  at 2.15, 69 
#set label "97"  at 2.55, 107
#set label "159" at 2.95, 169
#set label "256" at 3.35, 266
plot \
  'chart9.dat' using ($1-0.8):($2) title "Class",      \
  'chart9.dat' using ($1-0.4):($3) title "Native",    \
  'chart9.dat' using     ($1):($4) title "Compressed Class"


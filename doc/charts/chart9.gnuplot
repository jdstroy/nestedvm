# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:500]
set xrange [1:9.0]
set data style boxes
set boxwidth 0.4
set xtics ("DJpeg" 2.0, "Freetype" 4.0, "Boehm-GC" 6.0, "LibMSPack" 8.0)
set xlabel "Application"
set ylabel "Size (kilobytes)"
set title "Size of MIPS Binary vs Java Bytecode"
set grid
#set label "59"  at 2.15, 69 
#set label "97"  at 2.55, 107
#set label "159" at 2.95, 169
#set label "256" at 3.35, 266
plot \
  'chart9.dat' using ($1-1.2):($2) title "Class",      \
  'chart9.dat' using ($1-0.8):($3) title "MIPS Binary",    \
  'chart9.dat' using ($1-0.4):($4) title "Compressed Class", \
  'chart9.dat' using     ($1):($5) title "Compressed MIPS"


# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:2.8]
set xrange [1:5.8]
set data style boxes
set boxwidth 0.4
set xtics ("Native" 1.8, "NestedVM -> GCJ" 3.4, "Hotspot" 5.0)
#set xlabel ""
set title "Native vs GCJ vs Hotspot"
set ylabel "Seconds"
set grid

plot \
  'chart7.dat' using ($1-0.8):($2) title "libjpeg",      \
  'chart7.dat' using ($1-0.4):($3) title "libfreetype",    \
  'chart7.dat' using     ($1):($4) title "libmspack"


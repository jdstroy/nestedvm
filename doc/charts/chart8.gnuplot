# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:9.0]
set xrange [1:5.8]
set data style boxes
set boxwidth 0.8
set xtics ("Native" 1.6, "NestedVM -> GCJ" 3.4, "NestedVM" 5.2)
#set xlabel ""
set title "Native/GCJ/Hotspot Boehm-GC (gctest)"
set ylabel "Seconds"
set grid
plot \
  'chart8.dat' using ($1):($2) title ""

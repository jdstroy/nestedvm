# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:2.5]
set xrange [1:4.0]
set data style boxes
set boxwidth 0.8
set xtics ("Native" 1.6, "NestedVM" 3.4)
#set xlabel ""
set title "IO Performance (Copy 100mb of Data)"
set ylabel "Seconds"
set grid
plot \
  'chart6.dat' using ($1):($2) title ""

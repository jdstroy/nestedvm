# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:4.5]
set xrange [1:5.8]
set data style boxes
set boxwidth 0.4
set xtics ("Libjpeg" 1.8, "libfreetype" 3.4, "libmspack"  5.0)
set xlabel "Application"
set ylabel "Seconds"
set title "Paged vs Flat Memory Access"
set grid

plot \
  'chart5.dat' using ($1-0.8):($2) title "Paged",      \
  'chart5.dat' using ($1-0.4):($3) title "Flat"

# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:6]
set xrange [1:7.4]
set data style boxes
set boxwidth 0.4
set xtics ("256" 1.8, "128" 3.4, "64" 5.0, "32" 6.6)
set xlabel "Max Instructions Per Method"
set ylabel "Seconds"
set title "Optimal Max Instructions Per Method"
set grid

plot \
  'chart1.dat' using ($1-0.8):($2) title "libjpeg",      \
  'chart1.dat' using ($1-0.4):($3) title "libfreetype",    \
  'chart1.dat' using     ($1):($4) title "libmspack"


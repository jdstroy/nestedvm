# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:60]
set xrange [1:4.2]
set data style boxes
set boxwidth 0.4
set xtics ("512" 1.8, "128" 3.4)
set xlabel "Max Instructions Per Method"
set ylabel "Seconds"
set title "JIT Doensn't Handle Largs Methods Well"
set grid

plot \
  'chart2.dat' using ($1-0.8):($2) title "libjpeg",      \
  'chart2.dat' using ($1-0.4):($3) title "libfreetype",    \
  'chart2.dat' using     ($1):($4) title "libmspack"


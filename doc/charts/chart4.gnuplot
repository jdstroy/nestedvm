# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:5.5]
set xrange [1:5.8]
set data style boxes
set boxwidth 0.4
set xtics ("libjpeg" 1.8, "libfreetype" 3.4, "libmspack"  5.0)
set xlabel "Application"
set ylabel "Seconds"
set title "Fields vs Local Variables"
set grid
plot \
  'chart4.dat' using ($1-0.8):($2) title "Fields Only",      \
  'chart4.dat' using ($1-0.4):($3) title "Some Local Vars", \
  'chart4.dat' using ($1):($4) title "All Local Vars"

# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:5.0]
set xrange [1:5.8]
set data style boxes
set boxwidth 0.4
set xtics ("Libjpeg" 1.8, "libfreetype" 3.4, "libmspack"  5.0)
set xlabel "Application"
set ylabel "Seconds"
set title "JavaSource vs Classfile"
set grid

plot \
  'chart3.dat' using ($1-0.8):($2) title "JavaSource",      \
  'chart3.dat' using ($1-0.4):($3) title "ClassFile"

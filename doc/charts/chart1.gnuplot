# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:300]
set xrange [1.8:4.5]
set data style boxes
set boxwidth 0.4
set xtics (" " 3)
set xlabel "X Axis Label"
set ylabel "Y Axis Label"
set grid
set label "59"  at 2.15, 69 
set label "97"  at 2.55, 107
set label "159" at 2.95, 169
set label "256" at 3.35, 266
plot \
  'chart1.dat' using ($1-0.8):($2) title "x86",      \
  'chart1.dat' using ($1-0.4):($3) title "Sparc",    \
  'chart1.dat' using     ($1):($4) title "HPL-PD",   \
  'chart1.dat' using ($1+0.4):($5) title "Canonical"



# don't touch this
set terminal postscript landscape color "Helvetica" 19 
set output 'unfilled.eps'

set yrange [0:5]
set xrange [1:8.4]
set data style boxes
set boxwidth 0.8
set xtics (\
		"None" 1.6, "Prune Cases" 2.8, "GCC\nOptimizations" 4.0, \
		"TABLE\nSWITCH" 5.2, "Combined" 6.4, \
		"Binary\n-to-\nBinary" 7.6)

set title "Effects of Optimizations on Binary-to-Souce and Binary-to-Binary Compilers"
set ylabel "Seconds"
set grid
#set label "59"  at 2.15, 69 
#set label "97"  at 2.55, 107
#set label "159" at 2.95, 169
#set label "256" at 3.35, 266
plot \
  'chart10.dat' using ($1):($2) title ""

% Change file for GNU Pascal and Linux
% (by Don Knuth, 2000; see ../tex-sparc for the prehistory)
% (I'm no longer keeping the "efficiency" changes; just enuf for TRIP test)
%
% Use this file as is to make an INITEX.  To get triptex, use the
% shell script ``ini_to_trip'' and re-TANGLE.

% History:
% 2000.04.30 first sketch --- untested --- see FIXTHIS for parts not done yet

% NOTE: the module numbers in this change file refer to Volume B.

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [0] WEAVE: only print changes
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
\def\botofcontents{\vskip 0pt plus 1fil minus 1.5in}
@y
\def\botofcontents{\vskip 0pt plus 1fil minus 1.5in}
\let\maybe=\iffalse
\def\title{\TeX82 changes for GNU Pascal}
\def\glob{13}\def\gglob{20, 26} % these are defined in module 1
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [1.2] banner line
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@d banner=='This is TeX, Version 3.14159' {printed when \TeX\ starts}
@y
@d banner=='This is '#27'[33;1mNesTeX'#27'[0m, Version 3.14159 for '#27'[31;1mJava'#27'[0m'
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [1.7] debug..gubed, stat..tats
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@d debug==@{ {change this to `$\\{debug}\equiv\null$' when debugging}
@d gubed==@t@>@} {change this to `$\\{gubed}\equiv\null$' when debugging}
@y
@d debug==@{ {the trip test will use debugging}
@d gubed==@t@>@}
@z
@x
@d stat==@{ {change this to `$\\{stat}\equiv\null$' when gathering
  usage statistics}
@d tats==@t@>@} {change this to `$\\{tats}\equiv\null$' when gathering
  usage statistics}
@y
@d stat==
@d tats==
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [1.8] init..tini
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@d init== {change this to `$\\{init}\equiv\.{@@\{}$' in the production version}
@d tini== {change this to `$\\{tini}\equiv\.{@@\}}$' in the production version}
@y
@d init==
@d tini==
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [1.10] othercases, feature of ISO Extended Pascal
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@d othercases == others: {default for cases not listed explicitly}
@y
@d othercases == otherwise {default for cases not listed explicitly}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [1.11] compile-time constants
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@!mem_max=30000; {greatest index in \TeX's internal |mem| array;
  must be strictly less than |max_halfword|;
  must be equal to |mem_top| in \.{INITEX}, otherwise |>=mem_top|}
@!mem_min=0; {smallest index in \TeX's internal |mem| array;
  must be |min_halfword| or more;
  must be equal to |mem_bot| in \.{INITEX}, otherwise |<=mem_bot|}
@!buf_size=500; {maximum number of characters simultaneously present in
  current lines of open files and in control sequences between
  \.{\\csname} and \.{\\endcsname}; must not exceed |max_halfword|}
@!error_line=72; {width of context lines on terminal error messages}
@!half_error_line=42; {width of first lines of contexts in terminal
  error messages; should be between 30 and |error_line-15|}
@!max_print_line=79; {width of longest text lines output; should be at least 60}
@!stack_size=200; {maximum number of simultaneous input sources}
@!max_in_open=6; {maximum number of input files and error insertions that
  can be going on simultaneously}
@!font_max=75; {maximum internal font number; must not exceed |max_quarterword|
  and must be at most |font_base+256|}
@!font_mem_size=20000; {number of words of |font_info| for all fonts}
@!param_size=60; {maximum number of simultaneous macro parameters}
@!nest_size=40; {maximum number of semantic levels simultaneously active}
@!max_strings=3000; {maximum number of strings; must not exceed |max_halfword|}
@!string_vacancies=8000; {the minimum number of characters that should be
  available for the user's control sequences and font names,
  after \TeX's own error messages are stored}
@!pool_size=32000; {maximum number of characters in strings, including all
  error messages and help texts, and the names of all fonts and
  control sequences; must exceed |string_vacancies| by the total
  length of \TeX's own strings, which is currently about 23000}
@!save_size=600; {space for saving values outside of current group; must be
  at most |max_halfword|}
@!trie_size=8000; {space for hyphenation patterns; should be larger for
  \.{INITEX} than it is in production versions of \TeX}
@!trie_op_size=500; {space for ``opcodes'' in the hyphenation patterns}
@!dvi_buf_size=800; {size of the output buffer; must be a multiple of 8}
@!file_name_size=40; {file names shouldn't be longer than this}
@!pool_name='TeXformats:TEX.POOL                     ';
  {string of length |file_name_size|; tells where the string pool appears}
@y
@!mem_max=300000; {greatest index in \TeX's internal |mem| array;
  must be strictly less than |max_halfword|;
  must be equal to |mem_top| in \.{INITEX}, otherwise |>=mem_top|}
@!mem_min=0; {smallest index in \TeX's internal |mem| array;
  must be |min_halfword| or more;
  must be equal to |mem_bot| in \.{INITEX}, otherwise |<=mem_bot|}
@!buf_size=5000; {maximum number of characters simultaneously present in
  current lines of open files and in control sequences between
  \.{\\csname} and \.{\\endcsname}; must not exceed |max_halfword|}
@!error_line=79; {width of context lines on terminal error messages}
@!half_error_line=50; {width of first lines of contexts in terminal
  error messages; should be between 30 and |error_line-15|}
@!max_print_line=79; {width of longest text lines output; should be at least 60}
@!stack_size=200; {maximum number of simultaneous input sources}
@!max_in_open=15; {maximum number of input files and error insertions that
  can be going on simultaneously}
@!font_max=127; {maximum internal font number; must not exceed |max_quarterword|
  and must be at most |font_base+256|}
@!font_mem_size=40000; {number of words of |font_info| for all fonts}
@!param_size=60; {maximum number of simultaneous macro parameters}
@!nest_size=40; {maximum number of semantic levels simultaneously active}
@!max_strings=30000; {maximum number of strings; must not exceed |max_halfword|}
@!string_vacancies=8000; {the minimum number of characters that should be
  available for the user's control sequences and font names,
  after \TeX's own error messages are stored}
@!pool_size=100000; {maximum number of characters in strings, including all
  error messages and help texts, and the names of all fonts and
  control sequences; must exceed |string_vacancies| by the total
  length of \TeX's own strings, which is currently about 23000}
@!save_size=6000; {space for saving values outside of current group; must be
  at most |max_halfword|}
@!trie_size=30000; {space for hyphenation patterns; should be larger for
  \.{INITEX} than it is in production versions of \TeX}
@!trie_op_size=500; {space for ``opcodes'' in the hyphenation patterns}
@!dvi_buf_size=800; {size of the output buffer; must be a multiple of 8}
@!file_name_size=1024; {file names shouldn't be longer than this}
@!pool_name='tex.pool';
  {string of length |file_name_size|; the string pool name}
@.TeXformats@>
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [1.12] sensitive compile-time constants
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@d mem_bot=0 {smallest index in the |mem| array dumped by \.{INITEX};
  must not be less than |mem_min|}
@d mem_top==30000 {largest index in the |mem| array dumped by \.{INITEX};
  must be substantially larger than |mem_bot|
  and not greater than |mem_max|}
@d font_base=0 {smallest internal font number; must not be less
  than |min_quarterword|}
@d hash_size=2100 {maximum number of control sequences; it should be at most
  about |(mem_max-mem_min)/10|}
@d hash_prime=1777 {a prime number equal to about 85\pct! of |hash_size|}
@d hyph_size=307 {another prime; the number of \.{\\hyphenation} exceptions}
@y
@d mem_bot=0 {smallest index in the |mem| array dumped by \.{INITEX};
  must not be less than |mem_min|}
@d mem_top==300000 {largest index in the |mem| array dumped by \.{INITEX};
  must be substantially larger than |mem_bot|
  and not greater than |mem_max|}
@d font_base=0 {smallest internal font number; must not be less
  than |min_quarterword|}
@d hash_size=10000 {maximum number of control sequences; it should be at most
  about |(mem_max-mem_min)/10|}
@d hash_prime=3443 {a prime number equal to about 85\pct! of |hash_size|}
@d hyph_size=307 {another prime; the number of \.{\\hyphenation} exceptions}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [2.23] allow any character that we can input to get in
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
for i:=0 to @'37 do xchr[i]:=' ';
for i:=@'177 to @'377 do xchr[i]:=' ';
@y
for i:=0 to @'37 do xchr[i]:=chr(i);
for i:=@'177 to @'377 do xchr[i]:=chr(i);
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [3.25] file types
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@!eight_bits=0..255; {unsigned one-byte quantity}
@!alpha_file=packed file of text_char; {files that contain textual data}
@y
@!eight_bits=ByteCard; {unsigned one-byte quantity}
@!alpha_file=t@&e@&x@&t; {files that contain textual data}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [3.27] file opening
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@ The \ph\ compiler with which the present version of \TeX\ was prepared has
extended the rules of \PASCAL\ in a very convenient way. To open file~|f|,
we can write
$$\vbox{\halign{#\hfil\qquad&#\hfil\cr
|reset(f,@t\\{name}@>,'/O')|&for input;\cr
|rewrite(f,@t\\{name}@>,'/O')|&for output.\cr}}$$
The `\\{name}' parameter, which is of type `{\bf packed array
$[\langle\\{any}\rangle]$ of \\{char}}', stands for the name of
the external file that is being opened for input or output.
Blank spaces that might appear in \\{name} are ignored.

The `\.{/O}' parameter tells the operating system not to issue its own
error messages if something goes wrong. If a file of the specified name
cannot be found, or if such a file cannot be opened for some other reason
(e.g., someone may already be trying to write the same file), we will have
|@!erstat(f)<>0| after an unsuccessful |reset| or |rewrite|.  This allows
\TeX\ to undertake appropriate corrective action.
@:PASCAL H}{\ph@>
@^system dependencies@>

\TeX's file-opening procedures return |false| if no file identified by
|name_of_file| could be opened.

@d reset_OK(#)==erstat(#)=0
@d rewrite_OK(#)==erstat(#)=0

@p function a_open_in(var f:alpha_file):boolean;
  {open a text file for input}
begin reset(f,name_of_file,'/O'); a_open_in:=reset_OK(f);
end;
@#
function a_open_out(var f:alpha_file):boolean;
  {open a text file for output}
begin rewrite(f,name_of_file,'/O'); a_open_out:=rewrite_OK(f);
end;
@#
function b_open_in(var f:byte_file):boolean;
  {open a binary file for input}
begin reset(f,name_of_file,'/O'); b_open_in:=reset_OK(f);
end;
@#
function b_open_out(var f:byte_file):boolean;
  {open a binary file for output}
begin rewrite(f,name_of_file,'/O'); b_open_out:=rewrite_OK(f);
end;
@#
function w_open_in(var f:word_file):boolean;
  {open a word file for input}
begin reset(f,name_of_file,'/O'); w_open_in:=reset_OK(f);
end;
@#
function w_open_out(var f:word_file):boolean;
  {open a word file for output}
begin rewrite(f,name_of_file,'/O'); w_open_out:=rewrite_OK(f);
end;
@y
@ An external C procedure, |test_access| is used to check whether or not the
open will work.  It is declared in the ``ext.h'' include file, and it returns
|true| or |false|. The |Trim(name_of_file)| global holds the file name whose access
is to be tested.
The first parameter for |test_access| is the access mode,
one of |read_access_mode| or |write_access_mode|.

We also implement path searching in |test_access|:  its second parameter is
one of the ``file path'' constants defined below.  If |Trim(name_of_file)|
doesn't start with |'/'| then |test_access| tries prepending pathnames
from the appropriate path list until success or the end of path list
is reached.
On return, |Trim(name_of_file)| contains the original name with the path
that succeeded (if any) prepended.  It is the name used in the various
open procedures.

Note that |a_open_in| has been redefined to take an additional argument,
which should be one of the ``file path'' specifiers.
Since |b_open_in| is only used for \.{TFM} files, and
|w_open_in| is only used for format files, we don't need a path specifying
argument for them.
Path searching is not done for output files.

@d read_access_mode=4  {``read'' mode for |test_access|}
@d write_access_mode=2 {``write'' mode for |test_access|}

@d no_file_path=0    {no path searching should be done}
@d input_file_path=1 {path specifier for \.{\\input} files}
@d read_file_path=2  {path specifier for \.{\\read} files}
@d font_file_path=3  {path specifier for \.{TFM} files}
@d format_file_path=4 {path specifier for format files}
@d pool_file_path=5  {path specifier for the pool file}

@p function a_open_in(var f:alpha_file):boolean;
  {open a text file for input}
var str:String;
begin
reset(f,name_of_file);
a_open_in:=IOResult=0;
end;



@#
function a_open_out(var f:alpha_file):boolean;
  {open a text file for output}
var @!ok:boolean;
begin
     rewrite(f,Trim(name_of_file));
a_open_out:=true;
end;
@#
function b_open_in(var f:byte_file):boolean;
  {open a binary file for input}
var @!ok:boolean;
begin
     reset(f,Trim(name_of_file));
b_open_in:=true;
end;
@#
function b_open_out(var f:byte_file):boolean;
  {open a binary file for output}
var @!ok:boolean;
begin
     rewrite(f,Trim(name_of_file));
b_open_out:=true;
end;
@#
function w_open_in(var f:word_file):boolean;
  {open a word file for input}
var @!ok:boolean;
begin
     reset(f,Trim(name_of_file));
w_open_in:=true;
end;
@#
function w_open_out(var f:word_file):boolean;
  {open a word file for output}
var @!ok:boolean;
begin
     rewrite(f,Trim(name_of_file));
w_open_out:=true;
end;
@z


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [3.32] term_in/out are input,output
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@!term_in:alpha_file; {the terminal as an input file}
@!term_out:alpha_file; {the terminal as an output file}

@y
@!{the terminal as an input file}

@z

@x
@d t_open_in==reset(term_in,'TTY:','/O/I') {open the terminal for text input}
@d t_open_out==rewrite(term_out,'TTY:','/O') {open the terminal for text output}
@y
@d term_in==Input
@d term_out==Output
@d t_open_in==do_nothing
@d t_open_out==do_nothing
@z


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [3.34] flushing output
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
these operations can be specified in \ph:
@^system dependencies@>

@d update_terminal == break(term_out) {empty the terminal output buffer}
@d clear_terminal == break_in(term_in,true) {clear the terminal input buffer}
@d wake_up_terminal == do_nothing {cancel the user's cancellation of output}
@y
these operations can be specified with Berkeley {\mc UNIX}:
@^system dependencies@>

@d update_terminal == {nothing necessary on UNIX}
@d clear_terminal == {nothing necessary on UNIX}
@d wake_up_terminal == {nothing necessary on UNIX}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [4.38] string data
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@!packed_ASCII_code = 0..255; {elements of |str_pool| array}
@y
@!packed_ASCII_code = ByteCard; {elements of |str_pool| array}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [4.51,52,53] make TEX.POOL lowercase in messages
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
else  bad_pool('! I can''t read TEX.POOL.')
@y
else  bad_pool('! I can''t read tex.pool.')
@z
@x
begin if eof(pool_file) then bad_pool('! TEX.POOL has no check sum.');
@y
begin if eof(pool_file) then bad_pool('! tex.pool has no check sum.');
@z
@x
    bad_pool('! TEX.POOL line doesn''t begin with two digits.');
@y
    bad_pool('! tex.pool line doesn''t begin with two digits.');
@z
@x
  bad_pool('! TEX.POOL check sum doesn''t have nine digits.');
@y
  bad_pool('! tex.pool check sum doesn''t have nine digits.');
@z
@x
done: if a<>@$ then bad_pool('! TEX.POOL doesn''t match; TANGLE me again.');
@y
done: if a<>@$ then bad_pool('! tex.pool doesn''t match; tangle me again.');
@z


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [8.110] ranges for quarter,half words FIXTHIS
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@d min_quarterword=0 {smallest allowable value in a |quarterword|}
@d max_quarterword=255 {largest allowable value in a |quarterword|}
@d min_halfword==0 {smallest allowable value in a |halfword|}
@d max_halfword==65535 {largest allowable value in a |halfword|}
@y
@d min_quarterword=-128 {smallest allowable value in a |quarterword|}
@d max_quarterword=127 {largest allowable value in a |quarterword|}
@d min_halfword==-2147483648 {smallest allowable value in a |halfword|}
@d max_halfword==2147483647 {largest allowable value in a |halfword|}
@z

@x
@<Types...@>=
@!quarterword = min_quarterword..max_quarterword; {1/4 of a word}
@!halfword=min_halfword..max_halfword; {1/2 of a word}
@!two_choices = 1..2; {used when there are two variants in a record}
@!four_choices = 1..4; {used when there are four variants in a record}
@y
@<Types...@>=
@!quarterword = min_quarterword..max_quarterword; {1/4 of a word}
@!halfword=min_halfword..max_halfword; {1/2 of a word}
@!two_choices = 1..2; {used when there are two variants in a record}
@!four_choices = 1..4; {used when there are four variants in a record}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [11.165] fix the word "free" so that it doesn't conflict with a runtime proc
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
been included. (You may want to decrease the size of |mem| while you
@^debugging@>
are debugging.)
@y
been included. (You may want to decrease the size of |mem| while you
@^debugging@>
are debugging.)

@d free==free_arr
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [12.186] glue_ratio fix  FIXTHIS
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
floating point underflow on the author's computer.
@^system dependencies@>
@^dirty \PASCAL@>

@<Display the value of |glue_set(p)|@>=
g:=float(glue_set(p));
if (g<>float_constant(0))and(glue_sign(p)<>normal) then
  begin print(", glue set ");
  if glue_sign(p)=shrinking then print("- ");
  if abs(mem[p+glue_offset].int)<@'4000000 then print("?.?")
  else if abs(g)>float_constant(20000) then
    begin if g>float_constant(0) then print_char(">")
    else print("< -");
    print_glue(20000*unity,glue_order(p),0);
    end
  else print_glue(round(unity*g),glue_order(p),0);
@^real multiplication@>
  end
@y
floating point underflow on the author's computer.
For the {\mc VAX}, the only possible random value that could hurt is
a reserved value with 1 in the sign bit and 0 for the (excess) exponent.
Because the sign-plus-exponent is in the middle of the word, the chances
of this happening are miniscule, and ignored here.
@^system dependencies@>
@^dirty \PASCAL@>

@<Display the value of |glue_set(p)|@>=
g:=float(glue_set(p));
if (g<>float_constant(0))and(glue_sign(p)<>normal) then
  begin print(", glue set ");
  if glue_sign(p)=shrinking then print("- ");
  if abs(float(glue_set(p)))>20000.0 then
     begin if float(glue_set(p))>0 then print_char(">")
     else print("< -");
     print_glue(20000*unity,glue_order(p),0);
     end
  else print_glue(round(float(glue_set(p))*unity),glue_order(p),0);
@^real multiplication@>
  end
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [29.513] area and extension rules
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@ The file names we shall deal with for illustrative purposes have the
following structure:  If the name contains `\.>' or `\.:', the file area
consists of all characters up to and including the final such character;
otherwise the file area is null.  If the remaining file name contains
`\..', the file extension consists of all such characters from the first
remaining `\..' to the end, otherwise the file extension is null.
@^system dependencies@>

We can scan such file names easily by using two global variables that keep track
of the occurrences of area and extension delimiters:

@<Glob...@>=
@!area_delimiter:pool_pointer; {the most recent `\.>' or `\.:', if any}
@!ext_delimiter:pool_pointer; {the relevant `\..', if any}
@y
@ The file names we shall deal with for SunOS have the
following structure:  If the name contains `\./', the file area
consists of all characters up to and including the final such character;
otherwise the file area is null.  If the remaining file name contains
`\..', the file extension consists of all such characters from the first
remaining `\..' to the end, otherwise the file extension is null.
@^system dependencies@>

We can scan such file names easily by using two global variables that keep
track of the occurrences of area and extension delimiters:

@<Glob...@>=
@!area_delimiter:pool_pointer; {the most recent `\./', if any}
@!ext_delimiter:pool_pointer; {the most recent `\..', if any}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [29.516] more_name
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
  if (c=">")or(c=":") then
@y
  if c="/" then
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [29.520] default format
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
@d format_default_length=20 {length of the |TEX_format_default| string}
@d format_area_length=11 {length of its area part}
@d format_ext_length=4 {length of its `\.{.fmt}' part}
@y
Under Linux we don't give the area part, instead depending
on the path searching that will happen during file opening.

@d format_default_length=9 {length of the |TEX_format_default| string}
@d format_area_length=0 {length of its area part}
@d format_ext_length=4 {length of its `\.{.fmt}' part}
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [29.521] plain format location
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
TEX_format_default:='TeXformats:plain.fmt';
@y
TEX_format_default:='plain.fmt';
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [29.524] format file opening: only try once, with path search
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
  pack_buffered_name(0,loc,j-1); {try first without the system file area}
  if w_open_in(fmt_file) then goto found;
  pack_buffered_name(format_area_length,loc,j-1);
    {now try the system format file area}
  if w_open_in(fmt_file) then goto found;
@y
  pack_buffered_name(0,loc,j-1);
  if w_open_in(fmt_file) then goto found;
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [29.525] make_name_string
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
@x
which simply makes a \TeX\ string from the value of |name_of_file|, should
ideally be changed to deduce the full name of file~|f|, which is the file
most recently opened, if it is possible to do this in a \PASCAL\ program.
@^system dependencies@>

This routine might be called after string memory has overflowed, hence
we dare not use `|str_room|'.

@p function make_name_string:str_number;
var k:1..file_name_size; {index into |name_of_file|}
begin if (pool_ptr+name_length>pool_size)or(str_ptr=max_strings)or
 (cur_length>0) then
  make_name_string:="?"
else  begin for k:=1 to name_length do append_char(xord[name_of_file[k]]);
  make_name_string:=make_string;
  end;
end;
@y
which simply makes a \TeX\ string from the value of |name_of_file|, should
ideally be changed to deduce the full name of file~|f|, which is the file
most recently opened, if it is possible to do this in a \PASCAL\ program.
With the Berkeley {\mc UNIX} version, we know that |name_of_file|
contains |name_of_file| prepended with the directory name that was found
by path searching.
If |name_of_file| starts with |'./'|, we don't use that part of the
name, since {\mc UNIX} users understand that.
@^system dependencies@>

This routine might be called after string memory has overflowed, hence
we dare not use `|str_room|'.

@p function make_name_string:str_number;
var k,@!kstart:1..file_name_size; {index into |name_of_file|}
begin
k:=1;
while (k<file_name_size) and (xord[name_of_file[k]]<>" ") do
    incr(k);
name_length:=k-1; {the real |name_length|}
if (pool_ptr+name_length>pool_size)or(str_ptr=max_strings)or
 (cur_length>0) then
  make_name_string:="?"
else begin
  if (xord[name_of_file[1]]=".") and (xord[name_of_file[2]]="/") then
      kstart:=3
  else
      kstart:=1;
  for k:=kstart to name_length do append_char(xord[name_of_file[k]]);
  make_name_string:=make_string;
  end
end;
@z

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% [30.563] opening tfm file: now path searching is done
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% Set temp_int to value of first byte
@x
@ @<Open |tfm_file| for input@>=
file_opened:=false;
if aire="" then pack_file_name(nom,TEX_font_area,".tfm")
else pack_file_name(nom,aire,".tfm");
if not b_open_in(tfm_file) then abort;
file_opened:=true
@y
@ Here we have to ``prime the pump'' for the |temp_int| trick explained below.

 @<Open |tfm_file| for input@>=
file_opened:=false;
pack_file_name(nom,aire,".tfm");
if not b_open_in(tfm_file) then abort;
begin
      var temp_int:integer;
      temp_int:=tfm_file^;
      if temp_int<0 then temp_int:=temp_int+256;
end;
file_opened:=true
@z


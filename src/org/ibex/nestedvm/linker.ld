ENTRY(_start)
GROUP(-lc -lgcc)

__DYNAMIC  =  0;

SECTIONS {
  . = 0x10000;
  .text : {
    KEEP(*(.init))
    KEEP(*(.fini))
    KEEP(*(.text))
    *(.text.*) 
    *(.gnu.linkonce.t*)
  }

  _etext = .;

  .ctors :
  {
    KEEP (*crtbegin.o(.ctors))
    KEEP (*(EXCLUDE_FILE (*crtend.o) .ctors))
    KEEP (*(SORT(.ctors.*)))
    KEEP (*(.ctors))
  }
  
  .dtors :
  {
    KEEP (*crtbegin.o(.dtors))
    KEEP (*(EXCLUDE_FILE (*crtend.o) .dtors))
    KEEP (*(SORT(.dtors.*)))
    KEEP (*(.dtors))
  }

  . = ALIGN(4k);

  .rodata : {
    *(.rodata*) *(.gnu.linkonce.r*)
    *(.gcc_except_table)
    KEEP(*(.eh_frame)) KEEP(*(.jcr))
  }

  .data : {
    *(.data*) *(.gnu.linkonce.d*)
  }

  . = ALIGN(16);
   _gp = . + 0x8000;
  .sdata : {
    *(.rosdata*) *(.sdata*) *(.gnu.linkonce.s) *(.gnu.linkonce.s.*)
  }
  .sbss : {
    *(.sbss*) *(.scommon*) *(.gnu.linkonce.sb*)
  }

  .bss : {
    *(.bss*) *(.gnu.linkonce.b*) *(COMMON) 
  }

  _end = .;
}

package org.ibex.nestedvm.util;

public final class Sort {
    private Sort() { }
    
    public interface Sortable {
        public int compareTo(Object o);
    }
    
    public static void sort(Sortable[] a) {
        throw new Error("FIXME");
    }
}

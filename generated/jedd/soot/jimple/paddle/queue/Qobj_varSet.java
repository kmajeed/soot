package soot.jimple.paddle.queue;

import soot.util.*;
import soot.jimple.paddle.bdddomains.*;
import soot.jimple.paddle.*;
import soot.jimple.toolkits.callgraph.*;
import soot.*;
import soot.util.queue.*;
import jedd.*;
import java.util.*;

public final class Qobj_varSet extends Qobj_var {
    private LinkedList readers = new LinkedList();
    
    public void add(AllocNode _obj, VarNode _var) {
        Robj_var.Tuple in = new Robj_var.Tuple(_obj, _var);
        for (Iterator it = readers.iterator(); it.hasNext(); ) {
            Robj_varSet reader = (Robj_varSet) it.next();
            reader.add(in);
        }
    }
    
    public void add(final jedd.internal.RelationContainer in) { throw new RuntimeException(); }
    
    public Robj_var reader() {
        Robj_var ret = new Robj_varSet();
        readers.add(ret);
        return ret;
    }
    
    public Qobj_varSet() { super(); }
}
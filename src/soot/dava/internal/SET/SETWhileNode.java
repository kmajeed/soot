package soot.dava.internal.SET;

import java.util.*;
import soot.util.*;
import soot.jimple.*;
import soot.dava.internal.asg.*;
import soot.dava.internal.AST.*;
import soot.dava.internal.javaRep.*;
import soot.dava.toolkits.base.misc.*;

public class SETWhileNode extends SETCycleNode
{
    public SETWhileNode( AugmentedStmt characterizingStmt, IterableSet body)
    {
	super( characterizingStmt, body);

	IterableSet subBody = (IterableSet) body.clone();
	subBody.remove( characterizingStmt);
	add_SubBody( subBody);
    }

    public IterableSet get_NaturalExits()
    {
	IterableSet c = new IterableSet();

	c.add( get_CharacterizingStmt());
	
	return c;
    }
    
    public ASTNode emit_AST()
    {
	return new ASTWhileNode( get_Label(), 
				 (ConditionExpr) ((IfStmt) get_CharacterizingStmt().get_Stmt()).getCondition(), 
				 emit_ASTBody( (IterableSet) body2childChain.get( subBodies.get(0))));
    }
    
    public AugmentedStmt get_EntryStmt()
    {
	return get_CharacterizingStmt();
    }
}
/* Soot - a J*va Optimization Framework
 * Copyright (C) 1997-2000 Etienne Gagnon.  All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

/*
 * Modified by the Sable Research Group and others 1997-1999.  
 * See the 'credits' file distributed with Soot for the complete list of
 * contributors.  (Soot is distributed at http://www.sable.mcgill.ca/soot)
 */


package soot.jimple.toolkits.typing.integer;

import soot.*;
import soot.jimple.*;
import soot.util.*;
import java.util.*;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.*;

/**
 * This class resolves the type of local variables.
 **/
public class TypeResolver
{
  /** All type variable instances **/
  private final List typeVariableList = new ArrayList();

  /** Hashtable: [TypeNode or Local] -> TypeVariable **/
  private final Map typeVariableMap = new HashMap();

  private final JimpleBody stmtBody;

  final TypeVariable BOOLEAN = typeVariable(ClassHierarchy.BOOLEAN);
  final TypeVariable BYTE = typeVariable(ClassHierarchy.BYTE);
  final TypeVariable SHORT = typeVariable(ClassHierarchy.SHORT);
  final TypeVariable CHAR = typeVariable(ClassHierarchy.CHAR);
  final TypeVariable INT = typeVariable(ClassHierarchy.INT);
  final TypeVariable TOP = typeVariable(ClassHierarchy.TOP);
  final TypeVariable R0_1 = typeVariable(ClassHierarchy.R0_1);
  final TypeVariable R0_127 = typeVariable(ClassHierarchy.R0_127);
  final TypeVariable R0_32767 = typeVariable(ClassHierarchy.R0_32767);

  private static final boolean DEBUG = false;

  // categories for type variables (solved = hard, unsolved = soft)
  private List unsolved;
  private List solved;

  /** Get type variable for the given local. **/
  TypeVariable typeVariable(Local local)
  {
    TypeVariable result = (TypeVariable) typeVariableMap.get(local);

    if(result == null)
      {
	int id = typeVariableList.size();
	typeVariableList.add(null);

	result = new TypeVariable(id, this);

	typeVariableList.set(id, result);
	typeVariableMap.put(local, result);
	
	if(DEBUG)
	  {
	    System.out.println("[LOCAL VARIABLE \"" + local + "\" -> " + id + "]");
	  }
      }
    
    return result;
  }

  /** Get type variable for the given type node. **/
  public TypeVariable typeVariable(TypeNode typeNode)
  {
    TypeVariable result = (TypeVariable) typeVariableMap.get(typeNode);

    if(result == null)
      {
	int id = typeVariableList.size();
	typeVariableList.add(null);

	result = new TypeVariable(id, this, typeNode);

	typeVariableList.set(id, result);
	typeVariableMap.put(typeNode, result);
      }

    return result;
  }

  /** Get type variable for the given type. **/
  public TypeVariable typeVariable(Type type)
  {
    return typeVariable(ClassHierarchy.typeNode((BaseType) type));
  }

  /** Get new type variable **/
  public TypeVariable typeVariable()
  {
    int id = typeVariableList.size();
    typeVariableList.add(null);
    
    TypeVariable result = new TypeVariable(id, this);
    
    typeVariableList.set(id, result);
    
    return result;
  }

  private TypeResolver(JimpleBody stmtBody)
  {
    this.stmtBody = stmtBody;
  }

  public static void resolve(JimpleBody stmtBody)
  {
    if(DEBUG)
      {
	System.out.println(stmtBody.getMethod());
      }

    try
      {
	TypeResolver resolver = new TypeResolver(stmtBody);
	resolver.resolve_step_1();
      }
    catch(TypeException e1)
      {
	if(DEBUG)
	  {
	    System.out.println("[integer] Step 1 Exception-->" + e1.getMessage());
	  }
	
	try
	  {
	    TypeResolver resolver = new TypeResolver(stmtBody);
	    resolver.resolve_step_2();
	  }
	catch(TypeException e2)
	  {
	    throw new RuntimeException(e2.getMessage());
	  }
      }
  }
  
  private void debug_vars(String message)
  {
    if(DEBUG)
      {
	int count = 0;
	System.out.println("**** START:" + message);
	for(Iterator i = typeVariableList.iterator(); i.hasNext(); )
	  {
	    TypeVariable var = (TypeVariable) i.next();
	    System.out.println(count++ + " " + var);
	  }
	System.out.println("**** END:" + message);
      }
  }

  private void debug_body()
  {
    if(DEBUG)
      {
	System.out.println("-- Body Start --");
	for(Iterator i = stmtBody.getUnits().iterator(); i.hasNext();)
	  {
	    Stmt stmt = (Stmt) i.next();
	    System.out.println(stmt);
	  }
	System.out.println("-- Body End --");
      }
  }

  private void resolve_step_1() throws TypeException
  {
    collect_constraints_1();
    debug_vars("constraints");

    compute_approximate_types();
    merge_connected_components();
    debug_vars("components");

    merge_single_constraints();
    debug_vars("single");

    assign_types_1();
    debug_vars("assign");

    try
      {
	check_constraints();
      }
    catch(TypeException e)
      {
	if(DEBUG)
	  {
	    System.out.println("[integer] Step 1(check) Exception [" + stmtBody.getMethod() + "]-->" + e.getMessage());
	  }
	
	check_and_fix_constraints();
      }
  }

  private void resolve_step_2() throws TypeException
  {
    collect_constraints_2();
    compute_approximate_types();
    assign_types_2();
    check_and_fix_constraints();
  }

  private void collect_constraints_1()
  {
    ConstraintCollector collector = new ConstraintCollector(this, true);

    for(Iterator i = stmtBody.getUnits().iterator(); i.hasNext();)
      {
	Stmt stmt = (Stmt) i.next();
	if(DEBUG)
	  {
	    System.out.print("stmt: ");
	  }
	collector.collect(stmt, stmtBody);
	if(DEBUG)
	  {
	    System.out.println(stmt);
	  }
      }
  }

  private void collect_constraints_2()
  {
    ConstraintCollector collector = new ConstraintCollector(this, false);

    for(Iterator i = stmtBody.getUnits().iterator(); i.hasNext();)
      {
	Stmt stmt = (Stmt) i.next();
	if(DEBUG)
	  {
	    System.out.print("stmt: ");
	  }
	collector.collect(stmt, stmtBody);
	if(DEBUG)
	  {
	    System.out.println(stmt);
	  }
      }
  }

  private void merge_connected_components() throws TypeException
  {
    compute_solved();
    List list = new LinkedList();
    list.addAll(solved);
    list.addAll(unsolved);
    
    StronglyConnectedComponents.merge(list);
  }
  
  private void merge_single_constraints() throws TypeException
  {
    boolean modified = true;
    
    while(modified)
      {
	modified = false;
	refresh_solved();
	
	for(Iterator i = unsolved.iterator(); i.hasNext(); )
	  {
	    TypeVariable var = (TypeVariable) i.next();
	    List children_to_remove = new LinkedList();
	    TypeNode lca = null;
	    
	    var.fixChildren();
	    
	    for(Iterator j = var.children().iterator(); j.hasNext(); )
	      {
		TypeVariable child = (TypeVariable) j.next();
		TypeNode type = child.type();
		
		if(type != null)
		  {
		    children_to_remove.add(child);
		    
		    if(lca == null)
		      {
			lca = type;
		      }
		    else
		      {
			lca = lca.lca_1(type);
		      }
		  }
	      }
	    
	    if(lca != null)
	      {
		if(DEBUG)
		  {
		    if(lca == ClassHierarchy.TOP)
		      {
			System.out.println("*** TOP *** " + var);
			for(Iterator j = children_to_remove.iterator(); j.hasNext();)
			  {
			    System.out.println("-- " + j.next());
			  }
		      }
		  }
		
		for(Iterator j = children_to_remove.iterator(); j.hasNext();)
		  {
		    TypeVariable child = (TypeVariable) j.next();
		    var.removeChild(child);
		  }
		
		var.addChild(typeVariable(lca));
	      }

	    if(var.children().size() == 1)
	      {
		TypeVariable child = (TypeVariable) var.children().get(0);
		TypeNode type = child.type();
		
		if(type == null || type.type() != null)
		  {
		    var.union(child);
		    modified = true;
		  }
	      }
	  }
      
	if(!modified)
	  {
	    for(Iterator i = unsolved.iterator(); i.hasNext(); )
	      {
		TypeVariable var = (TypeVariable) i.next();
		List parents_to_remove = new LinkedList();
		TypeNode gcd = null;
		
		var.fixParents();
		
		for(Iterator j = var.parents().iterator(); j.hasNext(); )
		  {
		    TypeVariable parent = (TypeVariable) j.next();
		    TypeNode type = parent.type();
		    
		    if(type != null)
		      {
			parents_to_remove.add(parent);
			
			if(gcd == null)
			  {
			    gcd = type;
			  }
			else
			  {
			    gcd = gcd.gcd_1(type);
			  }
		      }
		  }
		
		if(gcd != null)
		  {
		    for(Iterator j = parents_to_remove.iterator(); j.hasNext();)
		      {
			TypeVariable parent = (TypeVariable) j.next();
			var.removeParent(parent);
		      }
		    
		    var.addParent(typeVariable(gcd));
		  }
		
		if(var.parents().size() == 1)
		  {
		    TypeVariable parent = (TypeVariable) var.parents().get(0);
		    TypeNode type = parent.type();
		    
		    if(type == null || type.type() != null)
		      {
			var.union(parent);
			modified = true;
		      }
		  }
	      }
	  }

	if(!modified)
	  {
	    for(Iterator i = unsolved.iterator(); i.hasNext(); )
	      {
		TypeVariable var = (TypeVariable) i.next();
		
		if(var.type() == null && var.inv_approx() != null && var.inv_approx().type() != null)
		  {
		    if(DEBUG)
		      {
			System.out.println("*** I->" + var.inv_approx().type() + " *** " + var);
		      }
		    
		    var.union(typeVariable(var.inv_approx()));
		    modified = true;
		  }
	      }
	  }

	if(!modified)
	  {
	    for(Iterator i = unsolved.iterator(); i.hasNext(); )
	      {
		TypeVariable var = (TypeVariable) i.next();
		
		if(var.type() == null && var.approx() != null && var.approx().type() != null)
		  {
		    if(DEBUG)
		      {
			System.out.println("*** A->" + var.approx().type() + " *** " + var);
		      }
		    
		    var.union(typeVariable(var.approx()));
		    modified = true;
		  }
	      }
	  }

	if(!modified)
	  {
	    for(Iterator i = unsolved.iterator(); i.hasNext(); )
	      {
		TypeVariable var = (TypeVariable) i.next();
		
		if(var.type() == null && var.approx() == ClassHierarchy.R0_32767)
		  {
		    if(DEBUG)
		      {
			System.out.println("*** R->SHORT *** " + var);
		      }
		    
		    var.union(SHORT);
		    modified = true;
		  }
	      }
	  }

	if(!modified)
	  {
	    for(Iterator i = unsolved.iterator(); i.hasNext(); )
	      {
		TypeVariable var = (TypeVariable) i.next();
		
		if(var.type() == null && var.approx() == ClassHierarchy.R0_127)
		  {
		    if(DEBUG)
		      {
			System.out.println("*** R->BYTE *** " + var);
		      }
		    
		    var.union(BYTE);
		    modified = true;
		  }
	      }
	  }

	if(!modified)
	  {
	    for(Iterator i = R0_1.parents().iterator(); i.hasNext(); )
	      {
		TypeVariable var = (TypeVariable) i.next();
		
		if(var.type() == null && var.approx() == ClassHierarchy.R0_1)
		  {
		    if(DEBUG)
		      {
			System.out.println("*** R->BOOLEAN *** " + var);
		      }
		    var.union(BOOLEAN);
		    modified = true;
		  }
	      }
	  }
      }
  }

  private void assign_types_1() throws TypeException
  {
    for(Iterator i = stmtBody.getLocals().iterator(); i.hasNext(); )
      {
	Local local = (Local) i.next();

	if(local.getType() instanceof IntegerType)
	  {
	    TypeVariable var = typeVariable(local);
	    
	    if(var.type() == null || var.type().type() == null)
	      {
		TypeVariable.error("Type Error(21):  Variable without type");
	      }
	    else
	      {
		local.setType(var.type().type());
	      }
	    
	    if(DEBUG)
	      {
		if((var != null) &&
		   (var.approx() != null) &&
		   (var.approx().type() != null) &&
		   (local != null) &&
		   (local.getType() != null) &&
		   !local.getType().equals(var.approx().type()))
		  {
		    System.out.println("local: " + local + ", type: " + local.getType() + ", approx: " + var.approx().type());
		  }
	      }
	  }
      }
  }
  
  private void assign_types_2() throws TypeException
  {
    for(Iterator i = stmtBody.getLocals().iterator(); i.hasNext(); )
      {
	Local local = (Local) i.next();

	if(local.getType() instanceof IntegerType)
	  {
	    TypeVariable var = typeVariable(local);
	    
	    if(var.inv_approx() != null && var.inv_approx() != null)
	      {
		local.setType(var.inv_approx().type());
	      }
	    else if(var.approx().type() != null)
	      {
		local.setType(var.approx().type());
	      }
	    else if(var.approx() == ClassHierarchy.R0_1)
	      {
		local.setType(BooleanType.v());
	      }
	    else if(var.approx() == ClassHierarchy.R0_127)
	      {
		local.setType(ByteType.v());
	      }
	    else
	      {
		local.setType(ShortType.v());
	      }
	  }
      }
  }

  private void check_constraints() throws TypeException
  {
    ConstraintChecker checker = new ConstraintChecker(this, false);
    StringBuffer s = null;

    if(DEBUG)
      {
	s = new StringBuffer("Checking:\n");
      }

    for(Iterator i = stmtBody.getUnits().iterator(); i.hasNext();)
      {
	Stmt stmt = (Stmt) i.next();
	if(DEBUG)
	  {
	    s.append(" " + stmt + "\n");
	  }
	try
	  {
	    checker.check(stmt, stmtBody);
	  }
	catch(TypeException e)
	  {
	    if(DEBUG)
	      {
		System.out.println(s);
	      }
	    throw e;
	  }
      }
  }

  private void check_and_fix_constraints() throws TypeException
  {
    ConstraintChecker checker = new ConstraintChecker(this, true);
    StringBuffer s = null;
    PatchingChain units = stmtBody.getUnits();
    Stmt[] stmts = new Stmt[units.size()];
    units.toArray(stmts);

    if(DEBUG)
      {
	s = new StringBuffer("Checking:\n");
      }

    for(int i = 0; i < stmts.length; i++)
      {
	Stmt stmt = stmts[i];

	if(DEBUG)
	  {
	    s.append(" " + stmt + "\n");
	  }
	try
	  {
	    checker.check(stmt, stmtBody);
	  }
	catch(TypeException e)
	  {
	    if(DEBUG)
	      {
		System.out.println(s);
	      }
	    throw e;
	  }
      }
  }

  private void compute_approximate_types() throws TypeException
  {
    TreeSet workList = new TreeSet();

    for(Iterator i = typeVariableList.iterator(); i.hasNext(); )
      {
	TypeVariable var = (TypeVariable) i.next();

	if(var.type() != null)
	  {
	    workList.add(var);
	  }
      }

    TypeVariable.computeApprox(workList);

    workList = new TreeSet();

    for(Iterator i = typeVariableList.iterator(); i.hasNext(); )
      {
	TypeVariable var = (TypeVariable) i.next();
	
	if(var.type() != null)
	  {
	    workList.add(var);
	  }
      }

    TypeVariable.computeInvApprox(workList);

    for(Iterator i = typeVariableList.iterator(); i.hasNext(); )
      {
	TypeVariable var = (TypeVariable) i.next();

	if (var.approx() == null)
	  {
	    var.union(INT);
	  }
      }
  }
  
  private void compute_solved()
  {
    Set unsolved_set = new TreeSet();
    Set solved_set = new TreeSet();
    
    for(Iterator i = typeVariableList.iterator(); i.hasNext(); )
      {
	TypeVariable var = (TypeVariable) i.next();
	
	if(var.type() == null)
	  {
	    unsolved_set.add(var);
	  }
	else
	  {
	    solved_set.add(var);
	  }
      }
    
    solved = new LinkedList(solved_set);
    unsolved = new LinkedList(unsolved_set);
  }

  private void refresh_solved() throws TypeException
  {
    Set unsolved_set = new TreeSet();
    Set solved_set = new TreeSet(solved);
    
    for(Iterator i = unsolved.iterator(); i.hasNext(); )
      {
	TypeVariable var = (TypeVariable) i.next();
	
	if(var.type() == null)
	  {
	    unsolved_set.add(var);
	  }
	else
	  {
	    solved_set.add(var);
	  }
      }
    
    solved = new LinkedList(solved_set);
    unsolved = new LinkedList(unsolved_set);
  }
}
/* Soot - a J*va Optimization Framework
 * Copyright (C) 2002 Ondrej Lhotak
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package soot.jimple.spark.builder;
import soot.jimple.spark.*;
import soot.jimple.spark.pag.*;
import soot.jimple.*;
import soot.*;
import java.util.*;
import soot.jimple.toolkits.invoke.InvokeGraph;
import soot.jimple.spark.internal.*;

/** Class implementing builder parameters (this decides
 * what kinds of nodes should be built for each kind of Soot value).
 * @author Ondrej Lhotak
 */
class StandardParms extends AbstractJimpleValueSwitch implements Parms {
    public StandardParms( PAG pag ) {
	this.pag = pag;
	ig = Scene.v().getActiveInvokeGraph();
    }
    /** Sets the method for which a graph is currently being built. */
    final public void setCurrentMethod( SootMethod m ) {
	currentMethod = m;
        if( m != null ) {
            if( !m.isStatic() ) {
                SootClass c = m.getDeclaringClass();
                if( c == null ) {
                    throw new RuntimeException( "Method "+m+" has no dclaring lass" );
                }
                caseThis( m );
            }
            for( int i = 0; i < m.getParameterCount(); i++ ) {
                if( m.getParameterType(i) instanceof RefLikeType ) {
                    caseParm( m, i );
                }
            }
            Type retType = m.getReturnType();
            if( retType instanceof RefLikeType ) {
                caseRet( m );
            }
        }
    }
    /** Adds method target as a possible target of the invoke expression in s.
     * If target is null, only creates the nodes for the call site,
     * without actually connecting them to any target method.
     * TouchedNodes is an out parameter that is filled in with all the
     * nodes to which edges were added by adding the target. It may be
     * null if the caller does not need this information. */
    final public void addCallTarget( Stmt s, SootMethod target, Collection addedEdges ) {
        InvokeExpr ie = (InvokeExpr) s.getInvokeExpr();
        int numArgs = ie.getArgCount();
        for( int i = 0; i < numArgs; i++ ) {
            Value arg = ie.getArg( i );
            if( !( arg.getType() instanceof RefLikeType ) ) continue;
            arg.apply( this );
            Node argNode = getNode();
            if( argNode == null ) continue;
            argNode = argNode.getReplacement();
            if( target != null ) {
                Node parm = caseParm( target, i ).getReplacement();
                addEdge( argNode, parm );
                if( addedEdges != null ) {
                    Node[] edge = { argNode, parm };
                    addedEdges.add( edge );
                }
            }
        }
        if( ie instanceof InstanceInvokeExpr ) {
            InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
            iie.getBase().apply( this );
            Node baseNode = getNode().getReplacement();
            if( target != null ) {
                Node thisRef = caseThis( target ).getReplacement();
                addEdge( baseNode, thisRef );
                if( addedEdges != null ) {
                    Node[] edge = { baseNode, thisRef };
                    addedEdges.add( edge );
                }
            }
        }
        if( s instanceof AssignStmt ) {
            Value dest = ( (AssignStmt) s ).getLeftOp();
            if( dest.getType() instanceof RefLikeType ) {
                dest.apply( this );
                Node destNode = getNode().getReplacement();
                if( destNode instanceof VarNode ) {
                    ((VarNode) destNode).setInterProcTarget();
                }
                if( target != null ) {
                    Node retNode = caseRet( target ).getReplacement();
                    addEdge( retNode, destNode );
                    if( addedEdges != null ) {
                        Node[] edge = { retNode, destNode };
                        addedEdges.add( edge );
                    }
                }
            }
        }
        if( ie instanceof InstanceInvokeExpr ) {
            InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;
            Local base = (Local) iie.getBase();
            if( target != null
            && Scene.v().getOrMakeFastHierarchy().
                    canStoreType( base.getType(), RefType.v("java.lang.Runnable") ) 
            && target.getSignature().equals( "void start()" )  
            && target.getDeclaringClass().declaresMethod( "void run()" ) ) {

                addCallTarget( s,
                    target.getDeclaringClass().getMethod( "void run()" ),
                    addedEdges );
            }
        }
    }
    /** Adds the edges required for this statement to the graph. */
    final public void handleStmt( Stmt s ) {
	if( s.containsInvokeExpr() ) {
	    InvokeExpr ie = (InvokeExpr) s.getInvokeExpr();
            Iterator it = ig.getTargetsOf( s ).iterator();
            while( it.hasNext() ) {
                SootMethod target = (SootMethod) it.next();
                addCallTarget( s, target, null );
            }
	    return;
	}
	s.apply( new AbstractStmtSwitch() {
	    final public void caseAssignStmt(AssignStmt as) {
                Value l = as.getLeftOp();
                Value r = as.getRightOp();
		if( !( l.getType() instanceof RefLikeType ) ) return;
		l.apply( StandardParms.this );
		Node dest = getNode();
		r.apply( StandardParms.this );
		Node src = getNode();
                if( l instanceof InstanceFieldRef ) {
                    ((InstanceFieldRef) l).getBase().apply( StandardParms.this );
                    pag.addDereference( (VarNode) getNode() );
                }
                if( r instanceof InstanceFieldRef ) {
                    ((InstanceFieldRef) r).getBase().apply( StandardParms.this );
                    pag.addDereference( (VarNode) getNode() );
                }
		addEdge( src, dest );
	    }
	    final public void caseReturnStmt(ReturnStmt rs) {
		if( !( rs.getOp().getType() instanceof RefLikeType ) ) return;
		rs.getOp().apply( StandardParms.this );
                Node retNode = getNode();
                addEdge( retNode, caseRet( currentMethod ) );
	    }
	    final public void caseIdentityStmt(IdentityStmt is) {
		if( !( is.getLeftOp().getType() instanceof RefLikeType ) ) return;
		is.getLeftOp().apply( StandardParms.this );
		Node dest = getNode();
		is.getRightOp().apply( StandardParms.this );
		Node src = getNode();
		addEdge( src, dest );
	    }
	    final public void caseThrowStmt(ThrowStmt ts) {
		ts.getOp().apply( StandardParms.this );
		addEdge( getNode(), caseThrow() );
	    }
	} );
    }
    final public Node getNode() {
	return (Node) getResult();
    }
    final public void addEdge( Node from, Node to ) {
        if( from != null ) {
            pag.addEdge( from, to );
        }
    }
    final public Node caseArgv() {
	AllocNode argv = pag.makeAllocNode( 
		new Pair( currentMethod, PointsToAnalysis.STRING_ARRAY_NODE ),
		strAr );
        VarNode sanl = pag.makeVarNode(
                new Pair( currentMethod, PointsToAnalysis.STRING_ARRAY_NODE_LOCAL ),
                strAr, null );
	AllocNode stringNode = pag.makeAllocNode( 
		new Pair( currentMethod, PointsToAnalysis.STRING_NODE ),
		string );
	VarNode stringNodeLocal = pag.makeVarNode(
		new Pair( currentMethod, PointsToAnalysis.STRING_NODE_LOCAL ),
		string, null );
	addEdge( argv, sanl );
	addEdge( stringNode, stringNodeLocal );
	addEdge( stringNodeLocal, 
                pag.makeFieldRefNode( sanl, ArrayElement.v() ) );
	return sanl;
    }
    final public Node caseAnyType() {
	return pag.makeAllocNode( AnyType.v(), AnyType.v() );
    }
    final public Node caseThis( SootMethod m ) {
	VarNode ret = pag.makeVarNode(
		    new Pair( m, PointsToAnalysis.THIS_NODE ),
		    m.getDeclaringClass().getType(), m );
        ret.setInterProcTarget();
        return ret;
    }

    final public Node caseParm( SootMethod m, int index ) {
	if( m.isStatic() || !pag.getOpts().parmsAsFields() ) {
	    VarNode ret = pag.makeVarNode(
			new Pair( m, new Integer( index ) ),
			m.getParameterType( index ), m );
            ret.setInterProcTarget();
            return ret;
	} else { 
	    return pag.makeFieldRefNode(
			new Pair( m, PointsToAnalysis.THIS_NODE ),
			m.getDeclaringClass().getType(),
			Parm.v( m, index ), m );
	}
    }

    final public Node caseRet( SootMethod m ) {
	if( m.isStatic() || !pag.getOpts().returnsAsFields() ) {
	    return pag.makeVarNode(
			Parm.v( m, PointsToAnalysis.RETURN_NODE ),
			m.getReturnType(), m );
	} else { 
	    return pag.makeFieldRefNode(
			new Pair( m, PointsToAnalysis.THIS_NODE ),
			m.getDeclaringClass().getType(),
			Parm.v( m, PointsToAnalysis.RETURN_NODE ), m );
	}
    }
    final public Node caseArray( Object base, ArrayType arrayType ) {
	return pag.makeFieldRefNode( base, arrayType,
		    ArrayElement.v(), currentMethod );
    }
    /* End of public methods. */
    /* End of package methods. */

    // OK, these ones are public, but they really shouldn't be; it's just
    // that Java requires them to be, because they override those other
    // public methods.
    final public void caseArrayRef( ArrayRef ar ) {
	setResult( caseArray( ar.getBase(), (ArrayType) ar.getBase().getType() ) );
    }
    final public void caseCastExpr( CastExpr ce ) {
	Pair castPair = new Pair( ce, PointsToAnalysis.CAST_NODE );
	ce.getOp().apply( this );
	Node opNode = getNode();
	Node castNode = pag.makeVarNode( castPair, ce.getCastType(), currentMethod );
	addEdge( opNode, castNode );
	setResult( castNode );
    }
    final public void caseCaughtExceptionRef( CaughtExceptionRef cer ) {
	setResult( caseThrow() );
    }
    final public void caseInstanceFieldRef( InstanceFieldRef ifr ) {
	if( pag.getOpts().ignoreBaseObjects() ) {
	    setResult( pag.makeVarNode( 
			ifr.getField(), 
			ifr.getField().getType(), currentMethod ) );
	} else {
	    setResult( pag.makeFieldRefNode( 
			ifr.getBase(), 
			ifr.getBase().getType(),
			ifr.getField(),
                        currentMethod ) );
	}
    }
    final public void caseLocal( Local l ) {
	setResult( pag.makeVarNode( l,  l.getType(), currentMethod ) );
    }
    final public void caseNewArrayExpr( NewArrayExpr nae ) {
        setResult( pag.makeAllocNode( nae, nae.getType() ) );
    }
    final public void caseNewExpr( NewExpr ne ) {
        if( pag.getOpts().mergeStringBuffer() 
        && ne.getType().equals( RefType.v("java.lang.StringBuffer" ) ) ) {
            setResult( pag.makeAllocNode( ne.getType(), ne.getType() ) );
        } else {
            setResult( pag.makeAllocNode( ne, ne.getType() ) );
        }
    }
    final public void caseNewMultiArrayExpr( NewMultiArrayExpr nmae ) {
        ArrayType type = (ArrayType) nmae.getType();
        AllocNode prevAn = pag.makeAllocNode(
            new Pair( nmae, new Integer( type.numDimensions ) ), type );
        VarNode prevVn = pag.makeVarNode( prevAn, prevAn.getType(), currentMethod );
        setResult( prevAn );
        while( true ) {
            Type t = type.getElementType();
            if( !( t instanceof ArrayType ) ) break;
            type = (ArrayType) t;
            AllocNode an = pag.makeAllocNode(
                new Pair( nmae, new Integer( type.numDimensions ) ), type );
            VarNode vn = pag.makeVarNode( an, an.getType(), currentMethod );
            addEdge( an, vn );
            addEdge( vn, pag.makeFieldRefNode( prevVn, ArrayElement.v() ) );
            prevAn = an;
            prevVn = vn;
        }
    }
    final public void caseParameterRef( ParameterRef pr ) {
	setResult( caseParm( currentMethod, pr.getIndex() ) );
    }
    final public void caseStaticFieldRef( StaticFieldRef sfr ) {
	setResult( pag.makeVarNode( 
		    sfr.getField(), 
		    sfr.getField().getType(), null ) );
    }
    final public void caseStringConstant( StringConstant sc ) {
        VarNode stringConstantLocal = pag.makeVarNode(
            PointsToAnalysis.STRING_NODE_LOCAL,
            RefType.v( "java.lang.String" ), null );
        AllocNode stringConstant = pag.makeAllocNode(
            PointsToAnalysis.STRING_NODE,
            RefType.v( "java.lang.String" ) );
        addEdge( stringConstant, stringConstantLocal );
        setResult( stringConstantLocal );
    }
    final public void caseThisRef( ThisRef tr ) {
	setResult( caseThis( currentMethod ) );
    }
    final public void caseNullConstant( NullConstant nr ) {
	setResult( null );
    }
    final public void defaultCase( Object v ) {
	throw new RuntimeException( "failed to handle "+v );
    }
    protected Node caseThrow() {
	return pag.makeVarNode( PointsToAnalysis.EXCEPTION_NODE,
		    RefType.v("java.lang.Throwable"), null );
    }
    private static final RefType string = RefType.v("java.lang.String");
    private static final ArrayType strAr = ArrayType.v(string, 1);
    private static final List strArL = Collections.singletonList( strAr );
    private static final String main = SootMethod.getSubSignature( "main", strArL, VoidType.v() );
    private static final String exit = SootMethod.getSubSignature( "exit", Collections.EMPTY_LIST, VoidType.v() );
    private static final String run = SootMethod.getSubSignature( "run", Collections.EMPTY_LIST, VoidType.v() );
    private static final String finalize = SootMethod.getSubSignature( "finalize", Collections.EMPTY_LIST, VoidType.v() );


    protected PAG pag;
    protected InvokeGraph ig;
    protected SootMethod currentMethod;
}

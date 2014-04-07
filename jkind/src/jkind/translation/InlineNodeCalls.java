package jkind.translation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import jkind.lustre.Equation;
import jkind.lustre.Expr;
import jkind.lustre.IdExpr;
import jkind.lustre.Node;
import jkind.lustre.NodeCallExpr;
import jkind.lustre.Program;
import jkind.lustre.TupleExpr;
import jkind.lustre.VarDecl;
import jkind.lustre.visitors.ExprMapVisitor;
import jkind.util.Util;

public class InlineNodeCalls extends ExprMapVisitor {
	public static Node program(Program program) {
		InlineNodeCalls inliner = new InlineNodeCalls(Util.getNodeTable(program.nodes));
		Node main = program.getMainNode();

		List<Expr> assertions = inliner.visitExprs(main.assertions);
		List<Equation> equations = inliner.visitEquationsQueue(main.equations);

		List<VarDecl> locals = append(main.locals, inliner.newLocals);
		List<String> properties = append(main.properties, inliner.newProperties);

		return new Node(main.location, main.id, main.inputs, main.outputs, locals, equations,
				properties, assertions);
	}

	private final Map<String, Node> nodeTable;
	private final List<VarDecl> newLocals = new ArrayList<>();
	private final List<String> newProperties = new ArrayList<>();
	private final Map<String, Integer> usedPrefixes = new HashMap<>();
	private final Queue<Equation> queue = new ArrayDeque<>();

	private InlineNodeCalls(Map<String, Node> nodeTable) {
		this.nodeTable = nodeTable;
	}

	public List<Equation> visitEquationsQueue(List<Equation> equations) {
		queue.addAll(equations);
		List<Equation> result = new ArrayList<>();

		while (!queue.isEmpty()) {
			Equation eq = queue.poll();
			result.add(new Equation(eq.location, eq.lhs, eq.expr.accept(this)));
		}
		return result;
	}

	@Override
	public Expr visit(NodeCallExpr e) {
		return TupleExpr.compress(visitNodeCallExpr(e));
	}

	public List<IdExpr> visitNodeCallExpr(NodeCallExpr e) {
		String prefix = newPrefix(e.node);
		Node node = nodeTable.get(e.node.substring(e.node.lastIndexOf('.') + 1));

		Map<String, IdExpr> translation = getTranslation(prefix, node);

		createInputEquations(node.inputs, e.args, translation);
		createAssignmentEquations(prefix, node.equations, translation);
		accumulateProperties(node.properties, translation);

		List<IdExpr> result = new ArrayList<>();
		for (VarDecl decl : node.outputs) {
			result.add(translation.get(decl.id));
		}
		return result;
	}

	private Map<String, IdExpr> getTranslation(String prefix, Node node) {
		Map<String, IdExpr> translation = new HashMap<>();
		for (VarDecl decl : Util.getVarDecls(node)) {
			String id = prefix + decl.id;
			newLocals.add(new VarDecl(id, decl.type));
			translation.put(decl.id, new IdExpr(id));
		}
		return translation;
	}

	private void createInputEquations(List<VarDecl> inputs, List<Expr> args,
			Map<String, IdExpr> translation) {
		for (int i = 0; i < inputs.size(); i++) {
			IdExpr idExpr = translation.get(inputs.get(i).id);
			Expr arg = args.get(i);
			queue.add(new Equation(idExpr, arg));
		}
	}

	private void createAssignmentEquations(final String prefix, List<Equation> equations,
			Map<String, IdExpr> translation) {
		SubstitutionVisitor substitution = new SubstitutionVisitor(translation) {
			@Override
			public Expr visit(NodeCallExpr e) {
				return new NodeCallExpr(e.location, prefix + e.node, visitExprs(e.args));
			}
		};

		for (Equation eq : equations) {
			List<IdExpr> lhs = new ArrayList<>();
			for (IdExpr idExpr : eq.lhs) {
				lhs.add(translation.get(idExpr.id));
			}
			Expr expr = eq.expr.accept(substitution);
			queue.add(new Equation(eq.location, lhs, expr));
		}
	}

	private String newPrefix(String prefix) {
		int i = 0;
		if (usedPrefixes.containsKey(prefix)) {
			i = usedPrefixes.get(prefix);
		}
		usedPrefixes.put(prefix, i + 1);
		return prefix + "~" + i + ".";
	}

	private void accumulateProperties(List<String> properties, Map<String, IdExpr> translation) {
		for (String property : properties) {
			newProperties.add(translation.get(property).id);
		}
	}

	private static <T> List<T> append(List<T> list1, List<T> list2) {
		List<T> result = new ArrayList<>();
		result.addAll(list1);
		result.addAll(list2);
		return result;
	}
}

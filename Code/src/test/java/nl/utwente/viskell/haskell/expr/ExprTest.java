package nl.utwente.viskell.haskell.expr;

import nl.utwente.viskell.ghcj.HaskellException;
import nl.utwente.viskell.haskell.env.Environment;
import nl.utwente.viskell.haskell.type.HaskellTypeError;
import nl.utwente.viskell.haskell.type.Type;
import nl.utwente.viskell.haskell.type.TypeClass;
import nl.utwente.viskell.haskell.type.TypeCon;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ExprTest {
    private final TypeCon integer = Type.con("Int");
    private final TypeCon floating = Type.con("Float");
    private final TypeCon doubl = Type.con("Double");
    private final TypeCon string = Type.con("String");

    private TypeClass num;

    private Expression expr;
    private Environment env;

    @Before
    public final void setUp() throws HaskellException {
        this.env = new Environment();

        this.num = new TypeClass("Num", integer, floating, doubl);
        this.env.addTypeClass(this.num);
        this.env.addTestSignature("(*)", "Num a => a -> a -> a");
        this.env.addTestSignature("map", "(a -> b) -> [a] -> [b]");

        this.expr = new Apply(
                new Apply(
                        this.env.useFun("map"),
                        this.env.useFun("(*)")
                ),
                new Value(
                        Type.listOf(this.integer),
                        "[1, 2, 3, 5, 7]"
                )
        );

    }

    @Test
    public final void testAnalyze() throws HaskellException {
        assertEquals("[Int -> Int]", this.expr.inferType().prettyPrint());
    }

    @Test(expected = HaskellTypeError.class)
    public final void testTypeclassError() throws HaskellException {
        expr = new Apply(
                new Apply(
                        this.env.useFun("map"),
                        this.env.useFun("(*)")
                ),
                new Value(
                        Type.listOf(this.string),
                        "[\"a\", \"b\", \"c\"]"
                )
        );
        assertNotEquals("[(String -> String)]", expr.inferType().prettyPrint());
    }

    @Test
    public final void testValueToHaskell() throws HaskellException {
        final Expression v = new Value(this.integer, "10");
        assertEquals(this.integer.prettyPrint(), v.inferType().prettyPrint());
        assertEquals("(10)", v.toHaskell());
    }

    @Test
    public final void testToHaskell() {
        assertEquals("((map (*)) ([1, 2, 3, 5, 7]))", this.expr.toHaskell());
    }
}

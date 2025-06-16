/*
 This file is part of the BlueJ program. 
 Copyright (C) 2014,2016,2017,2022  Michael Kolling and John Rosenberg
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.parser;

import java.io.StringReader;
import java.util.LinkedList;
import java.util.List;

import bluej.extensions2.SourceType;
import bluej.parser.lexer.LocatableToken;

import junit.framework.TestCase;

public class NewParserTest extends TestCase
{
    /**
     * Test array as type parameter
     */
    public void test1()
    {
        StringReader sr = new StringReader(
                "LinkedList<String[]>"
        );
        InfoParser ip = new InfoParser(sr, null);
        List<LocatableToken> ll = new LinkedList<LocatableToken>();
        assertTrue(ip.parseTypeSpec(false, true, ll));
        // 6 tokens: LinkedList, '<', String, '[', ']', '>'
        assertEquals(6, ll.size());
    }

    /**
     * Test handling of '>>' sequence in type spec
     */
    public void test2()
    {
        StringReader sr = new StringReader(
                "LinkedList<List<String[]>>"
        );
        InfoParser ip = new InfoParser(sr, null);
        List<LocatableToken> ll = new LinkedList<LocatableToken>();
        assertTrue(ip.parseTypeSpec(false, true, ll));
        // 8 tokens: LinkedList, '<', List, '<', String, '[', ']', '>>'
        assertEquals(8, ll.size());
    }

    /**
     * Test multiple type parameters
     */
    public void test3()
    {
        StringReader sr = new StringReader(
                "Map<String,Integer> v1; "
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test generic inner class of generic outer class
     */
    public void test4()
    {
        StringReader sr = new StringReader(
                "Outer<String>.Inner<String> v8; "
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test wildcard type parameters
     */
    public void test5()
    {
        StringReader sr = new StringReader(
                "A<?> v8; " +
                "A<? extends String> v9; " +
                "A<? super String> v10;"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
        ip.parseStatement();
        ip.parseStatement();
    }

    /**
     * Test less-than operator.
     */
    public void test6()
    {
        StringReader sr = new StringReader(
                "b = (i < j);"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test a funky statement.
     */
    public void test7()
    {
        StringReader sr = new StringReader(
                "boolean.class.equals(T.class);"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test a class declaration with a single type parameter.
     */
    public void test8()
    {
        StringReader sr = new StringReader(
                "class A<T>{}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }

    /**
     * Test a class declaration containing a semi-colon
     */
    public void test9()
    {
        StringReader sr = new StringReader(
                "class A{;}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }

    /**
     * Test a simple enum
     */
    public void test10()
    {
        StringReader sr = new StringReader(
                "enum A {" +
                "    one, two, three;" +
                "    private int x;" +
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }

    /**
     * Test array declarators after a variable name.
     */
    public void test11()
    {
        StringReader sr = new StringReader(
                "int a[] = {1, 2, 3};"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test array declarators after a method parameter name.
     */
    public void test12()
    {
        StringReader sr = new StringReader(
                "int a[], int[] b);"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseMethodParamsBody();
    }

    /** 
     * Test array declarators after a field name.
     */
    public void test13()
    {
        StringReader sr = new StringReader(
                "class A { int x[] = {1,2,3}, y = 5; }"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }
    
    /**
     * Test multiple field definition in one statement.
     */
    public void test13p2()
    {
        StringReader sr = new StringReader(
                "class A { private int x, y; }"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }

    /**
     * Test multiple variable declaration in a single statement.
     */
    public void test14()
    {
        StringReader sr = new StringReader(
                "int x[], y = 3, z, q;"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test annotation declaration
     */
    public void test15()
    {
        StringReader sr = new StringReader(
                "public @interface Copyright{  String value();}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }

    /**
     * Test use of marker annotation
     */
    public void test16()
    {
        StringReader sr = new StringReader(
                "@Preliminary public class TimeTravel { }"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }

    /**
     * Test the use of an annotation.
     */
    public void test17()
    {
        StringReader sr = new StringReader(
                "@Copyright(\"2002 Yoyodyne Propulsion Systems\")"+
                "public class NewParserTest { }"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }

    /**
     * Test the '?:' operator.
     */
    public void testQuestionOperator()
    {
        StringReader sr = new StringReader(
                "Object g = (x<y) ? null : null;"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test a static method call.
     */
    public void testStaticMethodCall()
    {
        StringReader sr = new StringReader(
                "AAA.bbb(1,2,3);"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test the declaration of an annotation.
     */
    public void test18()
    {
        StringReader sr = new StringReader(
                "public @interface RequestForEnhancement { " +
                "int id();" +
                "String synopsis();"+
                "String engineer()  default \"[unassigned]\"; "+
                "String date()      default \"[unimplemented]\"; "+
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }


    /**
     * Test the use of an annotation.
     */
    public void test19()
    {
        StringReader sr = new StringReader(
                "public @RequestForEnhancement(" +
                "id       = 2868724," +
                "synopsis = \"Enable time-travel\","+
                "engineer = \"Mr. Peabody\", "+
                "date     = \"4/1/3007\""+
                ")"+
                "static void travelThroughTime(Date destination) { } }"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseClassBody();
    }

    /**
     * Test the use of an annotation for a method.
     */
    public void test20()
    {
        StringReader sr = new StringReader(
                "@Test public static void m1() { } }"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseClassBody();
    }

    /**
     * Test the use of a qualified annotation
     */
    public void test21()
    {
        StringReader sr = new StringReader(
                "@Test.RequestForEnhancement int req;"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }



    public void test22()
    {
        StringReader sr = new StringReader(
                "@Expression(\"execution(* com.mypackage.Target.*(..))\") "+
                "Pointcut pc1; "
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();

    }

    public void test23()
    {
        StringReader sr = new StringReader(
                "@Expression(\"execution(* com.mypackage.Target.*(..))\") "+
                "volatile Pointcut pc1; "
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    public void test24()
    {
        StringReader sr = new StringReader(
                "(byte)++(bb)"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseExpression();
    }

    public void test25()
    {
        StringReader sr = new StringReader(
                "new String[]{\"hello\", \"goodbye\",}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseExpression();
    }
    
    // Lambda syntax tests
    private void checkLambdaExpression(String s)
    {
        // test when parenthesized:
        StringReader sr = new StringReader("(" + s + ")");
        SourceParser ip = new SourceParser(sr);
        ip.parseExpression();
        
        // test when used in assigment:
        sr = new StringReader("Runnable r = " + s + ";");
        ip = new SourceParser(sr);
        ip.parseStatement();
        
        // test when used as method parameter:
        sr = new StringReader("doSomething(" + s + ");");
        ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    
    public void testLambdaNoParameters1()
    {
        checkLambdaExpression("() -> {}");
    }

    public void testLambdaNoParameters2()
    {
        checkLambdaExpression("() -> 42");   // No parameters; expression body
    }

    public void testLambdaNoParameters3()
    {
        checkLambdaExpression("() -> null"); // No parameters; expression body
    }

    public void testLambdaNoParameters4()
    {
        checkLambdaExpression("() -> {return 42;}"); // No parameters; block body with return
    }

    public void testLambdaNoParameters5()
    {
        checkLambdaExpression("() -> System.gc()"); // No parameters; void block body
    }

    public void testLambdaNoParameters6()
    {
        String s = "() -> {\n "
                + "    if (true) return 12;\n"
                + "    else {\n"
                + "        int result = 15;\n"
                + "        for (int i = 1; i < 10; i++)\n"
                + "            result *= i;\n"
                + "        return result;\n"
                + "    }\n"
                + "}\n"; // Complex block body with returns
        
        checkLambdaExpression(s);
    }
    
    public void testLambdaSingleParameter1()
    {
        checkLambdaExpression("(int x) -> x+1"); // Single declared-type parameter
    }
    
    public void testLambdaSingleParameter2()
    {
        checkLambdaExpression("(x) -> x+1"); // Single inferred-type parameter
    }
    
    public void testLambdaSingleParameter3()
    {
        checkLambdaExpression("x -> x+1"); // Parens optional for single inferred-type case
    }
    
    public void testLambdaSingleParameter4()
    {
        checkLambdaExpression("t -> { t.start(); } "); // Single inferred-type parameter
    }
    
    public void testLambdaSingleParameter5()
    {
        checkLambdaExpression("(final int x) -> x+1"); // Modified declared-type parameter
    }
    
    public void testLambdaSingleParameter6()
    {
        checkLambdaExpression("(CustomClass x) -> x+1"); // Modified declared-type parameter
    }
    
    public void testLambdaSingleParameter7()
    {
        checkLambdaExpression("(int... x) -> x+1"); // Modified declared-type parameter
    }

    public void testLambdaVarParameter1()
    {
        checkLambdaExpression("(var x) -> x+1");
    }

    public void testLambdaVarParameter2()
    {
        checkLambdaExpression("(var x, y) -> x+1"); // Modified declared-type parameter
    }

    public void testLambdaVarParameter3()
    {
        checkLambdaExpression("(var x, var y) -> x+1"); // Modified declared-type parameter
    }
    
    public void testLambdaVarParameter4()
    {
        checkLambdaExpression("(x, var y) -> x+1"); // Modified declared-type parameter
    }

    public void testLambdaVarParameter5()
    {
        checkLambdaExpression("(x, var y, int... z) -> x+1"); // Modified declared-type parameter
    }
    
    public void testLambdaMultipleParameters1()
    {
        checkLambdaExpression("(int x, float y) -> x+y"); // Multiple declared-type parameters
    }
    
    public void testLambdaMultipleParameters2()
    {
        checkLambdaExpression("(x,y) -> x+y"); // Multiple inferred-type parameters
    }    

    public void testMethodRef2()
    {
        checkLambdaExpression("SomeClass::someMethod");
    }
    
    public void testMethodRef3()
    {
        checkLambdaExpression("somepkg.someotherpkg.SomeClass::someMethod");
    }
    
    public void testMethodRef4()
    {
        checkLambdaExpression("SomeClass::new");
    }
    
    /** Test generic method call */
    public void testGenericMethodCall()
    {
        // someMethod might be declared something like:
        //    public <T> void someMethod(T arg) { }
        StringReader sr = new StringReader(
                "this.<String>someMethod(\"hello\")"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseExpression();
    }
    
    public void testPrimitiveCast()
    {
        StringReader sr = new StringReader(
                "(byte)(a + 1)"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseExpression();
    }
    
    public void testSynchronizedModifier()
    {
        StringReader sr = new StringReader(
                "interface A {" +
                "synchronized int someMethod();" +
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
        
        sr = new StringReader("synchronized { throw new Exception(); }");
        ip = new SourceParser(sr);
        ip.parseStatement();
        
        sr = new StringReader("synchronized(getSomeValue()) { throw new Exception(); }");
        ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    public void testVarargsMethod()
    {
        StringReader sr = new StringReader(
                "interface A {" +
                "synchronized int someMethod(int ... a);" +
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseTypeDef();
    }
    
    /**
     * Test for loop with double initializer
     */
    public void testForLoop()
    {
        StringReader sr = new StringReader(
                "for (int i = 8, j; ; ) {" +
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    /**
     * Test for loop where initializer has modifier(s)
     */
    public void testForLoop2()
    {
        StringReader sr = new StringReader(
                "for (final int i : intArray) {" +
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    /**
     * Test for loop where initializer variables are already declared
     */
    public void testForLoop3()
    {
        // if i and j are already declared, this should still parse:
        StringReader sr = new StringReader(
                "for (i = 0, j = 8; i++; i < 10) {" +
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test for loop where loop var is an array (and brackets on LHS)
     */
    public void testForLoop4()
    {
        StringReader sr = new StringReader(
                "for (int[][] lesser : multidimArray) {}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }

    /**
     * Test for loop where loop var is an array (and brackets on RHS)
     */
    public void testForLoop5()
    {
        StringReader sr = new StringReader(
                "for (int lesser[][] : multidimArray) {}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }


    public void testFunkyCast()
    {
        StringReader sr = new StringReader(
                "return (Insets)((ContainerPeer)peer).insets().clone();"
                );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    public void testMethodParamModifier()
    {
        StringReader sr = new StringReader(
                "interface I {" +
                "void someMethod(final String argument);" +
                "}"
                );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    public void testParenthesizedValue()
    {
        StringReader sr = new StringReader(
                "new int[] { 1, 2 + (someValue), 3 }"
                );
        SourceParser ip = new SourceParser(sr);
        ip.parseExpression();
    }
    
    public void testTopLevelExtraSemis()
    {
        StringReader sr = new StringReader(
                "import java.lang.*; ;" +
                "interface A {" +
                "};"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseCU();
    }
    
    public void testParenthesizedInTrinary()
    {
        StringReader sr = new StringReader(
                "sb.append((isFilled) ? \"yes\": \"no\");"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseStatement();
    }
    
    public void testDefaultMethodModifier()
    {
        StringReader sr = new StringReader(
                "interface A {\n" +
                "  default int someMethod() { return 3; }\n" +
                "}"
        );
        SourceParser ip = new SourceParser(sr);
        ip.parseCU();
    }
    
    public void testConstructor1()
    {
        new SourceParser(new StringReader("Foo() { return; } }")).parseClassBody();
    }

    public void testConstructor2()
    {
        new SourceParser(new StringReader("public Foo() { return; } }")).parseClassBody();
    }

    public void testConstructor3()
    {
        new SourceParser(new StringReader("<T> Foo(T t) { return; } }")).parseClassBody();
    }

    public void testConstructor4()
    {
        new SourceParser(new StringReader("public <T, U> Foo() { return; } }")).parseClassBody();
    }

    public void testMethod1()
    {
        new SourceParser(new StringReader("void foo() { return; } }")).parseClassBody();
    }

    public void testMethod2()
    {
        new SourceParser(new StringReader("<T, U> void foo() { return; } }")).parseClassBody();
    }

    public void testMethod3()
    {
        new SourceParser(new StringReader("public <T, U> void foo() { return; } }")).parseClassBody();
    }

    public void testMethod4()
    {
        new SourceParser(new StringReader("public <T, U> java.lang.String[] foo() { return; } }")).parseClassBody();
    }

    public void testField1()
    {
        new SourceParser(new StringReader("int foo; }")).parseClassBody();
    }

    public void testField2()
    {
        new SourceParser(new StringReader("int foo[]; }")).parseClassBody();
    }

    public void testField3()
    {
        new SourceParser(new StringReader("int foo = 0; }")).parseClassBody();
    }

    public void testTopLevelRecord1()
    {
        new SourceParser(new StringReader("""
            record Foo(int x) {}
            """
        )).parseCU();
    }
    public void testTopLevelRecord2()
    {
        new SourceParser(new StringReader("""
            public record R(int x, String s, double t)
            {
                public void foo() {return 6;}
            }
            """
        )).parseCU();
    }

    public void testTopLevelRecord3()
    {
        new SourceParser(new StringReader("""
            public record GenericR<T>(T a, T b)
            {
                public GenericR(T both)
                {
                    this(both, both);
                }
                public T foo() {return a;}
            }
            """
        )).parseCU();
    }

    public void testTopLevelRecord4()
    {
        new SourceParser(new StringReader("""
            public record GenericR<T, U>(T a, U b)
            {
                public T foo() {return a;}
            }
            """
        )).parseCU();
    }

    public void testTopLevelRecord5()
    {
        new SourceParser(new StringReader("""
            public record GenericR<T, U>(T a, U b) implements Cloneable
            {
                public T foo() {return a;}
            }
            """
        )).parseCU();
    }

    public void testTopLevelRecord6()
    {
        new SourceParser(new StringReader("""
            public record GenericR<T, U>(T a, U... b) implements Cloneable
            {
                public T foo() {return a;}
                private record Point(int x, Double y) {}
            }
            """
        )).parseCU();
    }

    public void testTopLevelRecord7()
    {
        new SourceParser(new StringReader("""
            record Foo() {}
            """
        )).parseCU();
    }
    public void testTopLevelRecord8()
    {
        new SourceParser(new StringReader("""
            record Foo(int... is) {}
            """
        )).parseCU();
    }
    
}

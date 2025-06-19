/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2011,2012,2013,2014,2016,2017,2021,2022,2024  Michael Kolling and John Rosenberg
 
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

import static bluej.parser.JavaErrorCodes.*;
import static bluej.parser.lexer.JavaTokenTypes.*;

import java.io.Reader;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import bluej.parser.lexer.JavaTokenFilter;
import bluej.parser.lexer.JavaTokenTypes;
import bluej.parser.lexer.LocatableToken;


/**
 * Base class for Java parsers.
 * 
 * <p>We parse the source, and when we see certain constructs we call a corresponding method
 * from our JavaParserCallbacks parent class, which subclasses can override (for instance,
 * beginForLoop, beginForLoopBody, endForLoop).
 * 
 * Almost all of the methods defined by this class are final, to avoid accidentally overriding them
 * in subclasses and accidentally changing the parser behaviour.
 * 
 * In general it is arranged so that a call to beginXYZ() is always followed by a call to
 * endXYZ(). 
 * 
 * @author Davin McCall
 */
public class JavaParser implements ParserBehavior
{
    private SourceParser parser;

    public JavaParser(SourceParser parser)
    {
        this.parser = parser;
    }

    public final JavaTokenFilter getTokenStream()
    {
        return parser.getTokenStream();
    }
    
    /**
     * Get the last token seen during the previous parse.
     * Many parser methods return after having read a complete structure (such as a class definition). This
     * method allows the retrieval of the last token which was actually part of the structure.
     */
    public final LocatableToken getLastToken()
    {
        return parser.getLastToken();
    }

    /**
     * Signal a parse error, occurring because the next token in the token stream is
     * not valid in the current context.
     * 
     * @param msg A message/code describing the error
     */
    private void error(String msg)
    {
        errorBehind(msg, getLastToken());
    }
    
    /**
     * Signal a parser error, occurring because the given token in the token stream is
     * not valid in the current context, but for which a useful error diagnosis can be
     * provided. The entire token will be highlighted as erroneous.
     * 
     * @param msg    A message/code describing the error
     * @paran token  The invalid token
     */
    private void error(String msg, LocatableToken token)
    {
        parser.error(msg, token.getLine(), token.getColumn(), token.getEndLine(), token.getEndColumn());
    }
    
    private void errorBefore(String msg, LocatableToken token)
    {
        parser.error(msg, token.getLine(), token.getColumn(), token.getLine(), token.getColumn());
    }
    
    private void errorBehind(String msg, LocatableToken token)
    {
        parser.error(msg, token.getEndLine(), token.getEndColumn(), token.getEndLine(), token.getEndColumn());
    }
    


    /**
     * Parse a compilation unit (from the beginning).
     */
    public void parseCU()
    {
        int state = 0;
        while (getTokenStream().LA(1).getType() != JavaTokenTypes.EOF) {
            if (getTokenStream().LA(1).getType() == JavaTokenTypes.SEMI) {
                nextToken();
                continue;
            }
            state = parseCUpart(state);
        }
        parser.finishedCU(state);
    }
    
    
    /**
     * Check whether a particular token is a type declaration initiator, i.e "class", "interface"
     * or "enum"
     */
    public final boolean isTypeDeclarator(LocatableToken token)
    {
        return token.getType() == JavaTokenTypes.LITERAL_class
        || token.getType() == JavaTokenTypes.LITERAL_enum
        || token.getType() == JavaTokenTypes.LITERAL_interface
        || token.getType() == JavaTokenTypes.LITERAL_record;
    }

    /**
     * Check whether a token is a primitive type - "int" "float" etc
     */
    public static boolean isPrimitiveType(LocatableToken token)
    {
        return token.getType() == JavaTokenTypes.LITERAL_void
        || token.getType() == JavaTokenTypes.LITERAL_boolean
        || token.getType() == JavaTokenTypes.LITERAL_byte
        || token.getType() == JavaTokenTypes.LITERAL_char
        || token.getType() == JavaTokenTypes.LITERAL_short
        || token.getType() == JavaTokenTypes.LITERAL_int
        || token.getType() == JavaTokenTypes.LITERAL_long
        || token.getType() == JavaTokenTypes.LITERAL_float
        || token.getType() == JavaTokenTypes.LITERAL_double;
    }

    public static final int TYPEDEF_CLASS = 0;
    public static final int TYPEDEF_INTERFACE = 1;
    public static final int TYPEDEF_ENUM = 2;
    public static final int TYPEDEF_RECORD = 6;
    public static final int TYPEDEF_ANNOTATION = 3;
    /** looks like a type definition, but has an error */
    public static final int TYPEDEF_ERROR = 4;
    /** doesn't parse as a type definition at all */
    public static final int TYPEDEF_EPIC_FAIL = 5;

    /**
     * Get the next token from the token stream.
     */
    protected final LocatableToken nextToken()
    {
        return parser.setLastToken(getTokenStream().nextToken());
    }

    public final int parseCUpart(int state)
    {
        LocatableToken token = nextToken();
        if (token.getType() == JavaTokenTypes.LITERAL_package) {
            if (state != 0) {
                error("Only one 'package' statement is allowed", token);
            }
            token = parsePackageStmt(token);
            parser.reachedCUstate(1); state = 1;
        }
        else if (token.getType() == JavaTokenTypes.LITERAL_import) {
            parseImportStatement(token);
            parser.reachedCUstate(1); state = 1;
        }
        else if (isModifier(token) || isTypeDeclarator(token)) {
            // optional: class/interface/enum
            parser.gotTopLevelDecl(token);
            parser.gotDeclBegin(token);
            getTokenStream().pushBack(token);
            parseModifiers();
            parseTypeDef(token);
            parser.reachedCUstate(2); state = 2;
        }
        else if (token.getType() == JavaTokenTypes.EOF) {
            return state;
        }
        else {
            // TODO give different diagnostic depending on state
            error("Expected: Type definition (class, interface or enum)", token);
        }
        return state;
    }

    /**
     * Parse a "package xyz;"-type statement. The "package"-literal token must have already
     * been read from the token stream.
     */
    public final LocatableToken parsePackageStmt(LocatableToken token)
    {
        parser.beginPackageStatement(token);
        token = nextToken();
        if (token.getType() != JavaTokenTypes.IDENT) {
            error("Expected identifier after 'package'");
            return null;
        }
        List<LocatableToken> pkgTokens = parseDottedIdent(token);
        parser.gotPackage(pkgTokens);
        LocatableToken lastPkgToken = parser.getLastToken();
        token = nextToken();
        if (token.getType() != JavaTokenTypes.SEMI) {
            getTokenStream().pushBack(token);
            parser.error(BJ003, lastPkgToken.getEndLine(), lastPkgToken.getEndColumn(),
                    lastPkgToken.getEndLine(), lastPkgToken.getEndColumn());
            return null;
        }
        else {
            parser.gotPackageSemi(token);
            return token;
        }
    }
    
    /**
     * Parse an import statement.
     */
    public final void parseImportStatement()
    {
        LocatableToken token = nextToken();
        if (token.getType() == JavaTokenTypes.LITERAL_import) {
            parseImportStatement(token);
        }
        else {
            error("Import statements must start with \"import\".");
        }
    }
    
    public final void parseImportStatement(final LocatableToken importToken)
    {
        LocatableToken token = importToken;
        parser.beginElement(token);
        boolean isStatic = false;
        token = getTokenStream().nextToken();
        if (token.getType() == JavaTokenTypes.LITERAL_static) {
            isStatic = true;
            token = getTokenStream().nextToken();
        }
        if (token.getType() != JavaTokenTypes.IDENT) {
            getTokenStream().pushBack(token);
            error("Expecting identifier (package containing element to be imported)");
            parser.endElement(token, false);
            return;
        }
        
        List<LocatableToken> tokens = parseDottedIdent(token);
        LocatableToken lastIdentToken = parser.getLastToken();
        if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT) {
            LocatableToken lastToken = nextToken(); // DOT
            token = nextToken();
            if (token.getType() == JavaTokenTypes.SEMI) {
                parser.error("Trailing '.' in import statement", lastToken.getLine(), lastToken.getColumn(),
                        lastToken.getEndLine(), lastToken.getEndColumn());
            }
            else if (token.getType() == JavaTokenTypes.STAR) {
                lastToken = token;
                token = nextToken();
                if (token.getType() != JavaTokenTypes.SEMI) {
                    getTokenStream().pushBack(token);
                    parser.error("Expected ';' following import statement", lastToken.getEndLine(), lastToken.getEndColumn(),
                            lastToken.getEndLine(), lastToken.getEndColumn());
                }
                else {
                    parser.gotWildcardImport(tokens, isStatic, importToken, token);
                    parser.gotImportStmtSemi(token);
                }
            }
            else {
                error("Expected package/class identifier, or '*', in import statement.");
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.SEMI) {
                    nextToken();
                }
            }
        }
        else {
            token = nextToken();
            if (token.getType() != JavaTokenTypes.SEMI) {
                getTokenStream().pushBack(token);
                parser.error("Expected ';' following import statement", lastIdentToken.getEndLine(), lastIdentToken.getEndColumn(),
                        lastIdentToken.getEndLine(), lastIdentToken.getEndColumn());
            }
            else {
                parser.gotImport(tokens, isStatic, importToken, token);
                parser.gotImportStmtSemi(token);
            }
        }
    }
    
    /**
     * Parse a type definition (class, interface, enum).
     * Returns with {@code lastToken} set to the last token seen as part of the definition.
     */
    public final void parseTypeDef()
    {
        parseModifiers();
        parseTypeDef(getTokenStream().LA(1));
    }
    
    /**
     * Parse a type definition (class, interface, enum).
     * Returns with {@code lastToken} set to the last token seen as part of the definition.
     * 
     * @param firstToken  the first token of the type definition, which might still be in the token
     *                    stream, or which might be a modifier already read.
     */
    public final void parseTypeDef(LocatableToken firstToken)
    {
        int tdType = parseTypeDefBegin();
        if (tdType != TYPEDEF_EPIC_FAIL) {
            parser.gotTypeDef(firstToken, tdType);
        }
        parser.modifiersConsumed();
        if (tdType == TYPEDEF_EPIC_FAIL) {
            parser.endDecl(getTokenStream().LA(1));
            return;
        }
        
        // Class name
        LocatableToken token = getTokenStream().nextToken();
        if (token.getType() != JavaTokenTypes.IDENT) {
            getTokenStream().pushBack(token);
            parser.gotTypeDefEnd(token, false);
            error("Expected identifier (in type definition)");
            return;
        }
        parser.gotTypeDefName(token);

        token = parseTypeDefPart2(tdType == TYPEDEF_RECORD);

        // Body!
        if (token == null) {
            parser.gotTypeDefEnd(getTokenStream().LA(1), false);
            return;
        }

        parser.setLastToken(parseTypeBody(tdType, token));
        parser.gotTypeDefEnd(getLastToken(), getLastToken().getType() == JavaTokenTypes.RCURLY);
    }
    
    /**
     * Parse a type body. Returns the last seen token, which might be the '}' closing the
     * type body or might be something else (if there is a parse error).
     * 
     * @param tdType  the type of the type definition (TYPEDEF_ constant specifying class, 
     *                interface, enum, annotation)
     * @param token  the '{' token opening the type body
     */
    public final LocatableToken parseTypeBody(int tdType, LocatableToken token)
    {
        parser.beginTypeBody(token);

        if (tdType == TYPEDEF_ENUM) {
            parseEnumConstants();
        }
        parseClassBody();

        token = nextToken();
        if (token.getType() != JavaTokenTypes.RCURLY) {
            error("Expected '}' (in class definition)");
        }

        parser.endTypeBody(token, token.getType() == JavaTokenTypes.RCURLY);
        return token;
    }
    
    // Possibilities:
    // 1 - parses ok, body should follow
    //       - class/interface TYPEDEF_CLAS / TYPEDEF_INTERFACE
    //       - enum            TYPEDEF_ENUM
    //       - annotation      TYPEDEF_ANNOTATION
    //       - record          TYPEDEF_RECORD
    // 2 - doesn't even look like a type definition (TYPEDEF_EPIC_FAIL)
    public final int parseTypeDefBegin()
    {
        parseModifiers();
        LocatableToken token = nextToken();
        
        boolean isAnnotation = token.getType() == JavaTokenTypes.AT;
        if (isAnnotation) {
            LocatableToken tdToken = nextToken();
            if (tdToken.getType() != JavaTokenTypes.LITERAL_interface) {
                error("Expected 'interface' after '@' in interface definition");
                getTokenStream().pushBack(tdToken);
                return TYPEDEF_EPIC_FAIL;
            }
            token = tdToken;
        }
        
        if (isTypeDeclarator(token)) {
            int tdType = -1;
            if (token.getType() == JavaTokenTypes.LITERAL_class) {
                tdType = TYPEDEF_CLASS;
            }
            else if (token.getType() == JavaTokenTypes.LITERAL_interface) {
                tdType = TYPEDEF_INTERFACE;
                //check for annotation type
                if(isAnnotation) {
                    tdType = TYPEDEF_ANNOTATION;                                                 
                }
            }
            else if (token.getType() == JavaTokenTypes.LITERAL_record) {
                tdType = TYPEDEF_RECORD;
            }
            else {
                tdType = TYPEDEF_ENUM;
            }
            
            return tdType;
        }
        else {
            error("Expected type declarator: 'class', 'interface', or 'enum'");
            return TYPEDEF_EPIC_FAIL;
        }
    }
    
    /**
     * Parse the part of a type definition after the name - the type parameters,
     * extended classes/interfaces and implemented interfaces. Returns the '{' token
     * (which begins the type definition body) on success or null on failure.
     */
    public final LocatableToken parseTypeDefPart2(boolean isRecord)
    {
        // template arguments
        LocatableToken token = nextToken();
        if (token.getType() == JavaTokenTypes.LT) {
            parseTypeParams();
            token = getTokenStream().nextToken();
        }
        
        if (isRecord)
        {
            if (token.getType() == JavaTokenTypes.LPAREN)
            {
                parser.beginRecordParameters(token);
                parseParameterList(true);
                token = nextToken();
                parser.endRecordParameters(token);
                if (token.getType() != JavaTokenTypes.RPAREN) {
                    error("Expected ')' at end of parameter list (in record declaration)");
                    getTokenStream().pushBack(token);
                }
                token = nextToken();
            }
            else
            {
                getTokenStream().pushBack(token);
                error("Expected '{' (in type definition)");
            }
        }

        // extends...
        if (token.getType() == JavaTokenTypes.LITERAL_extends) {
            parser.beginTypeDefExtends(token);
            do {
                parseTypeSpec(true);
                token = nextToken();
            }
            while (token.getType() == JavaTokenTypes.COMMA);
            
            if (token.getType() == JavaTokenTypes.DOT) {
                // Incomplete type spec
                error("Incomplete type specification", token);
                // Don't push the token back on the token stream - it really is part of the type
                return null;
            }
            parser.endTypeDefExtends();
        }

        // implements...
        if (token.getType() == JavaTokenTypes.LITERAL_implements) {
            parser.beginTypeDefImplements(token);
            do {
                parseTypeSpec(true);
                token = nextToken();
            }
            while (token.getType() == JavaTokenTypes.COMMA);

            if (token.getType() == JavaTokenTypes.DOT) {
                // Incomplete type spec
                error("Incomplete type specification", token);
                // Don't push the token back on the token stream - it really is part of the type
                return null;
            }
            parser.endTypeDefImplements();
        }

        // permits...
        if (token.getType() == JavaTokenTypes.LITERAL_permits) {
            parser.beginTypeDefPermits(token);
            do {
                parseTypeSpec(true);
                token = nextToken();
            }
            while (token.getType() == JavaTokenTypes.COMMA);

            if (token.getType() == JavaTokenTypes.DOT) {
                // Incomplete type spec
                error("Incomplete type specification", token);
                // Don't push the token back on the token stream - it really is part of the type
                return null;
            }
            parser.endTypeDefPermits();
        }
        
        if (token.getType() == JavaTokenTypes.LCURLY) {
            return token;
        }
        else {
            getTokenStream().pushBack(token);
            error("Expected '{' (in type definition)");
            return null;
        }
    }
        
    public final void parseEnumConstants()
    {
        LocatableToken token = nextToken();
        while (token.getType() == JavaTokenTypes.IDENT) {
            // The identifier is the constant name - there may be constructor arguments as well
            token = nextToken();
            if (token.getType() == JavaTokenTypes.LPAREN) {
                parseArgumentList(token);
                token = nextToken();
            }
            
            // "body"
            if (token.getType() == JavaTokenTypes.LCURLY) {
                parser.beginAnonClassBody(token, true);
                parseClassBody();
                token = nextToken();
                if (token.getType() != JavaTokenTypes.RCURLY) {
                    error("Expected '}' at end of enum constant body");
                    parser.endAnonClassBody(token, false);
                }
                else {
                    parser.endAnonClassBody(token, true);
                    token = nextToken();
                }
            }

            if (token.getType() == JavaTokenTypes.SEMI) {
                return;
            }

            if (token.getType() == JavaTokenTypes.RCURLY) {
                // This is valid
                getTokenStream().pushBack(token);
                return;
            }

            if (token.getType() != JavaTokenTypes.COMMA) {
                error("Expecting ',' or ';' after enum constant declaration");
                getTokenStream().pushBack(token);
                return;
            }
            token = nextToken();
        }
    }
        
    /**
     * Parse formal type parameters. The opening '<' should have been read already.
     */
    public final void parseTypeParams()
    {
        DepthRef dr = new DepthRef();
        dr.depth = 1;

        while (true) {
            LocatableToken idToken = nextToken();
            if (idToken.getType() != JavaTokenTypes.IDENT) {
                error("Expected identifier (in type parameter list)");
                getTokenStream().pushBack(idToken);
                return;
            }
            parser.gotTypeParam(idToken);

            LocatableToken token = nextToken();
            if (token.getType() == JavaTokenTypes.LITERAL_extends) {
                do {
                    LinkedList<LocatableToken> boundTokens = new LinkedList<LocatableToken>();
                    if (parseTargType(false, boundTokens, dr)) {
                        parser.gotTypeParamBound(boundTokens);
                    }
                    if (dr.depth <= 0) {
                        return;
                    }
                    token = nextToken();
                } while (token.getType() == JavaTokenTypes.BAND);
            }

            if (token.getType() != JavaTokenTypes.COMMA) {
                if (token.getType() != JavaTokenTypes.GT) {
                    error("Expecting '>' at end of type parameter list");
                    getTokenStream().pushBack(token);
                }
                break;
            }
        }
    }

    /**
     * Check whether a token represents a modifier (or an "at" symbol,
     * denoting an annotation).
     */
    public static boolean isModifier(LocatableToken token)
    {
        int tokType = token.getType();
        return (tokType == JavaTokenTypes.LITERAL_public
                || tokType == JavaTokenTypes.LITERAL_private
                || tokType == JavaTokenTypes.LITERAL_protected
                || tokType == JavaTokenTypes.ABSTRACT
                || tokType == JavaTokenTypes.FINAL
                || tokType == JavaTokenTypes.LITERAL_static
                || tokType == JavaTokenTypes.LITERAL_volatile
                || tokType == JavaTokenTypes.LITERAL_native
                || tokType == JavaTokenTypes.STRICTFP
                || tokType == JavaTokenTypes.LITERAL_transient
                || tokType == JavaTokenTypes.LITERAL_synchronized
                || tokType == JavaTokenTypes.AT
                || tokType == JavaTokenTypes.LITERAL_default
                || tokType == JavaTokenTypes.LITERAL_sealed
                || tokType == JavaTokenTypes.LITERAL_non_sealed);
    }

    /**
     * Parse a modifier list (and return all modifier tokens in a list)
     */
    public final List<LocatableToken> parseModifiers()
    {
        List<LocatableToken> rval = new LinkedList<LocatableToken>();
        
        LocatableToken token = getTokenStream().nextToken();
        while (isModifier(token)) {
            if (token.getType() == JavaTokenTypes.AT) {
                if( getTokenStream().LA(1).getType() == JavaTokenTypes.IDENT) {
                    parser.setLastToken(token);
                    parseAnnotation();
                }
                else {
                    getTokenStream().pushBack(token);
                    return rval;
                }
            }
            else {
                parser.gotModifier(token);
            }
            parser.setLastToken(token);
            rval.add(token);
            token = nextToken();
        }                       
        getTokenStream().pushBack(token);
        
        return rval;
    }

    /**
     * Having seen '{', parse the rest of a class body.
     */
    public final void parseClassBody()
    {
        LocatableToken token = getTokenStream().nextToken();
        while (token.getType() != JavaTokenTypes.RCURLY) {
            if (token.getType() == JavaTokenTypes.EOF) {
                error("Unexpected end-of-file in type body; missing '}'", token);
                return;
            }
            parseClassElement(token);
            token = nextToken();
        }
        getTokenStream().pushBack(token);
    }
    
    public final void parseClassElement(LocatableToken token)
    {
        if (token.getType() == JavaTokenTypes.SEMI) {
            // A spurious semicolon.
            return;
        }

        parser.gotDeclBegin(token);
        getTokenStream().pushBack(token);
        LocatableToken hiddenToken = token.getHiddenBefore();
        
        // field declaration, method declaration, inner class
        List<LocatableToken> modifiers = parseModifiers();
        LocatableToken firstMod = null;
        if (! modifiers.isEmpty()) {
            firstMod = modifiers.get(0);
        }
        
        token = nextToken();
        if (token.getType() == JavaTokenTypes.LITERAL_class
                || token.getType() == JavaTokenTypes.LITERAL_interface
                || token.getType() == JavaTokenTypes.LITERAL_enum
                || token.getType() == JavaTokenTypes.LITERAL_record
                || token.getType() == JavaTokenTypes.AT) {
            parser.gotInnerType(token);
            getTokenStream().pushBack(token);
            parseTypeDef(firstMod != null ? firstMod : token);
        }
        else {
            // Not an inner type: should be a method/constructor or field,
            // or (possibly static) a initialisation block
            if (token.getType() == JavaTokenTypes.LCURLY) {
                // initialisation block
                LocatableToken firstToken = firstMod == null ? token : firstMod;
                parser.beginInitBlock(firstToken, token);
                parser.modifiersConsumed();
                parseStmtBlock();
                token = nextToken();
                if (token.getType() != JavaTokenTypes.RCURLY) {
                    error("Expecting '}' (at end of initialisation block)");
                    getTokenStream().pushBack(token);
                    parser.endInitBlock(token, false);
                    parser.endElement(token, false);
                }
                else {
                    parser.endInitBlock(token, true);
                    parser.endElement(token, true);
                }
            }
            else if (token.getType() == JavaTokenTypes.LT
                    || token.getType() == JavaTokenTypes.IDENT
                    || isPrimitiveType(token)) {
                // method/constructor, field
                LocatableToken first = firstMod != null ? firstMod : token;
                if (token.getType() == JavaTokenTypes.LT) {
                    // generic method
                    parser.gotMethodTypeParamsBegin();
                    parseTypeParams();
                    parser.endMethodTypeParams();
                }
                else {
                    getTokenStream().pushBack(token);
                }
                // Might be a constructor:
                boolean isConstructor = getTokenStream().LA(1).getType() == JavaTokenTypes.IDENT
                        && getTokenStream().LA(2).getType() == JavaTokenTypes.LPAREN;
                if (!isConstructor && !parseTypeSpec(true)) {
                    parser.endDecl(getTokenStream().LA(1));
                    return;
                }
                LocatableToken idToken = getTokenStream().nextToken(); // identifier
                if (idToken.getType() != JavaTokenTypes.IDENT) {
                    parser.modifiersConsumed();
                    getTokenStream().pushBack(idToken);
                    errorBefore("Expected identifier (method or field name).", idToken);
                    parser.endDecl(idToken);
                    return;
                }

                token = nextToken();
                int ttype = token.getType();
                if (ttype == JavaTokenTypes.LBRACK || ttype == JavaTokenTypes.SEMI
                        || ttype == JavaTokenTypes.ASSIGN || ttype == JavaTokenTypes.COMMA) {
                    // This must be a field declaration
                    parser.beginFieldDeclarations(first);
                    if (ttype == JavaTokenTypes.LBRACK) {
                        getTokenStream().pushBack(token);
                        parseArrayDeclarators();
                        token = nextToken();
                        ttype = token.getType();
                    }
                    parser.gotField(first, idToken, ttype == JavaTokenTypes.ASSIGN);
                    if (ttype == JavaTokenTypes.SEMI) {
                        parser.endField(token, true);
                        parser.endFieldDeclarations(token, true);
                    }
                    else if (ttype == JavaTokenTypes.ASSIGN) {
                        parseExpression();
                        parseSubsequentDeclarations(DECL_TYPE_FIELD, true);
                    }
                    else if (ttype == JavaTokenTypes.COMMA) {
                        getTokenStream().pushBack(token);
                        parseSubsequentDeclarations(DECL_TYPE_FIELD, true);
                    }
                    else {
                        error("Expected ',', '=' or ';' after field declaration");
                        getTokenStream().pushBack(token);
                        parser.endField(token, false);
                        parser.endFieldDeclarations(token, false);
                    }
                    parser.modifiersConsumed();
                }
                else if (ttype == JavaTokenTypes.LPAREN) {
                    // method declaration
                    if (isConstructor) {
                        parser.gotConstructorDecl(idToken, hiddenToken);
                    }
                    else {
                        parser.gotMethodDeclaration(idToken, hiddenToken);
                    }
                    parser.modifiersConsumed();
                    parseMethodParamsBody();
                }
                else {
                    parser.modifiersConsumed();
                    getTokenStream().pushBack(token);
                    error("Expected ';' or '=' or '(' (in field or method declaration).");
                    parser.endDecl(token);
                }
            }
            else {
                error("Unexpected token \"" + token.getText() + "\" in type declaration body");
                parser.endDecl(getTokenStream().LA(1));
            }
        }
        
    }

    protected final void parseArrayDeclarators()
    {
        if (getTokenStream().LA(1).getType() != JavaTokenTypes.LBRACK) {
            return;
        }

        LocatableToken token = nextToken();
        while (token.getType() == JavaTokenTypes.LBRACK) {
            token = nextToken();
            if (token.getType() != JavaTokenTypes.RBRACK) {
                errorBefore("Expecting ']' (to match '[')", token);
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.RBRACK) {
                    // Try and recover
                    token = nextToken(); // ']'
                }
                else {
                    getTokenStream().pushBack(token);
                    return;
                }
            }
            parser.gotArrayDeclarator();
            token = nextToken();
        }
        getTokenStream().pushBack(token);
    }
        
    /**
     * We've got the return type, name, and opening parenthesis of a method/constructor
     * declaration. Parse the rest.
     */
    public final void parseMethodParamsBody()
    {
        parseParameterList(false);
        parser.gotAllMethodParameters();
        LocatableToken token = nextToken();
        if (token.getType() != JavaTokenTypes.RPAREN) {
            error("Expected ')' at end of parameter list (in method declaration)");
            getTokenStream().pushBack(token);
            parser.endMethodDecl(token, false);
            return;
        }
        token = nextToken();
        if (token.getType() == JavaTokenTypes.LITERAL_throws) {
            parser.beginThrows(token);
            do {
                parseTypeSpec(true);
                token = nextToken();
            } while (token.getType() == JavaTokenTypes.COMMA);
            parser.endThrows();
        }
        if (token.getType() == JavaTokenTypes.LCURLY) {
            // method body
            parser.beginMethodBody(token);
            parseStmtBlock();
            token = nextToken();
            if (token.getType() != JavaTokenTypes.RCURLY) {
                error("Expected '}' at end of method body");
                getTokenStream().pushBack(token);
                parser.endMethodBody(token, false);
                parser.endMethodDecl(token, false);
            }
            else {
                parser.endMethodBody(token, true);
                parser.endMethodDecl(token, true);
            }
            return;
        }
        else if (token.getType() == JavaTokenTypes.LITERAL_default) {
            parseExpression();
            token = nextToken();
        }
        
        if (token.getType() != JavaTokenTypes.SEMI) {
            getTokenStream().pushBack(token);
            error(BJ000);
            parser.endMethodDecl(token, false);
        }
        else {
            parser.endMethodDecl(token, true);
        }
    }

    /**
     * Parse a statement block - such as a method body. The opening curly brace should already be consumed.
     * On return the closing curly (if present) remains in the token stream.
     */
    public final void parseStmtBlock()
    {
        while(true) {
            LocatableToken token = nextToken();
            if (token.getType() == JavaTokenTypes.EOF
                    || token.getType() == JavaTokenTypes.RCURLY) {
                getTokenStream().pushBack(token);
                return;
            }
            parser.beginElement(token);
            LocatableToken ntoken = parseStatement(token, false);
            if (ntoken != null) {
                parser.endElement(ntoken, true);
            }
            else {
                ntoken = getTokenStream().LA(1);
                parser.endElement(getTokenStream().LA(1), false);
                if (ntoken == token) {
                    nextToken();
                    error("Invalid beginning of statement.", token);
                    continue;
                    // TODO we can just skip the token and keep processing, but we should be
                    // context aware. For instance if token is "catch" and we are in a try block,
                    // should bail out altogether now so that processing can continue upstream.
                }
            }
        }
    }

    public final LocatableToken parseStatement()
    {
        return parseStatement(nextToken(), false);
    }

    private static int [] statementTokenIndexes = new int[JavaTokenTypes.INVALID + 1];
    
    static {
        statementTokenIndexes[JavaTokenTypes.SEMI] = 1;
        statementTokenIndexes[JavaTokenTypes.LITERAL_return] = 2;
        statementTokenIndexes[JavaTokenTypes.LITERAL_for] = 3;
        statementTokenIndexes[JavaTokenTypes.LITERAL_while] = 4;
        statementTokenIndexes[JavaTokenTypes.LITERAL_if] = 5;
        statementTokenIndexes[JavaTokenTypes.LITERAL_do] = 6;
        statementTokenIndexes[JavaTokenTypes.LITERAL_assert] = 7;
        statementTokenIndexes[JavaTokenTypes.LITERAL_switch] = 8;
        statementTokenIndexes[JavaTokenTypes.LITERAL_case] = 9;
        statementTokenIndexes[JavaTokenTypes.LITERAL_default] = 10;
        statementTokenIndexes[JavaTokenTypes.LITERAL_continue] = 11;
        statementTokenIndexes[JavaTokenTypes.LITERAL_break] = 12;
        statementTokenIndexes[JavaTokenTypes.LITERAL_throw] = 13;
        statementTokenIndexes[JavaTokenTypes.LITERAL_try] = 14;
        statementTokenIndexes[JavaTokenTypes.IDENT] = 15;
        statementTokenIndexes[JavaTokenTypes.LITERAL_synchronized] = 16;
        statementTokenIndexes[JavaTokenTypes.LITERAL_yield] = 116;
        
        // Modifiers
        statementTokenIndexes[JavaTokenTypes.LITERAL_public] = 17;
        statementTokenIndexes[JavaTokenTypes.LITERAL_private] = 18;
        statementTokenIndexes[JavaTokenTypes.LITERAL_protected] = 19;
        statementTokenIndexes[JavaTokenTypes.ABSTRACT] = 20;
        statementTokenIndexes[JavaTokenTypes.FINAL] = 21;
        statementTokenIndexes[JavaTokenTypes.LITERAL_static] = 22;
        statementTokenIndexes[JavaTokenTypes.LITERAL_volatile] = 23;
        statementTokenIndexes[JavaTokenTypes.LITERAL_native] = 24;
        statementTokenIndexes[JavaTokenTypes.STRICTFP] = 25;
        statementTokenIndexes[JavaTokenTypes.LITERAL_transient] = 26;
        // statementTokenIndexes[JavaTokenTypes.LITERAL_synchronized] = 27;
        statementTokenIndexes[JavaTokenTypes.AT] = 27;
        
        // type declarators
        statementTokenIndexes[JavaTokenTypes.LITERAL_class] = 28;
        statementTokenIndexes[JavaTokenTypes.LITERAL_enum] = 29;
        statementTokenIndexes[JavaTokenTypes.LITERAL_interface] = 30;
        
        // primitive types
        statementTokenIndexes[JavaTokenTypes.LITERAL_void] = 31;
        statementTokenIndexes[JavaTokenTypes.LITERAL_boolean] = 32;
        statementTokenIndexes[JavaTokenTypes.LITERAL_byte] = 33;
        statementTokenIndexes[JavaTokenTypes.LITERAL_char] = 34;
        statementTokenIndexes[JavaTokenTypes.LITERAL_short] = 35;
        statementTokenIndexes[JavaTokenTypes.LITERAL_int] = 36;
        statementTokenIndexes[JavaTokenTypes.LITERAL_long] = 37;
        statementTokenIndexes[JavaTokenTypes.LITERAL_float] = 38;
        statementTokenIndexes[JavaTokenTypes.LITERAL_double] = 39;
        
        statementTokenIndexes[JavaTokenTypes.LCURLY] = 40;
    }
    
    /**
     * Parse a statement. Return the last token that is part of the statement (i.e the ';' or '}'
     * terminator), or null if an error was encountered.
     * 
     * @param token  The first token of the statement
     * @param allowComma  if true, allows multiple statements separated by commas. Each
     *                    statement will be parsed.
     */
    public final LocatableToken parseStatement(LocatableToken token, boolean allowComma)
    {
        while (true) {
            switch (statementTokenIndexes[token.getType()]) {
            case 1: // SEMI
                parser.gotEmptyStatement();
                return token; // empty statement
            case 2: // LITERAL_return
                token = nextToken();
                parser.gotReturnStatement(token.getType() != JavaTokenTypes.SEMI);
                if (token.getType() != JavaTokenTypes.SEMI) {
                    getTokenStream().pushBack(token);
                    parseExpression();
                    token = nextToken();
                }
                if (token.getType() != JavaTokenTypes.SEMI) {
                    getTokenStream().pushBack(token);
                    error(BJ003);
                    return null;
                }
                return token;
            case 3: // LITERAL_for
                return parseForStatement(token);
            case 4: // LITERAL_while
                return parseWhileStatement(token);
            case 5: // LITERAL_if    
                return parseIfStatement(token);
            case 6: // LITERAL_do
                return parseDoWhileStatement(token);
            case 7: // LITERAL_assert
                return parseAssertStatement(token);
            case 8: // LITERAL_switch
                return parseSwitchStatement(token);
            case 9: // LITERAL_case
            {
                parser.beginSwitchCase(getTokenStream().LA(1));
                boolean hadCommas = false;
                // Special case: null, which can be followed by default:
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.LITERAL_null)
                {
                    // Consume null, then get next:
                    token = nextToken();
                    token = nextToken();
                    // Now check for comma:
                    if (token.getType() == JavaTokenTypes.COMMA)
                    {
                        // Get next token after that:
                        token = nextToken();
                        if (token.getType() != JavaTokenTypes.LITERAL_default)
                        {
                            error("Only default can follow null in a case");
                        }
                        else
                        {
                            // Fine, get the token after that (the arrow):
                            token = nextToken();
                        }
                    }
                }
                else
                {
                    // Could be a pattern variable:
                    // A declaration of a variable?
                    PatternParse pp = lookAheadParsePattern();
                    switch (pp) {
                        case PatternParse.RecordPattern, PatternParse.TypeThenVariableName -> {
                            if (!parseRecordPattern(false))
                            {
                                error("Failed to parse record pattern");
                                getTokenStream().pushBack(token);
                                parser.endSwitchCase(token, true);
                                return null;
                            }
                            token = nextToken();
                            if (token.getType() == JavaTokenTypes.LITERAL_when) {
                                parseExpression(false, false);
                                token = nextToken();
                            }
                        }
                        default -> {
                            // No (unbracketed) lambdas allowed in a case expression:
                            parseExpression(false, false);
                            token = nextToken();
                            while (token.getType() == JavaTokenTypes.COMMA) {
                                parseExpression(false, false);
                                token = nextToken();
                                hadCommas = true;
                            }
                        }
                    }
                }
                parser.gotSwitchCaseType(token, token.getType() == JavaTokenTypes.LAMBDA);
                if (token.getType() == JavaTokenTypes.LAMBDA)
                {
                    // Right-hand side can be a single expression, a block or a throw
                    // If we look at the first token we can spot the last two:
                    token = getTokenStream().LA(1);
                    if (token.getType() == JavaTokenTypes.LCURLY || token.getType() == JavaTokenTypes.LITERAL_throw)
                    {
                        // Block or throw; both are statements:
                        token = parseStatement();
                    }
                    else
                    {
                        parseExpression();
                        // Expression should be followed by a semi-colon:
                        token = getTokenStream().nextToken();
                        if (token.getType() != JavaTokenTypes.SEMI)
                        {
                            error("Expecting ';' after case body");
                            getTokenStream().pushBack(token);
                            parser.endSwitchCase(token, true);
                            return null;
                        }
                    }
                    parser.endSwitchCase(token, true);
                }
                else if (token.getType() != JavaTokenTypes.COLON)
                {
                    error("Expecting ':' at end of case expression");
                    getTokenStream().pushBack(token);
                    parser.endSwitchCase(token, false);
                    return null;
                }
                else if (hadCommas)
                {
                    // COLON and hadCommas; incorrect:
                    error("Comma-separated expressions not valid before ':' in case expression");
                    getTokenStream().pushBack(token);
                    parser.endSwitchCase(token, false);
                    return null;
                }
                else
                {
                    parser.endSwitchCase(token, false);
                }
                return token;
            }
            case 10: // LITERAL_default
                parser.gotSwitchDefault();
                token = nextToken();
                if (token.getType() != JavaTokenTypes.COLON && token.getType() != JavaTokenTypes.LAMBDA) {
                    error("Expecting ':' or '->' at end of case expression");
                    getTokenStream().pushBack(token);
                    return null;
                }
                return token;
            case 11: // LITERAL_continue
            case 12: // LITERAL_break
                // There might be a label afterwards
                LocatableToken keywordToken = token;
                token = nextToken();
                if (token.getType() == JavaTokenTypes.IDENT) {
                    token = nextToken();
                    parser.gotBreakContinue(keywordToken, token);
                }
                else
                {
                    parser.gotBreakContinue(keywordToken, null);
                }
                if (token.getType() != JavaTokenTypes.SEMI) {
                    getTokenStream().pushBack(token);
                    error(BJ003);
                    return null;
                }
                return token;
            case 13: // LITERAL_throw
                parser.gotThrow(token);
                parseExpression();
                token = nextToken();
                if (token.getType() != JavaTokenTypes.SEMI) {
                    getTokenStream().pushBack(token);
                    error(BJ003);
                    return null;
                }
                return token;
            case 14: // LITERAL_try
                return parseTryCatchStmt(token);
            case 15: // IDENT
                // A label?
                LocatableToken ctoken = nextToken();
                if (ctoken.getType() == JavaTokenTypes.COLON) {
                    return ctoken;
                }
                getTokenStream().pushBack(ctoken);
                getTokenStream().pushBack(token);

                // A declaration of a variable?
                List<LocatableToken> tlist = new LinkedList<LocatableToken>();
                boolean isTypeSpec = parseTypeSpec(true, true, tlist);
                token = getTokenStream().LA(1);
                pushBackAll(tlist);
                if (isTypeSpec && token.getType() == JavaTokenTypes.IDENT) {
                    token = tlist.get(0);
                    parser.gotDeclBegin(token);
                    return parseVariableDeclarations(token, true);
                }
                else {
                    parser.gotStatementExpression();
                    parseExpression();                                              
                    token = getTokenStream().nextToken();
                    if (token.getType() == JavaTokenTypes.COMMA && allowComma) {
                        token = getTokenStream().nextToken();
                        continue;
                    }
                    if (token.getType() != JavaTokenTypes.SEMI) {
                        getTokenStream().pushBack(token);
                        error("Expected ';' at end of previous statement");
                        return null;
                    }
                    return token;
                }
            case 16: // LITERAL_synchronized
                // Synchronized block
                parser.beginSynchronizedBlock(token);
                token = nextToken();
                if (token.getType() == JavaTokenTypes.LPAREN) {
                    parseExpression();
                    token = nextToken();
                    if (token.getType() != JavaTokenTypes.RPAREN) {
                        errorBefore("Expecting ')' at end of expression", token);
                        getTokenStream().pushBack(token);
                        parser.endSynchronizedBlock(token, false);
                        return null;
                    }
                    token = getTokenStream().nextToken();
                }
                if (token.getType() == JavaTokenTypes.LCURLY) {
                    parser.beginStmtblockBody(token);
                    parseStmtBlock();
                    token = nextToken();
                    if (token.getType() != JavaTokenTypes.RCURLY) {
                        error("Expecting '}' at end of synchronized block");
                        getTokenStream().pushBack(token);
                        parser.endStmtblockBody(token, false);
                        parser.endSynchronizedBlock(token, false);
                        return null;
                    }
                    parser.endStmtblockBody(token, true);
                    parser.endSynchronizedBlock(token, true);
                    return token;
                }
                else {
                    error("Expecting statement block after 'synchronized'");
                    getTokenStream().pushBack(token);
                    parser.endSynchronizedBlock(token, false);
                    return null;
                }
            case 116: // LITERAL_yield
                parser.gotYieldStatement();
                parseExpression();
                token = nextToken();
                if (token.getType() != JavaTokenTypes.SEMI) {
                    getTokenStream().pushBack(token);
                    error(BJ003);
                    return null;
                }
                return token;
            case 17: // LITERAL_public
            case 18: // LITERAL_private
            case 19: // LITERAL_protected
            case 20: // ABSTRACT
            case 21: // FINAL
            case 22: // LITERAL_static
            case 23: // LITERAL_volatile
            case 24: // LITERAL_native
            case 25: // STRICTFP
            case 26: // LITERAL_transient
            case 27: // AT
                getTokenStream().pushBack(token);
                parser.gotDeclBegin(token);
                parseModifiers();
                if (isTypeDeclarator(getTokenStream().LA(1)) || getTokenStream().LA(1).getType() == JavaTokenTypes.AT) {
                    parser.gotInnerType(getTokenStream().LA(1));
                    parseTypeDef(token);
                }
                else {
                    parseVariableDeclarations(token, true);
                }
                return null;
            case 28: // LITERAL_class
            case 29: // LITERAL_enum
            case 30: // LITERAL_interface
                getTokenStream().pushBack(token);
                parser.gotDeclBegin(token);
                parseTypeDef(token);
                return null;
            case 31: // LITERAL_void
            case 32: // LITERAL_boolean
            case 33: // LITERAL_byte
            case 34: // LITERAL_char
            case 35: // LITERAL_short
            case 36: // LITERAL_int
            case 37: // LITERAL_long
            case 38: // LITERAL_float
            case 39: // LITERAL_double
                // primitive
                getTokenStream().pushBack(token);
                tlist = new LinkedList<LocatableToken>();
                parseTypeSpec(false, true, tlist);

                if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT) {
                    // int.class, or int[].class are possible
                    pushBackAll(tlist);
                    parseExpression();
                    token = nextToken();
                    if (token.getType() != JavaTokenTypes.SEMI) {
                        error("Expected ';' after expression-statement");
                        return null;
                    }
                    return token;
                }
                else {
                    pushBackAll(tlist);
                    parser.gotDeclBegin(token);
                    return parseVariableDeclarations(token, true);
                }
            case 40: // LCURLY
                parser.beginStmtblockBody(token);
                parseStmtBlock();
                token = nextToken();
                if (token.getType() != JavaTokenTypes.RCURLY) {
                    error("Expecting '}' at end of statement block");
                    if (token.getType() != JavaTokenTypes.RPAREN) {
                        getTokenStream().pushBack(token);
                    }
                    parser.endStmtblockBody(token, false);
                    return null;
                }
                parser.endStmtblockBody(token, true);
                return token;
            }

            // Expression, or not valid.
            if (! isExpressionTokenType(token.getType())) {
                error("Not a valid statement beginning.", token);
                return null;
            }

            getTokenStream().pushBack(token);
            parser.gotStatementExpression();
            parseExpression();
            token = getTokenStream().nextToken();
            if (token.getType() != JavaTokenTypes.SEMI) {
                getTokenStream().pushBack(token);
                error("Expected ';' at end of previous statement");
                return null;
            }
            return token;
        }
    }

    /**
     * Parse a try/catch/finally. The first token is 'try'.
     * @param token  The first token (must be 'try').
     * @return  the last token that is part of the try/catch/finally, or null
     */
    public final LocatableToken parseTryCatchStmt(LocatableToken token)
    {
        parser.beginTryCatchSmt(token, getTokenStream().LA(1).getType() == JavaTokenTypes.LPAREN);
        token = nextToken();
        if (token.getType() == JavaTokenTypes.LPAREN) {
            // Java 7 try-with-resource
            do {
                token = getTokenStream().LA(1);
                // Specification allows either a variable declaration (with initializer) or
                // expression.
                if (token.getType() == JavaTokenTypes.IDENT) {
                    List<LocatableToken> tlist = new LinkedList<LocatableToken>();
                    boolean isTypeSpec = parseTypeSpec(true, true, tlist);
                    token = getTokenStream().LA(1);
                    pushBackAll(tlist);
                    if (isTypeSpec && token.getType() == JavaTokenTypes.IDENT) {
                        parser.gotDeclBegin(tlist.get(0));
                        parseVariableDeclarations(tlist.get(0), false);
                    }
                    else {
                        parseExpression();                                              
                    }
                }
                else if (isModifier(token)) {
                    getTokenStream().nextToken(); // remove the modifier from the token stream
                    parseVariableDeclarations();
                }
                else {
                    parseExpression();
                }
                token = getTokenStream().nextToken();
            } while (token.getType() == JavaTokenTypes.SEMI);
            if (token.getType() != JavaTokenTypes.RPAREN) {
                errorBefore("Missing closing ')' after resources in 'try' statement", token);
            }
            token = nextToken();
        }
        if (token.getType() != JavaTokenTypes.LCURLY) {
            error ("Expecting '{' after 'try'");
            getTokenStream().pushBack(token);
            parser.endTryCatchStmt(token, false);
            return null;
        }
        parser.beginTryBlock(token);
        parseStmtBlock();
        token = nextToken();
        if (token.getType() == JavaTokenTypes.RCURLY) {
            parser.endTryBlock(token, true);
        }
        else if (token.getType() == JavaTokenTypes.LITERAL_catch
                || token.getType() == JavaTokenTypes.LITERAL_finally) {
            // Invalid, but we can recover
            getTokenStream().pushBack(token);
            error("Missing '}' at end of 'try' block");
            parser.endTryBlock(token, false);
        }
        else {
            getTokenStream().pushBack(token);
            error("Missing '}' at end of 'try' block");
            parser.endTryBlock(token, false);
            parser.endTryCatchStmt(token, false);
            return null;
        }

        int laType = getTokenStream().LA(1).getType();
        while (laType == JavaTokenTypes.LITERAL_catch
                || laType == JavaTokenTypes.LITERAL_finally) {
            token = nextToken();
            parser.gotCatchFinally(token);
            if (laType == JavaTokenTypes.LITERAL_catch) {
                token = nextToken();
                if (token.getType() != JavaTokenTypes.LPAREN) {
                    error("Expecting '(' after 'catch'");
                    getTokenStream().pushBack(token);
                    parser.endTryCatchStmt(token, false);
                    return null;
                }
                
                while (true) {
                    if (getTokenStream().LA(1).getType() == JavaTokenTypes.FINAL) {
                        // Java 7 "final re-throw"
                        token = nextToken();
                    }
                    
                    parseTypeSpec(true);
                    token = nextToken();
                    if (token.getType() != JavaTokenTypes.BOR) {
                        // Java 7 multi-catch
                        break;
                    }
                    parser.gotMultiCatch(token);
                }
                
                if (token.getType() != JavaTokenTypes.IDENT) {
                    error("Expecting identifier after type (in 'catch' expression)");
                    getTokenStream().pushBack(token);
                    parser.endTryCatchStmt(token, false);
                    return null;
                }
                parser.gotCatchVarName(token);
                token = nextToken();
                
                if (token.getType() != JavaTokenTypes.RPAREN) {
                    error("Expecting ')' after identifier (in 'catch' expression)");
                    getTokenStream().pushBack(token);
                    parser.endTryCatchStmt(token, false);
                    return null;
                }
            }
            token = nextToken();
            if (token.getType() != JavaTokenTypes.LCURLY) {
                error("Expecting '{' after 'catch'/'finally'");
                getTokenStream().pushBack(token);
                parser.endTryCatchStmt(token, false);
                return null;
            }
            token = parseStatement(token, false); // parse as a statement block
            laType = getTokenStream().LA(1).getType();
        }
        if (token != null) {
            parser.endTryCatchStmt(token, true);
        }
        else {
            parser.endTryCatchStmt(getTokenStream().LA(1), false);
        }
        return token;
    }

    /**
     * Parse an "assert" statement. Returns the concluding semi-colon token, or null on error.
     * {@code lastToken} will be set to the last token which is part of the statement.
     * 
     * @param token   The token corresponding to the "assert" keyword.
     */
    public final LocatableToken parseAssertStatement(LocatableToken token)
    {
        parser.gotAssert();
        parseExpression();
        token = getTokenStream().nextToken();
        if (token.getType() == JavaTokenTypes.COLON) {
            // Should be followed by a string
            parser.setLastToken(token);
            parseExpression();
            token = getTokenStream().nextToken();
        }
        if (token.getType() != JavaTokenTypes.SEMI) {
            error("Expected ';' at end of assertion statement");
            getTokenStream().pushBack(token);
            return null;
        }
        parser.setLastToken(token);
        return token;
    }

    public final LocatableToken parseSwitchExpression(LocatableToken token)
    {
        parser.beginSwitchStmt(token, true);
        token = nextToken();
        if (token.getType() != JavaTokenTypes.LPAREN) {
            error("Expected '(' after 'switch'");
            getTokenStream().pushBack(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        parseExpression();
        token = nextToken();
        if (token.getType() != JavaTokenTypes.RPAREN) {
            error("Expected ')' at end of expression (in 'switch(...)')");
            getTokenStream().pushBack(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        token = getTokenStream().nextToken();
        if (token.getType() != JavaTokenTypes.LCURLY) {
            error("Expected '{' after 'switch(...)'");
            getTokenStream().pushBack(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        parser.beginSwitchBlock(token);
        parseStmtBlock();
        token = nextToken();
        if (token.getType() != JavaTokenTypes.RCURLY) {
            error("Missing '}' at end of 'switch' statement block");
            getTokenStream().pushBack(token);
            parser.endSwitchBlock(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        parser.endSwitchBlock(token);
        parser.endSwitchStmt(token, true);
        return token;
    }
    
    /** Parse a "switch(...) {  }" statement. */
    public final LocatableToken parseSwitchStatement(LocatableToken token)
    {
        parser.beginSwitchStmt(token, false);
        token = nextToken();
        if (token.getType() != JavaTokenTypes.LPAREN) {
            error("Expected '(' after 'switch'");
            getTokenStream().pushBack(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        parseExpression();
        token = nextToken();
        if (token.getType() != JavaTokenTypes.RPAREN) {
            error("Expected ')' at end of expression (in 'switch(...)')");
            getTokenStream().pushBack(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        token = getTokenStream().nextToken();
        if (token.getType() != JavaTokenTypes.LCURLY) {
            error("Expected '{' after 'switch(...)'");
            getTokenStream().pushBack(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        parser.beginSwitchBlock(token);
        parseStmtBlock();
        token = nextToken();
        if (token.getType() != JavaTokenTypes.RCURLY) {
            error("Missing '}' at end of 'switch' statement block");
            getTokenStream().pushBack(token);
            parser.endSwitchBlock(token);
            parser.endSwitchStmt(token, false);
            return null;
        }
        parser.endSwitchBlock(token);
        parser.endSwitchStmt(token, true);
        return token;
    }
    
    public final LocatableToken parseDoWhileStatement(LocatableToken token)
    {
        parser.beginDoWhile(token);
        token = nextToken(); // '{' or a statement
        LocatableToken ntoken = parseStatement(token, false);
        if (ntoken != null || token != getTokenStream().LA(1)) {
            parser.beginDoWhileBody(token);
            if (ntoken == null) {
                parser.endDoWhileBody(getTokenStream().LA(1), false);
            }
            else {
                parser.endDoWhileBody(ntoken, true);
            }
        }

        token = nextToken();
        if (token.getType() != JavaTokenTypes.LITERAL_while) {
            error("Expecting 'while' after statement block (in 'do ... while')");
            getTokenStream().pushBack(token);
            parser.endDoWhile(token, false);
            return null;
        }
        token = nextToken();
        if (token.getType() != JavaTokenTypes.LPAREN) {
            error("Expecting '(' after 'while'");
            getTokenStream().pushBack(token);
            parser.endDoWhile(token, false);
            return null;
        }
        parseExpression();
        token = nextToken();
        if (token.getType() != JavaTokenTypes.RPAREN) {
            error("Expecting ')' after conditional expression (in 'while' statement)");
            getTokenStream().pushBack(token);
            parser.endDoWhile(token, false);
            return null;
        }
        token = nextToken(); // should be ';'
        parser.endDoWhile(token, true);
        return token;
    }
        
    public final LocatableToken parseWhileStatement(LocatableToken token)
    {
        parser.beginWhileLoop(token);
        token = nextToken();
        if (token.getType() != JavaTokenTypes.LPAREN) {
            error("Expecting '(' after 'while'");
            getTokenStream().pushBack(token);
            parser.endWhileLoop(token, false);
            return null;
        }
        parseExpression();
        token = nextToken();
        if (token.getType() != JavaTokenTypes.RPAREN) {
            error("Expecting ')' after conditional expression (in 'while' statement)");
            getTokenStream().pushBack(token);
            parser.endWhileLoop(token, false);
            return null;
        }
        token = nextToken();
        parser.beginWhileLoopBody(token);
        token = parseStatement(token, false);
        if (token != null) {
            parser.endWhileLoopBody(token, true);
            parser.endWhileLoop(token, true);
        }
        else {
            token = getTokenStream().LA(1);
            parser.endWhileLoopBody(token, false);
            parser.endWhileLoop(token, false);
            token = null;
        }
        return token;
    }

    /**
     * Parse a "for(...)" loop (old or new style).
     * @param forToken  The "for" token, which has already been extracted from the token stream.
     * @return The last token that is part of the loop (or null).
     */
    public final LocatableToken parseForStatement(LocatableToken forToken)
    {
        // TODO: if we get an unexpected token in the part between '(' and ')' check
        // if it is ')'. If so we might still expect a loop body to follow.
        parser.beginForLoop(forToken);
        LocatableToken token = nextToken();
        if (token.getType() != JavaTokenTypes.LPAREN) {
            error("Expecting '(' after 'for'");
            getTokenStream().pushBack(token);
            parser.endForLoop(token, false);
            return null;
        }
        if (getTokenStream().LA(1).getType() != JavaTokenTypes.SEMI) {
            // Could be an old or new style for-loop.
            List<LocatableToken> tlist = new LinkedList<LocatableToken>();

            LocatableToken first = getTokenStream().LA(1);
            boolean isTypeSpec = false;
            if (isModifier(getTokenStream().LA(1))) {
                parseModifiers();
                isTypeSpec = true;
                parseTypeSpec(false, true, tlist);
            }
            else {
                isTypeSpec = parseTypeSpec(true, true, tlist);
            }
            
            if (isTypeSpec && getTokenStream().LA(1).getType() == JavaTokenTypes.IDENT) {
                // for (type var ...
                parser.beginForInitDecl(first);
                parser.gotTypeSpec(tlist);
                LocatableToken idToken = nextToken(); // identifier
                parser.gotForInit(first, idToken);
                // Array declarators can follow name
                parseArrayDeclarators();

                token = nextToken();
                if (token.getType() == JavaTokenTypes.COLON) {
                    parser.determinedForLoop(true, false);
                    // This is a "new" for loop (Java 5)
                    parser.endForInit(idToken, true);
                    parser.endForInitDecls(idToken, true);
                    parser.modifiersConsumed();
                    parseExpression();
                    token = nextToken();
                    if (token.getType() != JavaTokenTypes.RPAREN) {
                        error("Expecting ')' (in for statement)");
                        getTokenStream().pushBack(token);
                        parser.endForLoop(token, false);
                        return null;
                    }
                    token = nextToken();
                    parser.beginForLoopBody(token);
                    token = parseStatement(token, false); // loop body
                    endForLoopBody(token);
                    endForLoop(token);
                    return token;
                }
                else {
                    parser.determinedForLoop(false, token.getType() == JavaTokenTypes.ASSIGN);
                    // Old style loop with initialiser
                    if (token.getType() == JavaTokenTypes.ASSIGN) {
                        parseExpression();
                    }
                    else {
                        getTokenStream().pushBack(token);
                    }
                    if (parseSubsequentDeclarations(DECL_TYPE_FORINIT, true) == null) {
                        parser.endForLoop(getTokenStream().LA(1), false);
                        parser.modifiersConsumed();
                        return null;
                    }
                    parser.modifiersConsumed();
                }
            }
            else {
                // Not a type spec, so, we might have a general statement
                pushBackAll(tlist);
                token = nextToken();
                parseStatement(token, true);
            }
        }
        else {
            token = nextToken(); // SEMI
        }

        // We're expecting a regular (old-style) statement at this point
        boolean semiFollows = getTokenStream().LA(1).getType() == JavaTokenTypes.SEMI;
        parser.gotForTest(!semiFollows);
        if (!semiFollows) {
            // test expression
            parseExpression();
        }
        token = nextToken();
        if (token.getType() != JavaTokenTypes.SEMI) {
            getTokenStream().pushBack(token);
            if (token.getType() == JavaTokenTypes.COMMA) {
                error(BJ003, token);  // common mistake: use ',' instead of ';'
            }
            else {
                error(BJ003);
            }
            parser.endForLoop(token, false);
            return null;
        }
        boolean bracketFollows = getTokenStream().LA(1).getType() == JavaTokenTypes.RPAREN;
        parser.gotForIncrement(!bracketFollows);
        if (!bracketFollows) {
            // loop increment expression
            parseExpression();
            while (getTokenStream().LA(1).getType() == JavaTokenTypes.COMMA) {
                nextToken();
                parseExpression();
            }
        }
        token = nextToken(); // ')'?
        if (token.getType() != JavaTokenTypes.RPAREN) {
            error("Expecting ')' (or ',') after 'for(...'");
            getTokenStream().pushBack(token);
            parser.endForLoop(token, false);
            return null;
        }
        token = nextToken();
        if (token.getType() == JavaTokenTypes.RCURLY
                || token.getType() == JavaTokenTypes.EOF) {
            error("Expecting statement after 'for(...)'");
            getTokenStream().pushBack(token);
            parser.endForLoop(token, false);
            return null;
        }
        parser.beginForLoopBody(token);
        token = parseStatement(token, false);
        endForLoopBody(token);
        endForLoop(token);
        return token;
    }

    private void endForLoop(LocatableToken token)
    {
        if (token == null) {
            parser.endForLoop(getTokenStream().LA(1), false);
        }
        else {
            parser.endForLoop(token, true);
        }
    }
    
    private void endForLoopBody(LocatableToken token)
    {
        if (token == null) {
            parser.endForLoopBody(getTokenStream().LA(1), false);
        }
        else {
            parser.endForLoopBody(token, true);
        }
    }
        
    /**
     * Parse an "if" statement.
     * @param token  The token corresponding to the "if" literal.
     */
    public final LocatableToken parseIfStatement(LocatableToken token)
    {
        parser.beginIfStmt(token);
        
        mainLoop:
        while(true) {
            token = nextToken(); // "("
            if (token.getType() != LPAREN) {
                getTokenStream().pushBack(token);
                if (token.getType() == LCURLY) {
                    error(BJ002, token);
                }
                else {
                    errorBefore(BJ001, token);
                }
                parser.endIfStmt(token, false);
                return null;
            }
            parseExpression();
            token = nextToken();
            if (token.getType() != JavaTokenTypes.RPAREN) {
                error("Expecting ')' after conditional expression (in 'if' statement)");
                getTokenStream().pushBack(token);
                if (token.getType() != JavaTokenTypes.LCURLY) {
                    parser.endIfStmt(token, false);
                    return null;
                }
            }
            token = nextToken();
            parser.beginIfCondBlock(token);
            token = parseStatement(token, false);
            endIfCondBlock(token);
            if (getTokenStream().LA(1).getType() == JavaTokenTypes.LITERAL_else) {
                getTokenStream().nextToken(); // "else"
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.LITERAL_if) {
                    parser.gotElseIf(token);
                    nextToken(); // "if"
                    continue mainLoop;
                }
                token = nextToken();
                parser.beginIfCondBlock(token);
                token = parseStatement(token, false);
                endIfCondBlock(token);
            }
            endIfStmt(token);
            return token;
        }
    }
    
    private void endIfCondBlock(LocatableToken token)
    {
        if (token != null) {
            parser.endIfCondBlock(token, true);
        }
        else {
            parser.endIfCondBlock(getTokenStream().LA(1), false);
        }
    }
    
    private void endIfStmt(LocatableToken token)
    {
        if (token != null) {
            parser.endIfStmt(token, true);
        }
        else {
            parser.endIfStmt(getTokenStream().LA(1), false);
        }
    }
       
    public final LocatableToken parseVariableDeclarations()
    {
        LocatableToken first = getTokenStream().LA(1);
        parser.gotDeclBegin(first);
        return parseVariableDeclarations(first, true);
    }
    
    /**
     * Parse a variable declaration, possibly with an initialiser, usually followed by ';'
     * 
     * @param first   The first token of the declaration (should still be
     *                in the token stream, unless it is a modifier)
     * @param mustEndWithSemi  If false, specifies that the declaration need not be terminated by a semi-colon,
     *                and that furthermore, the character terminating the declaration should be left in the token
     *                stream.
     */
    public final LocatableToken parseVariableDeclarations(LocatableToken first, boolean mustEndWithSemi)
    {
        parser.beginVariableDecl(first);
        parseModifiers();
        boolean r = parseVariableDeclaration(first);
        // parseVariableDeclaration calls modifiersConsumed(); i.e. we act as if
        // the modifiers are consumed by the type rather than the variables.
        // This is necessary because an initializer expression might contain an anonymous
        // class containing modifiers.
        if (r) {
            return parseSubsequentDeclarations(DECL_TYPE_VAR, mustEndWithSemi);
        }
        else {
            parser.endVariableDecls(getTokenStream().LA(1), false);
            return null;
        }
    }

    /* Types for parseSubsequentDeclarations and friends */
    
    /** for loop initializer */
    protected static final int DECL_TYPE_FORINIT = 0;
    /** variable */
    protected static final int DECL_TYPE_VAR = 1;
    /** field */
    protected static final int DECL_TYPE_FIELD = 2;
    
    /**
     * After seeing a type and identifier declaration, this will parse any
     * the subsequent declarations, and check for a terminating semicolon.
     * 
     * @return  the last token that is part of the declarations, or null on error (or mustEndWithSemi == false).
     */
    protected final LocatableToken parseSubsequentDeclarations(int type, boolean mustEndWithSemi)
    {
        LocatableToken prevToken = getLastToken();
        LocatableToken token = nextToken();
        while (token.getType() == JavaTokenTypes.COMMA) {
            endDeclaration(type, token, false);
            LocatableToken first = token;
            token = nextToken();
            if (token.getType() != JavaTokenTypes.IDENT) {
                endDeclarationStmt(type, token, false);
                error("Expecting variable identifier (or change ',' to ';')");
                return null;
            }
            parseArrayDeclarators();
            LocatableToken idtoken = token;
            prevToken = getLastToken();
            token = nextToken();
            gotSubsequentDecl(type, first, idtoken, token.getType() == JavaTokenTypes.ASSIGN);
            if (token.getType() == JavaTokenTypes.ASSIGN) {
                parseExpression();
                prevToken = getLastToken();
                token = nextToken();
            }
        }

        if (! mustEndWithSemi) {
            getTokenStream().pushBack(token);
            endDeclaration(type, token, false);
            endDeclarationStmt(type, token, false);
            return null;
        }
        
        if (token.getType() != JavaTokenTypes.SEMI) {
            getTokenStream().pushBack(token);
            errorBehind(BJ003, prevToken);
            endDeclaration(type, token, false);
            endDeclarationStmt(type, token, false);
            return null;
        }
        else {
            endDeclaration(type, token, true);
            endDeclarationStmt(type, token, true);
            return token;
        }
    }

    private void endDeclaration(int type, LocatableToken token, boolean included)
    {
        if (type == DECL_TYPE_FIELD) {
            parser.endField(token, included);
        }
        else if (type == DECL_TYPE_VAR) {
            parser.endVariable(token, included);
        }
        else {
            parser.endForInit(token, included);
        }
    }
    
    private void endDeclarationStmt(int type, LocatableToken token, boolean included)
    {
        if (type == DECL_TYPE_FIELD) {
            parser.endFieldDeclarations(token, included);
        }
        else if (type == DECL_TYPE_VAR) {
            parser.endVariableDecls(token, included);
        }
        else {
            parser.endForInitDecls(token, included);
        }
    }
    
    private void gotSubsequentDecl(int type, LocatableToken firstToken,
            LocatableToken nameToken, boolean inited)
    {
        if (type == DECL_TYPE_FIELD) {
            parser.gotSubsequentField(firstToken, nameToken, inited);
        }
        else if (type == DECL_TYPE_VAR) {
            parser.gotSubsequentVar(firstToken, nameToken, inited);
        }
        else {
            parser.gotSubsequentForInit(firstToken, nameToken, inited);
        }
    }
    
    /**
     * Parse a variable (or field or parameter) declaration, possibly including an initialiser
     * (but not including modifiers)
     */
    private boolean parseVariableDeclaration(LocatableToken first)
    {
        List<LocatableToken> typeSpecTokens = new LinkedList<LocatableToken>();
        if (!parseTypeSpec(false, true, typeSpecTokens)) {
            return false;
        }
        parser.gotTypeSpec(typeSpecTokens);
        
        LocatableToken token = nextToken();
        if (token.getType() != JavaTokenTypes.IDENT) {
            error("Expecting identifier (in variable/field declaration)");
            getTokenStream().pushBack(token);
            return false;
        }
        
        // Array declarators can follow name
        parseArrayDeclarators();

        LocatableToken idToken = token;
        token = nextToken();
        parser.gotVariableDecl(first, idToken, token.getType() == JavaTokenTypes.ASSIGN);
        parser.modifiersConsumed();

        if (token.getType() == JavaTokenTypes.ASSIGN) {
            parseExpression();
        }
        else {
            getTokenStream().pushBack(token);
        }
        return true;
    }

    // Parses a record pattern and handles the declaration of any variable(s).
    private boolean parseRecordPattern(boolean partOfInstanceof)
    {
        List<LocatableToken> typeSpecTokens = new LinkedList<LocatableToken>();
        if (!parseTypeSpec(false, partOfInstanceof, typeSpecTokens)) {
            return false;
        }
        parser.gotTypeSpec(typeSpecTokens);

        LocatableToken token = nextToken();
        // Instanceof parses the array declarations as part of the type, whereas case parses
        // them separately here (it just works out that way because of how they handle variable declarations):
        if (!partOfInstanceof && token.getType() == JavaTokenTypes.LBRACK)
        {
            // Square bracket, can be an array part of a type if not outermost
            getTokenStream().pushBack(token);
            parseArrayDeclarators();
            token = nextToken();
        }
        switch (token.getType())
        {
            case JavaTokenTypes.LPAREN -> {
                // Now we process a nested pattern
                token = nextToken();
                if (token.getType() != JavaTokenTypes.RPAREN)
                {
                    getTokenStream().pushBack(token);
                    if (!parseRecordPattern(partOfInstanceof))
                        return false;
                    token = nextToken();
                    while (token.getType() == JavaTokenTypes.COMMA) {
                        // Can have comma then another pattern:
                        if (!parseRecordPattern(partOfInstanceof))
                            return false;
                        token = nextToken();
                    }
                }

                // Must now be closing bracket:
                if (token.getType() != JavaTokenTypes.RPAREN)
                {
                    return false;
                }
            }

            case JavaTokenTypes.IDENT -> {
                // A variable name for a declaration

                // Have to treat instanceof differently, because it has different scope rules:
                if (partOfInstanceof)
                {
                    parser.gotInstanceOfVar(token);
                }
                else
                {
                    parser.gotVariableDecl(typeSpecTokens.get(0), token, false);
                    parser.endVariable(token, true);
                }
            }
        }

        return true;
    }
        
    /**
     * Parse a type specification. This includes class name(s) (Xyz.Abc), type arguments
     * to generic types, and array declarators.
     * 
     * <p>The final set of array declarators will not be parsed if they contain a dimension value.
     * Eg for "Abc[10][][]" this method will leave "[10][][]" unprocessed and still in the token stream.
     * 
     *  @param processArray   if false, no '[]' sequences will be parsed, only the element type.
     *  @return  true iff a type specification was successfully parsed
     */
    public final boolean parseTypeSpec(boolean processArray)
    {
        List<LocatableToken> tokens = new LinkedList<LocatableToken>();
        boolean rval = parseTypeSpec(false, processArray, tokens);
        if (rval) {
            parser.gotTypeSpec(tokens);
        }
        return rval;
    }
        
    /**
     * Parse a type specification. This could be a primitive type (including void),
     * or a class type (qualified or not, possibly with type parameters). This can
     * do a speculative parse if the following tokens might either be a type specification
     * or a statement-expression.
     * 
     * @param speculative  Whether this is a speculative parse, i.e. we might not actually
     *                     have a type specification. If this is set some parse errors will
     *                     simply return false.
     * @param processArray  Whether to parse '[]' array declarators. If false only the
     *                     element type will be parsed.
     * @param ttokens   A list which will be filled with tokens. If the return is true, the tokens
     *                  make up a possible type specification; otherwise the tokens should be
     *                  pushed back on the token stream.
     * 
     * @return true if we saw what might be a type specification (even if it
     *                         contains errors), or false if it does not appear to be
     *                     a type specification.
     */
    public final boolean parseTypeSpec(boolean speculative, boolean processArray, List<LocatableToken> ttokens)
    {
        int ttype = parseBaseType(speculative, ttokens);
        if (ttype == TYPE_ERROR) {
            return false;
        }
        else if (ttype == TYPE_PRIMITIVE) {
            speculative = false;
        }
        else {
            LocatableToken token = nextToken();
            if (token.getType() == JavaTokenTypes.LT) {
                ttokens.add(token);

                // Type parameters? (or is it a "less than" comparison?)
                DepthRef dr = new DepthRef();
                dr.depth = 1;
                if (!parseTargs(speculative, ttokens, dr)) {
                    return false;
                }
            }
            else {
                getTokenStream().pushBack(token);
            }
        }

        // check for inner type
        LocatableToken token = nextToken();
        if (token.getType() == JavaTokenTypes.DOT) {
            if (getTokenStream().LA(1).getType() == JavaTokenTypes.IDENT) {
                ttokens.add(token);
                return parseTypeSpec(speculative, true, ttokens);
            }
            else {
                getTokenStream().pushBack(token);
                return true;
            }
        }
        else if (processArray)
        {
            // check for array declarators
            while (token.getType() == JavaTokenTypes.LBRACK
                    && getTokenStream().LA(1).getType() == JavaTokenTypes.RBRACK) {
                ttokens.add(token);
                token = nextToken(); // RBRACK
                ttokens.add(token);
                token = nextToken();
            }
        }

        getTokenStream().pushBack(token);
        return true;
    }

    private static final int TYPE_PRIMITIVE = 0;
    private static final int TYPE_OTHER = 1;
    private static final int TYPE_ERROR = 2;

    /**
     * Parse a type "base" - a primitive type or a class type without type parameters.
     * The type parameters may follow.
     * 
     * @param speculative
     * @param ttokens
     * @return
     */
    private int parseBaseType(boolean speculative, List<LocatableToken> ttokens)
    {
        LocatableToken token = nextToken();
        if (isPrimitiveType(token)) {
            // Ok, we have a base type
            ttokens.add(token);
            return TYPE_PRIMITIVE;
        }
        else {
            if (token.getType() != JavaTokenTypes.IDENT) {
                if (! speculative) {
                    error("Expected type identifier");
                }
                getTokenStream().pushBack(token);
                return TYPE_ERROR;
            }

            ttokens.addAll(parseDottedIdent(token));
        }
        return TYPE_OTHER;
    }

    private boolean parseTargs(boolean speculative, List<LocatableToken> ttokens, DepthRef dr)
    {
        // We already have opening '<' and depth reflects this.

        int beginDepth = dr.depth;
        LocatableToken token;
        boolean needBaseType = true;

        while (dr.depth >= beginDepth) {

            if (getTokenStream().LA(1).getType() == JavaTokenTypes.QUESTION) {
                // Wildcard
                token = nextToken();
                ttokens.add(token);
                token = nextToken();
                if (token.getType() == JavaTokenTypes.LITERAL_extends
                        || token.getType() == JavaTokenTypes.LITERAL_super) {
                    ttokens.add(token);
                    needBaseType = true;
                }
                else {
                    getTokenStream().pushBack(token);
                    needBaseType = false;
                }
            }

            if (needBaseType) {
                boolean r = parseTargType(speculative, ttokens, dr);
                if (!r) {
                    return false;
                }
                if (dr.depth < beginDepth) {
                    break;
                }
            }

            token = nextToken();
            // Type parameters being closed
            if (token.getType() == JavaTokenTypes.GT
                    || token.getType() == JavaTokenTypes.SR
                    || token.getType() == JavaTokenTypes.BSR) {
                ttokens.add(token);
                if (token.getType() == JavaTokenTypes.GT) {
                    dr.depth--;
                }
                else if (token.getType() == JavaTokenTypes.SR) {
                    dr.depth -= 2;
                }
                else if (token.getType() == JavaTokenTypes.BSR) {
                    dr.depth -= 3;
                }
            }
            else if (token.getType() == JavaTokenTypes.COMMA) {
                needBaseType = true;
                ttokens.add(token);
            }
            else {
                if (! speculative) {
                    error("Expected '>' to close type parameter list");
                }
                getTokenStream().pushBack(token);
                return false;
            }
        }
        return true;
    }

    /**
     * Parse a type argument, type part. The "? super" or "? extends" have already been dealt
     * with. The type part may itself have type arguments, and might be followed by a comma
     * or a closing '>' sequence.
     * 
     * @param speculative  Should be true if this is a speculative type parse
     * @param ttokens  A list of tokens. All tokens processed will be added to this list.
     * @param dr  Depth reference.
     */
    private boolean parseTargType(boolean speculative, List<LocatableToken> ttokens, DepthRef dr)
    {
        LocatableToken token;
        int beginDepth = dr.depth;
        
        if (getTokenStream().LA(1).getType() == JavaTokenTypes.GT) {
            // Java 7 Diamond operator
            ttokens.add(getTokenStream().nextToken());
            dr.depth--;
            return true;
        }
        
        int ttype = parseBaseType(speculative, ttokens);
        if (ttype == TYPE_ERROR) {
            return false;
        }

        if (ttype == TYPE_OTHER) {
            // May be type parameters
            if (getTokenStream().LA(1).getType() == JavaTokenTypes.LT) {
                dr.depth++;
                ttokens.add(nextToken());
                if (!parseTargs(speculative, ttokens, dr)) {
                    return false;
                }
                if (dr.depth < beginDepth) {
                    return true;
                }
            }

            token = nextToken();
            if (token.getType() == JavaTokenTypes.DOT && getTokenStream().LA(1).getType() == JavaTokenTypes.IDENT) {
                ttokens.add(token);
                if (!parseTargType(speculative, ttokens, dr)) {
                    return false;
                }
                return true;
            }
        }
        else {
            token = nextToken();
        }

        // Array declarators?
        while (token.getType() == JavaTokenTypes.LBRACK
                && getTokenStream().LA(1).getType() == JavaTokenTypes.RBRACK) {
            ttokens.add(token);
            token = nextToken(); // RBRACK
            ttokens.add(token);
            token = nextToken();
        }

        getTokenStream().pushBack(token);

        return true;
    }
        
    /**
     * Parse a dotted identifier. This could be a variable, method or type name.
     * @param first The first token in the dotted identifier (should be an IDENT)
     * @return A list of tokens making up the dotted identifier
     */
    public final List<LocatableToken> parseDottedIdent(LocatableToken first)
    {
        List<LocatableToken> rval = new LinkedList<LocatableToken>();
        rval.add(first);
        LocatableToken token = nextToken();
        while (token.getType() == JavaTokenTypes.DOT) {
            LocatableToken ntoken = nextToken();
            if (ntoken.getType() != JavaTokenTypes.IDENT) {
                // This could be for example "xyz.class"
                getTokenStream().pushBack(ntoken);
                break;
            }
            rval.add(token);
            rval.add(ntoken);
            token = nextToken();
        }
        getTokenStream().pushBack(token);
        return rval;
    }
        
    /**
     * Check whether a token is an operator. Note that the LPAREN token can be an operator
     * (method call) or value (parenthesized expression).
     * 
     * "new" is not classified as an operator here (an operator operates on a value).
     */
    public static boolean isOperator(LocatableToken token)
    {
        int ttype = token.getType();
        return ttype == JavaTokenTypes.PLUS
        || ttype == JavaTokenTypes.MINUS
        || ttype == JavaTokenTypes.STAR
        || ttype == JavaTokenTypes.DIV
        || ttype == JavaTokenTypes.LBRACK
        || ttype == JavaTokenTypes.LPAREN
        || ttype == JavaTokenTypes.PLUS_ASSIGN
        || ttype == JavaTokenTypes.STAR_ASSIGN
        || ttype == JavaTokenTypes.MINUS_ASSIGN
        || ttype == JavaTokenTypes.DIV_ASSIGN
        || ttype == JavaTokenTypes.DOT
        || ttype == JavaTokenTypes.EQUAL
        || ttype == JavaTokenTypes.NOT_EQUAL
        || ttype == JavaTokenTypes.LT
        || ttype == JavaTokenTypes.LE
        || ttype == JavaTokenTypes.GT
        || ttype == JavaTokenTypes.GE
        || ttype == JavaTokenTypes.ASSIGN
        || ttype == JavaTokenTypes.BNOT
        || ttype == JavaTokenTypes.LNOT
        || ttype == JavaTokenTypes.INC
        || ttype == JavaTokenTypes.DEC
        || ttype == JavaTokenTypes.BOR
        || ttype == JavaTokenTypes.BOR_ASSIGN
        || ttype == JavaTokenTypes.BAND
        || ttype == JavaTokenTypes.BAND_ASSIGN
        || ttype == JavaTokenTypes.BXOR
        || ttype == JavaTokenTypes.BXOR_ASSIGN
        || ttype == JavaTokenTypes.LOR
        || ttype == JavaTokenTypes.LAND
        || ttype == JavaTokenTypes.SL
        || ttype == JavaTokenTypes.SL_ASSIGN
        || ttype == JavaTokenTypes.SR
        || ttype == JavaTokenTypes.SR_ASSIGN
        || ttype == JavaTokenTypes.BSR
        || ttype == JavaTokenTypes.BSR_ASSIGN
        || ttype == JavaTokenTypes.MOD
        || ttype == JavaTokenTypes.MOD_ASSIGN
        || ttype == JavaTokenTypes.LITERAL_instanceof;
    }
        
    /**
     * Check whether an operator is a binary operator.
     * 
     * "instanceof" is not considered to be a binary operator (operates on only one value).
     */
    public final boolean isBinaryOperator(LocatableToken token)
    {
        int ttype = token.getType();
        return ttype == JavaTokenTypes.PLUS
        || ttype == JavaTokenTypes.MINUS
        || ttype == JavaTokenTypes.STAR
        || ttype == JavaTokenTypes.DIV
        || ttype == JavaTokenTypes.MOD
        || ttype == JavaTokenTypes.BOR
        || ttype == JavaTokenTypes.BXOR
        || ttype == JavaTokenTypes.BAND
        || ttype == JavaTokenTypes.SL
        || ttype == JavaTokenTypes.SR
        || ttype == JavaTokenTypes.BSR
        || ttype == JavaTokenTypes.BSR_ASSIGN
        || ttype == JavaTokenTypes.SR_ASSIGN
        || ttype == JavaTokenTypes.SL_ASSIGN
        || ttype == JavaTokenTypes.BAND_ASSIGN
        || ttype == JavaTokenTypes.BXOR_ASSIGN
        || ttype == JavaTokenTypes.BOR_ASSIGN
        || ttype == JavaTokenTypes.MOD_ASSIGN
        || ttype == JavaTokenTypes.DIV_ASSIGN
        || ttype == JavaTokenTypes.STAR_ASSIGN
        || ttype == JavaTokenTypes.MINUS_ASSIGN
        || ttype == JavaTokenTypes.PLUS_ASSIGN
        || ttype == JavaTokenTypes.ASSIGN
        || ttype == JavaTokenTypes.DOT
        || ttype == JavaTokenTypes.EQUAL
        || ttype == JavaTokenTypes.NOT_EQUAL
        || ttype == JavaTokenTypes.LT
        || ttype == JavaTokenTypes.LE
        || ttype == JavaTokenTypes.GT
        || ttype == JavaTokenTypes.GE
        || ttype == JavaTokenTypes.LAND
        || ttype == JavaTokenTypes.LOR;
    }
        
    public final boolean isUnaryOperator(LocatableToken token)
    {
        int ttype = token.getType();
        return ttype == JavaTokenTypes.PLUS
        || ttype == JavaTokenTypes.MINUS
        || ttype == JavaTokenTypes.LNOT
        || ttype == JavaTokenTypes.BNOT
        || ttype == JavaTokenTypes.INC
        || ttype == JavaTokenTypes.DEC;
    }

    /**
     * Parse an annotation (having already seen '@', and having an identifier next in the token stream)
     */
    public final void parseAnnotation()
    {
        LocatableToken token = nextToken(); // IDENT
        List<LocatableToken> annName = parseDottedIdent(token);
        boolean paramsFollow = getTokenStream().LA(1).getType() == JavaTokenTypes.LPAREN;
        parser.gotAnnotation(annName, paramsFollow);
        if (paramsFollow) {
            // arguments
            token = getTokenStream().nextToken(); // LPAREN
            parseArgumentList(token);
        }
    }

    private static int [] expressionTokenIndexes = new int[JavaTokenTypes.INVALID+1];
    
    static {
        expressionTokenIndexes[JavaTokenTypes.LITERAL_new] = 1;
        expressionTokenIndexes[JavaTokenTypes.LCURLY] = 2;
        expressionTokenIndexes[JavaTokenTypes.IDENT] = 3;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_this] = 4;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_super] = 5;
        
        expressionTokenIndexes[JavaTokenTypes.STRING_LITERAL] = 6;
        expressionTokenIndexes[JavaTokenTypes.CHAR_LITERAL] = 7;
        expressionTokenIndexes[JavaTokenTypes.NUM_INT] = 8;
        expressionTokenIndexes[JavaTokenTypes.NUM_LONG] = 9;
        expressionTokenIndexes[JavaTokenTypes.NUM_DOUBLE] = 10;
        expressionTokenIndexes[JavaTokenTypes.NUM_FLOAT] = 11;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_null] = 12;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_true] = 13;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_false] = 14;
        
        expressionTokenIndexes[JavaTokenTypes.LPAREN] = 15;
        
        expressionTokenIndexes[JavaTokenTypes.LITERAL_void] = 16;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_boolean] = 17;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_byte] = 18;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_char] = 19;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_short] = 20;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_int] = 21;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_long] = 22;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_float] = 23;
        expressionTokenIndexes[JavaTokenTypes.LITERAL_double] = 24;
        
        expressionTokenIndexes[JavaTokenTypes.PLUS] = 25;
        expressionTokenIndexes[JavaTokenTypes.MINUS] = 26;
        expressionTokenIndexes[JavaTokenTypes.LNOT] = 27;
        expressionTokenIndexes[JavaTokenTypes.BNOT] = 28;
        expressionTokenIndexes[JavaTokenTypes.INC] = 29;
        expressionTokenIndexes[JavaTokenTypes.DEC] = 30;
        
        expressionTokenIndexes[JavaTokenTypes.LITERAL_switch] = 31;
    }
    
    private static int [] expressionOpIndexes = new int[JavaTokenTypes.INVALID+1];
    
    static {
        expressionOpIndexes[JavaTokenTypes.RPAREN] = 1;
        expressionOpIndexes[JavaTokenTypes.SEMI] = 2;
        expressionOpIndexes[JavaTokenTypes.RBRACK] = 3;
        expressionOpIndexes[JavaTokenTypes.COMMA] = 4;
        expressionOpIndexes[JavaTokenTypes.COLON] = 5;
        expressionOpIndexes[JavaTokenTypes.EOF] = 6;
        expressionOpIndexes[JavaTokenTypes.RCURLY] = 7;
        
        expressionOpIndexes[JavaTokenTypes.LBRACK] = 8;
        expressionOpIndexes[JavaTokenTypes.LITERAL_instanceof] = 9;
        expressionOpIndexes[JavaTokenTypes.DOT] = 10;
        
        // Binary operators (not DOT)
        expressionOpIndexes[JavaTokenTypes.PLUS] = 11;
        expressionOpIndexes[JavaTokenTypes.MINUS] = 11;
        expressionOpIndexes[JavaTokenTypes.STAR] = 11;
        expressionOpIndexes[JavaTokenTypes.DIV] = 11;
        expressionOpIndexes[JavaTokenTypes.MOD] = 11;
        expressionOpIndexes[JavaTokenTypes.BOR] = 11;
        expressionOpIndexes[JavaTokenTypes.BXOR] = 11;
        expressionOpIndexes[JavaTokenTypes.BAND] = 11;
        expressionOpIndexes[JavaTokenTypes.SL] = 11;
        expressionOpIndexes[JavaTokenTypes.SR] = 11;
        expressionOpIndexes[JavaTokenTypes.BSR] = 11;
        expressionOpIndexes[JavaTokenTypes.BSR_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.SR_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.SL_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.BAND_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.BXOR_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.BOR_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.MOD_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.DIV_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.STAR_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.MINUS_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.PLUS_ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.ASSIGN] = 11;
        expressionOpIndexes[JavaTokenTypes.EQUAL] = 11;
        expressionOpIndexes[JavaTokenTypes.NOT_EQUAL] = 11;
        expressionOpIndexes[JavaTokenTypes.LT] = 11;
        expressionOpIndexes[JavaTokenTypes.LE] = 11;
        expressionOpIndexes[JavaTokenTypes.GT] = 11;
        expressionOpIndexes[JavaTokenTypes.GE] = 11;
        expressionOpIndexes[JavaTokenTypes.LAND] = 11;
        expressionOpIndexes[JavaTokenTypes.LOR] = 11;
        expressionOpIndexes[JavaTokenTypes.METHOD_REFERENCE] = 11;
    }
    
    /**
     * Check whether the given token type can lead an
     * expression.
     */
    private boolean isExpressionTokenType(int ttype)
    {
        return expressionTokenIndexes[ttype] != 0;
    }
    
    private void parseLambdaBody()
    {
        boolean blockFollows = getTokenStream().LA(1).getType() == JavaTokenTypes.LCURLY;
        parser.beginLambdaBody(blockFollows, blockFollows ? getTokenStream().LA(1) : null);
        if (blockFollows) {
            parser.beginStmtblockBody(nextToken()); // consume the curly
            parseStmtBlock();
            LocatableToken token = nextToken();
            if (token.getType() != JavaTokenTypes.RCURLY) {
                error("Expecting '}' at end of lambda block");
                getTokenStream().pushBack(token);
                parser.endStmtblockBody(token, false);
                parser.endLambdaBody(token);
            }
            else {
                parser.endStmtblockBody(token, true);
                parser.endLambdaBody(token);
            }
        }
        else {
            parseExpression(true, true);
            parser.endLambdaBody(null);
        }
    }

    public final void parseExpression()
    {
        parseExpression(false, true);
    }
    
    /**
     * Parse an expression
     */
    private final void parseExpression(boolean isLambdaBody, boolean lambdaAllowed)
    {
        LocatableToken token = nextToken();
        parser.beginExpression(token, isLambdaBody);

        exprLoop:
        while (true) {
            int index = expressionTokenIndexes[token.getType()];
            switch (index) {
            case 1: // LITERAL_new
                // new XYZ(...)
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.EOF) {
                    parser.gotIdentifierEOF(token);
                    parser.endExpression(getTokenStream().LA(1), true);
                    return;
                }
                parseNewExpression(token);
                break;
            case 2: // LCURLY
                // an initialiser list for an array
                do {
                    if (getTokenStream().LA(1).getType() == JavaTokenTypes.RCURLY) {
                        token = nextToken(); // RCURLY
                        break;
                    }
                    parseExpression();
                    token = nextToken();
                } while (token.getType() == JavaTokenTypes.COMMA);
                if (token.getType() != JavaTokenTypes.RCURLY) {
                    errorBefore("Expected '}' at end of initialiser list expression", token);
                    getTokenStream().pushBack(token);
                }
                break;
            case 3: // IDENT
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.LPAREN) {
                    // Method call
                    parser.gotMethodCall(token);
                    parseArgumentList(nextToken());
                }
                else if (getTokenStream().LA(1).getType() == JavaTokenTypes.LAMBDA && lambdaAllowed) {
                    nextToken(); // consume LAMBDA symbol
                    parseLambdaBody();
                }
                else if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT &&
                        getTokenStream().LA(2).getType() == JavaTokenTypes.IDENT &&
                        getTokenStream().LA(3).getType() != JavaTokenTypes.LPAREN) {
                    parser.gotCompoundIdent(token);
                    nextToken(); // dot
                    token = getTokenStream().nextToken();
                    while (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT &&
                            getTokenStream().LA(2).getType() == JavaTokenTypes.IDENT &&
                            getTokenStream().LA(3).getType() != JavaTokenTypes.LPAREN &&
                            getTokenStream().LA(3).getType() != JavaTokenTypes.EOF)
                    {
                        parser.gotCompoundComponent(token);
                        nextToken(); // dot
                        token = getTokenStream().nextToken();
                    }
                    
                    // We either don't have a dot, or we do have a dot but not an
                    // identifier after it.
                    if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT) {
                        LocatableToken dotToken = nextToken();
                        LocatableToken ntoken = nextToken();
                        if (ntoken.getType() == JavaTokenTypes.LITERAL_class) {
                            parser.completeCompoundClass(token);
                            parser.gotClassLiteral(ntoken);
                        }
                        else if (ntoken.getType() == JavaTokenTypes.LITERAL_this) {
                            parser.completeCompoundClass(token);
                            // TODO gotThisAccessor
                        }
                        else if (ntoken.getType() == JavaTokenTypes.LITERAL_super) {
                            parser.completeCompoundClass(token);
                            // TODO gotSuperAccessor
                        }
                        else {
                            parser.completeCompoundValue(token);
                            // Treat dot as an operator (below)
                            getTokenStream().pushBack(ntoken);
                            getTokenStream().pushBack(dotToken);
                        }
                    }
                    else {
                        // No dot follows; last member
                        if (getTokenStream().LA(1).getType() == JavaTokenTypes.EOF) {
                            parser.completeCompoundValueEOF(token);
                        }
                        else {
                            if (getTokenStream().LA(1).getType() == JavaTokenTypes.LBRACK
                                    && getTokenStream().LA(2).getType() == JavaTokenTypes.RBRACK) {
                                parser.completeCompoundClass(token);
                                parseArrayDeclarators();
                                if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT &&
                                        getTokenStream().LA(2).getType() == JavaTokenTypes.LITERAL_class) {
                                    token = nextToken();
                                    token = nextToken();
                                    parser.gotClassLiteral(token);
                                }
                                else {
                                    error("Expecting \".class\"");
                                }
                            }
                            parser.completeCompoundValue(token);
                        }
                    }
                }
                else if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT) {
                    parser.gotParentIdentifier(token);
                    if (getTokenStream().LA(2).getType() == JavaTokenTypes.LITERAL_class) {
                        token = nextToken(); // dot
                        token = nextToken(); // class
                        parser.gotClassLiteral(token);
                    }
                }
                else if (getTokenStream().LA(1).getType() == JavaTokenTypes.LBRACK
                        && getTokenStream().LA(2).getType() == JavaTokenTypes.RBRACK) {
                    parser.gotArrayTypeIdentifier(token);
                    parseArrayDeclarators();
                    if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT &&
                            getTokenStream().LA(2).getType() == JavaTokenTypes.LITERAL_class) {
                        token = nextToken();
                        token = nextToken();
                        parser.gotClassLiteral(token);
                    }
                    else {
                        error("Expecting \".class\"");
                    }
                }
                else if (getTokenStream().LA(1).getType() == JavaTokenTypes.EOF) {
                    parser.gotIdentifierEOF(token);
                }
                else {
                    parser.gotIdentifier(token);
                }
                break;
            case 4: // LITERAL_this
            case 5: // LITERAL_super
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.LPAREN) {
                    // call to constructor or superclass constructor
                    parser.gotConstructorCall(token);
                    parseArgumentList(nextToken());
                }
                else {
                    parser.gotLiteral(token);
                }
                break;
            case 6: // STRING_LITERAL
            case 7: // CHAR_LITERAL
            case 8: // NUM_INT
            case 9: // NUM_LONG
            case 10: // NUM_DOUBLE
            case 11: // NUM_FLOAT
            case 12: // LITERAL_null
            case 13: // LITERAL_true
            case 14: // LITERAL_false
                // Literals need no further processing
                parser.gotLiteral(token);
                break;
            case 15: // LPAREN
                // Either a parenthesised expression, or a type cast
                // We handle cast to primitive specially - it can be followed by +, ++, -, --
                // and yet be a cast.
                boolean isPrimitive = isPrimitiveType(getTokenStream().LA(1));

                List<LocatableToken> tlist = new LinkedList<LocatableToken>();
                boolean isTypeSpec = parseTypeSpec(true, true, tlist);
                
                // We have a cast if
                // -it's a type spec
                // -it's followed by ')'
                // -it's not followed by an operator OR
                //  the type is primitive and the following operator is a unary operator
                //  OR following the ')' is '('
                // -it's not followed by an expression terminator - ; : , ) } ] EOF

                int tt2 = getTokenStream().LA(2).getType();
                boolean isCast = isTypeSpec && getTokenStream().LA(1).getType() == JavaTokenTypes.RPAREN && (tt2 != JavaTokenTypes.LAMBDA);
                if (tt2 != JavaTokenTypes.LPAREN) {
                    isCast &= !isOperator(getTokenStream().LA(2)) || (isPrimitive
                            && isUnaryOperator(getTokenStream().LA(2)));
                    isCast &= tt2 != JavaTokenTypes.SEMI && tt2 != JavaTokenTypes.RPAREN
                            && tt2 != JavaTokenTypes.RCURLY && tt2 != JavaTokenTypes.EOF;
                    isCast &= tt2 != JavaTokenTypes.COMMA && tt2 != JavaTokenTypes.COLON
                            && tt2 != JavaTokenTypes.RBRACK;
                    isCast &= tt2 != JavaTokenTypes.QUESTION;
                }
                
                if (isCast) {
                    // This surely must be type cast
                    parser.gotTypeCast(tlist);
                    token = nextToken(); // RPAREN
                    token = nextToken();
                    continue exprLoop;
                }
                else {
                    // This may be either an expression OR a Lambda function.
                    //check if it is a Lambda function.

                    boolean isLambda = false;
                    if (isTypeSpec) {
                        if (getTokenStream().LA(1).getType() == JavaTokenTypes.RPAREN && tt2 == JavaTokenTypes.LAMBDA) {
                            isLambda = true;
                        }
                        if (! isLambda && getTokenStream().LA(1).getType() == JavaTokenTypes.IDENT) {
                            // A lambda parameter with name and type
                            isLambda = true;
                        }
                        if (! isLambda && getTokenStream().LA(1).getType() == JavaTokenTypes.TRIPLE_DOT) {
                            // A lambda parameter with name and type
                            isLambda = true;
                        }
                    }
                    pushBackAll(tlist);
                    int tt1 = getTokenStream().LA(1).getType();
                    tt2 = getTokenStream().LA(2).getType();
                    if (! isLambda) {
                        if (tt1 == JavaTokenTypes.RPAREN && tt2 == JavaTokenTypes.LAMBDA) {
                            isLambda = true;
                        }
                        else if (isModifier(getTokenStream().LA(1))) {
                            isLambda = true;
                        }
                        else if (tt1 == JavaTokenTypes.IDENT && tt2 == JavaTokenTypes.COMMA) {
                            isLambda = true;
                        }
                    }
                    
                    if (isLambda && lambdaAllowed) {
                        // now we need to consume the tokens.
                        parseLambdaParameterList();
                        token = nextToken();
                        if (token.getType() == JavaTokenTypes.RPAREN) token = nextToken();
                        //Now we are expecting the lambda symbol.
                        if (token.getType() != JavaTokenTypes.LAMBDA){
                            error("Lambda identifier misplaced or not found");
                            parser.endExpression(token, false);
                            return;
                        }
                        parseLambdaBody();
                        break;
                    } else {
                        //it is an expression.
                        parseExpression();
                        token = nextToken();
                        if (token.getType() != JavaTokenTypes.RPAREN) {
                            getTokenStream().pushBack(token);
                            error("Unmatched '(' in expression; expecting ')'");
                            parser.endExpression(token, false);
                            return;
                        }
                    }
                }
                break;
            case 16: // LITERAL_void
            case 17: // LITERAL_boolean
            case 18: // LITERAL_byte
            case 19: // LITERAL_char
            case 20: // LITERAL_short
            case 21: // LITERAL_int
            case 22: // LITERAL_long
            case 23: // LITERAL_float
            case 24: // LITERAL_double
                // Not really part of an expression, but may be followed by
                // .class or [].class  (eg int.class, int[][].class)
                parser.gotPrimitiveTypeLiteral(token);
                parseArrayDeclarators();
                if (getTokenStream().LA(1).getType() == JavaTokenTypes.DOT &&
                        getTokenStream().LA(2).getType() == JavaTokenTypes.LITERAL_class) {
                    token = nextToken();
                    token = nextToken();
                    parser.gotClassLiteral(token);
                }
                else {
                    error("Expecting \".class\"");
                }
                break;
            case 25: // PLUS
            case 26: // MINUS
            case 27: // LNOT
            case 28: // BNOT
            case 29: // INC
            case 30: // DEC
                // Unary operator
                parser.gotUnaryOperator(token);
                token = nextToken();
                continue exprLoop;
            case 31: // LITERAL_switch
                // Switch expression
                parseSwitchExpression(token);
                break;
            default:
                getTokenStream().pushBack(token);
                error("Invalid expression token: " + token.getText());
                parser.endExpression(token, true);
                return;
            }

            // Now we get an operator, or end of expression
            opLoop:
            while (true) {
                token = getTokenStream().nextToken();
                switch (expressionOpIndexes[token.getType()]) {
                case 1: // RPAREN
                case 2: // SEMI
                case 3: // RBRACK
                case 4: // COMMA
                case 5: // COLON
                case 6: // EOF
                case 7: // RCURLY
                    // These are all legitimate expression endings
                    getTokenStream().pushBack(token);
                    parser.endExpression(token, false);
                    return;
                case 8: // LBRACK
                    // Array subscript?
                    if (getTokenStream().LA(1).getType() == JavaTokenTypes.RBRACK) {
                        // No subscript means that this is a type - must be followed by
                        // ".class" normally. Eg Object[].class
                        token = nextToken(); // RBRACK
                        continue;
                    }
                    parseExpression();
                    token = nextToken();
                    if (token.getType() != JavaTokenTypes.RBRACK) {
                        error("Expected ']' after array subscript expression");
                        getTokenStream().pushBack(token);
                    }
                    parser.gotArrayElementAccess();
                    break;
                case 9: // LITERAL_instanceof
                    parser.gotInstanceOfOperator(token);
                    switch (lookAheadParsePattern())
                    {
                        case PatternParse.TypeThenVariableName, PatternParse.RecordPattern -> {
                            parseRecordPattern(true);
                        }
                        case PatternParse.OnlyType -> {
                            parseTypeSpec(true);
                        }
                        default -> {
                            error("Expected type or pattern following instanceof");
                        }
                    }
                    break;
                case 10: // DOT
                    // Handle dot operator specially, as there are some special cases
                    LocatableToken opToken = token;
                    token = nextToken();
                    if (token.getType() == JavaTokenTypes.EOF) {
                        // Not valid, but may be useful for subclasses
                        parser.gotDotEOF(opToken);
                        break opLoop;
                    }
                    LocatableToken la1 = getTokenStream().LA(1);
                    if (la1.getType() == JavaTokenTypes.EOF
                            && la1.getColumn() == token.getEndColumn()
                            && la1.getLine() == token.getEndLine()) {
                        // Something that might look like a keyword, but might in fact
                        // be partially complete identifier.
                        String tokText = token.getText();
                        if (tokText != null && tokText.length() > 0) {
                            if (Character.isJavaIdentifierStart(tokText.charAt(0))) {
                                parser.gotMemberAccessEOF(token);
                                // break opLoop;
                                continue;
                            }
                        }
                    }
                    
                    if (token.getType() == JavaTokenTypes.LITERAL_class) {
                        // Class literal: continue and look for another operator
                        continue;
                    }
                    else if (token.getType() == JavaTokenTypes.IDENT) {
                        if (getTokenStream().LA(1).getType() == JavaTokenTypes.LPAREN) {
                            // Method call
                            parser.gotMemberCall(token, Collections.<LocatableToken>emptyList());
                            parseArgumentList(nextToken());
                        }
                        else {
                            parser.gotMemberAccess(token);
                        }
                        continue;
                    }
                    else if (token.getType() == JavaTokenTypes.LT) {
                        // generic method call
                        DepthRef dr = new DepthRef();
                        List<LocatableToken> ttokens = new LinkedList<LocatableToken>();
                        ttokens.add(token);
                        dr.depth = 1;
                        if (!parseTargs(false, ttokens, dr)) {
                            continue;  // we're a bit lost now really...
                        }
                        token = nextToken();
                        if (token.getType() != JavaTokenTypes.IDENT) {
                            error("Expecting method name (in call to generic method)");
                            continue;
                        }
                        parser.gotMemberCall(token, ttokens);
                        token = nextToken();
                        if (token.getType() != JavaTokenTypes.LPAREN) {
                            error("Expecting '(' after method name");
                            continue;
                        }
                        parseArgumentList(token);
                        continue;
                    }
                    parser.gotBinaryOperator(opToken);
                    break opLoop;
                case 11: // binary operator
                    if (token.getType() == JavaTokenTypes.METHOD_REFERENCE &&
                            getTokenStream().LA(1).getType() == JavaTokenTypes.LITERAL_new) {
                        nextToken(); // consume LITERAL_new
                        continue;
                    }
                    else {
                        // Binary operators - need another operand
                        parser.gotBinaryOperator(token);
                        token = nextToken();
                    }
                    break opLoop;
                    
                default:
                    if (token.getType() == JavaTokenTypes.INC
                            || token.getType() == JavaTokenTypes.DEC) {
                        // post operators (unary)
                        parser.gotPostOperator(token);
                        continue;
                    }
                    else if (token.getType() == JavaTokenTypes.QUESTION) {
                        parser.gotQuestionOperator(token);
                        parseExpression();
                        token = nextToken();
                        if (token.getType() != JavaTokenTypes.COLON) {
                            error("Expecting ':' (in ?: operator)");
                            getTokenStream().pushBack(token);
                            parser.endExpression(token, true);
                            return;
                        }
                        parser.gotQuestionColon(token);
                        token = nextToken();
                        break opLoop;
                    }
                    else {
                        getTokenStream().pushBack(token);
                        parser.endExpression(token, false);
                        return;
                    }
                }
            }
        }
    }

    public void gotComment(LocatableToken t) {
        parser.gotComment(t);
    }

    // Java now has the notion of patterns, which can occur in instanceof:
    //     if (obj instance String s) { }
    // Or in case statements:
    //     case String s -> { }
    // In both cases they can be record patterns, with variables declared inside a nested structure:
    //     case Rectangle(Point(int ax, int ay), Point b) -> {}
    // The challenge with parsing them is that when you parse the type, you don't know
    // in the instanceof cases if there will be a variable name following, and you don't know if
    // the type could actually be a pattern until you hit the round bracket.  Also, in case statements
    // it could actually be an expression instead.
    // So when we parse there are multiple possible outcomes:
    // - Just a type (valid for instanceof)
    // - A type and then a variable name
    // - A record pattern (including new variable names, but not followed by them)
    // - None of the above (could be a parse error, but could be an expression that is valid for case)
    private enum PatternParse
    {
        OnlyType,
        TypeThenVariableName,
        RecordPattern,
        Other
    }
    // Looks ahead in token stream to estimate what kind of pattern follows (see comment and data type above)
    // When it returns, the token stream will be in the same state as when the method is called; nothing
    // is actually consumed or processed.  This is used for after "instanceof", and after "case".
    // Note that a parse error still may occur (especially for record patterns) when parsing fully:
    private PatternParse lookAheadParsePattern() {
        List<LocatableToken> tlist = new LinkedList<LocatableToken>();
        boolean isTypeSpec = parseTypeSpec(true, true, tlist);
        LocatableToken laToken = getTokenStream().LA(1);
        pushBackAll(tlist);
        if (isTypeSpec) {
            return switch (laToken.getType()) {
                case JavaTokenTypes.IDENT ->
                    // A type then variable name e.g. String s or List<String> list
                        PatternParse.TypeThenVariableName;
                case JavaTokenTypes.LPAREN ->
                    // A record pattern, e.g. Empty() or Point(int x, int y)
                        PatternParse.RecordPattern;
                default ->
                    // Just a type, e.g. String or Integer:
                        PatternParse.OnlyType;
            };
        }
        else
        {
            return PatternParse.Other;
        }
    }


    public final LocatableToken parseArrayInitializerList(LocatableToken token)
    {
        // an initialiser list for an array
        do {
            if (getTokenStream().LA(1).getType() == JavaTokenTypes.RCURLY) {
                token = nextToken(); // RCURLY
                break;
            }
            parseExpression();
            token = nextToken();
        } while (token.getType() == JavaTokenTypes.COMMA);
        if (token.getType() != JavaTokenTypes.RCURLY) {
            errorBefore("Expected '}' at end of initialiser list expression", token);
            getTokenStream().pushBack(token);
        }
        return token;
    }
    
    public final void parseNewExpression(LocatableToken token)
    {
        // new XYZ(...)
        parser.gotExprNew(token);
        token = nextToken();
        if (token.getType() != JavaTokenTypes.IDENT && !isPrimitiveType(token)) {
            getTokenStream().pushBack(token);
            error("Expected type identifier after \"new\" (in expression)");
            parser.endExprNew(token, false);
            return;
        }
        getTokenStream().pushBack(token);
        parseTypeSpec(false);
        token = nextToken();

        if (token.getType() == JavaTokenTypes.LBRACK) {
            while (true) {
                // array dimensions
                boolean withDimension = false;
                if (getTokenStream().LA(1).getType() != JavaTokenTypes.RBRACK) {
                    withDimension = true;
                    parseExpression();
                }
                token = nextToken();
                if (token.getType() != JavaTokenTypes.RBRACK) {
                    getTokenStream().pushBack(token);
                    errorBefore("Expecting ']' after array dimension (in new ... expression)", token);
                    parser.endExprNew(token, false);
                    return;
                }
                else {
                    parser.gotNewArrayDeclarator(withDimension);
                }
                if (getTokenStream().LA(1).getType() != JavaTokenTypes.LBRACK) {
                    break;
                }
                token = nextToken();
            }
            
            if (getTokenStream().LA(1).getType() == JavaTokenTypes.LCURLY) {
                // Array initialiser list
                token = nextToken();
                parser.beginArrayInitList(token);
                token = parseArrayInitializerList(token);
                parser.endArrayInitList(token);
                parser.endExprNew(token, token.getType() == JavaTokenTypes.RCURLY);
                return;
            }

            parser.endExprNew(token, true);
            return;
        }

        if (token.getType() != JavaTokenTypes.LPAREN) {
            getTokenStream().pushBack(token);
            error("Expected '(' or '[' after type name (in 'new ...' expression)");
            parser.endExprNew(token, false);
            return;
        }
        parseArgumentList(token);

        if (getTokenStream().LA(1).getType() == JavaTokenTypes.LCURLY) {
            // a class body (anonymous inner class)
            token = nextToken(); // LCURLY
            parser.beginAnonClassBody(token, false);
            parseClassBody();
            token = nextToken();
            if (token.getType() != JavaTokenTypes.RCURLY) {
                error("Expected '}' at end of inner class body");
                getTokenStream().pushBack(token);
                getTokenStream().pushBack(token);
                parser.endAnonClassBody(token, false);
                parser.endExprNew(token, false);
                return;
            }
            parser.endAnonClassBody(token, true);
        }
        parser.endExprNew(token, true);
    }
    
    /**
     * Parse a comma-separated, possibly empty list of arguments to a method/constructor.
     * The closing ')' will be consumed by this method. 
     * @param token   the '(' token
     */
    public final void parseArgumentList(LocatableToken token)
    {
        parser.beginArgumentList(token);
        token = nextToken();
        if (token.getType() != JavaTokenTypes.RPAREN) {
            getTokenStream().pushBack(token);
            do  {
                parseExpression();
                token = nextToken();
                parser.endArgument();
            } while (token.getType() == JavaTokenTypes.COMMA);
            if (token.getType() != JavaTokenTypes.RPAREN) {
                errorBefore("Expecting ',' or ')' (in argument list)", token);
                getTokenStream().pushBack(token);
            }
        }
        parser.endArgumentList(token);
        return;
    }
    
    /**
     * Parse a list of formal parameters in Lambda (possibly empty)
     */
    public final void parseLambdaParameterList()
    {
        LocatableToken token = nextToken();
        
        while (token.getType() != JavaTokenTypes.RPAREN
                && token.getType() != JavaTokenTypes.RCURLY) {
            getTokenStream().pushBack(token);
            parser.gotLambdaFormalParam();
            //parse modifiers if any
            List<LocatableToken> rval = parseModifiers();
            
            int tt1 = getTokenStream().LA(1).getType();
            int tt2 = getTokenStream().LA(2).getType();
            if (tt1 == JavaTokenTypes.IDENT && (tt2 == JavaTokenTypes.COMMA || tt2 == JavaTokenTypes.RPAREN)) {
                token = nextToken(); // identifier
                parser.gotLambdaFormalName(token);
                token = nextToken();
            }
            else {
                if (! parseTypeSpec(false, true, rval)) {
                    parser.modifiersConsumed();
                    error("Formal lambda parameter specified incorrectly");
                    return;
                }
                parser.gotLambdaFormalType(rval);
                token = nextToken();
                if (token.getType() == JavaTokenTypes.TRIPLE_DOT) {
                    token = nextToken();
                }
                if (token.getType() != JavaTokenTypes.IDENT) {
                    parser.modifiersConsumed();
                    error("Formal lambda parameter lacks a name");
                    return;
                }
                parser.gotLambdaFormalName(token);
                parseArrayDeclarators();
                token = nextToken();
            }

            parser.modifiersConsumed();
 
            if (token.getType() != JavaTokenTypes.COMMA) {
                break;
            }
            token = nextToken();
        }
        getTokenStream().pushBack(token);
    }

    /**
     * Parse a list of formal parameters (possibly empty)
     */
    public final void parseParameterList(boolean areRecordParameters)
    {
        LocatableToken token = nextToken();
        while (token.getType() != JavaTokenTypes.RPAREN
                && token.getType() != JavaTokenTypes.RCURLY) {
            getTokenStream().pushBack(token);
            LocatableToken first = token;

            parser.beginFormalParameter(token);
            parseModifiers();
            parseTypeSpec(true);
            LocatableToken idToken = nextToken(); // identifier
            LocatableToken varargsToken = null;
            if (idToken.getType() == JavaTokenTypes.TRIPLE_DOT) {
                // var args
                varargsToken = idToken;
                idToken = nextToken();
            }
            if (idToken.getType() != JavaTokenTypes.IDENT) {
                error("Expected parameter identifier (in method/record parameter)");
                // TODO skip to next ',', ')' or '}' if there is one soon (LA(3)?)
                getTokenStream().pushBack(idToken);
                return;
            }
            parseArrayDeclarators();
            if (areRecordParameters)
                parser.gotRecordParameter(first, idToken, varargsToken);
            else
                parser.gotMethodParameter(idToken, varargsToken);

            parser.modifiersConsumed();
            token = nextToken();
            if (token.getType() != JavaTokenTypes.COMMA) {
                break;
            }
            token = nextToken();
        }
        getTokenStream().pushBack(token);
    }

    private void pushBackAll(List<LocatableToken> tokens)
    {
        ListIterator<LocatableToken> i = tokens.listIterator(tokens.size());
        while (i.hasPrevious()) {
            getTokenStream().pushBack(i.previous());
        }
    }

    private class DepthRef
    {
        int depth;
    }
}

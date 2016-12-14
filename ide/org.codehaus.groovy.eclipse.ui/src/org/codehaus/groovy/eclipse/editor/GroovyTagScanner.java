/*
 * Copyright 2009-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.eclipse.editor;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.eclipse.GroovyPlugin;
import org.codehaus.groovy.eclipse.core.preferences.PreferenceConstants;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.internal.ui.text.AbstractJavaScanner;
import org.eclipse.jdt.internal.ui.text.CombinedWordRule;
import org.eclipse.jdt.internal.ui.text.CombinedWordRule.WordMatcher;
import org.eclipse.jdt.internal.ui.text.JavaWhitespaceDetector;
import org.eclipse.jdt.internal.ui.text.JavaWordDetector;
import org.eclipse.jdt.ui.text.IColorManager;
import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWhitespaceDetector;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;

/**
 * A code scanner for Groovy files.
 *
 * Much of this class has been adapted from {@link JavaCodeScanner}
 *
 * @author Andrew Eisenberg
 * @created Dec 31, 2010
 */
public class GroovyTagScanner extends AbstractJavaScanner {

    /**
     * Rule to detect java operators.
     *
     * @since 3.0
     */
    protected class OperatorRule implements IRule {

        /** Java operators */
        private final char[] JAVA_OPERATORS = { ';', '(', ')', '{', '}', '.', '=', '/', '\\', '+', '-', '*', '[', ']', '<', '>', ':', '?', '!', ',', '|', '&', '^', '%', '~' };

        /** Token to return for this rule */
        private final IToken fToken;

        /**
         * Creates a new operator rule.
         *
         * @param token Token to use for this rule
         */
        public OperatorRule(IToken token) {
            fToken = token;
        }

        /**
         * Is this character an operator character?
         *
         * @param character Character to determine whether it is an operator
         *            character
         * @return <code>true</code> iff the character is an operator,
         *         <code>false</code> otherwise.
         */
        public boolean isOperator(char character) {
            for (int index = 0; index < JAVA_OPERATORS.length; index++) {
                if (JAVA_OPERATORS[index] == character)
                    return true;
            }
            return false;
        }

        /*
         * @see
         * org.eclipse.jface.text.rules.IRule#evaluate(org.eclipse.jface.text
         * .rules.ICharacterScanner)
         */
        public IToken evaluate(ICharacterScanner scanner) {

            int character = scanner.read();
            if (isOperator((char) character)) {
                do {
                    character = scanner.read();
                } while (isOperator((char) character));
                scanner.unread();
                return fToken;
            } else {
                scanner.unread();
                return Token.UNDEFINED;
            }
        }
    }

    /**
     * Rule to detect java brackets.
     *
     * @since 3.3
     */
    private static final class BracketRule implements IRule {

        /** Java brackets */
        private final char[] JAVA_BRACKETS = { '(', ')', '{', '}', '[', ']' };

        /** Token to return for this rule */
        private final IToken fToken;

        /**
         * Creates a new bracket rule.
         *
         * @param token Token to use for this rule
         */
        public BracketRule(IToken token) {
            fToken = token;
        }

        /**
         * Is this character a bracket character?
         *
         * @param character Character to determine whether it is a bracket
         *            character
         * @return <code>true</code> iff the character is a bracket,
         *         <code>false</code> otherwise.
         */
        public boolean isBracket(char character) {
            for (int index = 0; index < JAVA_BRACKETS.length; index++) {
                if (JAVA_BRACKETS[index] == character)
                    return true;
            }
            return false;
        }

        /*
         * @see
         * org.eclipse.jface.text.rules.IRule#evaluate(org.eclipse.jface.text
         * .rules.ICharacterScanner)
         */
        public IToken evaluate(ICharacterScanner scanner) {

            int character = scanner.read();
            if (isBracket((char) character)) {
                do {
                    character = scanner.read();
                } while (isBracket((char) character));
                scanner.unread();
                return fToken;
            } else {
                scanner.unread();
                return Token.UNDEFINED;
            }
        }
    }

    /**
     * An annotation rule matches the '@' symbol, any following whitespace and
     * a following java identifier or the <code>interface</code> keyword.
     *
     * It does not match if there is a comment between the '@' symbol and
     * the identifier. See https://bugs.eclipse.org/bugs/show_bug.cgi?id=82452
     *
     * @since 3.1
     */
    private static class AnnotationRule implements IRule {
        /**
         * A resettable scanner supports marking a position in a scanner and
         * unreading back to the marked position.
         */
        private static final class ResettableScanner implements ICharacterScanner {
            private final ICharacterScanner fDelegate;

            private int fReadCount;

            /**
             * Creates a new resettable scanner that will forward calls
             * to <code>scanner</code>, but store a marked position.
             *
             * @param scanner the delegate scanner
             */
            public ResettableScanner(final ICharacterScanner scanner) {
                Assert.isNotNull(scanner);
                fDelegate = scanner;
                mark();
            }

            /*
             * @see org.eclipse.jface.text.rules.ICharacterScanner#getColumn()
             */
            public int getColumn() {
                return fDelegate.getColumn();
            }

            /*
             * @see
             * org.eclipse.jface.text.rules.ICharacterScanner#getLegalLineDelimiters
             * ()
             */
            public char[][] getLegalLineDelimiters() {
                return fDelegate.getLegalLineDelimiters();
            }

            /*
             * @see org.eclipse.jface.text.rules.ICharacterScanner#read()
             */
            public int read() {
                int ch = fDelegate.read();
                if (ch != ICharacterScanner.EOF)
                    fReadCount++;
                return ch;
            }

            /*
             * @see org.eclipse.jface.text.rules.ICharacterScanner#unread()
             */
            public void unread() {
                if (fReadCount > 0)
                    fReadCount--;
                fDelegate.unread();
            }

            /**
             * Marks an offset in the scanned content.
             */
            public void mark() {
                fReadCount = 0;
            }

            /**
             * Resets the scanner to the marked position.
             */
            public void reset() {
                while (fReadCount > 0)
                    unread();

                while (fReadCount < 0)
                    read();
            }
        }

        /**
         * Do not mark '@' followed by newline; even if it's legal it's uncommon.
         * Doing sp could be confusing when an incomplete annotation is present
         * and the modifier of a method or field is highlighted as an annotation.
         */
        private final IWhitespaceDetector fWhitespaceDetector = new IWhitespaceDetector() {
            public boolean isWhitespace(char ch) {
                return Character.isWhitespace(ch) && ch != '\n' && ch != '\r';
            }
        };

        private final IWordDetector fWordDetector = new JavaWordDetector();

        private final IToken fInterfaceToken;

        private final IToken fAnnotationToken;

        /**
         * Creates a new rule.
         *
         * @param interfaceToken the token to return if
         *            <code>'@\s*interface'</code> is matched
         * @param annotationToken the token to return if <code>'@\s*\w+'</code>
         *            is matched, but not <code>'@\s*interface'</code>
         */
        public AnnotationRule(IToken interfaceToken, Token annotationToken) {
            fInterfaceToken = interfaceToken;
            fAnnotationToken = annotationToken;
        }

        /*
         * @see
         * org.eclipse.jface.text.rules.IRule#evaluate(org.eclipse.jface.text
         * .rules.ICharacterScanner)
         */
        public IToken evaluate(ICharacterScanner scanner) {

            ResettableScanner resettable = new ResettableScanner(scanner);
            if (resettable.read() == '@')
                if (skipWhitespace(resettable))
                    return readAnnotation(resettable);

            resettable.reset();
            return Token.UNDEFINED;
        }

        private IToken readAnnotation(ResettableScanner scanner) {
            StringBuffer buffer = new StringBuffer();

            if (!readIdentifier(scanner, buffer)) {
                scanner.reset();
                return Token.UNDEFINED;
            }

            if ("interface".equals(buffer.toString())) //$NON-NLS-1$
                return fInterfaceToken;

            while (readSegment(new ResettableScanner(scanner))) {
                // do nothing
            }
            return fAnnotationToken;
        }

        private boolean readSegment(ResettableScanner scanner) {
            scanner.mark();
            if (skipWhitespace(scanner) && skipDot(scanner) && skipWhitespace(scanner) && readIdentifier(scanner, null))
                return true;

            scanner.reset();
            return false;
        }

        private boolean skipDot(ICharacterScanner scanner) {
            int ch = scanner.read();
            if (ch == '.')
                return true;

            scanner.unread();
            return false;
        }

        private boolean readIdentifier(ICharacterScanner scanner, StringBuffer buffer) {
            int ch = scanner.read();
            boolean read = false;
            while (fWordDetector.isWordPart((char) ch)) {
                if (buffer != null)
                    buffer.append((char) ch);
                ch = scanner.read();
                read = true;
            }

            if (ch != ICharacterScanner.EOF)
                scanner.unread();

            return read;
        }

        private boolean skipWhitespace(ICharacterScanner scanner) {
            while (fWhitespaceDetector.isWhitespace((char) scanner.read())) {
                // do nothing
            }

            scanner.unread();
            return true;
        }
    }

    private static final String[] types = {
        "boolean",
        "byte",
        "char",
        "class",
        "double",
        "float",
        "int",
        "interface",
        "long",
        "short",
        "void"
    };
    private static final String[] keywords = {
        "abstract",
        "assert",
        "break",
        "case",
        "catch",
        "const",
        "continue",
        "default",
        "do",
        "else",
        "enum",
        "extends",
        "false",
        "final",
        "finally",
        "for",
        "goto",
        "if",
        "implements",
        "import",
        "instanceof",
        "interface",
        "native",
        "new",
        "null",
        "package",
        "private",
        "protected",
        "public",
        //"return", use the special return keyword now so returns can be highlighted differently
        "static",
        "super",
        "switch",
        "synchronized",
        "this",
        "throw",
        "throws",
        "transient",
        "true",
        "try",
        "void",
        "volatile",
        "while",
    };
    private static final String[] groovyKeywords = {
        "as",
        "in",
        "def",
        "trait",
    };
    private static final String   returnKeyword = "return";

    private static final String[] fgTokenProperties = {
        PreferenceConstants.GROOVY_EDITOR_DEFAULT_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_GJDK_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_JAVAKEYWORDS_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_GROOVYKEYWORDS_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_JAVATYPES_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_ANNOTATION_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_BRACKET_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_OPERATOR_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_RETURN_COLOR,
        PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_STRINGS_COLOR,
    };

    private final List<IRule> initialAdditionalRules;
    private final List<IRule> additionalRules;
    private final List<String> additionalGroovyKeywords;
    private final List<String> additionalGJDKWords;

    /**
     * @deprecated
     */
    @Deprecated
    public GroovyTagScanner(IColorManager manager) {
        this(manager, null, null, null);
    }

    /**
     * @param manager the color manager
     * @param additionalRules Additional scanner rules for sub-types to add new kinds of partitioning
     * @param additionalGroovyKeywords Additional keywords for sub-types to add new kinds of syntax highlighting
     * @deprecated use the syntaxHighlightingExtender extension point instead.  This gets all of the additional keyword
     * highlighting into editors of files in a project with a particular nature.
     */
    @Deprecated
    public GroovyTagScanner(IColorManager manager, List<IRule> initialAdditionalRules, List<IRule> additionalRules, List<String> additionalGroovyKeywords) {
        this(manager, initialAdditionalRules, additionalRules, additionalGroovyKeywords, null);
    }

    /**
     * @param manager the color manager
     * @param additionalRules Additional scanner rules for sub-types to add new kinds of partitioning
     * @param additionalGroovyKeywords Additional keywords for sub-types to add new kinds of groovy keyword syntax highlighting
     * @param additionalGJDKKeywords Additional keywords for sub-types to add new kinds of gjdk syntax highlightin
     */
    public GroovyTagScanner(IColorManager manager, List<IRule> initialAdditionalRules, List<IRule> additionalRules, List<String> additionalGroovyKeywords, List<String> additionalGJDKKeywords) {
        super(manager, GroovyPlugin.getDefault().getPreferenceStore());
        this.initialAdditionalRules = initialAdditionalRules;
        this.additionalRules = additionalRules;
        this.additionalGroovyKeywords = additionalGroovyKeywords;
        this.additionalGJDKWords = additionalGJDKKeywords;
        initialize();
    }

    @Override
    protected String[] getTokenProperties() {
        return fgTokenProperties;
    }

    @Override
    protected List<IRule> createRules() {

        List<IRule> rules = new ArrayList<IRule>();

        // initial additional rules
        if (initialAdditionalRules != null) {
            rules.addAll(initialAdditionalRules);
        }

        // Add rule for character constants.
        Token token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_STRINGS_COLOR);
        rules.add(new SingleLineRule("'", "'", token, '\\')); //$NON-NLS-2$ //$NON-NLS-1$

        // Add generic whitespace rule.
        rules.add(new WhitespaceRule(new JavaWhitespaceDetector()));

        // Add JLS3 rule for /@\s*interface/
        AnnotationRule atInterfaceRule = new AnnotationRule(
                getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_ANNOTATION_COLOR),
                getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_ANNOTATION_COLOR));
        rules.add(atInterfaceRule);

        // combined rule for all keywords
        JavaWordDetector wordDetector = new JavaWordDetector();
        token = getToken(PreferenceConstants.GROOVY_EDITOR_DEFAULT_COLOR);
        CombinedWordRule combinedWordRule = new CombinedWordRule(wordDetector, token);

        // Java keywords
        WordMatcher javaKeywordsMatcher = new WordMatcher();
        token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_JAVAKEYWORDS_COLOR);
        for (int i = 0; i < keywords.length; i++) {
            javaKeywordsMatcher.addWord(keywords[i], token);
        }
        combinedWordRule.addWordMatcher(javaKeywordsMatcher);

        // Java types
        WordMatcher javaTypesMatcher = new WordMatcher();
        token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_JAVATYPES_COLOR);
        for (int i = 0; i < types.length; i++) {
            javaTypesMatcher.addWord(types[i], token);
        }
        combinedWordRule.addWordMatcher(javaTypesMatcher);

        // Groovy Keywords, including additional keywords
        WordMatcher groovyKeywordsMatcher = new WordMatcher();
        token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_GROOVYKEYWORDS_COLOR);
        for (int i = 0; i < groovyKeywords.length; i++) {
            groovyKeywordsMatcher.addWord(groovyKeywords[i], token);
        }
        if (additionalGroovyKeywords != null) {
            for (String additional : additionalGroovyKeywords) {
                groovyKeywordsMatcher.addWord(additional, token);
            }
        }
        combinedWordRule.addWordMatcher(groovyKeywordsMatcher);

        // GJDK words, including additional keywords
        WordMatcher gjdkWordsMatcher = new WordMatcher();
        token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_GJDK_COLOR);
        if (additionalGJDKWords != null) {
            for (String additional : additionalGJDKWords) {
                gjdkWordsMatcher.addWord(additional, token);
            }
        }
        combinedWordRule.addWordMatcher(gjdkWordsMatcher);

        // Add rule for brackets
        // this rule must come before the operator rule
        token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_BRACKET_COLOR);
        rules.add(new BracketRule(token));

        // Add rule for operators
        token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_OPERATOR_COLOR);
        rules.add(new OperatorRule(token));

        // Add word rule for keyword 'return'.
        CombinedWordRule.WordMatcher returnWordRule = new CombinedWordRule.WordMatcher();
        token = getToken(PreferenceConstants.GROOVY_EDITOR_HIGHLIGHT_RETURN_COLOR);
        returnWordRule.addWord(returnKeyword, token);
        combinedWordRule.addWordMatcher(returnWordRule);

        rules.add(combinedWordRule);

        // additional rules
        if (additionalRules != null) {
            rules.addAll(additionalRules);
        }

        setDefaultReturnToken(getToken(PreferenceConstants.GROOVY_EDITOR_DEFAULT_COLOR));

        return rules;
    }
}

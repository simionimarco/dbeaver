/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2023 DBeaver Corp and others
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

package org.jkiss.dbeaver.model.sql.parser;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextUtilities;
import org.jkiss.dbeaver.model.sql.SQLSyntaxManager;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.utils.CommonUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Used to scan and detect for SQL keywords.
 */
public class SQLWordPartDetector extends SQLIdentifierDetector
{
    private String prevKeyWord = "";
    private String prevDelimiter = null;
    private List<String> prevWords = null;
    private String nextWord;
    private String wordPart;
    private String fullWord;
    private int cursorOffset;
    private int startOffset;
    private int endOffset;
    private int delimiterOffset;
    private int prevKeyWordOffset = -1;

    /**
     * Method SQLWordPartDetector.
     *
     * @param document text document
     * @param syntaxManager syntax manager
     * @param documentOffset into the SQL document
     */
    public SQLWordPartDetector(IDocument document, SQLSyntaxManager syntaxManager, int documentOffset)
    {
        this(document, syntaxManager, documentOffset, 1);
    }

    public SQLWordPartDetector(IDocument document, SQLSyntaxManager syntaxManager, int documentOffset, int prevWordsParse)
    {
        super(syntaxManager.getDialect(), syntaxManager.getStructSeparator(), syntaxManager.getIdentifierQuoteStrings());
        cursorOffset = documentOffset;
        startOffset = documentOffset - 1;
        endOffset = documentOffset;
        int topIndex = 0, documentLength = document.getLength();
        try {
            String contentType = TextUtilities.getContentType(document, SQLParserPartitions.SQL_PARTITIONING, documentOffset, true);
            boolean inQuote = SQLParserPartitions.CONTENT_TYPE_SQL_QUOTED.equals(contentType);
            boolean inString = SQLParserPartitions.CONTENT_TYPE_SQL_STRING.equals(contentType);
            while (startOffset >= topIndex && startOffset < documentLength) {
                char c = document.getChar(startOffset);
                if (inQuote || inString) {
                    // Opening quote
                    if (isQuote(c)) {
                        if (startOffset > 1 && syntaxManager.getStructSeparator() == document.getChar(startOffset - 1)) {
                            // Previous char is a separator. Keep going. This is a part of a long name #13004
                            startOffset--;
                            inQuote = false;
                        } else {
                            startOffset--;
                            break;
                        }
                    } else if (isStringQuote(c)) {
                        break;
                    }
                    startOffset--;
                } else if (isQuote(c)) {
                    startOffset--;
                    inQuote = true;
                } else if (isStringQuote(c)) {
                    startOffset--;
                    inString = true;
                } else if (isWordPart(c)) {
                    startOffset--;
                } else {
                    break;
                }
            }
            while (endOffset < documentLength && isWordPart(document.getChar(endOffset))) {
                endOffset++;
            }

            int prevOffset = startOffset;
            //we've been one step too far : increase the offset
            startOffset++;
            wordPart = document.get(startOffset, documentOffset - startOffset);
            fullWord = document.get(startOffset, endOffset - startOffset);

            // Get previous keyword
            while (prevOffset >= topIndex) {
                StringBuilder prevPiece = new StringBuilder();
                while (prevOffset >= topIndex) {
                    char ch = document.getChar(prevOffset);
                    if (isWordPart(ch)) {
                        break;
                    } else if (!Character.isWhitespace(ch)) {
                        delimiterOffset = prevOffset;
                    }
                    prevPiece.insert(0, ch);
                    prevOffset--;
                }
                if (prevDelimiter == null) {
                    //startOffset - prevPiece.length();
                    prevDelimiter = prevPiece.toString().trim();
                }
                for (String delim : syntaxManager.getStatementDelimiters()) {
                    if (prevPiece.indexOf(delim) != -1) {
                        // Statement delimiter found - do not process to previous keyword
                        return;
                    }
                }
                inQuote = false;
                int prevStartOffset = prevOffset + 1;
                while (prevOffset >= topIndex) {
                    char ch = document.getChar(prevOffset);
                    if (isQuote(ch)) {
                        inQuote = !inQuote;
                        prevOffset--;
                    } else if (inQuote || isWordPart(ch)) {
                        prevOffset--;
                    } else {
                        prevOffset++;
                        break;
                    }
                }
                if (prevOffset < topIndex) {
                    prevOffset = topIndex;
                }

                String prevWord = document.get(prevOffset, prevStartOffset - prevOffset);
                if (dialect.isEntityQueryWord(prevWord) || dialect.isAttributeQueryWord(prevWord) || SQLUtils.isExecKeyword(dialect, prevWord)) {
                    if (CommonUtils.isEmpty(prevKeyWord)) {
                        this.prevKeyWord = prevWord.toUpperCase(Locale.ENGLISH);
                        this.prevKeyWordOffset = prevOffset;
                        if (prevWordsParse <= 1) {
                            break;
                        }
                    } else {
                        if (prevWords != null && prevWords.size() >= prevWordsParse) {
                            break;
                        }
                    }
                }
                if (prevWords == null) {
                    prevWords = new ArrayList<>();
                }
                if (!prevWord.equals(prevKeyWord)) {
                    // Add only second word (first is in prevKeyword)
                    prevWords.add(prevWord);
                }
                prevOffset--;
            }

            // Get next keyword
            {
                int nextOffset = documentOffset;
                // Skip whitespaces
                while (nextOffset < documentLength) {
                    char ch = document.getChar(nextOffset);
                    if (!isWordPart(ch)) {
                        nextOffset++;
                    } else {
                        break;
                    }
                }
                int wordPos = nextOffset;
                while (nextOffset < documentLength) {
                    char ch = document.getChar(nextOffset);
                    if (!isWordPart(ch)) {
                        break;
                    }
                    nextOffset++;
                }
                if (nextOffset > wordPos) {
                    nextWord = document.get(wordPos, nextOffset - wordPos);
                }
            }
        } catch (BadLocationException e) {
            // do nothing
        }
    }

    /**
     * Method getWordPart.
     *
     * @return String
     */
    public String getWordPart()
    {
        return wordPart;
    }

    public String getFullWord()
    {
        return fullWord;
    }

    public String getPrevDelimiter()
    {
        return prevDelimiter;
    }

    public List<String> getPrevWords()
    {
        return prevWords;
    }

    public int getCursorOffset()
    {
        return cursorOffset;
    }

    public int getStartOffset()
    {
        return startOffset;
    }

    public int getEndOffset()
    {
        return endOffset;
    }

    public int getLength()
    {
        return endOffset - startOffset;
    }

    /**
     * Previous valuable entity or attribute manipulation keyword.
     * All functions, aggregate operators and other keywords are ignored.
     */
    public String getPrevKeyWord() {
        return prevKeyWord;
    }
    
    public int getPrevKeyWordOffset() {
        return prevKeyWordOffset;
    }

    public String getNextWord() {
        return nextWord;
    }

    public String[] splitWordPart()
    {
        return super.splitIdentifier(wordPart);
    }

    public void moveToDelimiter() {
        int shift = startOffset - delimiterOffset;
        startOffset -= shift;
    }

    public void shiftOffset(int offset) {
        startOffset += offset;
    }
}
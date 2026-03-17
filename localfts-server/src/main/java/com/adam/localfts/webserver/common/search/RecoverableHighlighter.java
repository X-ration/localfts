/**
 * 基于Highlighter(org.apache.lucene.search.highlight)扩展
 * 原始协议：Apache License 2.0
 */
package com.adam.localfts.webserver.common.search;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.util.PriorityQueue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class RecoverableHighlighter extends Highlighter {
    protected int maxDocCharsToAnalyze = DEFAULT_MAX_CHARS_TO_ANALYZE;
    protected Formatter formatter;
    protected Encoder encoder;
    protected Fragmenter textFragmenter=new SimpleFragmenter();
    protected Scorer fragmentScorer=null;
    public RecoverableHighlighter(Scorer fragmentScorer) {
        this(new SimpleHTMLFormatter(), fragmentScorer);
    }

    public RecoverableHighlighter(Formatter formatter, Scorer fragmentScorer) {
        this(formatter, new DefaultEncoder(), fragmentScorer);
    }

    public RecoverableHighlighter(Formatter formatter, Encoder encoder, Scorer fragmentScorer) {
        super(formatter, encoder, fragmentScorer);
        this.formatter = formatter;
        this.encoder = encoder;
        this.fragmentScorer = fragmentScorer;
    }

    /**
     * Copied and modified from Highlighter#getBestTextFragments
     * @see org.apache.lucene.search.highlight.Highlighter#getBestTextFragments(TokenStream, String, boolean, int)
     * Low level api to get the most relevant (formatted) sections of the document.
     * This method has been made public to allow visibility of score information held in TextFragment objects.
     * Thanks to Jason Calabrese for help in redefining the interface.
     * @throws IOException If there is a low-level I/O error
     * @throws InvalidTokenOffsetsException thrown if any token's endOffset exceeds the provided fieldText's length
     */
    public final TextFragment[] getOriginalBestTextFragments(
            TokenStream tokenStream,
            String fieldText,
            String originalText,
            boolean mergeContiguousFragments,
            int maxNumFragments)
            throws IOException, InvalidTokenOffsetsException
    {
        ArrayList<TextFragment> docFrags = new ArrayList<>();
        StringBuilder newText=new StringBuilder();

        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        TextFragment currentFrag =  new TextFragment(newText,newText.length(), docFrags.size());

        if (fragmentScorer instanceof QueryScorer) {
            ((QueryScorer) fragmentScorer).setMaxDocCharsToAnalyze(maxDocCharsToAnalyze);
        }

        TokenStream newStream = fragmentScorer.init(tokenStream);
        if(newStream != null) {
            tokenStream = newStream;
        }
        fragmentScorer.startFragment(currentFrag);
        docFrags.add(currentFrag);

        FragmentQueue fragQueue = new FragmentQueue(maxNumFragments);

        try
        {

            String tokenText;
            int startOffset;
            int endOffset;
            int lastEndOffset = 0;
            textFragmenter.start(fieldText, tokenStream);

            TokenGroup tokenGroup=new TokenGroup(tokenStream);

            tokenStream.reset();
            for (boolean next = tokenStream.incrementToken(); next && (offsetAtt.startOffset()< maxDocCharsToAnalyze);
                 next = tokenStream.incrementToken())
            {
                if(  (offsetAtt.endOffset()>fieldText.length())
                        ||
                        (offsetAtt.startOffset()>fieldText.length())
                )
                {
                    throw new InvalidTokenOffsetsException("Token "+ termAtt.toString()
                            +" exceeds length of provided fieldText sized "+fieldText.length());
                }
                if((tokenGroup.getNumTokens() >0)&&(tokenGroup.isDistinct()))
                {
                    //the current token is distinct from previous tokens -
                    // markup the cached token group info
                    startOffset = tokenGroup.getStartOffset();
                    endOffset = tokenGroup.getEndOffset();
                    tokenText = fieldText.substring(startOffset, endOffset);
                    String markedUpText=formatter.highlightTerm(encoder.encodeText(tokenText), tokenGroup);
                    //store any whitespace etc from between this and last group
                    if (startOffset > lastEndOffset)
//                        newText.append(encoder.encodeText(fieldText.substring(lastEndOffset, startOffset)));
                        newText.append(encoder.encodeText(originalText.substring(lastEndOffset, startOffset)));
                    newText.append(markedUpText);
                    lastEndOffset=Math.max(endOffset, lastEndOffset);
                    tokenGroup.clear();

                    //check if current token marks the start of a new fragment
                    if(textFragmenter.isNewFragment())
                    {
                        currentFrag.setScore(fragmentScorer.getFragmentScore());
                        //record stats for a new fragment
                        currentFrag.textEndPos = newText.length();
                        currentFrag =new TextFragment(newText, newText.length(), docFrags.size());
                        fragmentScorer.startFragment(currentFrag);
                        docFrags.add(currentFrag);
                    }
                }

                tokenGroup.addToken(fragmentScorer.getTokenScore());

//        if(lastEndOffset>maxDocBytesToAnalyze)
//        {
//          break;
//        }
            }
            currentFrag.setScore(fragmentScorer.getFragmentScore());

            if(tokenGroup.getNumTokens() >0)
            {
                //flush the accumulated fieldText (same code as in above loop)
                startOffset = tokenGroup.getStartOffset();
                endOffset = tokenGroup.getEndOffset();
                tokenText = fieldText.substring(startOffset, endOffset);
                String markedUpText=formatter.highlightTerm(encoder.encodeText(tokenText), tokenGroup);
                //store any whitespace etc from between this and last group
                if (startOffset > lastEndOffset)
                    newText.append(encoder.encodeText(fieldText.substring(lastEndOffset, startOffset)));
                newText.append(markedUpText);
                lastEndOffset=Math.max(lastEndOffset,endOffset);
            }

            //Test what remains of the original fieldText beyond the point where we stopped analyzing
            if (
//          if there is fieldText beyond the last token considered..
                    (lastEndOffset < fieldText.length())
                            &&
//          and that fieldText is not too large...
                            (fieldText.length()<= maxDocCharsToAnalyze)
            )
            {
                //append it to the last fragment
                newText.append(encoder.encodeText(fieldText.substring(lastEndOffset)));
            }

            currentFrag.textEndPos = newText.length();

            //sort the most relevant sections of the fieldText
            for (Iterator<TextFragment> i = docFrags.iterator(); i.hasNext();)
            {
                currentFrag = i.next();

                //If you are running with a version of Lucene before 11th Sept 03
                // you do not have PriorityQueue.insert() - so uncomment the code below
        /*
                  if (currentFrag.getScore() >= minScore)
                  {
                    fragQueue.put(currentFrag);
                    if (fragQueue.size() > maxNumFragments)
                    { // if hit queue overfull
                      fragQueue.pop(); // remove lowest in hit queue
                      minScore = ((TextFragment) fragQueue.top()).getScore(); // reset minScore
                    }


                  }
        */
                //The above code caused a problem as a result of Christoph Goller's 11th Sept 03
                //fix to PriorityQueue. The correct method to use here is the new "insert" method
                // USE ABOVE CODE IF THIS DOES NOT COMPILE!
                fragQueue.insertWithOverflow(currentFrag);
            }

            //return the most relevant fragments
            TextFragment frag[] = new TextFragment[fragQueue.size()];
            for (int i = frag.length - 1; i >= 0; i--)
            {
                frag[i] = fragQueue.pop();
            }

            //merge any contiguous fragments to improve readability
            if(mergeContiguousFragments)
            {
                mergeContiguousFragments(frag);
                ArrayList<TextFragment> fragTexts = new ArrayList<>();
                for (int i = 0; i < frag.length; i++)
                {
                    if ((frag[i] != null) && (frag[i].getScore() > 0))
                    {
                        fragTexts.add(frag[i]);
                    }
                }
                frag= fragTexts.toArray(new TextFragment[0]);
            }

            return frag;

        }
        finally
        {
            if (tokenStream != null)
            {
                try
                {
                    tokenStream.end();
                    tokenStream.close();
                }
                catch (Exception e)
                {
                }
            }
        }
    }

    /** Copied from Highlighter#mergeContiguousFragments
     * @see org.apache.lucene.search.highlight.Highlighter#mergeContiguousFragments(TextFragment[])
     * Improves readability of a score-sorted list of TextFragments by merging any fragments
     * that were contiguous in the original text into one larger fragment with the correct order.
     * This will leave a "null" in the array entry for the lesser scored fragment.
     *
     * @param frag An array of document fragments in descending score
     */
    private void mergeContiguousFragments(TextFragment[] frag)
    {
        boolean mergingStillBeingDone;
        if (frag.length > 1)
            do
            {
                mergingStillBeingDone = false; //initialise loop control flag
                //for each fragment, scan other frags looking for contiguous blocks
                for (int i = 0; i < frag.length; i++)
                {
                    if (frag[i] == null)
                    {
                        continue;
                    }
                    //merge any contiguous blocks
                    for (int x = 0; x < frag.length; x++)
                    {
                        if (frag[x] == null)
                        {
                            continue;
                        }
                        if (frag[i] == null)
                        {
                            break;
                        }
                        TextFragment frag1 = null;
                        TextFragment frag2 = null;
                        int frag1Num = 0;
                        int frag2Num = 0;
                        int bestScoringFragNum;
                        int worstScoringFragNum;
                        //if blocks are contiguous....
                        if (frag[i].follows(frag[x]))
                        {
                            frag1 = frag[x];
                            frag1Num = x;
                            frag2 = frag[i];
                            frag2Num = i;
                        }
                        else
                        if (frag[x].follows(frag[i]))
                        {
                            frag1 = frag[i];
                            frag1Num = i;
                            frag2 = frag[x];
                            frag2Num = x;
                        }
                        //merging required..
                        if (frag1 != null)
                        {
                            if (frag1.getScore() > frag2.getScore())
                            {
                                bestScoringFragNum = frag1Num;
                                worstScoringFragNum = frag2Num;
                            }
                            else
                            {
                                bestScoringFragNum = frag2Num;
                                worstScoringFragNum = frag1Num;
                            }
                            frag1.merge(frag2);
                            frag[worstScoringFragNum] = null;
                            mergingStillBeingDone = true;
                            frag[bestScoringFragNum] = frag1;
                        }
                    }
                }
            }
            while (mergingStillBeingDone);
    }
}
class FragmentQueue extends PriorityQueue<TextFragment>
{
    public FragmentQueue(int size)
    {
        super(size);
    }

    @Override
    public final boolean lessThan(TextFragment fragA, TextFragment fragB)
    {
        if (fragA.getScore() == fragB.getScore())
            return fragA.fragNum > fragB.fragNum;
        else
            return fragA.getScore() < fragB.getScore();
    }
}

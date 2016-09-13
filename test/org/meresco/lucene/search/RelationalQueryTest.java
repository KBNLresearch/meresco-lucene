package org.meresco.lucene.search;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.meresco.lucene.Lucene;
import org.meresco.lucene.LuceneSettings;
import org.meresco.lucene.MultiLuceneTest;
import org.meresco.lucene.SeecrTestCase;
import org.meresco.lucene.search.join.JoinANDQuery;
import org.meresco.lucene.search.join.LuceneQuery;
import org.meresco.lucene.search.join.NoOp;
import org.meresco.lucene.search.join.RelationalQuery;
import org.meresco.lucene.search.join.Result;

public class RelationalQueryTest extends SeecrTestCase {
    private Lucene luceneA;
    private Lucene luceneB;
    private Lucene luceneC;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        LuceneSettings settingsA = new LuceneSettings();
        LuceneSettings settingsB = new LuceneSettings();
        LuceneSettings settingsC = new LuceneSettings();
        settingsC.similarity = new TermFrequencySimilarity();
        luceneA = new Lucene("coreA", this.tmpDir.resolve("a"), settingsA);
        luceneB = new Lucene("coreB", this.tmpDir.resolve("b"), settingsB);
        luceneC = new Lucene("coreC", this.tmpDir.resolve("c"), settingsC);
        MultiLuceneTest.prepareFixture(luceneA, luceneB, luceneC);
    }

    @After
    public void tearDown() throws Exception {
        luceneA.close();
        luceneB.close();
        luceneC.close();
        super.tearDown();
    }

    @Test
    public void test() {
        // String query = "(summary.title =/boost=3 aap) AND (holding.holder = bieb)";

        // Query root = new JoinANDQuery(
        // new LuceneQuery("title= aap", "summary", "key", "ranks"),
        // new LuceneQuery("holder = bieb", "holding", "item", null));

        // root.execute(/* lucenesearcher */ null);
    }

    @Test
    public void testSimpleQuery() {
        RelationalQuery root = new JoinANDQuery(
                new LuceneQuery(luceneC, "C", new TermQuery(new Term("O", "true"))/* here all those args*/),
                new LuceneQuery(luceneA, "A", new TermQuery(new Term("M", "true"))));
        Result result = root.execute(new NoOp());
                
    }
}

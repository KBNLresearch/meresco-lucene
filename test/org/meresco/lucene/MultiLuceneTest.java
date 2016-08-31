/* begin license *
 *
 * "Meresco Lucene" is a set of components and tools to integrate Lucene (based on PyLucene) into Meresco
 *
 * Copyright (C) 2015-2016 Koninklijke Bibliotheek (KB) http://www.kb.nl
 * Copyright (C) 2015-2016 Seecr (Seek You Too B.V.) http://seecr.nl
 * Copyright (C) 2016 Stichting Kennisnet http://www.kennisnet.nl
 *
 * This file is part of "Meresco Lucene"
 *
 * "Meresco Lucene" is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * "Meresco Lucene" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "Meresco Lucene"; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * end license */

package org.meresco.lucene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.FixedBitSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.meresco.lucene.LuceneResponse.DrilldownData;
import org.meresco.lucene.QueryConverter.FacetRequest;
import org.meresco.lucene.search.TermFrequencySimilarity;

public class MultiLuceneTest extends SeecrTestCase {

    private Lucene luceneA;
    private Lucene luceneB;
    private Lucene luceneC;
    private MultiLucene multiLucene;

    @SuppressWarnings({ "unchecked", "serial", "rawtypes" })
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

        multiLucene = new MultiLucene(new ArrayList<Lucene>() {{ add(luceneA); add(luceneB); add(luceneC);}});

        LuceneTest.addDocument(luceneA, "A",      new HashMap() {{put("A", 1);}}, new HashMap() {{put("M", "false"); put("Q", "false"); put("U", "false"); put("S", "1");}});
        LuceneTest.addDocument(luceneA, "A-U",    new HashMap() {{put("A", 2 );}}, new HashMap() {{put("M", "false"); put("Q", "false"); put("U", "true" ); put("S", "2");}});
        LuceneTest.addDocument(luceneA, "A-Q",    new HashMap() {{put("A", 3 );}}, new HashMap() {{put("M", "false"); put("Q", "true" ); put("U", "false"); put("S", "3");}});
        LuceneTest.addDocument(luceneA, "A-QU",   new HashMap() {{put("A", 4 );}}, new HashMap() {{put("M", "false"); put("Q", "true" ); put("U", "true" ); put("S", "4");}});
        LuceneTest.addDocument(luceneA, "A-M",    new HashMap() {{put("A", 5 ); put("C", 5);}}, new HashMap() {{put("M", "true" ); put("Q", "false"); put("U", "false"); put("S", "5");}});
        LuceneTest.addDocument(luceneA, "A-MU",   new HashMap() {{put("A", 6 ); put("C", 12);}}, new HashMap() {{put("M", "true" ); put("Q", "false"); put("U", "true" ); put("S", "6");}});
        LuceneTest.addDocument(luceneA, "A-MQ",   new HashMap() {{put("A", 7 );}}, new HashMap() {{put("M", "true" ); put("Q", "true" ); put("U", "false"); put("S", "7");}});
        LuceneTest.addDocument(luceneA, "A-MQU",  new HashMap() {{put("A", 8 );}}, new HashMap() {{put("M", "true" ); put("Q", "true" ); put("U", "true" ); put("S", "8");}});

        LuceneTest.addDocument(luceneB, "B-N>A-M",   new HashMap() {{put("B", 5 ); put("D", 5);}}, new HashMap() {{put("N", "true" ); put("O", "true" ); put("P", "false"); put("T", "A"); put("intField", "1");}});
        LuceneTest.addDocument(luceneB, "B-N>A-MU",  new HashMap() {{put("B", 6 );}}, new HashMap() {{put("N", "true" ); put("O", "false"); put("P", "false"); put("T", "B"); put("intField", "2");}});
        LuceneTest.addDocument(luceneB, "B-N>A-MQ",  new HashMap() {{put("B", 7 );}}, new HashMap() {{put("N", "true" ); put("O", "true" ); put("P", "false"); put("T", "C"); put("intField", "3");}});
        LuceneTest.addDocument(luceneB, "B-N>A-MQU", new HashMap() {{put("B", 8 );}}, new HashMap() {{put("N", "true" ); put("O", "false"); put("P", "false"); put("T", "D"); put("intField", "4");}});
        LuceneTest.addDocument(luceneB, "B-N",       new HashMap() {{put("B", 9 );}}, new HashMap() {{put("N", "true" ); put("O", "true" ); put("P", "false"); put("T", "E"); put("intField", "5");}});
        LuceneTest.addDocument(luceneB, "B",         new HashMap() {{put("B", 10);}}, new HashMap() {{put("N", "false"); put("O", "false"); put("P", "false"); put("T", "F"); put("intField", "6");}});
        LuceneTest.addDocument(luceneB, "B-P>A-M",   new HashMap() {{put("B", 5 );}}, new HashMap() {{put("N", "false"); put("O", "true" ); put("P", "true" ); put("T", "G"); put("intField", "7");}});
        LuceneTest.addDocument(luceneB, "B-P>A-MU",  new HashMap() {{put("B", 6 );}}, new HashMap() {{put("N", "false"); put("O", "false"); put("P", "true" ); put("T", "H"); put("intField", "8");}});
        LuceneTest.addDocument(luceneB, "B-P>A-MQ",  new HashMap() {{put("B", 7 );}}, new HashMap() {{put("N", "false"); put("O", "false" ); put("P", "true" ); put("T", "I"); put("intField", "9");}});
        LuceneTest.addDocument(luceneB, "B-P>A-MQU", new HashMap() {{put("B", 8 );}}, new HashMap() {{put("N", "false"); put("O", "false"); put("P", "true" ); put("T", "J"); put("intField", "10");}});
        LuceneTest.addDocument(luceneB, "B-P",       new HashMap() {{put("B", 11);}}, new HashMap() {{put("N", "false"); put("O", "true" ); put("P", "true" ); put("T", "K"); put("intField", "11");}});

        LuceneTest.addDocument(luceneC, "C-R", new HashMap() {{put("C", 5); put("C2", 12);}}, new HashMap() {{put("R", "true");}});
        LuceneTest.addDocument(luceneC, "C-S", new HashMap() {{put("C", 8);}}, new HashMap() {{put("S", "true");}});
        LuceneTest.addDocument(luceneC, "C-S2", new HashMap() {{put("C", 7);}}, new HashMap() {{put("S", "false");}});

        luceneA.commit();
        luceneB.commit();
        luceneC.commit();

        settingsA.commitCount = 1;
        settingsB.commitCount = 1;
        settingsC.commitCount = 1;
    }

    @After
    public void tearDown() throws Exception {
        luceneA.close();
        luceneB.close();
        luceneC.close();
        super.tearDown();
    }

    @Test
    public void testQueryOneIndexWithComposedQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA", new TermQuery(new Term("Q", "true")));
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHits(result, "A-Q", "A-QU", "A-MQ", "A-MQU");
    }

    @Test
    public void testQueryOneIndexWithComposedQueryWithFilterQueries() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.addFilterQuery("coreA", new TermQuery(new Term("Q", "true")));
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHits(result, "A-Q", "A-QU", "A-MQ", "A-MQU");
    }

    @Test
    public void testJoinQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");
    }

//    testMultipleJoinQueriesKeepsCachesWithinMaxSize
    @Test
    public void testJoinQueryWithFilters() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.addFilterQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");
    }

    @Test
    public void testJoinWithFacetInResultCore() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.setCoreQuery("coreB", new TermQuery(new Term("O", "true")));
        q.addFacet("coreA", new QueryConverter.FacetRequest("cat_M", 10));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(1, result.total);
        assertEquals(1, result.drilldownData.size());
        assertEquals("true", result.drilldownData.get(0).terms.get(0).label);
        assertEquals(1, result.drilldownData.get(0).terms.get(0).count);
    }

    @Test
    public void testJoinFacet() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_N", 10));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.drilldownData.size());
        assertEquals("cat_N", result.drilldownData.get(0).fieldname);
        assertEquals("true", result.drilldownData.get(0).terms.get(0).label);
        assertEquals("false", result.drilldownData.get(0).terms.get(1).label);
        assertEquals(2, result.drilldownData.get(0).terms.get(0).count);
        assertEquals(2, result.drilldownData.get(0).terms.get(1).count);
        assertEquals("cat_O", result.drilldownData.get(1).fieldname);
        assertEquals("false", result.drilldownData.get(1).terms.get(0).label);
        assertEquals("true", result.drilldownData.get(1).terms.get(1).label);
        assertEquals(3, result.drilldownData.get(1).terms.get(0).count);
        assertEquals(1, result.drilldownData.get(1).terms.get(1).count);
    }

    @Test
    public void testJoinFacetWithDrilldownQueryFilters() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("M", "true")));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addDrilldownQuery("coreA", "cat_Q", "true");
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.total);
        assertEquals(1, result.drilldownData.size());
        DrilldownData catO = result.drilldownData.get(0);
        assertEquals("cat_O", catO.fieldname);
        assertEquals(2, catO.terms.size());
        assertEquals("false", catO.terms.get(0).label);
        assertEquals(3, catO.terms.get(0).count);
        assertEquals("true", catO.terms.get(1).label);
        assertEquals(1, catO.terms.get(1).count);
    }

    @Test
    public void testMultipleDrilldownQueries() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.addDrilldownQuery("coreA", "cat_Q", "true");
        q.addDrilldownQuery("coreA", "cat_Q", "false");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(0, result.total);
    }

    @Test
    public void testJoinFacetWithJoinDrilldownQueryFilters() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("M", "true")));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addDrilldownQuery("coreB", "cat_O", "true");
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.total);
        assertEquals(1, result.drilldownData.size());
        DrilldownData catO = result.drilldownData.get(0);
        assertEquals("cat_O", catO.fieldname);
        assertEquals(1, catO.terms.size());
        assertEquals("true", catO.terms.get(0).label);
        assertEquals(3, catO.terms.get(0).count);
    }

    @Test
    public void testJoinDrilldownQueryFilters() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("M", "true")));
        q.addDrilldownQuery("coreA", "cat_Q", "true");
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.total);
    }

    @Test
    public void testJoinFacetWithFilter() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("M", "true")));
        q.addFilterQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.total);

        assertEquals(1, result.drilldownData.size());
        DrilldownData catO = result.drilldownData.get(0);
        assertEquals("cat_O", catO.fieldname);
        assertEquals(2, catO.terms.size());
        assertEquals("false", catO.terms.get(0).label);
        assertEquals(3, catO.terms.get(0).count);
        assertEquals("true", catO.terms.get(1).label);
        assertEquals(1, catO.terms.get(1).count);
    }

    @Test
    public void testJoinFacetWillNotFilter() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_N", 10));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(8, result.total);

        assertEquals(1, result.drilldownData.size());
        DrilldownData catN = result.drilldownData.get(0);
        assertEquals("cat_N", catN.fieldname);
        assertEquals(2, catN.terms.size());
        assertEquals("true", catN.terms.get(0).label);
        assertEquals(4, catN.terms.get(0).count);
        assertEquals("false", catN.terms.get(1).label);
        assertEquals(4, catN.terms.get(1).count);
    }

    @Test
    public void testJoinFacetAndQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_N", 10));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");

        assertEquals(2, result.drilldownData.size());
        DrilldownData catN = result.drilldownData.get(0);
        assertEquals("cat_N", catN.fieldname);
        assertEquals(1, catN.terms.size());
        assertEquals("true", catN.terms.get(0).label);
        assertEquals(4, catN.terms.get(0).count);

        DrilldownData catO = result.drilldownData.get(1);
        assertEquals("cat_O", catO.fieldname);
        assertEquals(2, catO.terms.size());
        assertEquals("true", catO.terms.get(0).label);
        assertEquals(2, catO.terms.get(0).count);
        assertEquals("false", catO.terms.get(1).label);
        assertEquals(2, catO.terms.get(1).count);
    }

    @Test
    public void testUniteResultFromTwoIndexes() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.setCoreQuery("coreB", null);
        q.addMatch("coreA", "coreB", "A", "B");
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(3, result.total);
        LuceneTest.compareHits(result, "A-QU", "A-MQ", "A-MQU");
    }

    @Test
    public void testUniteResultFromTwoIndexesCached() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.setCoreQuery("coreB", null);
        q.addMatch("coreA", "coreB", "A", "B");
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        LuceneResponse resultOne = multiLucene.executeComposedQuery(q);

        q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("U", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        q.addUnite("coreA", new TermQuery(new Term("U", "false")), "coreB", new TermQuery(new Term("N", "true")));
        multiLucene.executeComposedQuery(q);

        q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.setCoreQuery("coreB", null);
        q.addMatch("coreA", "coreB", "A", "B");
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        LuceneResponse resultAgain = multiLucene.executeComposedQuery(q);
        assertEquals(resultOne.total, resultAgain.total);

        for (int i = 0; i < resultOne.hits.size(); i++) {
            assertEquals(resultOne.hits.get(i).id, resultAgain.hits.get(i).id);
        }
    }

     @SuppressWarnings({ "rawtypes", "serial", "unchecked" })
     @Test
     public void testUniteResultFromTwoIndexesCachedAfterUpdate() throws Throwable {
         ComposedQuery q = new ComposedQuery("coreA");
         q.setCoreQuery("coreA", new TermQuery(new Term("Q", "true")));
         q.setCoreQuery("coreB", null);
         q.addMatch("coreA", "coreB", "A", "B");
         q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
         LuceneResponse resultOne = multiLucene.executeComposedQuery(q);
         assertEquals(3, resultOne.total);

         LuceneTest.addDocument(luceneA, "A-MQU", new HashMap() {{put("A", 8);}}, new HashMap() {{put("M", "true"); put("Q", "false"); put("U", "true"); put("S", "8");}});
         LuceneResponse resultAgain = multiLucene.executeComposedQuery(q);
         assertEquals(2, resultAgain.total);
    }

    @Test
    public void testUniteResultFromTwoIndexes_filterQueries() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.addFilterQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(3, result.total);
        LuceneTest.compareHits(result, "A-QU", "A-MQ", "A-MQU");
    }

    @Test
    public void testUniteAndFacets() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.addFacet("coreA", new QueryConverter.FacetRequest("cat_Q", 10));
        q.addFacet("coreA", new QueryConverter.FacetRequest("cat_U", 10));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_N", 10));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        q.addOtherCoreFacetFilter("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(3, result.total);
        LuceneTest.compareHits(result, "A-QU", "A-MQ", "A-MQU");

        assertEquals(4, result.drilldownData.size());
        DrilldownData catQ = result.drilldownData.get(0);
        assertEquals("cat_Q", catQ.fieldname);
        assertEquals(1, catQ.terms.size());
        assertEquals("true", catQ.terms.get(0).label);
        assertEquals(3, catQ.terms.get(0).count);
        DrilldownData catU = result.drilldownData.get(1);
        assertEquals("cat_U", catU.fieldname);
        assertEquals(2, catU.terms.size());
        assertEquals("true", catU.terms.get(0).label);
        assertEquals(2, catU.terms.get(0).count);
        assertEquals("false", catU.terms.get(1).label);
        assertEquals(1, catU.terms.get(1).count);
        DrilldownData catN = result.drilldownData.get(2);
        assertEquals("cat_N", catN.fieldname);
        assertEquals(1, catN.terms.size());
        assertEquals("true", catN.terms.get(0).label);
        assertEquals(2, catN.terms.get(0).count);
        DrilldownData catO = result.drilldownData.get(3);
        assertEquals("cat_O", catO.fieldname);
        assertEquals(2, catO.terms.size());
        assertEquals("true", catO.terms.get(0).label);
        assertEquals(1, catO.terms.get(0).count);
        assertEquals("false", catO.terms.get(1).label);
        assertEquals(1, catO.terms.get(1).count);
    }

    @Test
    public void testUniteAndFacetsWithForeignQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreB", new TermQuery(new Term("O", "true")));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_N", 10));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.total);
        LuceneTest.compareHits(result, "A-M", "A-MQ");

        assertEquals(2, result.drilldownData.size());
        DrilldownData catN = result.drilldownData.get(0);
        assertEquals("cat_N", catN.fieldname);
        assertEquals(2, catN.terms.size());
        assertEquals("true", catN.terms.get(0).label);
        assertEquals(2, catN.terms.get(0).count);
        assertEquals("false", catN.terms.get(1).label);
        assertEquals(1, catN.terms.get(1).count);
        DrilldownData catO = result.drilldownData.get(1);
        assertEquals("cat_O", catO.fieldname);
        assertEquals(1, catO.terms.size());
        assertEquals("true", catO.terms.get(0).label);
        assertEquals(3, catO.terms.get(0).count);
    }

    @Test
    public void testUniteAndFacetsWithForeignQueryWithSpecialFacetsQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreB", new TermQuery(new Term("O", "true")));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_N", 10));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_O", 10));
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        q.addOtherCoreFacetFilter("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.total);
        LuceneTest.compareHits(result, "A-M", "A-MQ");

        assertEquals(2, result.drilldownData.size());
        DrilldownData catN = result.drilldownData.get(0);
        assertEquals("cat_N", catN.fieldname);
        assertEquals(1, catN.terms.size());
        assertEquals("true", catN.terms.get(0).label);
        assertEquals(2, catN.terms.get(0).count);
        DrilldownData catO = result.drilldownData.get(1);
        assertEquals("cat_O", catO.fieldname);
        assertEquals(1, catO.terms.size());
        assertEquals("true", catO.terms.get(0).label);
        assertEquals(2, catO.terms.get(0).count);
    }

    @Test
    public void testUniteMakesItTwoCoreQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(3, result.total);
        LuceneTest.compareHits(result, "A-QU", "A-MQ", "A-MQU");
    }

    @Test
    public void testStartStopSortKeys() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.setCoreQuery("coreB", null);
        q.addMatch("coreA", "coreB", "A", "B");
        q.addUnite("coreA", new TermQuery(new Term("U", "true")), "coreB", new TermQuery(new Term("N", "true")));
        q.queryData.sort = new Sort(new SortField("S", SortField.Type.STRING, false));
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(3, result.total);
        LuceneTest.compareHits(result, "A-QU", "A-MQ", "A-MQU");

        q.queryData.sort = new Sort(new SortField("S", SortField.Type.STRING, true));
        q.queryData.stop = 2;
        result = multiLucene.executeComposedQuery(q);
        assertEquals(3, result.total);
        LuceneTest.compareHits(result, "A-MQ", "A-MQU");

        q.queryData.start = 1;
        q.queryData.stop = 10;
        result = multiLucene.executeComposedQuery(q);
        assertEquals(3, result.total);
        LuceneTest.compareHits(result, "A-MQ", "A-QU");
    }

    @SuppressWarnings({ "rawtypes", "serial", "unchecked" })
    @Test
    public void testCachingCollectorsAfterUpdate() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        LuceneTest.addDocument(luceneB, "B-N>A-MQU", new HashMap() {{put("B", 8);}}, new HashMap() {{put("N", "true"); put("O", "false"); put("P", "false");}});
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");
        result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");
        //assertTrue(result.queryTime < 5);
        LuceneTest.addDocument(luceneB, "B-N>A-MQU", new HashMap() {{put("B", 80);}}, new HashMap() {{put("N", "true"); put("O", "false"); put("P", "false");}});
        result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ");
    }

    @SuppressWarnings({ "rawtypes", "serial", "unchecked" })
    @Test
    public void testCachingCollectorsAfterUpdateInSegmentWithMultipleDocuments() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        LuceneTest.addDocument(luceneB, "B-N>A-MQU", new HashMap() {{put("B", 8);}}, new HashMap() {{put("N", "true"); put("O", "false"); put("P", "false");}});
        result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");
        result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");
        assertTrue(result.queryTime < 5);
        LuceneTest.addDocument(luceneB, "B-N>A-MU", new HashMap() {{put("B", 60);}}, new HashMap() {{put("N", "true"); put("O", "false"); put("P", "false");}});
        result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MQ", "A-MQU");
        LuceneTest.compareHits(result, "A-M", "A-MQ", "A-MQU");
    }

    @SuppressWarnings({ "rawtypes", "serial", "unchecked" })
    @Test
    public void testCachingCollectorsAfterDelete() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        LuceneTest.addDocument(luceneB, "B-N>A-MQU", new HashMap() {{put("B", 8);}}, new HashMap() {{put("N", "true"); put("O", "false"); put("P", "false");}});
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MU", "A-MQ", "A-MQU");
        luceneB.deleteDocument("B-N>A-MU");
        result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-M", "A-MQ", "A-MQU");
    }

    @Test
    public void testJoinQueryOnOptionalKey() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "C", "B");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(1, result.total);
        LuceneTest.compareHits(result, "A-M");
    }

    @Test
    public void testJoinQueryOnOptionalKeyOtherSide() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "D");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(1, result.total);
        LuceneTest.compareHits(result, "A-M");
    }

    @Test
    public void testJoinQueryThreeCores() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.setCoreQuery("coreC", new TermQuery(new Term("R", "true")));
        q.addFacet("coreA", new QueryConverter.FacetRequest("cat_M", 10));
        q.addFacet("coreB", new QueryConverter.FacetRequest("cat_N", 10));
        q.addFacet("coreC", new QueryConverter.FacetRequest("cat_R", 10));
        q.addMatch("coreA", "coreB", "A", "B");
        q.addMatch("coreA", "coreC", "A", "C");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(1, result.total);
        LuceneTest.compareHits(result, "A-M");
        Collections.sort(result.drilldownData, new Comparator<DrilldownData>(){
			@Override
			public int compare(DrilldownData o1, DrilldownData o2) {
				return o1.fieldname.compareTo(o2.fieldname);
			}
        });
        assertEquals(3, result.drilldownData.size());
        assertEquals("cat_M", result.drilldownData.get(0).fieldname);
        assertEquals(1, result.drilldownData.get(0).terms.size());
        assertEquals("true", result.drilldownData.get(0).terms.get(0).label);
        assertEquals(1, result.drilldownData.get(0).terms.get(0).count);
        assertEquals("cat_N", result.drilldownData.get(1).fieldname);
        assertEquals(1, result.drilldownData.get(1).terms.size());
        assertEquals("true", result.drilldownData.get(1).terms.get(0).label);
        assertEquals(1, result.drilldownData.get(1).terms.get(0).count);
        assertEquals("cat_R", result.drilldownData.get(2).fieldname);
        assertEquals(1, result.drilldownData.get(2).terms.size());
        assertEquals("true", result.drilldownData.get(2).terms.get(0).label);
        assertEquals(1, result.drilldownData.get(2).terms.get(0).count);
    }

    @Test
    public void testRankQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.setRankQuery("coreC", new TermQuery(new Term("S", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        q.addMatch("coreA", "coreC", "A", "C");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHitsOrdered(result, "A-MQU", "A-M", "A-MU", "A-MQ");
    }

    @Test
    public void testMultipleRankQuery() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.setRankQuery("coreA", new TermQuery(new Term("Q", "true")));
        q.setRankQuery("coreC", new TermQuery(new Term("S", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        q.addMatch("coreA", "coreC", "A", "C");

        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHitsOrdered(result, "A-MQU", "A-MQ", "A-M", "A-MU");
    }

    @SuppressWarnings({ "rawtypes", "serial", "unchecked" })
    @Test
    public void testScoreCollectorCacheInvalidation() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setRankQuery("coreC", new TermQuery(new Term("S", "true")));
        q.addMatch("coreA", "coreC", "A", "C");
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(8, result.total);
        assertEquals("A-MQU", result.hits.get(0).id);
        LuceneTest.compareHits(result, "A", "A-U", "A-Q", "A-QU", "A-M", "A-MU", "A-MQ", "A-MQU");

        LuceneTest.addDocument(luceneC, "C-S>A-MQ", new HashMap() {{put("C", 7);}}, new HashMap() {{put("S", "true");}});
        try {
            result = multiLucene.executeComposedQuery(q);
            assertEquals(8, result.total);
            assertEquals("A-MQ", result.hits.get(0).id);
            assertEquals("A-MQU", result.hits.get(1).id);
            LuceneTest.compareHits(result, "A", "A-U", "A-Q", "A-QU", "A-M", "A-MU", "A-MQ", "A-MQU");
        } finally {
            this.luceneC.deleteDocument("C-S>A-MQ");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
    @Test
    public void testNullIteratorOfPForDeltaIsIgnoredInFinalKeySet() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "no_match")));
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "UNKOWN", "UNKOWN");
        multiLucene.executeComposedQuery(q);
        luceneB.maybeCommitAfterUpdate(); // Force to write new segment; Old segment remains in seen list
        LuceneTest.addDocument(luceneB, "new", null, new HashMap() {{put("ignored", "true");}}); // Add new document to force recreating finalKeySet
        try {
            LuceneResponse result = multiLucene.executeComposedQuery(q);
            assertEquals(0, result.hits.size());
        } finally {
            luceneB.deleteDocument("new");
        }
    }

     @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
     @Test
     public void testKeyFilterIgnoresKeysOutOfBoundsOfKeySet() throws Throwable {
         LuceneTest.addDocument(luceneB, "100", new HashMap() {{put("B", 100);}}, null); // Force key to be much more than bits in long[] in FixedBitSet, so it must be OutOfBounds
         ComposedQuery q = new ComposedQuery("coreA");
         q.setCoreQuery("coreA", new MatchAllDocsQuery());
         q.setCoreQuery("coreB", new MatchAllDocsQuery());
         q.addMatch("coreA", "coreB", "A", "B");
         LuceneResponse result = multiLucene.executeComposedQuery(q);
         assertEquals(4, result.hits.size());
     }

     @Test
     public void testCollectScoresWithNoResultAndBooleanQueryDoesntFailOnFakeScorerInAggregateScoreCollector() throws Throwable {
         BooleanQuery.Builder q = new BooleanQuery.Builder();
         q.add(new TermQuery(new Term("M", "true")), Occur.SHOULD);
         q.add(new TermQuery(new Term("M", "true")), Occur.SHOULD);
         ComposedQuery cq = new ComposedQuery("coreA", q.build());
         cq.queryData.start = 0;
         cq.queryData.stop = 0;
         cq.setRankQuery("coreC", new TermQuery(new Term("S", "true")));
         cq.addMatch("coreA", "coreC", "A", "C");
         LuceneResponse result = multiLucene.executeComposedQuery(cq);
         assertEquals(4, result.total);
         assertEquals(0, result.hits.size());
     }

    @Test
    public void testCachingKeyCollectorsIntersectsWithACopyOfTheKeys() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("O", "true")));
        q.addFilterQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(2, result.hits.size());

        q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new MatchAllDocsQuery());
        q.addFilterQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.hits.size());
    }

    @Test
    public void testTwoCoreQueryWithThirdCoreDrilldownWithOtherCore() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new MatchAllDocsQuery());
        q.addFacet("coreC", new FacetRequest("cat_R", 10));
        q.addMatch("coreA", "coreB", "A", "B");
        q.addMatch("coreA", "coreC", "C", "C2");
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(4, result.total);
        LuceneTest.compareHits(result, "A-M", "A-MQ", "A-MU", "A-MQU");
        assertEquals(1, result.drilldownData.size());
        assertEquals("cat_R", result.drilldownData.get(0).fieldname);
        assertEquals(1, result.drilldownData.get(0).terms.size());
        assertEquals("true", result.drilldownData.get(0).terms.get(0).label);
        assertEquals(1, result.drilldownData.get(0).terms.get(0).count);
    }

    @Test
    public void testFilterQueryInTwoDifferentCores() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new MatchAllDocsQuery());
        q.addFilterQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addFilterQuery("coreC", new MatchAllDocsQuery());
        q.addMatch("coreA", "coreB", "A", "B");
        q.addMatch("coreA", "coreC", "C", "C2");
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        LuceneTest.compareHits(result, "A-MU");
        assertEquals(1, result.total);
    }

    @Test
    public void testScoreCollectorOnDifferentKeys() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setRankQuery("coreB", new TermQuery(new Term("N", "true")));
        q.setRankQuery("coreC", new TermQuery(new Term("R", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        q.addMatch("coreA", "coreC", "C", "C2");
        LuceneResponse result = multiLucene.executeComposedQuery(q);
        assertEquals(8, result.total);
        assertEquals("A-MU", result.hits.get(0).id);
        assertTrue(result.hits.get(0).score > result.hits.get(1).score);
    }
//    testJoinSort
//    testSortWithJoinField


    @Test
    public void testQueryConvertors() throws Throwable {
        luceneA.getSettings().facetsConfig.setIndexFieldName("dim1", "otherfield");
        Map<String, QueryConverter> converters = multiLucene.getQueryConverters();
        Term drilldownTerm = converters.get("coreA").createDrilldownTerm("dim1");
        assertEquals("otherfield", drilldownTerm.field());

        drilldownTerm = converters.get("coreB").createDrilldownTerm("dim1");
        assertEquals("$facets", drilldownTerm.field());
    }

    @Test
    public void testExportKeys() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new MatchAllDocsQuery());
        q.setCoreQuery("coreB", new TermQuery(new Term("N", "true")));
        q.addMatch("coreA", "coreB", "A", "B");
        LuceneResponse result = multiLucene.executeComposedQuery(q, "A");
        assertEquals(4, result.total);
        FixedBitSet expected = new FixedBitSet(result.keys.length());
        expected.set(5);
        expected.set(6);
        expected.set(7);
        expected.set(8);
        assertEquals(expected, result.keys);
    }

    @Test
    public void testExportKeysSingleCore() throws Throwable {
        ComposedQuery q = new ComposedQuery("coreA");
        q.setCoreQuery("coreA", new TermQuery(new Term("M", "true")));
        LuceneResponse result = multiLucene.executeComposedQuery(q, "A");
        assertEquals(4, result.total);
        FixedBitSet expected = new FixedBitSet(result.keys.length());
        expected.set(5);
        expected.set(6);
        expected.set(7);
        expected.set(8);
        assertEquals(expected, result.keys);
    }
}

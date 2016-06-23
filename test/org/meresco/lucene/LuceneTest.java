/* begin license *
 *
 * "Meresco Lucene" is a set of components and tools to integrate Lucene (based on PyLucene) into Meresco
 *
 * Copyright (C) 2015 Koninklijke Bibliotheek (KB) http://www.kb.nl
 * Copyright (C) 2015-2016 Seecr (Seek You Too B.V.) http://seecr.nl
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.FacetField;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.OpenBitSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.meresco.lucene.Lucene.TermCount;
import org.meresco.lucene.LuceneResponse.ClusterHit;
import org.meresco.lucene.LuceneResponse.DedupHit;
import org.meresco.lucene.LuceneResponse.GroupingHit;
import org.meresco.lucene.LuceneResponse.Hit;
import org.meresco.lucene.QueryConverter.FacetRequest;
import org.meresco.lucene.search.MerescoCluster.DocScore;
import org.meresco.lucene.search.MerescoCluster.TermScore;
import org.meresco.lucene.search.join.AggregateScoreSuperCollector;
import org.meresco.lucene.search.join.KeySuperCollector;
import org.meresco.lucene.search.join.ScoreSuperCollector;


public class LuceneTest extends SeecrTestCase {
    private Lucene lucene;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        LuceneSettings settings = new LuceneSettings();
        settings.commitCount = 1;
        lucene = new Lucene(this.tmpDir, settings);
    }

    @After
    public void tearDown() throws Exception {
        lucene.close();
        super.tearDown();
    }

    @Test
    public void testAddDocument() throws Throwable {
        Document doc = new Document();
        doc.add(new StringField("naam", "waarde", Store.NO));
        lucene.addDocument("id1", doc);
        LuceneResponse response = lucene.executeQuery(new MatchAllDocsQuery());
        assertEquals(1, response.total);
        assertEquals(1, response.hits.size());
        assertEquals("id1", response.hits.get(0).id);
    }

    @Test
    public void testAddDocumentWithoutIdentifier() throws Throwable {
        Document doc = new Document();
        doc.add(new StringField("naam", "waarde", Store.NO));
        lucene.addDocument(doc);
        LuceneResponse response = lucene.executeQuery(new MatchAllDocsQuery());
        assertEquals(1, response.total);
        assertEquals(1, response.hits.size());
        assertEquals(null, response.hits.get(0).id);
    }

    @Test
    public void testAddDeleteDocument() throws Throwable {
        lucene.addDocument("id1", new Document());
        lucene.addDocument("id2", new Document());
        lucene.addDocument("id3", new Document());
        lucene.deleteDocument("id1");
        LuceneResponse response = lucene.executeQuery(new MatchAllDocsQuery());
        assertEquals(2, response.total);
        assertEquals(2, response.hits.size());
    }

    @Test
    public void testAddTwiceUpdatesDocument() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field0", "value0", Store.NO));
        doc1.add(new StringField("field1", "value1", Store.NO));
        Document doc2 = new Document();
        doc2.add(new StringField("field0", "value0", Store.NO));
        lucene.addDocument("id:0", doc1);
        lucene.addDocument("id:0", doc2);
        LuceneResponse result = lucene.executeQuery(new TermQuery(new Term("field0", "value0")));
        assertEquals(1, result.total);
        result = lucene.executeQuery(new TermQuery(new Term("field1", "value1")));
        assertEquals(0, result.total);
    }

    @Test
    public void testTwoQueries() throws Throwable {
        LuceneResponse result = lucene.executeQuery(new MatchAllDocsQuery());
        assertEquals(0, result.total);

        result = lucene.executeQuery(new MatchAllDocsQuery());
        assertEquals(0, result.total);
    }

    @Test
    public void testFacets() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "id0", Store.NO));
        doc1.add(new FacetField("facet-field2", "first item0"));
        doc1.add(new FacetField("facet-field3", "second item"));

        Document doc2 = new Document();
        doc2.add(new StringField("field1", "id1", Store.NO));
        doc2.add(new FacetField("facet-field2", "first item1"));
        doc2.add(new FacetField("facet-field3", "other value"));

        Document doc3 = new Document();
        doc3.add(new StringField("field1", "id2", Store.NO));
        doc3.add(new FacetField("facet-field2", "first item2"));
        doc3.add(new FacetField("facet-field3", "second item"));

        lucene.addDocument("id0", doc1);
        lucene.addDocument("id1", doc2);
        lucene.addDocument("id2", doc3);

        LuceneResponse result = lucene.executeQuery(new MatchAllDocsQuery());
        assertEquals(3, result.total);
        assertEquals(0, result.drilldownData.size());

        ArrayList<FacetRequest> facets = new ArrayList<FacetRequest>();
        facets.add(new FacetRequest("facet-field2", 10));
        result = lucene.executeQuery(new MatchAllDocsQuery(), facets);
        assertEquals(3, result.total);
        assertEquals(1, result.drilldownData.size());
        assertEquals("facet-field2", result.drilldownData.get(0).fieldname);
        assertEquals(3, result.drilldownData.get(0).terms.size());
        assertEquals("first item0", result.drilldownData.get(0).terms.get(0).label);
        assertEquals("first item1", result.drilldownData.get(0).terms.get(1).label);
        assertEquals("first item2", result.drilldownData.get(0).terms.get(2).label);
        assertEquals(1, result.drilldownData.get(0).terms.get(0).count);
        assertEquals(1, result.drilldownData.get(0).terms.get(1).count);
        assertEquals(1, result.drilldownData.get(0).terms.get(2).count);

        facets = new ArrayList<FacetRequest>();
        facets.add(new FacetRequest("facet-field3", 10));
        result = lucene.executeQuery(new MatchAllDocsQuery(), facets);
        assertEquals(3, result.total);
        assertEquals(1, result.drilldownData.size());
        assertEquals("facet-field3", result.drilldownData.get(0).fieldname);
        assertEquals(2, result.drilldownData.get(0).terms.size());
        assertEquals("second item", result.drilldownData.get(0).terms.get(0).label);
        assertEquals("other value", result.drilldownData.get(0).terms.get(1).label);
        assertEquals(2, result.drilldownData.get(0).terms.get(0).count);
        assertEquals(1, result.drilldownData.get(0).terms.get(1).count);

        facets.get(0).maxTerms = 0;
        result = lucene.executeQuery(new MatchAllDocsQuery(), facets);
        assertEquals(3, result.total);
        assertEquals(1, result.drilldownData.size());
        assertEquals(2, result.drilldownData.get(0).terms.size());

        assertEquals(result.drilldownData, lucene.facets(facets, null, null, null));
    }

    @Test
    public void testHierarchicalFacets() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "id0", Store.NO));
        doc1.add(new FacetField("facet-field", "first", "second"));
        doc1.add(new FacetField("facet-field", "subfirst", "subsecond"));
        this.lucene.getSettings().facetsConfig.setHierarchical("facet-field", true);
        this.lucene.getSettings().facetsConfig.setMultiValued("facet-field", true);
        this.lucene.addDocument("id1", doc1);

        ArrayList<FacetRequest> facets = new ArrayList<FacetRequest>();
        FacetRequest facet = new FacetRequest("facet-field", 10);
        facet.path = new String[] {"first"};
        facets.add(facet);

        LuceneResponse result = lucene.executeQuery(new MatchAllDocsQuery(), facets);
        assertEquals(1, result.total);
        assertEquals(1, result.drilldownData.size());
        assertEquals("facet-field", result.drilldownData.get(0).fieldname);
        assertEquals(1, result.drilldownData.get(0).terms.size());
        assertEquals("second", result.drilldownData.get(0).terms.get(0).label);
    }

    @Test
    public void testFacetsInDifferentIndexFieldName() throws Throwable {
        FacetsConfig facetsConfig = lucene.getSettings().facetsConfig;
        facetsConfig.setIndexFieldName("field0", "$facets_1");
        facetsConfig.setIndexFieldName("field2", "$facets_1");

        Document doc1 = new Document();
        doc1.add(new FacetField("field0", "value0"));
        doc1.add(new FacetField("field1", "value1"));
        doc1.add(new FacetField("field2", "value2"));
        lucene.addDocument("id0", doc1);

        ArrayList<FacetRequest> facets = new ArrayList<FacetRequest>();
        facets.add(new FacetRequest("field0", 10));
        facets.add(new FacetRequest("field1", 10));
        facets.add(new FacetRequest("field2", 10));
        LuceneResponse result = lucene.executeQuery(new MatchAllDocsQuery(), facets);
        assertEquals(3, result.drilldownData.size());
        assertEquals("field0", result.drilldownData.get(0).fieldname);
        assertEquals(1, result.drilldownData.get(0).terms.size());
        assertEquals("field1", result.drilldownData.get(1).fieldname);
        assertEquals(1, result.drilldownData.get(1).terms.size());
        assertEquals("field2", result.drilldownData.get(2).fieldname);
        assertEquals(1, result.drilldownData.get(2).terms.size());
    }

    @SuppressWarnings("serial")
    @Test
    public void testFacetIndexFieldNames() throws Throwable {
        FacetsConfig facetsConfig = lucene.getSettings().facetsConfig;
        facetsConfig.setIndexFieldName("field0", "$facets_1");
        facetsConfig.setIndexFieldName("field2", "$facets_1");

        ArrayList<FacetRequest> facets = new ArrayList<FacetRequest>();
        facets.add(new FacetRequest("field0", 10));
        facets.add(new FacetRequest("field1", 10));
        facets.add(new FacetRequest("field2", 10));

        assertArrayEquals(new String[] {"$facets", "$facets_1"}, lucene.getIndexFieldNames(facets));
        assertArrayEquals(new String[] {"$facets_1"}, lucene.getIndexFieldNames(new ArrayList<FacetRequest>() {{ add(new FacetRequest("field0", 10)); }}));
        assertArrayEquals(new String[] {"$facets"}, lucene.getIndexFieldNames(new ArrayList<FacetRequest>() {{ add(new FacetRequest("field1", 10)); }}));
        assertArrayEquals(new String[] {"$facets_1"}, lucene.getIndexFieldNames(new ArrayList<FacetRequest>() {{ add(new FacetRequest("field0", 10)); add(new FacetRequest("field2", 10)); }}));
    }

    @Test
    public void testSorting() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "AA", Store.NO));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new StringField("field1", "BB", Store.NO));
        lucene.addDocument("id2", doc2);

        Document doc3 = new Document();
        doc3.add(new StringField("field1", "CC", Store.NO));
        lucene.addDocument("id3", doc3);

        Sort sort = new Sort();
        sort.setSort(new SortField("field1", SortField.Type.STRING, false));
        QueryData q = new QueryData();
        q.sort = sort;
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(3, result.total);
        assertEquals("id1", result.hits.get(0).id);
        assertEquals("id2", result.hits.get(1).id);
        assertEquals("id3", result.hits.get(2).id);

        sort = new Sort();
        sort.setSort(new SortField("field1", SortField.Type.STRING, true));
        q.sort = sort;
        result = lucene.executeQuery(q);
        assertEquals(3, result.total);
        assertEquals("id3", result.hits.get(0).id);
        assertEquals("id2", result.hits.get(1).id);
        assertEquals("id1", result.hits.get(2).id);
    }

    @Test
    public void testCommitTimer() throws Throwable {
        lucene.close();
        LuceneSettings settings = new LuceneSettings();
        settings.commitTimeout = 1;
        lucene = new Lucene(this.tmpDir, settings);
        lucene.addDocument("id1", new Document());
        Thread.sleep(500);
        assertEquals(0, lucene.executeQuery(new MatchAllDocsQuery()).total);
        Thread.sleep(550);
        assertEquals(1, lucene.executeQuery(new MatchAllDocsQuery()).total);
    }

    @Test
    public void testCommitTimerAndCount() throws Throwable {
        lucene.close();
        LuceneSettings settings = new LuceneSettings();
        settings.commitTimeout = 1;
        settings.commitCount = 2;
        lucene = new Lucene(this.tmpDir, settings);
        lucene.addDocument("id1", new Document());
        lucene.addDocument("id2", new Document());
        assertEquals(2, lucene.executeQuery(new MatchAllDocsQuery()).total);
        lucene.addDocument("id3", new Document());
        Thread.sleep(1050);
        assertEquals(3, lucene.executeQuery(new MatchAllDocsQuery()).total);
    }

    @Test
    public void testStartStop() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "AA", Store.NO));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new StringField("field1", "BB", Store.NO));
        lucene.addDocument("id2", doc2);

        Document doc3 = new Document();
        doc3.add(new StringField("field1", "CC", Store.NO));
        lucene.addDocument("id3", doc3);

        LuceneResponse result = lucene.executeQuery(new MatchAllDocsQuery());
        assertEquals(3, result.total);
        assertEquals(3, result.hits.size());
        result = lucene.executeQuery(new MatchAllDocsQuery(), 1, 10);
        assertEquals(3, result.total);
        assertEquals(2, result.hits.size());
        result = lucene.executeQuery(new MatchAllDocsQuery(), 0, 2);
        assertEquals(3, result.total);
        assertEquals(2, result.hits.size());
        result = lucene.executeQuery(new MatchAllDocsQuery(), 2, 2);
        assertEquals(3, result.total);
        assertEquals(0, result.hits.size());
        result = lucene.executeQuery(new MatchAllDocsQuery(), 1, 2);
        assertEquals(3, result.total);
        assertEquals(1, result.hits.size());
        result = lucene.executeQuery(new MatchAllDocsQuery(), 0, 0);
        assertEquals(3, result.total);
        assertEquals(0, result.hits.size());
    }

    @SuppressWarnings("serial")
    @Test
    public void testQueryWithFilter() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "value0", Store.NO));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new StringField("field1", "value1", Store.NO));
        lucene.addDocument("id2", doc2);

        assertEquals(2, lucene.executeQuery(new MatchAllDocsQuery(), 0, 0).total);
        final Filter f = new QueryWrapperFilter(new TermQuery(new Term("field1", "value1")));
        assertEquals(1, lucene.executeQuery(new QueryData(), null, null, new ArrayList<Filter>() {{ add(f); }}, null, null).total);
    }

    @SuppressWarnings("serial")
    @Test
    public void testQueryWithFilterQuery() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "value0", Store.NO));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new StringField("field1", "value1", Store.NO));
        lucene.addDocument("id2", doc2);

        assertEquals(2, lucene.executeQuery(new MatchAllDocsQuery(), 0, 0).total);
        final Query f = new TermQuery(new Term("field1", "value1"));
        assertEquals(1, lucene.executeQuery(new QueryData(), new ArrayList<Query>() {{ add(f); }}, null, null, null, null).total);
    }

    @SuppressWarnings("serial")
    @Test
    public void testQueryWithKeyCollectors() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field0", "value", Store.NO));
        doc1.add(new NumericDocValuesField("field1", 1));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new NumericDocValuesField("field1", 2));
        lucene.addDocument("id2", doc2);

        final KeySuperCollector k = new KeySuperCollector("field1");
        assertEquals(2, lucene.executeQuery(new QueryData(), null, null, null, null, new ArrayList<KeySuperCollector>() {{ add(k); }}).total);

        OpenBitSet collectedKeys = k.getCollectedKeys();
        assertEquals(false, collectedKeys.get(0));
        assertEquals(true, collectedKeys.get(1));
        assertEquals(true, collectedKeys.get(2));
        assertEquals(false, collectedKeys.get(3));
        assertEquals(collectedKeys, lucene.collectKeys(null, "field1", new MatchAllDocsQuery(), false));

        final KeySuperCollector k1 = new KeySuperCollector("field1");
        TermQuery field0Query = new TermQuery(new Term("field0", "value"));
        QueryData q = new QueryData();
        q.query = field0Query;
        assertEquals(1, lucene.executeQuery(q, null, null, null, null, new ArrayList<KeySuperCollector>() {{ add(k1); }}).total);

        OpenBitSet keysWithFilter = k1.getCollectedKeys();
        assertEquals(false, keysWithFilter.get(0));
        assertEquals(true, keysWithFilter.get(1));
        assertEquals(false, keysWithFilter.get(2));
        assertEquals(keysWithFilter, lucene.collectKeys(field0Query, "field1", new MatchAllDocsQuery()));

    }

    @SuppressWarnings("serial")
    @Test
    public void testQueryWithScoreCollectors() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field0", "value", Store.NO));
        doc1.add(new NumericDocValuesField("field1", 1));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new NumericDocValuesField("field1", 2));
        lucene.addDocument("id2", doc2);

        final ScoreSuperCollector scoreCollector = this.lucene.scoreCollector("field1", new MatchAllDocsQuery());
        assertEquals(1.0, scoreCollector.score(1), 0);
        assertEquals(1.0, scoreCollector.score(2), 0);

        final AggregateScoreSuperCollector aggregator = new AggregateScoreSuperCollector("field1", new ArrayList<ScoreSuperCollector>() {{add(scoreCollector);}});
        ArrayList<AggregateScoreSuperCollector> aggregators = new ArrayList<AggregateScoreSuperCollector>() {{add(aggregator);}};
        LuceneResponse result = this.lucene.executeQuery(new QueryData(), null, null, null, aggregators, null);
        assertEquals(2, result.total);
        assertEquals(2, result.hits.get(0).score, 0);
        assertEquals(2, result.hits.get(1).score, 0);
    }

    @Test
    public void testPrefixSearch() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "value0", Store.NO));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new StringField("field1", "value1", Store.NO));
        lucene.addDocument("id2", doc2);

        Document doc3 = new Document();
        doc3.add(new StringField("field1", "value2", Store.NO));
        lucene.addDocument("id3", doc3);

        List<TermCount> terms = lucene.termsForField("field1", "val", 10);
        assertEquals(3, terms.size());
        assertEquals("value0", terms.get(0).term);
        assertEquals(1, terms.get(0).count);
        assertEquals("value1", terms.get(1).term);
        assertEquals(1, terms.get(0).count);
        assertEquals("value2", terms.get(2).term);
        assertEquals(1, terms.get(0).count);
    }

    @Test
    public void testNumDocs() throws Throwable {
        assertEquals(0, lucene.maxDoc());
        assertEquals(0, lucene.numDocs());
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "value0", Store.NO));
        lucene.addDocument("id1", doc1);
        assertEquals(1, lucene.maxDoc());
        assertEquals(1, lucene.numDocs());
    }

    @SuppressWarnings("serial")
    @Test
    public void testFieldname() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new StringField("field1", "value0", Store.NO));
        lucene.addDocument("id1", doc1);

        List<String> fieldnames = lucene.fieldnames();
        assertEquals(2, fieldnames.size());
        assertEquals(new ArrayList<String>() {{ add("__id__"); add("field1"); }}, fieldnames);
    }

    @SuppressWarnings("serial")
    @Test
    public void testDrilldownFieldname() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new FacetField("cat", "cat 1"));
        lucene.addDocument("id1", doc1);

        Document doc2 = new Document();
        doc2.add(new FacetField("cat", "cat 2"));
        lucene.addDocument("id2", doc2);

        List<String> fieldnames = lucene.drilldownFieldnames(50, null);
        assertEquals(1, fieldnames.size());
        assertEquals(new ArrayList<String>() {{ add("cat"); }}, fieldnames);

        fieldnames = lucene.drilldownFieldnames(50, "cat");
        assertEquals(2, fieldnames.size());
        assertEquals(new ArrayList<String>() {{ add("cat 2"); add("cat 1");}}, fieldnames);
    }

    @SuppressWarnings({ "serial", "rawtypes", "unchecked" })
    @Test
    public void testSuggestions() throws Throwable {
        addDocument(lucene, "id:0", null, new HashMap() {{put("field1", "value0"); put("field2", "value2" ); put("field5", "value2" );}});

        assertEquals("value2", lucene.suggest("value0", 2, "field5")[0].string);
        assertEquals("value2", lucene.suggest("valeu", 2, "field5")[0].string);

        QueryConverter.SuggestionRequest sr = new QueryConverter.SuggestionRequest("field5", 2);
        sr.add("value0");
        sr.add("valeu");
        QueryData q = new QueryData();
        q.suggestionRequest = sr;
        LuceneResponse response = lucene.executeQuery(q);
        compareHits(response, "id:0");
        assertEquals("value2", response.suggestions.get("value0")[0].string);
        assertEquals("value2", response.suggestions.get("valeu")[0].string);
    }

    @SuppressWarnings({ "serial", "unchecked", "rawtypes" })
    @Test
    public void testDedupFilterCollectorSortedByField() throws Throwable {
        addDocument(lucene, "urn:1", new HashMap() {{put("__key__", 42); put("__key__.date", 2012);}}, new HashMap() {{put("field0", "v0"); put("A", "cat-A");}});
        addDocument(lucene, "urn:2", new HashMap() {{put("__key__", 42); put("__key__.date", 2013);}}, new HashMap() {{put("field0", "v1"); put("A", "cat-A");}}); //first hit of 3 duplicates
        addDocument(lucene, "urn:3", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v2"); put("A", "cat-A");}});
        addDocument(lucene, "urn:4", null, new HashMap() {{put("field0", "v3"); put("A", "cat-A");}});
        QueryData q = new QueryData();
        q.query = new MatchAllDocsQuery();
        q.facets = new ArrayList<FacetRequest>() {{add(new FacetRequest("cat_A", 10));}};
        q.dedupField = "__key__";
        q.dedupSortField = "__key__.date";
        LuceneResponse result = lucene.executeQuery(q);
        // expected two hits: "urn:2" (3x) and "urn:4" in no particular order
        assertEquals(2, result.total);
        assertEquals(4, (int) result.totalWithDuplicates);
        compareHits(result, "urn:2", "urn:4");
        Collections.sort(result.hits);
        DedupHit hit0 = (DedupHit) result.hits.get(0);
        DedupHit hit1 = (DedupHit) result.hits.get(1);
        assertEquals("__key__", hit0.duplicateField);
        assertEquals(3, hit0.duplicateCount);
        assertEquals(1, hit1.duplicateCount);
        assertEquals("cat-A", result.drilldownData.get(0).terms.get(0).label);
        assertEquals(4, result.drilldownData.get(0).terms.get(0).count);
    }

    @SuppressWarnings({ "serial", "unchecked", "rawtypes" })
    @Test
    public void testGroupingCollector() throws Throwable {
        addDocument(lucene, "urn:1", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v0");}});
        addDocument(lucene, "urn:2", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v1");}});
        addDocument(lucene, "urn:3", new HashMap() {{put("__key__", 43);}}, new HashMap() {{put("field0", "v2");}});
        addDocument(lucene, "urn:4", null, new HashMap() {{put("field0", "v3");}});

        QueryData q = new QueryData();
        q.stop = 3;
        q.groupingField = "__key__";
        q.sort = new Sort(new SortField("field0", SortField.Type.STRING));
        LuceneResponse result = lucene.executeQuery(q);
        // expected two hits: "urn:2" (3x) and "urn:4" in no particular order
        assertEquals(4, result.total);
        compareHits(result, "urn:1", "urn:3", "urn:4");
        GroupingHit hit0 = (GroupingHit) result.hits.get(0);
        GroupingHit hit1 = (GroupingHit) result.hits.get(1);
        GroupingHit hit2 = (GroupingHit) result.hits.get(2);
        assertEquals("__key__", hit0.groupingField);
        assertEquals(new ArrayList<String>() {{ add("urn:1"); add("urn:2"); }}, hit0.duplicates);
        assertEquals(new ArrayList<String>() {{ add("urn:3"); }}, hit1.duplicates);
        assertEquals(new ArrayList<String>() {{ add("urn:4"); }}, hit2.duplicates);
    }

    @SuppressWarnings({ "serial", "unchecked", "rawtypes" })
    @Test
    public void testGroupingOnNonExistingField() throws Throwable {
        addDocument(lucene, "urn:1", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v0");}});
        addDocument(lucene, "urn:2", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v1");}});
        addDocument(lucene, "urn:3", new HashMap() {{put("__key__", 43);}}, new HashMap() {{put("field0", "v2");}});
        addDocument(lucene, "urn:4", null, new HashMap() {{put("field0", "v3");}});

        QueryData q = new QueryData();
        q.groupingField = "__other_key__";
        q.stop = 3;
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(4, result.total);
        assertEquals(3, result.hits.size());
    }

    @SuppressWarnings({ "serial", "rawtypes", "unchecked" })
    @Test
    public void testDontGroupIfMaxResultsAreLessThanTotalRecords() throws Throwable {
        addDocument(lucene, "urn:1", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v0");}});
        addDocument(lucene, "urn:2", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v1");}});

        QueryData q = new QueryData();
        q.groupingField = "__key__";
        LuceneResponse result = lucene.executeQuery(q);

        assertEquals(2, result.total);
        compareHits(result, "urn:1", "urn:2");

        Collections.sort(result.hits);
        GroupingHit hit0 = (GroupingHit) result.hits.get(0);
        GroupingHit hit1 = (GroupingHit) result.hits.get(1);
        assertEquals(new ArrayList<String>() {{ add("urn:1"); }}, hit0.duplicates);
        assertEquals(new ArrayList<String>() {{ add("urn:2"); }}, hit1.duplicates);
    }

    @SuppressWarnings({ "serial", "rawtypes", "unchecked" })
    @Test
    public void testGroupingCollectorReturnsMaxHitAfterGrouping() throws Throwable {
        addDocument(lucene, "urn:1", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v0");}});
        addDocument(lucene, "urn:2", new HashMap() {{put("__key__", 42);}}, new HashMap() {{put("field0", "v1");}});
        for (int i=3; i<11; i++)
            addDocument(lucene, "urn:" + i, null, new HashMap() {{put("field0", "v0");}});

        QueryData q = new QueryData();
        q.groupingField = "__key__";
        q.stop = 5;
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(10, result.total);
        assertEquals(5, result.hits.size());
    }

    @Test
    public void testInterpolateEps() throws Throwable {
    	ClusterConfig clusterConfig = lucene.getSettings().clusterConfig;
        assertEquals(0, lucene.interpolateEpsilon(0, 10, clusterConfig), 0);
        assertEquals(0, lucene.interpolateEpsilon(10, 10, clusterConfig), 0);
        assertEquals(0.004, lucene.interpolateEpsilon(11, 10, clusterConfig), 0);
        assertEquals(0.4, lucene.interpolateEpsilon(110, 10, clusterConfig), 0);
        assertEquals(0.4, lucene.interpolateEpsilon(111, 10, clusterConfig), 0);

        assertEquals(0, lucene.interpolateEpsilon(0, 20, clusterConfig), 0);
        assertEquals(0, lucene.interpolateEpsilon(20, 20, clusterConfig), 0);
        assertEquals(0.004, lucene.interpolateEpsilon(21, 20, clusterConfig), 0);
        assertEquals(0.32, lucene.interpolateEpsilon(100, 20, clusterConfig), 0);
        assertEquals(0.4, lucene.interpolateEpsilon(120, 20, clusterConfig), 0);
        assertEquals(0.4, lucene.interpolateEpsilon(121, 20, clusterConfig), 0);
    }

    @SuppressWarnings("serial")
    @Test
    public void testClusteringOnVectors() throws Throwable {
        LuceneSettings settings = lucene.getSettings();
        lucene.close();
        lucene = new Lucene(this.tmpDir, settings) {
            @Override
            double interpolateEpsilon(int hits, int slice, ClusterConfig clusterConfig) {
                return 0.4;
            }
        };
        List<ClusterConfig.ClusterField> clusterFields = new ArrayList<ClusterConfig.ClusterField>();
        clusterFields.add(new ClusterConfig.ClusterField("termvector", 1.0, "vuur"));
        lucene.getSettings().clusterConfig.clusterFields = clusterFields;

        FieldType fieldType = new FieldType(TextField.TYPE_NOT_STORED);
        fieldType.setStoreTermVectors(true);

        for (int i=0; i<5; i++) {
            Document doc = new Document();
            doc.add(new Field("termvector", "aap noot vuur " + i, fieldType));
            lucene.addDocument("id:" + i, doc);
        }
        lucene.addDocument("id:6", new Document());

        QueryData q = new QueryData();
        q.clustering = true;
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(2, result.hits.size());
        ClusterHit hit0 = (ClusterHit) result.hits.get(0);
        ClusterHit hit1 = (ClusterHit) result.hits.get(1);
        assertEquals(0, hit0.topDocs.length);
        assertEquals(5, hit1.topDocs.length);
        List<String> ids = new ArrayList<>();
        for (DocScore scoreDoc : hit1.topDocs) {
            ids.add(scoreDoc.identifier);
        }
        Collections.sort(ids);
        assertEquals(new ArrayList<String>() {{add("id:0"); add("id:1"); add("id:2"); add("id:3"); add("id:4");}}, ids);
    }


    @Test
    public void testClusteringShowOnlyRequestTop() throws Throwable {
        FieldType fieldType = new FieldType(TextField.TYPE_NOT_STORED);
        fieldType.setStoreTermVectors(true);

        for (int i=0; i<5; i++) {
            Document doc = new Document();
            doc.add(new Field("termvector", "aap noot vuur " + i, fieldType));
            lucene.addDocument("id:" + i, doc);
        }
        for (int i=5; i<10; i++) {
            Document doc = new Document();
            doc.add(new Field("termvector", "something", fieldType));
            lucene.addDocument("id:" + i, doc);
        }
        for (int i=10; i<15; i++) {
            Document doc = new Document();
            doc.add(new Field("termvector", "totally other data with more text", fieldType));
            lucene.addDocument("id:" + i, doc);
        }
        QueryData q = new QueryData();
        q.clustering = true;
        q.start = 0;
        q.stop = 2;

        List<ClusterConfig.ClusterField> clusterFields = new ArrayList<ClusterConfig.ClusterField>();
        clusterFields.add(new ClusterConfig.ClusterField("termvector", 1.0, null));
        lucene.getSettings().clusterConfig.clusterFields = clusterFields;

        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(15, result.total);
        assertEquals(2, result.hits.size());
    }

    @SuppressWarnings("serial")
    @Test
    public void testClusteringRanksMostRelevantOfGroup() throws Throwable {
        LuceneSettings settings = lucene.getSettings();
        lucene.close();
        lucene = new Lucene(this.tmpDir, settings) {
            @Override
            double interpolateEpsilon(int hits, int slice, ClusterConfig clusterConfig) {
                return 10.0;
            }
        };

        FieldType fieldType = new FieldType(TextField.TYPE_NOT_STORED);
        fieldType.setStoreTermVectors(true);
        Document doc = new Document();
        doc.add(new Field("termvector", "aap", fieldType));
        doc.add(new Field("termvector", "noot", fieldType));
        doc.add(new Field("termvector", "mies", fieldType));
        doc.add(new Field("termvector", "vuur", fieldType));
        lucene.addDocument("id:1", doc);

        doc = new Document();
        doc.add(new Field("termvector", "aap", fieldType));
        doc.add(new Field("termvector", "noot", fieldType));
        doc.add(new Field("termvector", "mies", fieldType));
        lucene.addDocument("id:2", doc);

        doc = new Document();
        doc.add(new Field("termvector", "aap", fieldType));
        doc.add(new Field("termvector", "noot", fieldType));
        lucene.addDocument("id:3", doc);

        settings.clusterConfig.clusteringEps = 10.0;
        List<ClusterConfig.ClusterField> clusterFields = new ArrayList<ClusterConfig.ClusterField>();
        clusterFields.add(new ClusterConfig.ClusterField("termvector", 1.0, null));
        lucene.getSettings().clusterConfig.clusterFields = clusterFields;

        QueryData q = new QueryData();
        q.clustering = true;
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(3, result.total);
        assertEquals(1, result.hits.size());
        ClusterHit hit0 = (ClusterHit) result.hits.get(0);
        List<String> ids = new ArrayList<>();
        for (DocScore scoreDoc : hit0.topDocs) {
            ids.add(scoreDoc.identifier);
        }
        List<String> terms = new ArrayList<>();
        for (TermScore termScore : hit0.topTerms) {
            terms.add(termScore.term);
        }
        assertEquals(new ArrayList<String>() {{add("id:1"); add("id:2"); add("id:3");}}, ids);
        assertEquals(new ArrayList<String>() {{add("aap"); add("noot"); add("mies"); add("vuur");}}, terms);
    }

    @Test
    public void testClusteringWinsOverGroupingAndDedup() throws Throwable {
        FieldType fieldType = new FieldType(TextField.TYPE_NOT_STORED);
        fieldType.setStoreTermVectors(true);

        for (int i=0; i<15; i++) {
            Document doc = new Document();
            doc.add(new Field("termvector", "aap noot vuur", fieldType));
            lucene.addDocument("id:" + i, doc);
        }
        Document doc = new Document();
        doc.add(new Field("termvector", "something else", fieldType));
        lucene.addDocument("id:95", doc);
        doc = new Document();
        doc.add(new Field("termvector", "totally other data with more text", fieldType));
        lucene.addDocument("id:96", doc);
        doc = new Document();
        doc.add(new Field("termvector", "this is again a record", fieldType));
        lucene.addDocument("id:97", doc);
        doc = new Document();
        doc.add(new Field("termvector", "and this is also just something", fieldType));
        lucene.addDocument("id:98", doc);
        QueryData q = new QueryData();
        q.clustering = true;
        q.dedupField = "dedupField";
        q.start = 0;
        q.stop = 5;

        List<ClusterConfig.ClusterField> clusterFields = new ArrayList<ClusterConfig.ClusterField>();
        clusterFields.add(new ClusterConfig.ClusterField("termvector", 1.0, null));
        lucene.getSettings().clusterConfig.clusterFields = clusterFields;

        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(5, result.hits.size());
        assertTrue(result.hits.get(0) instanceof ClusterHit);
    }

    @Test
    public void testClusterOnMultipleFields() throws Throwable {
        FieldType fieldType = new FieldType(TextField.TYPE_NOT_STORED);
        fieldType.setStoreTermVectors(true);
        for (int i=0; i<15; i++) {
            Document doc = new Document();
            doc.add(new Field("termvector1", "aap noot vuur", fieldType));
            lucene.addDocument("id:" + i, doc);
        }

        Document doc = new Document();
        doc.add(new Field("termvector1", "aap noot vuur", fieldType));
        doc.add(new Field("termvector2", "mies water", fieldType));
        lucene.addDocument("id:100", doc);

        doc = new Document();
        doc.add(new Field("termvector1", "aap vuur", fieldType));
        doc.add(new Field("termvector2", "mies", fieldType));
        lucene.addDocument("id:200", doc);

        doc = new Document();
        doc.add(new Field("termvector2", "iets", fieldType));
        lucene.addDocument("id:300", doc);

        lucene.addDocument("id:400", new Document());

        QueryData q = new QueryData();
        q.clustering = true;
        q.start = 0;
        q.stop = 10;

        List<ClusterConfig.ClusterField> clusterFields = new ArrayList<ClusterConfig.ClusterField>();
        clusterFields.add(new ClusterConfig.ClusterField("termvector1", 1.0, null));
        lucene.getSettings().clusterConfig.clusterFields = clusterFields;

        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(4, result.hits.size());
        for (Hit hit : result.hits) {
            ClusterHit clusterHit = (ClusterHit) hit;
            boolean id0 = false;
            boolean id100 = false;
            for (DocScore docScore : clusterHit.topDocs) {
                if (docScore.identifier.equals("id:0"))
                    id0 = true;
                if (docScore.identifier.equals("id:100"))
                    id100 = true;
            }
            if (id0)
                assertTrue(id100);
        }

        clusterFields = new ArrayList<ClusterConfig.ClusterField>();
        clusterFields.add(new ClusterConfig.ClusterField("termvector1", 1.0, null));
        clusterFields.add(new ClusterConfig.ClusterField("termvector2", 1.0, null));
        lucene.getSettings().clusterConfig.clusterFields = clusterFields;

        result = lucene.executeQuery(q);
        assertEquals(5, result.hits.size());
        for (Hit hit : result.hits) {
            ClusterHit clusterHit = (ClusterHit) hit;
            boolean id0 = false;
            boolean id100 = false;
            for (DocScore docScore : clusterHit.topDocs) {
                if (docScore.identifier.equals("id:0"))
                    id0 = true;
                if (docScore.identifier.equals("id:100"))
                    id100 = true;
            }
            if (id0)
                assertFalse(id100);
        }
    }

    @Test
    public void testCollectUntilStopWithForGrouping() throws Throwable {
        for (int i=0; i<20; i++) {
            Document doc = new Document();
            doc.add(new NumericDocValuesField("__key__", 42L));
            doc.add(new IntField("sort", i, Field.Store.NO));
            lucene.addDocument("id:" + i, doc);
        }
        Document doc = new Document();
        doc.add(new StringField("sort", "100", Field.Store.NO));
        lucene.addDocument("id:100", doc);

        QueryData q = new QueryData();
        q.groupingField = "__key__";
        q.start = 0;
        q.stop = 2;
        q.sort = new Sort(new SortField("sort", SortField.Type.INT));
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(21, result.total);
        assertEquals(2, result.hits.size());

        q.stop = 5;
        result = lucene.executeQuery(q);
        assertEquals(21, result.total);
        assertEquals(2, result.hits.size());
    }

    @Test
    public void testReturnNoMoreThanStopForGrouping() throws Throwable {
        for (int i=0; i<50; i++)
            lucene.addDocument("id:" + i, new Document());
        lucene.addDocument("id:100", new Document());

        QueryData q = new QueryData();
        q.groupingField = "__key__";
        q.start = 5;
        q.stop = 7;
        q.sort = new Sort(new SortField("sort", SortField.Type.STRING));
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(51, result.total);
        assertEquals(2, result.hits.size());
    }

    @Test
    public void testReturnNoMoreThanStopForClustering() throws Throwable {
        for (int i=0; i<50; i++)
            lucene.addDocument("id:" + i, new Document());
        lucene.addDocument("id:100", new Document());

        QueryData q = new QueryData();
        q.clustering = true;
        q.start = 5;
        q.stop = 7;
        q.sort = new Sort(new SortField("sort", SortField.Type.STRING));
        LuceneResponse result = lucene.executeQuery(q);
        assertEquals(51, result.total);
        assertEquals(2, result.hits.size());
    }

    @SuppressWarnings("serial")
    @Test
    public void testFilterCaching() throws Throwable {
        for (int i=0; i<10; i++) {
            final int j = i;
            addDocument(lucene, "id:" + i, null, new HashMap<String, String>() {{put("field" + j, "value0");}});
        }
        final BooleanQuery query = new BooleanQuery();
        for (int i=0; i<100; i++)
            query.add(new TermQuery(new Term("field" + i, "value0")), Occur.SHOULD);
        LuceneResponse response = lucene.executeQuery(new QueryData(), new ArrayList<Query>() {{ add(query); }}, null, null, null, null);
        LuceneResponse responseWithCaching = lucene.executeQuery(new QueryData(), new ArrayList<Query>() {{ add(query); }}, null, null, null, null);
        assertTrue(responseWithCaching.queryTime < response.queryTime);
    }

    @Test
    public void testScoreCollectorCaching() throws Throwable {
        lucene.getSettings().commitCount = 1000;
        for (int i=0; i<100; i++) {
            Document doc1 = new Document();
            doc1.add(new StringField("field0", "value", Store.NO));
            doc1.add(new NumericDocValuesField("field1", i));
            lucene.addDocument("id" + i, doc1);
        }
        lucene.realCommit();

        long t0 = System.currentTimeMillis();
        ScoreSuperCollector scoreCollector1 = this.lucene.scoreCollector("field1", new MatchAllDocsQuery());
        long t1 = System.currentTimeMillis();
        ScoreSuperCollector scoreCollector2 = this.lucene.scoreCollector("field1", new MatchAllDocsQuery());
        long t2 = System.currentTimeMillis();
        assertTrue(t2 - t1 <= t1 - t0);
        assertTrue(t2 - t1 < 2);
        assertEquals(0, scoreCollector1.score(100), 0);
        assertSame(scoreCollector1, scoreCollector2);

        Document doc3 = new Document();
        doc3.add(new NumericDocValuesField("field1", 100));
        lucene.addDocument("id3", doc3);
        lucene.realCommit();
        scoreCollector1 = this.lucene.scoreCollector("field1", new MatchAllDocsQuery());
        assertEquals(1.0, scoreCollector1.score(100), 0);
        assertNotSame(scoreCollector1, scoreCollector2);
    }

    @Test
    public void testKeyCollectorCaching() throws Throwable {
        lucene.getSettings().commitCount = 1000;
        for (int i=0; i<100; i++) {
            Document doc1 = new Document();
            doc1.add(new StringField("field0", "value", Store.NO));
            doc1.add(new NumericDocValuesField("field1", i));
            lucene.addDocument("id" + i, doc1);
        }
        lucene.realCommit();
        long t0 = System.currentTimeMillis();
        OpenBitSet keys1 = this.lucene.collectKeys(new MatchAllDocsQuery(), "field1", null);
        long t1 = System.currentTimeMillis();
        OpenBitSet keys2 = this.lucene.collectKeys(new MatchAllDocsQuery(), "field1", null);
        long t2 = System.currentTimeMillis();
        assertTrue(t2 - t1 <= t1 - t0);
        assertTrue(t2 - t1 < 2);
        assertTrue(keys1.get(1));
        assertTrue(keys1.get(2));
        assertFalse(keys1.get(100));
        assertSame(keys1, keys2);

        Document doc3 = new Document();
        doc3.add(new NumericDocValuesField("field1", 100));
        lucene.addDocument("id3", doc3);
        lucene.realCommit();
        keys1 = this.lucene.collectKeys(new MatchAllDocsQuery(), "field1", null);
        assertTrue(keys1.get(1));
        assertTrue(keys1.get(2));
        assertTrue(keys1.get(100));
        assertNotSame(keys1, keys2);
    }

    @Test
    public void testDontClearCachesIfNothingChanged() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new NumericDocValuesField("keyfield", 1));
        lucene.addDocument("id1", doc1);
        ScoreSuperCollector scoreCollector1 = lucene.scoreCollector("keyfield", new MatchAllDocsQuery());
        OpenBitSet keys1 = lucene.collectKeys(new MatchAllDocsQuery(), "keyfield", null);
        lucene.realCommit();
        lucene.realCommit();
        ScoreSuperCollector scoreCollector2 = lucene.scoreCollector("keyfield", new MatchAllDocsQuery());
        OpenBitSet keys2 = lucene.collectKeys(new MatchAllDocsQuery(), "keyfield", null);
        assertSame(scoreCollector1, scoreCollector2);
        assertSame(keys1, keys2);
        lucene.addDocument("id1", new Document());
        lucene.realCommit();
        ScoreSuperCollector scoreCollector3 = lucene.scoreCollector("keyfield", new MatchAllDocsQuery());
        OpenBitSet keys3 = lucene.collectKeys(new MatchAllDocsQuery(), "keyfield", null);
        assertNotSame(scoreCollector1, scoreCollector3);
        assertNotSame(keys1, keys3);
    }

    @Test
    public void testQueryConvert() throws Throwable {
        lucene.getSettings().facetsConfig.setIndexFieldName("dim1", "otherfield");
        QueryConverter queryConverter = lucene.getQueryConverter();
        Term drilldownTerm = queryConverter.createDrilldownTerm("dim1");
        assertEquals("otherfield", drilldownTerm.field());
    }

    @Test
    public void testSimilarDocuments() throws Throwable {
        FieldType textTypeWithTV = new FieldType(TextField.TYPE_NOT_STORED);
        textTypeWithTV.setStoreTermVectors(true);

        for (int i = 0; i < 10; i++) {
            Document doc1 = new Document();
            doc1.add(new Field("a", "Dit is veld a", textTypeWithTV));
            lucene.addDocument("id-" + i, doc1);
        }

        Document doc2 = new Document();
        doc2.add(new Field("a", "Dit is veld a in doc 2", textTypeWithTV));
        lucene.addDocument("id:0", doc2);

        Document doc3 = new Document();
        doc3.add(new Field("a", "Dit is veld a in doc 3", textTypeWithTV));
        lucene.addDocument("id:1", doc3);

        LuceneResponse response = lucene.similarDocuments("id:0");
        assertEquals(1, response.total);
        compareHits(response, "id:1");

        response = lucene.similarDocuments("id-0");
        assertEquals(11, response.total);
    }

    @Test
    public void testNoSimilarDocumentIfNotExists() throws Throwable {
        LuceneResponse response = lucene.similarDocuments("id:0");
        assertEquals(0, response.total);
    }

    @Test
    public void testNoSimilarDocumentIfNoTermVectors() throws Throwable {
        Document doc1 = new Document();
        doc1.add(new Field("a", "Dit is veld a", TextField.TYPE_NOT_STORED));
        lucene.addDocument("id:0", doc1);

        LuceneResponse response = lucene.similarDocuments("id:0");
        assertEquals(0, response.total);
    }

    public static void compareHits(LuceneResponse response, String... hitIds) {
        Set<String> responseHitIds = new HashSet<String>();
        for (Hit hit : response.hits)
            responseHitIds.add(hit.id);
        Set<String> expectedHitIds = new HashSet<String>();
        for (String hitId : hitIds)
            expectedHitIds.add(hitId);
        assertEquals(expectedHitIds, responseHitIds);
    }

    public static void compareHitsOrdered(LuceneResponse response, String... hitIds) {
        List<String> responseHitIds = new ArrayList<String>();
        for (Hit hit : response.hits)
            responseHitIds.add(hit.id);
        List<String> expectedHitIds = new ArrayList<String>();
        for (String hitId : hitIds)
            expectedHitIds.add(hitId);
        assertEquals(expectedHitIds, responseHitIds);
    }

    public static void addDocument(Lucene lucene, String identifier, Map<String, Integer> keys, Map<String, String> fields) throws Exception {
        Document doc = new Document();
        if (keys != null)
            for (String keyField : keys.keySet())
                doc.add(new NumericDocValuesField(keyField, keys.get(keyField)));
        if (fields != null) {
            for (String fieldname : fields.keySet())
                if (fieldname.equals("intField"))
                    doc.add(new IntField(fieldname, Integer.parseInt(fields.get(fieldname)), Store.NO));
                else {
                    doc.add(new StringField(fieldname, fields.get(fieldname), Store.NO));
                    doc.add(new FacetField("cat_" + fieldname, fields.get(fieldname)));
                }
        }
        lucene.addDocument(identifier, doc);
        lucene.commit();
    }
}

## begin license ##
#
# "Meresco Lucene" is a set of components and tools to integrate Lucene (based on PyLucene) into Meresco
#
# Copyright (C) 2013-2015 Seecr (Seek You Too B.V.) http://seecr.nl
# Copyright (C) 2013-2014 Stichting Bibliotheek.nl (BNL) http://www.bibliotheek.nl
# Copyright (C) 2015 Koninklijke Bibliotheek (KB) http://www.kb.nl
#
# This file is part of "Meresco Lucene"
#
# "Meresco Lucene" is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation; either version 2 of the License, or
# (at your option) any later version.
#
# "Meresco Lucene" is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with "Meresco Lucene"; if not, write to the Free Software
# Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
#
## end license ##

from cqlparser import parseString as parseCql
from meresco.core import Observable
from meresco.lucene import Lucene, TermFrequencySimilarity, LuceneSettings, DrilldownField
from meresco.lucene.composedquery import ComposedQuery
from meresco.lucene.fieldregistry import KEY_PREFIX, FieldRegistry, INTFIELD
from meresco.lucene.lucenequerycomposer import LuceneQueryComposer
from meresco.lucene.multilucene import MultiLucene
from org.apache.lucene.search import MatchAllDocsQuery, BooleanQuery, BooleanClause, SortField
from org.meresco.lucene.search import JoinSortCollector, JoinSortField
from os.path import join
from seecr.test import SeecrTestCase, CallTrace
from weightless.core import be, compose, retval, consume

from lucenetest import createDocument


class MultiLuceneTest(SeecrTestCase):

    def testMultipleJoinQueriesKeepsCachesWithinMaxSize(self):
        for i in xrange(25):
            self.addDocument(self.luceneB, identifier=str(i), keys=[('X', i)], fields=[('Y', str(i))])
        for i in xrange(25):
            q = ComposedQuery('coreA', query=MatchAllDocsQuery())
            q.setCoreQuery(core='coreB', query=luceneQueryFromCql('Y=%s' % i))
            q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'X'))
            consume(self.dna.any.executeComposedQuery(q))

    def testInfoOnQuery(self):
        q = ComposedQuery('coreA')
        q.addFilterQuery('coreB', query=luceneQueryFromCql('N=true'))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals({
            'query': {
                'cores': ['coreB', 'coreA'],
                'drilldownQueries': {},
                'facets': {},
                'filterQueries': {'coreB': ['N:true']},
                'matches': {'coreA->coreB': [{'core': 'coreA', 'uniqueKey': '__key__.A'}, {'core': 'coreB', 'key': '__key__.B'}]},
                'otherCoreFacetFilters': {},
                'queries': {'coreA': None},
                'rankQueries': {},
                'resultsFrom': 'coreA',
                'sortKeys': [],
                'unites': []
            },
            'type': 'ComposedQuery'
        }, result.info)

    def testJoinFacetFromBPointOfView(self):
        q = ComposedQuery('coreB')
        q.setCoreQuery(core='coreA', query=luceneQueryFromCql('Q=true'))
        q.setCoreQuery(core='coreB', query=None, facets=[
                dict(fieldname='cat_N', maxTerms=10),
                dict(fieldname='cat_O', maxTerms=10),
            ])
        try:
            q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX + 'A'), dict(core='coreB', key=KEY_PREFIX + 'B'))
        except ValueError, e:
            self.assertEquals("Match for result core 'coreB' must have a uniqueKey specification.", str(e))
            return

        # for future reference
        self.assertEquals(4, result.total)
        self.assertEquals(set(['B-N>A-MQ', 'B-N>A-MQU', 'B-P>A-MQ', 'B-P>A-MQU']), self.hitIds(result.hits))
        self.assertEquals([{
                'terms': [
                        {'count': 2, 'term': u'false'},
                        {'count': 2, 'term': u'true'},
                    ],
                'fieldname': u'cat_N'
            }, {
                'terms': [
                    {'count': 2, 'term': u'false'},
                    {'count': 2, 'term': u'true'},
                ],
                'fieldname': u'cat_O'
             }], result.drilldownData)

    def testCoreInfo(self):
        infos = list(compose(self.dna.all.coreInfo()))
        self.assertEquals(3, len(infos))

    def testUniteResultFromTwoIndexesCached(self):
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=luceneQueryFromCql('Q=true'), facets=[
                dict(fieldname='cat_Q', maxTerms=10),
                dict(fieldname='cat_U', maxTerms=10),
            ])
        q.setCoreQuery(core='coreB', query=None, facets=[
                dict(fieldname='cat_N', maxTerms=10),
                dict(fieldname='cat_O', maxTerms=10),
            ])
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        q.addUnite(dict(core='coreA', query=luceneQueryFromCql('U=true')), dict(core='coreB', query=luceneQueryFromCql('N=true')))
        resultOne = retval(self.dna.any.executeComposedQuery(q))

        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=luceneQueryFromCql('U=true'))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        q.addUnite(dict(core='coreA', query=luceneQueryFromCql('U=false')), dict(core='coreB', query=luceneQueryFromCql('N=true')))
        consume(self.dna.any.executeComposedQuery(q))

        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=luceneQueryFromCql('Q=true'), facets=[
                dict(fieldname='cat_Q', maxTerms=10),
                dict(fieldname='cat_U', maxTerms=10),
            ])
        q.setCoreQuery(core='coreB', query=None, facets=[
                dict(fieldname='cat_N', maxTerms=10),
                dict(fieldname='cat_O', maxTerms=10),
            ])
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        q.addUnite(dict(core='coreA', query=luceneQueryFromCql('U=true')), dict(core='coreB', query=luceneQueryFromCql('N=true')))
        resultAgain = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(resultOne.total, resultAgain.total)
        self.assertEquals(resultOne.hits, resultAgain.hits)
        self.assertEquals(resultOne.drilldownData, resultAgain.drilldownData)

    def testUniteResultFromTwoIndexesCachedAfterUpdate(self):
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=luceneQueryFromCql('Q=true'), facets=[
                dict(fieldname='cat_Q', maxTerms=10),
                dict(fieldname='cat_U', maxTerms=10),
            ])
        q.setCoreQuery(core='coreB', query=None, facets=[
                dict(fieldname='cat_N', maxTerms=10),
                dict(fieldname='cat_O', maxTerms=10),
            ])
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        q.addUnite(dict(core='coreA', query=luceneQueryFromCql('U=true')), dict(core='coreB', query=luceneQueryFromCql('N=true')))
        q.addOtherCoreFacetFilter(core='coreB', query=luceneQueryFromCql('N=true'))
        resultOne = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(3, resultOne.total)
        self.assertEquals([{
                'terms': [
                    {'count': 3, 'term': u'true'}
                ], 'path': [], 'fieldname': u'cat_Q'
            }, {
                'terms': [
                    {'count': 2, 'term': u'true'},
                    {'count': 1, 'term': u'false'}
                ], 'path': [], 'fieldname': u'cat_U'
            }, {
                'terms': [
                    {'count': 2, 'term': u'true'}
                ], 'path': [], 'fieldname': u'cat_N'
            }, {
                'terms': [
                    {'count': 1, 'term': u'true'},
                    {'count': 1, 'term': u'false'},
                ], 'path': [], 'fieldname': u'cat_O'
            }], resultOne.drilldownData)

        self.addDocument(self.luceneA, identifier='A-MQU',  keys=[('A', 8 )], fields=[('M', 'true' ), ('Q', 'false' ), ('U', 'true' ), ('S', '8')])

        resultAgain = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(2, resultAgain.total)
        self.assertEquals([{
                'terms': [
                    {'count': 2, 'term': u'true'}
                ], 'path': [], 'fieldname': u'cat_Q'
            }, {
                'terms': [
                    {'count': 1, 'term': u'false'},
                    {'count': 1, 'term': u'true'},
                ], 'path': [], 'fieldname': u'cat_U'
            }, {
                'terms': [
                    {'count': 1, 'term': u'true'}
                ], 'path': [], 'fieldname': u'cat_N'
            }, {
                'terms': [
                    {'count': 1, 'term': u'true'}
                ], 'path': [], 'fieldname': u'cat_O'
            }], resultAgain.drilldownData)

    def NOT_YET_SUPPORTED_testUniteResultsFromCoreB(self):
        q = ComposedQuery('coreB')
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX +'A'), dict(core='coreB', key=KEY_PREFIX +'B'))
        q.addUnite(dict(core='coreA', query=luceneQueryFromCql('U=true')), dict(core='coreB', query=luceneQueryFromCql('N=true')))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(['B-N', 'B-N>A-M', 'B-N>A-MQ', 'B-N>A-MQU', 'B-N>A-MU', 'B-P>A-MQU', 'B-P>A-MU', ], sorted(result.hits))

    def NOT_YET_SUPPORTED_testUniteAndFacetsResultsFromCoreB(self):
        q = ComposedQuery('coreB')
        q.setCoreQuery(core='coreA', query=luceneQueryFromCql('Q=true'))
        q.setCoreQuery(core='coreB', query=None, facets=[
                dict(fieldname='cat_N', maxTerms=10),
                dict(fieldname='cat_O', maxTerms=10),
            ])
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX + 'A'), dict(core='coreB', key=KEY_PREFIX + 'B'))
        q.addUnite(dict(core='coreA', query=luceneQueryFromCql('U=true')), dict(core='coreB', query=luceneQueryFromCql('N=true')))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set(['B-N>A-MQ', 'B-N>MQU']), self.hitIds(result.hits))
        self.assertEquals(2, result.total)
        self.assertEquals([{
                'terms': [
                    {'count': 2, 'term': u'true'},
                ],
                'fieldname': u'cat_N'
            }, {
                'terms': [
                    {'count': 1, 'term': u'false'},
                    {'count': 1, 'term': u'true'},
                ],
                'fieldname': u'cat_O'
            }], result.drilldownData)

    def testCachingCollectorsAfterUpdate(self):
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        q.setCoreQuery(core='coreB', query=luceneQueryFromCql("N=true"))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        self.addDocument(self.luceneB, identifier='B-N>A-MQU', keys=[('B', 8 )], fields=[('N', 'true' ), ('O', 'false'), ('P', 'false')])
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MU', u'A-MQ', u'A-MQU']), self.hitIds(result.hits))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MU', u'A-MQ', u'A-MQU']), self.hitIds(result.hits))
        self.assertTrue(result.queryTime < 5, result.queryTime)
        self.addDocument(self.luceneB, identifier='B-N>A-MQU', keys=[('B', 80 )], fields=[('N', 'true' ), ('O', 'false'), ('P', 'false')])
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MU', u'A-MQ']), self.hitIds(result.hits))

    def testCachingCollectorsAfterUpdateInSegmentWithMultipleDocuments(self):
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        q.setCoreQuery(core='coreB', query=luceneQueryFromCql("N=true"))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.addDocument(self.luceneB, identifier='B-N>A-MQU', keys=[('B', 8 )], fields=[('N', 'true' ), ('O', 'false'), ('P', 'false')])
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MU', u'A-MQ', u'A-MQU']), self.hitIds(result.hits))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MU', u'A-MQ', u'A-MQU']), self.hitIds(result.hits))
        self.assertTrue(result.queryTime < 5, result.queryTime)
        self.addDocument(self.luceneB, identifier='B-N>A-MU', keys=[('B', 60 )], fields=[('N', 'true' ), ('O', 'false'), ('P', 'false')])
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MQ', u'A-MQU']), self.hitIds(result.hits))

    def testCachingCollectorsAfterDelete(self):
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        q.setCoreQuery(core='coreB', query=luceneQueryFromCql("N=true"))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        self.addDocument(self.luceneB, identifier='B-N>A-MQU', keys=[('B', 8 )], fields=[('N', 'true' ), ('O', 'false'), ('P', 'false')])
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MU', u'A-MQ', u'A-MQU']), self.hitIds(result.hits))
        consume(self.luceneB.delete(identifier='B-N>A-MU'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set([u'A-M', u'A-MQ', u'A-MQU']), self.hitIds(result.hits))


    def NOT_YET_SUPPORTED_testJoinQueryOnOptionalKeyUniteResultsWithoutKey(self):
        q = ComposedQuery('coreA')
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'C'), dict(core='coreB', key=KEY_PREFIX+'B'))
        q.addUnite(dict(core='coreA', query=luceneQueryFromCql('U=true')), dict(core='coreB', query=luceneQueryFromCql('N=true')))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(set(['A-U', 'A-QU', 'A-MU', 'A-MQU', 'A-M']), self.hitIds(result.hits))

    def XXX_NOT_YET_IMPLEMENTED_testRankQueryInSingleCoreQuery(self):
        q = ComposedQuery('coreA', query=MatchAllDocsQuery())
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        q.setRankQuery(core='coreA', query=luceneQueryFromCql('Q=true'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(8, result.total)
        self.assertEquals([u'A-Q', u'A-QU', u'A-MQ', u'A-MQU', u'A', u'A-U', u'A-M', u'A-MU'], [hit.id for hit in result.hits])

    def testScoreCollectorCacheInvalidation(self):
        q = ComposedQuery('coreA', query=MatchAllDocsQuery())
        q.setRankQuery(core='coreC', query=luceneQueryFromCql('S=true'))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreC', key=KEY_PREFIX+'C'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(8, result.total)
        self.assertEquals(u'A-MQU', result.hits[0].id)
        self.assertEquals(set([u'A', u'A-U', u'A-Q', u'A-QU', u'A-M', u'A-MU', u'A-MQ']), set([hit.id for hit in result.hits[1:]]))

        self.addDocument(self.luceneC, identifier='C-S>A-MQ', keys=[('C', 7)], fields=[('S', 'true')])
        try:
            result = retval(self.dna.any.executeComposedQuery(q))
            self.assertEquals(8, result.total)
            self.assertEquals(set([u'A-MQ' , u'A-MQU']), set([hit.id for hit in result.hits[:2]]))
            self.assertEquals(set([u'A', u'A-U', u'A-Q', u'A-QU', u'A-M', u'A-MU']), set([hit.id for hit in result.hits[2:]]))
        finally:
            self.luceneC.delete(identifier='C-S>A-MQ')

    def testNullIteratorOfPForDeltaIsIgnoredInFinalKeySet(self):
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=luceneQueryFromCql('N=no_match'))
        q.setCoreQuery(core='coreB', query=luceneQueryFromCql('N=true'))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'UNKOWN'), dict(core='coreB', key=KEY_PREFIX+'UNKOWN'))
        retval(self.dna.any.executeComposedQuery(q))
        self.luceneB.commit() # Force to write new segment; Old segment remains in seen list
        self.addDocument(self.luceneB, identifier='new', keys=[], fields=[('ignored', 'true')]) # Add new document to force recreating finalKeySet
        try:
            result = retval(self.dna.any.executeComposedQuery(q))
            self.assertEquals(0, len(result.hits))
        finally:
            self.luceneB.delete(identifier='new')

    def testKeyFilterIgnoresKeysOutOfBoundsOfKeySet(self):
        self.addDocument(self.luceneB, identifier=str(100), keys=[('B', 100)], fields=[]) # Force key to be much more than bits in long[] in FixedBitSet, so it must be OutOfBounds
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        q.setCoreQuery(core='coreB', query=MatchAllDocsQuery())
        q.addFacet(core='coreB', facet=dict(fieldname='cat_M', maxTerms=10))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(4, len(result.hits))

    def testCollectScoresWithNoResultAndBooleanQueryDoesntFailOnFakeScorerInAggregateScoreCollector(self):
        q = BooleanQuery()
        q.add(luceneQueryFromCql('M=true'), BooleanClause.Occur.SHOULD)
        q.add(luceneQueryFromCql('M=true'), BooleanClause.Occur.SHOULD)
        q = ComposedQuery('coreA', query=q)
        q.start = 0
        q.stop = 0
        q.setRankQuery(core='coreC', query=luceneQueryFromCql('S=true'))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreC', key=KEY_PREFIX+'C'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(4, result.total)
        self.assertEquals([], result.hits)

    def testCachingKeyCollectorsIntersectsWithACopyOfTheKeys(self):
        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        q.setCoreQuery(core='coreB', query=luceneQueryFromCql("O=true"))
        q.addFilterQuery(core='coreB', query=luceneQueryFromCql("N=true"))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(2, len(result.hits))

        q = ComposedQuery('coreA')
        q.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        q.setCoreQuery(core='coreB', query=MatchAllDocsQuery())
        q.addFilterQuery(core='coreB', query=luceneQueryFromCql("N=true"))
        q.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        result = retval(self.dna.any.executeComposedQuery(q))
        self.assertEquals(4, len(result.hits))

    def testJoinSort(self):
        cq = ComposedQuery('coreA')
        cq.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        cq.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        cq.addSortKey({'sortBy': 'T', 'sortDescending': False, 'core': 'coreB'})
        cq.addSortKey({'sortBy': 'S', 'sortDescending': False, 'core': 'coreA'})
        result = retval(self.dna.any.executeComposedQuery(cq))
        self.assertEqual(['A-M', 'A-MU', 'A-MQ', 'A-MQU', 'A', 'A-U', 'A-Q', 'A-QU'], [hit.id for hit in result.hits])

        cq = ComposedQuery('coreA')
        cq.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        cq.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        cq.addSortKey({'sortBy': 'T', 'sortDescending': True, 'core': 'coreB'})
        cq.addSortKey({'sortBy': 'S', 'sortDescending': False, 'core': 'coreA'})
        result = retval(self.dna.any.executeComposedQuery(cq))
        self.assertEqual(['A-MQU', 'A-MQ', 'A-MU', 'A-M', 'A', 'A-U', 'A-Q', 'A-QU'], [hit.id for hit in result.hits])

        cq = ComposedQuery('coreA')
        cq.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        cq.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        cq.addSortKey({'sortBy': 'intField', 'sortDescending': True, 'core': 'coreB'})
        cq.addSortKey({'sortBy': 'S', 'sortDescending': False, 'core': 'coreA'})
        result = retval(self.dna.any.executeComposedQuery(cq))
        self.assertEqual(['A-MQU', 'A-MQ', 'A-MU', 'A-M', 'A', 'A-U', 'A-Q', 'A-QU'], [hit.id for hit in result.hits])

        cq = ComposedQuery('coreA')
        cq.setCoreQuery(core='coreA', query=MatchAllDocsQuery())
        cq.addMatch(dict(core='coreA', uniqueKey=KEY_PREFIX+'A'), dict(core='coreB', key=KEY_PREFIX+'B'))
        cq.addSortKey({'sortBy': 'intField', 'sortDescending': True, 'core': 'coreB', 'missingValue': 20})
        cq.addSortKey({'sortBy': 'S', 'sortDescending': False, 'core': 'coreA'})
        result = retval(self.dna.any.executeComposedQuery(cq))
        self.assertEqual(['A', 'A-U', 'A-Q', 'A-QU', 'A-MQU', 'A-MQ', 'A-MU', 'A-M'], [hit.id for hit in result.hits])

    def testSortWithJoinField(self):
        joinSortCollector = JoinSortCollector(KEY_PREFIX + 'A', KEY_PREFIX+'B')
        self.luceneB.search(query=MatchAllDocsQuery(), collector=joinSortCollector)

        sortField = JoinSortField('T', self.registry.sortFieldType('T'), False, joinSortCollector)
        sortField.setMissingValue(self.registry.defaultMissingValueForSort('T', False))

        result = retval(self.luceneA.executeQuery(MatchAllDocsQuery(), sortKeys=[sortField]))
        self.assertEqual(['A-M', 'A-MU', 'A-MQ', 'A-MQU', 'A', 'A-U', 'A-Q', 'A-QU'], [hit.id for hit in result.hits])

        sortField = JoinSortField('T', self.registry.sortFieldType('T'), True, joinSortCollector)
        sortField.setMissingValue(self.registry.defaultMissingValueForSort('T', True))

        result = retval(self.luceneA.executeQuery(MatchAllDocsQuery(), sortKeys=[sortField]))
        self.assertEqual(['A-MQU', 'A-MQ', 'A-MU', 'A-M', 'A', 'A-U', 'A-Q', 'A-QU'], [hit.id for hit in result.hits])

    def addDocument(self, lucene, identifier, keys, fields):
        consume(lucene.addDocument(
            identifier=identifier,
            document=createDocument([(KEY_PREFIX + keyField, keyValue) for (keyField, keyValue) in keys]+fields, facets=[('cat_'+field, value) for field, value in fields], registry=self.registry),
        ))

def luceneQueryFromCql(cqlString):
    return LuceneQueryComposer(unqualifiedTermFields=[], luceneSettings=LuceneSettings()).compose(parseCql(cqlString))

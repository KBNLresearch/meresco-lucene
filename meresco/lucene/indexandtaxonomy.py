## begin license ##
#
# "Meresco Lucene" is a set of components and tools to integrate Lucene (based on PyLucene) into Meresco
#
# Copyright (C) 2013-2014 Seecr (Seek You Too B.V.) http://seecr.nl
# Copyright (C) 2013-2014 Stichting Bibliotheek.nl (BNL) http://www.bibliotheek.nl
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

from org.apache.lucene.index import DirectoryReader
from org.apache.lucene.search.similarities import BM25Similarity
from org.apache.lucene.facet.taxonomy.directory import DirectoryTaxonomyReader
from org.meresco.lucene.search import SuperIndexSearcher
from org.apache.lucene.search import IndexSearcher
from java.util.concurrent import Executors


class IndexAndTaxonomy(object):

    def __init__(self, settings, indexDirectory=None, taxoDirectory=None):
        self._settings = settings
        self._similarity = settings.similarity
        reader = DirectoryReader.open(indexDirectory)
        self._executor = Executors.newFixedThreadPool(settings.numberOfConcurrentTasks);
        self.searcher = SuperIndexSearcher(reader, self._executor, settings.numberOfConcurrentTasks) if settings.multithreaded else IndexSearcher(reader)
        self.searcher.setSimilarity(self._similarity)
        self.taxoReader = DirectoryTaxonomyReader(taxoDirectory)
        self._bm25Arguments = None
        self.similarityWrapper = SimilarityWrapper()
        self.similarityWrapper.get = lambda: self.searcher.getSimilarity().toString()
        self.similarityWrapper.set = self._setBM25Similarity

    def reopen(self):
        currentReader = self.searcher.getIndexReader()
        reader = DirectoryReader.openIfChanged(currentReader)
        if reader is None:
            return
        currentReader.close()
        if self._settings.multithreaded:
            self.searcher = SuperIndexSearcher(reader, self._executor, self._settings.numberOfConcurrentTasks)
        else:
            self.searcher = IndexSearcher(reader)
        self.searcher.setSimilarity(self._similarity)
        taxoReader = DirectoryTaxonomyReader.openIfChanged(self.taxoReader)
        if taxoReader is None:
            return
        self.taxoReader.close()
        self.taxoReader = taxoReader

    def _setBM25Similarity(self, k1=None, b=None):
        # This method must be thread-safe
        if k1 is None or b is None:
            self._similarity = self._settings.similarity
        else:
            self._similarity = BM25Similarity(*self._bm25Arguments)
        self.searcher.setSimilarity(self._similarity)

    def close(self):
        self.taxoReader.close()
        self.searcher.getIndexReader().close()

class SimilarityWrapper(object):
    pass

## begin license ##
#
# "Meresco Lucene" is a set of components and tools to integrate Lucene (based on PyLucene) into Meresco
#
# Copyright (C) 2014-2016 Seecr (Seek You Too B.V.) http://seecr.nl
# Copyright (C) 2014 Stichting Bibliotheek.nl (BNL) http://www.bibliotheek.nl
# Copyright (C) 2015-2016 Koninklijke Bibliotheek (KB) http://www.kb.nl
# Copyright (C) 2016 Stichting Kennisnet http://www.kennisnet.nl
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

from copy import copy
from org.meresco.lucene.py_analysis import MerescoStandardAnalyzer
from meresco.components.json import JsonDict
from meresco.lucene.fieldregistry import FieldRegistry

class LuceneSettings(object):
    def __init__(self,
                commitTimeout=10,
                commitCount=100000,
                readonly=False,
                lruTaxonomyWriterCacheSize=4000,
                analyzer=MerescoStandardAnalyzer(),
                _analyzer=dict(type="MerescoStandardAnalyzer"),
                similarity=dict(type="BM25Similarity"),
                fieldRegistry=FieldRegistry(),
                maxMergeAtOnce=2,
                segmentsPerTier=8.0,
                numberOfConcurrentTasks=6,
                verbose=True,
            ):
        self.commitTimeout = commitTimeout
        self.commitCount = commitCount
        self.readonly = readonly
        self.lruTaxonomyWriterCacheSize = lruTaxonomyWriterCacheSize
        self.analyzer = analyzer
        self._analyzer = _analyzer
        self.similarity = similarity
        self.fieldRegistry = fieldRegistry
        self.maxMergeAtOnce = maxMergeAtOnce
        self.segmentsPerTier = segmentsPerTier
        self.numberOfConcurrentTasks = numberOfConcurrentTasks
        self.verbose = verbose

    def clone(self, **kwargs):
        arguments = copy(self.__dict__)
        arguments.update(kwargs)
        return LuceneSettings(**arguments)

    def asPostDict(self):
        drilldownFields = []
        fieldRegistry = self.fieldRegistry
        for fieldname, options in fieldRegistry.drilldownFieldNames.items():
            drilldownFields.append({
                    "dim": fieldname,
                    "hierarchical": options["hierarchical"],
                    "multiValued": options["multiValued"],
                    "fieldname": options["indexFieldName"]
                })
        return JsonDict(
                commitTimeout=self.commitTimeout,
                commitCount=self.commitCount,
                lruTaxonomyWriterCacheSize=self.lruTaxonomyWriterCacheSize,
                analyzer=self._analyzer,
                similarity=self.similarity,
                maxMergeAtOnce=self.maxMergeAtOnce,
                segmentsPerTier=self.segmentsPerTier,
                numberOfConcurrentTasks=self.numberOfConcurrentTasks,
                drilldownFields=drilldownFields
            )
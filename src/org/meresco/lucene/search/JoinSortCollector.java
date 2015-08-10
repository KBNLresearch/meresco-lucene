/* begin license *
 *
 * "Meresco Lucene" is a set of components and tools to integrate Lucene (based on PyLucene) into Meresco
 *
 * Copyright (C) 2015 Koninklijke Bibliotheek (KB) http://www.kb.nl
 * Copyright (C) 2015 Seecr (Seek You Too B.V.) http://seecr.nl
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

package org.meresco.lucene.search;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.FieldCache;
import org.apache.lucene.search.FieldComparator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.lucene.util.BytesRef;
import org.meresco.lucene.search.join.KeyValuesCache;


interface JoinFieldComparator {
    void setOtherCoreContext(AtomicReaderContext context);
}


public class JoinSortCollector extends Collector {
    protected String resultKeyName;
    private String otherKeyName;
    private int[] keys;
    private int[] resultKeys;
    private int docBase;
    private IndexReaderContext topLevelReaderContext;
    private static int docIdsByKeyInitialSize = 0;
    protected int[] docIdsByKey = new int[docIdsByKeyInitialSize];

    public JoinSortCollector(String resultKeyName, String otherKeyName) {
        this.resultKeyName = resultKeyName;
        this.otherKeyName = otherKeyName;
    }

    public SortField sortField(String field, Type type, boolean reverse) {
        return new JoinSortField(field, type, reverse, this);
    }

    public FieldComparator<?> getComparator(String field, Type type, boolean reverse, final int numHits, final int sortPos, Object missingValue) throws IOException {
        switch(type) {
            case STRING:
                return new JoinTermOrdValComparator(numHits, field, missingValue == SortField.STRING_LAST, this);
            case INT:
                return new JoinIntComparator(numHits, field, (Integer) missingValue, this);
            default:
                throw new IllegalStateException("Illegal join sort type: " + type);
        }
    }

    @Override
    public void setScorer(Scorer scorer) throws IOException {}

    @Override
    public void collect(int doc) throws IOException {
        int key = this.keys[doc];
        if (key >= this.docIdsByKey.length)
            resizeDocIdsByKey((int) ((key + 1) * 1.25));
        this.docIdsByKey[key] = doc + docBase + 1;  // increment to distinguish docId==0 from key not present
    }

    void resizeDocIdsByKey(int newSize) {
        if (newSize <= docIdsByKey.length) {
            return;
        }
        docIdsByKeyInitialSize  = newSize;
        int[] dest = new int[newSize];
        System.arraycopy(docIdsByKey, 0, dest, 0, docIdsByKey.length);
        this.docIdsByKey = dest;
    }

    @Override
    public void setNextReader(AtomicReaderContext context) throws IOException {
        if (this.topLevelReaderContext == null) {
            this.topLevelReaderContext = ReaderUtil.getTopLevelContext(context);
        }
        keys = KeyValuesCache.get(context, this.otherKeyName);
        docBase = context.docBase;
    }

    @Override
    public boolean acceptsDocsOutOfOrder() {
        return false;
    }

    int otherDocIdForDocId(int doc, JoinFieldComparator comparator) {
        int key = this.resultKeys[doc];
        if (key < this.docIdsByKey.length) {
            int otherDoc = this.docIdsByKey[key] - 1;
            if (otherDoc > 0) {
                AtomicReaderContext context = this.contextForDocId(otherDoc);
                comparator.setOtherCoreContext(context);
                return otherDoc - context.docBase;
            }
        }
        comparator.setOtherCoreContext(null);
        return -1;
    }

    private AtomicReaderContext contextForDocId(int docId) {
        List<AtomicReaderContext> leaves = this.topLevelReaderContext.leaves();
        return leaves.get(ReaderUtil.subIndex(docId, leaves));
    }

    public void setResultCoreContext(AtomicReaderContext context) throws IOException {
        this.resultKeys = KeyValuesCache.get(context, this.resultKeyName);
    }
}


class JoinTermOrdValComparator extends FieldComparator.TermOrdValComparator implements JoinFieldComparator {
    final private JoinSortCollector collector;

    public JoinTermOrdValComparator(int numHits, String field, boolean reverse, JoinSortCollector collector) {
        super(numHits, field, reverse);
        this.collector = collector;
    }

    @Override
    protected SortedDocValues getSortedDocValues(AtomicReaderContext context, String field) throws IOException {
        if (context != null)
            return super.getSortedDocValues(context, field);
        return new SortedDocValues() {

            @Override
            public int getOrd(int docID) {
                return -1;
            }

            @Override
            public int lookupTerm(BytesRef key) {
                return -1;
            }

            @Override
            public BytesRef lookupOrd(int ord) {
                return null;
            }

            @Override
            public int getValueCount() {
                return 0;
            }
        };
    }

    @Override
    public JoinTermOrdValComparator setNextReader(AtomicReaderContext context) throws IOException {
        this.collector.setResultCoreContext(context);
        return this;
    }

    @Override
    public int compareBottom(int doc) {
        return super.compareBottom(this.collector.otherDocIdForDocId(doc, this));
    }

    @Override
    public int compareTop(int doc) {
        return super.compareTop(this.collector.otherDocIdForDocId(doc, this));
    }

    @Override
    public void copy(int slot, int doc) {
        super.copy(slot, this.collector.otherDocIdForDocId(doc, this));
    }

    public void setOtherCoreContext(AtomicReaderContext context) {
        try {
            super.setNextReader(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}


class JoinIntComparator extends FieldComparator.IntComparator implements JoinFieldComparator {
    private JoinSortCollector collector;

    public JoinIntComparator(int numHits, String field, Integer missingValue, JoinSortCollector collector) {
        super(numHits, field, null, missingValue);
        this.collector = collector;
    }

    @Override
    protected FieldCache.Ints getIntValues(AtomicReaderContext context, String field) throws IOException {
        if (context != null)
            return super.getIntValues(context, field);

        return new FieldCache.Ints() {
            @Override
            public int get(int docID) {
                return 0;
            }
        };
    }

    @Override
    public JoinIntComparator setNextReader(AtomicReaderContext context) throws IOException {
        this.collector.setResultCoreContext(context);
        return this;
    }

    @Override
    public int compareBottom(int doc) {
        return super.compareBottom(this.collector.otherDocIdForDocId(doc, this));
    }

    @Override
    public int compareTop(int doc) {
        return super.compareTop(this.collector.otherDocIdForDocId(doc, this));
    }

    @Override
    public void copy(int slot, int doc) {
        super.copy(slot, this.collector.otherDocIdForDocId(doc, this));
    }

    public void setOtherCoreContext(AtomicReaderContext context) {
        try {
            super.setNextReader(context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

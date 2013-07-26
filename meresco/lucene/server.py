## begin license ##
#
# "Meresco Lucene" is a set of components and tools to integrate Lucene (based on PyLucene) into Meresco
#
# Copyright (C) 2013 Seecr (Seek You Too B.V.) http://seecr.nl
# Copyright (C) 2013 Stichting Bibliotheek.nl (BNL) http://www.bibliotheek.nl
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

from os.path import dirname, abspath, join, realpath
from sys import stdout

from seecr.html import DynamicHtml

from meresco.components.http import StringServer, ObservableHttpServer, BasicHttpHandler, ApacheLogger, PathFilter, PathRename, FileServer
from meresco.components.http.utils import ContentTypePlainText
from meresco.components.sru import SruRecordUpdate, SruParser, SruHandler
from meresco.components.drilldown import SRUTermDrilldown
from meresco.components import Xml2Fields, Venturi, StorageComponent, XmlPrintLxml, FilterField, RenameField, FilterMessages
from meresco.components.autocomplete import Autocomplete
from meresco.core import Observable, TransactionScope
from meresco.core.processtools import setSignalHandlers

from meresco.lucene import Lucene, Fields2LuceneDoc, CqlToLuceneQuery, SORTED_PREFIX, UNTOKENIZED_PREFIX, version, MultiLucene
from meresco.lucene.remote import LuceneRemoteService, LuceneRemote

from weightless.io import Reactor
from weightless.core import compose, be


myPath = abspath(dirname(__file__))
dynamicPath = join(myPath, 'html', 'dynamic')
staticPath = join(myPath, 'html', 'static')

def main(reactor, port, databasePath):
    lucene = Lucene(path=join(databasePath, 'lucene'), reactor=reactor, commitCount=53, name='main')
    multiLuceneHelix = (MultiLucene(defaultCore='main'),
            (Lucene(path=join(databasePath, 'lucene-empty'), reactor=reactor, name='empty-core'),),
            (lucene,),
        )
    storageComponent = StorageComponent(directory=join(databasePath, 'storage'))
    indexHelix = (Fields2LuceneDoc('record', addTimestamp=True),
            (lucene,)
        )

    return \
    (Observable(),
        (ObservableHttpServer(reactor=reactor, port=port),
            (BasicHttpHandler(),
                (ApacheLogger(outputStream=stdout),
                    (PathFilter("/info", excluding=[
                            '/info/version',
                            '/info/name',
                            '/update',
                            '/sru',
                            '/remote',
                            '/via-remote-sru',
                        ]),
                        (DynamicHtml(
                                [dynamicPath],
                                reactor=reactor,
                                indexPage='/info',
                                additionalGlobals={
                                    'VERSION': version,
                                }
                            ),
                        )
                    ),
                    (PathFilter("/info/version"),
                        (StringServer(version, ContentTypePlainText), )
                    ),
                    (PathFilter("/info/name"),
                        (StringServer('Meresco Lucene', ContentTypePlainText),)
                    ),
                    (PathFilter("/static"),
                        (PathRename(lambda path: path[len('/static'):]),
                            (FileServer(staticPath),)
                        )
                    ),
                    (PathFilter("/update"),
                        (SruRecordUpdate(),
                            (TransactionScope('record'),
                                (Venturi(should=[{'partname': 'record', 'xpath': '.'}], namespaces={'doc': 'http://meresco.org/namespace/example'}),
                                    (FilterMessages(allowed=['delete']),
                                        (lucene,),
                                        (storageComponent,)
                                    ),
                                    (FilterMessages(allowed=['add']),
                                        (Xml2Fields(),
                                            (RenameField(lambda name: name.split('.', 1)[-1]),
                                                indexHelix,
                                                (FilterField(lambda name: name == 'intfield1'),
                                                    (RenameField(lambda name: SORTED_PREFIX + name),
                                                        indexHelix,
                                                    )
                                                ),
                                                (FilterField(lambda name: name in ['field2', 'field3']),
                                                    (RenameField(lambda name: UNTOKENIZED_PREFIX + name),
                                                        indexHelix,
                                                    )
                                                ),
                                            )
                                        ),
                                    ),
                                    (XmlPrintLxml(fromKwarg='lxmlNode', toKwarg='data'),
                                        (storageComponent,)
                                    )
                                )
                            )
                        )
                    ),
                    (PathFilter('/sru'),
                        (SruParser(defaultRecordSchema='record'),
                            (SruHandler(),
                                (CqlToLuceneQuery([]),
                                    (lucene,)
                                ),
                                (SRUTermDrilldown(defaultFormat='xml'),),
                                (storageComponent,),
                            )
                        )
                    ),
                    (PathFilter('/via-remote-sru'),
                        (SruParser(defaultRecordSchema='record'),
                            (SruHandler(),
                                (LuceneRemote(host='localhost', port=port, path='/remote'),),
                                (SRUTermDrilldown(defaultFormat='xml'),),
                                (storageComponent,),
                            )
                        )
                    ),
                    (PathFilter('/remote'),
                        (LuceneRemoteService(reactor=reactor),
                            (CqlToLuceneQuery([]),
                                multiLuceneHelix,
                            )
                        )
                    ),
                    (PathFilter('/autocomplete'),
                        (Autocomplete('localhost', port, '/autocomplete', '__all__', '?', 5, '?', '?'),
                            (lucene,),
                        )
                    )
                )
            )
        )
    )




def startServer(port, stateDir, **kwargs):
    setSignalHandlers()
    print 'Firing up Meresco Lucene Server.'
    reactor = Reactor()
    databasePath = realpath(abspath(stateDir))

    #main
    dna = main(reactor, port=port, databasePath=databasePath)
    #/main

    server = be(dna)
    list(compose(server.once.observer_init()))

    print "Ready to rumble"
    stdout.flush()
    reactor.loop()

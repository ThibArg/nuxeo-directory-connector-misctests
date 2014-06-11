nuxeo-directory-connector-misctests
===================================

### IMPORTANT

This is a fork of [nuxeo-directory-connector](https://github.com/tiry/nuxeo-directory-connector)

Adding some examples and, hopefully, explanations.

**Important #2**: You need the `jersey` client installed in Nuxeo, if it is not already done. See below, "Trouble Shooting".

### Topics

* [Building the Plugin and Using Eclipse](#building-the-plugin-and-using-eclipse)
* [The `NuxeoDemoComInMemoryConnector` DirectoryConnector](#the-nuxeodemocominmemoryconnector-directoryconnector): Connects to demo.nuxeo.com and fetches documents which contain "nuxeo" (fulltext search). Query is done only once.
* [The `NuxeoDemoComDynamicConnector` DirectoryConnector](#the-nuxeodemocomdynamicconnector-directoryconnector): Connects to demo.nuxeo.com and fetches documents whose title contains a value entered by the user in a suggestion widget. Query is done every time the value changes.


### Building the Plugin and Using Eclipse

Assuming `maven` (at least version 3.2.1) is configured and installed:
```bash
cd /path/to/nuxeo-directory-connector-misctests
mvn package
```
The .jar is in `{nuxeo-directory-connector-misctests}/target/`

To import it in Eclipse, first ask maven to do build it for Eclipse...
```bash
mvn eclipse:eclipse
```
...then, in Eclipse, choose "File" -> "Import Existing project" and follow the instruction.


### The `NuxeoDemoComInMemoryConnector` DirectoryConnector

This directory connects to demo.nuxeo.com and fetches documents which contain "nuxeo" (fulltext search).

It extends `JsonInMemoryDirectoryConnector`, which means basically:
* There is a _single_ call to the REST service
* This call gets all documents which contain "nuxeo"
  * The connector asks for max. 200 elements
* It maps the result to the `entries` field of the JSON result. This field contains an array with all the found documents
* Then, when the user enters something in the suggestion widget (bound to this directory), the internal query is done on the _pre-fetched_ data, so basically, we loop through the result (mapped to `entries`), and for each entry, we check if the `title` field contains the value.

The class is commented, so you should find explanations in the code, but there is something which may request a bit more explanations: **We need to contribute an extension point to declare this directory**, so nuxeo can use it. This is done in the `OSGI-INF/miscDirectoryConnectorsContrib.xml` file (which also contains comments):

* A new directory is declared, named "demoNuxeoComDocuments"
* This directory:
  * Uses the `vocabulary` schema, which is declared in the `resources/schemas` folder
  * Declares the class to be used to handle the vocabulary: Our `NuxeoDemoComInMemoryConnector` class
  * Declares the mapping between:
    * The fields used in the `vocabulary` schema (`ìd` and `label`)
    * And the JSON properties received after the call to the service (`uid` and `title`)
    * (see below the JSON returned by demo.nuxeo.com)

  * Defines the URL to use: `http://demo.nuxeo.com/nuxeo/api/v1/path///@search?fullText=nuxeo`
    * This URL will be called only once, the first time the user clicks in the Suggestion widget
    * Then, when the user enters something in the widget, filtering is done on the existing, prefetched values: _No new request is sent to `demo.nuxeo.com`_.

##### Summary: How to Use the `JsonInMemoryDirectoryConnector` (and its `BaseJSONDirectoryConnector` parent) with a `Vocabulary`-like table

* Spend time checking the webservice you need to call, and look at the result it returns:
  * Deduce the URL to call
  * Deduce the mapping that must be done
  * In our example, the JSON returned by the query contains an `entries` property, which is an array of documents. Our custom class extracts this property. Then, for each document, we have `uid` and `title` (and we ignore the other properties):
<pre>
{
  "entity-type": "documents",
  . . .
  "entries": [ {
    "entity-type": "document"
    "repository": "default"
    "<b>uid</b>": "2e668ca7-c7af-4489-9de3-045118688649"
    "<b>title</b>": "The title"
    . . .
    },
    . . .
  ],
  . . .
}
</pre>

* Create a class wich extends `JsonInMemoryDirectoryConnector`, and overrides the following methods:
  * `extractResult(JsonNode responseAsJson)`: You must here return the node which contains an array of your results (`"entries"` in our example)
  * `getEntryMap(String id)`: Because we are handling this example as a `vocabulary` schema, we must handle the `obsolete` field (see the example code)
  * `call(String url)`: If you need to tune the call. In the `NuxeoDemoComInMemoryConnector`, we add the `Authorization` header and the `pageSize` query parameter
  * `queryEntryIds(Map<String, Serializable> filter, Set<String> fulltext)`: This is the filter applied when the user enters values in the widget

**NOTICE**: You dont' *need* to override all these methods, of course. If the default behavior fits your needs, no problem :-)

* Add create the xml contribution which declares your directory, its schema, the class it uses, the url, etc.


##### Using this Directory in a SuggestionWidget with Nuxeo Studio

* Drag-drop a Directory Suggestion widget
* Scroll down the "Layout Widget Editor" that pops up
* Click the "Custom properties configuration" to add a custom property
* Enter "directoryName" as the key and the name of you directory as the value (in this example, "demoNuxeoComDocuments")
* (save)


### The `NuxeoDemoComDynamicConnector` DirectoryConnector

This directory connects to demo.nuxeo.com and fetches documents whose title contain the value entered by the user in the suggestion widget's box.

It extends `BaseJSONDirectoryConnector`, and the way it works is a bit different from the `...InMemoryConnector`:
* The REST service is called _every time_ the user changes the value in the suggesiton widget
* Then, it is called once/found document, so to display its title

This means that if a query finds 40 documents, there will be 41 calls: First to query the documents, then one/document to get their title.

The class is commented, so you should find explanations in the code, but there is something which may request a bit more explanations: **We need to contribute an extension point to declare this directory**, so nuxeo can use it. This is done in the `OSGI-INF/miscDirectoryConnectorsContrib.xml` file (which also contains comments):

* A new directory is declared, named "demoNuxeoComDocumentsDynamic"
* This directory:
  * Uses the `vocabulary` schema, which is declared in the `resources/schemas` folder
  * Declares the class to be used to handle the vocabulary: Our `NuxeoDemoComDynamicConnector` class
  * Declares the mapping between:
    * The fields used in the `vocabulary` schema (`ìd` and `label`)
    * And the JSON properties received after the call to the service (`uid` and `title`)
    * (see below the JSON returned by demo.nuxeo.com)

  * Defines the URL to use: `http://demo.nuxeo.com/nuxeo/api/v1/`
    This URL is the base for the two different queries we have, and the appropriate resource endpoint is added to this URL depending on the context:
      * When searching the documents for the value entered by the user, we use the `/path` pattern, and set the path to the root (single "/"):
          `path///@search?query=SELECT * FROM Document WHERE ...`
      * Or when fetching the title of a document, either to display it in a View layout for example, or to fill the suggestion widget with the titles of all the documents found by the query:
          `id/the-id`

##### Summary: How to Use the `BaseJSONDirectoryConnector` with a `Vocabulary`-like table

* Spend time checking the webservice you need to call, and look at the result it returns:
  * Deduce the URL to call
  * Deduce the mapping that must be done
  * We are doing two different requests here:
    * One which returns a list of documents (the quey made when the user changes the value). In our example (querying demo.nuxeo.com) the JSON returned by this query contains an `entries` property, which is an array of documents. Our custom class extracts this property. Then, for each document, we have its `uid` (and we ignore the other properties):

    <pre>
    {
      "entity-type": "documents",
      . . .
      "entries": [ {
        "entity-type": "document"
        "repository": "default"
        "<b>uid</b>": "2e668ca7-c7af-4489-9de3-045118688649"
        "title": "The title"
        . . .
        },
        . . .
      ],
      . . .
    }
    </pre>

    * And one which returns a document (when querying the document to get its title) itself, where whe have the `title`(and ignore the other properties):
    
    <pre>
    {
      "entity-type": "document",
      "repository": "default",
      . . .
      "<b>title</b>": "The title",
      . . .
    }
    </pre>


* Create a class wich extends `BaseJSONDirectoryConnector`, and overrides the following methods:
  * `call(String url)`: If you need to tune the call. In the `NuxeoDemoComDynamicConnector`, we add the `Authorization` header and the `pageSize` query parameter
  * `queryEntryIds(Map<String, Serializable> filter, Set<String> fulltext)`: This is the filter applied when the user enters values in the widget
  * `getEntryMap(String id)`: This is where you add `call()` to get the title of the element

* Add create the xml contribution which declares your directory, its schema, the class it uses, the url, etc.

**NOTICE**: You dont' *need* to override all these methods, of course. If the default behavior fits your needs, no problem :-)

##### Using this Directory in a SuggestionWidget with Nuxeo Studio

* Drag-drop a Directory Suggestion widget
* Scroll down the "Layout Widget Editor" that pops up
* Click the "Custom properties configuration" to add a custom property
* Enter "directoryName" as the key and the name of you directory as the value (in this example, "demoNuxeoComDocumentsDynamic")
* Set the "Minimum characters" field to the correct value for your need
* (save)


### Trouble Shooting

The connector requests the `jersey` library to be installed nuxeo side (it uses it to connect to the WebService). If you test it without this library installed, you will have a "ClassNotFound" error. Thanks to `maven` when you compiled the plugin, the `jersey` library was downloaded (since it is required to compile), so you can copy it to nuxeo. It is located in a directory inside the `.M2` directory which itself is much likely at the first level of you user folder: `~/.m2/repository/com/sun/jersey/jersey-client/1.17.1/jersey-client-1.17.1.jar`. So, you stop nuxeo server, copy `jersey-client-1.17.1.jar` to `nxserver/lib` and restart the server.


### That's all :-)

(I like saying "that's all after spending quite some time building and explaining the thing :-) :-))

### Lisense is the business firendly LGPL 2.1
<pre>
(C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.

All rights reserved. This program and the accompanying materials
are made available under the terms of the GNU Lesser General Public License
(LGPL) version 2.1 which accompanies this distribution, and is available at
http://www.gnu.org/licenses/lgpl-2.1.html

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Lesser General Public License for more details.
</pre>

==========
==========
Here is the original README

## What is this project ?

Nuxeo Directory Connector is a simple Addon for Nuxeo Platforms that allows to wrap a external service as a Directory.

Basically, what this addons provides is all the plumbing code to expose a Java Class implementing a simple interface as a Nuxeo Directory.

## Why would you use this ?

You should consider this as a sample code that you can use as a guide to implement a new type of Directory on top of a custom service provider.

Typical use case is to wrapp a remote WebService as a Nuxeo Directory.

Usaing a directory to wrap a WebService provides some direct benefits :

 - ability to use a XSD schema to define the structure of the entities your expose 

      - entries are exposed as DocumentModels
      - you can then use Nuxeo Layout system to define display 
      - you can then use Nuxeo Studio to do this configuration

 - ability to reuse existing Directory features

      - Field Mapping
      - Entries caching
      - Widgets to search / select an entry

## History

This code was initially written against a Nuxeo 5.4 to be able to resuse a custom WebService as user provider.

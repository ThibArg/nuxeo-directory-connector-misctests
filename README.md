nuxeo-directory-connector-misctests
===================================

## IMPORTANT

This is fork of [nuxeo-directory-connector](https://github.com/tiry/nuxeo-directory-connector)

Adding some examples and, hopefully, explanations.

### The `NuxeoDemoComInMemoryConnector` DirectoryConnector

This directory connects to demo.nuxeo.com and fetches documents which contains "nuxeo". It is a fulltext search.

It extends `JsonInMemoryDirectoryConnector`, which means basically:
* There is a _single_ call to the REST service
* This call gets all documents which contain "nuxeo"
  * The connector asks for max. 200 elements
* It maps the result to the `entries` field of the JSON result. This field contains an array with all the found documents
* Then, when the user enters something in the suggestion widget (bound to this directory), the internal query is done on the _pre-fetched_ data, so basically, we loop through the result (mapped to `entries`), and for each entry, we check if the `title` field contains the value.

The class is commented, so you should find explanations in the code, but there is one other point which requests a bit more explanations: **We need to contribute an extension point, declaring this directory**, so nuxeo can use it. This is done in the `OSGI-INF/miscDirectoryConnectorsContrib.xml` file (which also contains comments):

* A new directory is declared, named "demoNuxeoComDocuments"
* This directory:
  * Uses the `vocabulary` schema, which is declared in the `resources/schemas` folder
  * Declares the class to be used to handle the vocabulary: Our `NuxeoDemoComInMemoryConnector` class
  * Declares and maps the appropriate mapping between:
    * The fields used in the `vocabulary` schema (`ìd` and `label`)
    * And the JSON properties received after the call to the service (`uid` and `title`)
  * Defines the URL to use: `http://demo.nuxeo.com/nuxeo/site/api/v1/path///@search?fullText=nuxeo`
    * This URL will be called only once, the first time the user clicks in the Suggestion widget
    * Then, when the user enters something in the widget, filtering is done on the existing, prefetched values: _No new request is sent to `demo.nuxeo.com`_.

##### Summary: How to Use the `JsonInMemoryDirectoryConnector` (and its `BaseJSONDirectoryConnector` parent) with a `Vocabulary`-like table

* Spend time checking the webservice you need to call, and look at the result it returns:
  * Deduce the URL to call
  * Deduce the mapping that must be done

* Create a class wich extends `JsonInMemoryDirectoryConnector`, and override the following methods:
  * `extractResult(JsonNode responseAsJson)`: You must here return the node which contains an array of your results
  * `getEntryMap(String id)`: Because we are handling this example as a `vocabulary` schema, we must handle the `obsolete` field (see the example code)
  * `call(String url)`: If you need to tune the call. In the `NuxeoDemoComInMemoryConnector`, we add the `Authorization` header and the `pageSize` query parameter
  * `queryEntryIds(Map<String, Serializable> filter, Set<String> fulltext)`: This is the filter applied when the user enters values in the widget

**NOTICE**: You dont' *need* tp override these methods, of course. If the default behavior feets you need, not problem :-)

##### Using this Directory in a SuggestionWidget with Nuxeo Studio

* Drag-drop a Directory Suggestion widget
* Scroll the "Layout Widget Editor"
* Click the "Custom properties configuration" to add a custom property
* Enter "directoryName" as the key and the name of you directory as the value (in this example, "demoNuxeoComDocuments")
* (save)

That's all :-)

(Like saying "that's all after spending quite sopme time building and explaining the thing :-) :-))

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



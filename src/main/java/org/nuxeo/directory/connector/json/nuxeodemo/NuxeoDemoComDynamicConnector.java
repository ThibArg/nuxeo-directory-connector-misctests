/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     thibaud
 */
package org.nuxeo.directory.connector.json.nuxeodemo;

import java.io.IOException;
import java.io.Serializable;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.nuxeo.directory.connector.json.BaseJSONDirectoryConnector;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/**
 * @author Thibaud Arguillere
 *
 * @since 5.9.4
 */
/*
 * [A lot of comments because this code is supposed to explain how
 *  to build your own directory connector connecting to a webservice
 *  that returns JSON data]
 *
 * In this example, we connect to a hard coded REST URL (defined in
 * OSGI-INF/miscDirectoryConnectorsContrib.xml):
 * http://demo.nuxeo.com/nuxeo/api/v1/ and we dynamically change the
 * resource end point depending on the need:
 *    -> A search (NXQL query) when we are requested to fill the
 *       widget. In this example, we use the "/path" pattern, and
 *       set the path to the root (single "/"):
 *              path///@search?query=SELECT * FROM Document WHERE ...
 *
 *    -> Or a direct access to the document when we are requested to
 *       give its title:
 *              id/the-uid-of-the-doc
 *
 * So, basically:
 *    - We override the call() method to handle:
 *          - Authorization
 *          - And pagination only when needed: When getting the document
 *            by its id, we can't use the pageSize query parameter in the
 *            URI
 *
 *    - We override queryEntryIds() to filter the list (when the user enters
 *      something), so the webservice is called every time the user enters
 *      something (which means the user changes the value of the suggestion
 *      box)
 *
 *    - And we override getEntryMap(), which is called when Nuxeo wants to
 *      display the value (here, the title) given the id
 *
 * Something IMPORTANT TO REMEMBER: This is the "BaseJSONDirectoryConnector", so:
 *      - The query is done every time the user changes the value in the suggestion
 *        box
 *              => If the WebService is slow, it will be slow for the user
 *
 *      - Then, a query by id is done for every found item, to fill the suggestion
 *        widget with the titles
 *
 * This means that if the initial query found 50 documents, then there will be
 * 50 calls, one/document
 *
 *      - In this example, connection to demo.nuxeo.com, we let the pageSize
 *        to the default value of 50 when Nuxeo calls queryEntryIds().
 *        Obviously, the bigger the pageSize, the slower the initial request:
 *              => You may want to take care of that.
 *              => As we request 50 documents, we may be missing some, if the
 *                 total found documents was > 50
 *
 *      - On the other hand, the more characters the user use, the less documents
 *        will be found, and the more accurate will be the result (sorry stating
 *        the obvious here, but it must be said :-))
 *
 *      - So, it may be a good idea to configure the suggestion widget so the query
 *        starts when at least some characters have been entered by the user (3, for
 *        example)
 *
 *      - If you are querying against an internal application in your own subnet
 *        with good speed, you probably will not have to worry about these, but
 *        here we are querying against demo.nuxeo.com, which really is remote.
 */
public class NuxeoDemoComDynamicConnector extends BaseJSONDirectoryConnector  {

    protected Log log = LogFactory.getLog(NuxeoDemoComDynamicConnector.class);

    // Base64 for Administrator:Administrator
    private static String kAUTHORIZATION = "Basic QWRtaW5pc3RyYXRvcjpBZG1pbmlzdHJhdG9y";
    // Tune the pageSize
    private static int kPAGE_SIZE = 200;
    // pageSize is not required when getting directly the document with its uid (/api/v1/id/the-uid)
    // (adding it returns a 404, which is normal, because the uid "abcdef-123456-7890ab&pagesize"
    // does not exist.
    private boolean needPageSize = false;

    private static int ZECOUNT = 0;
    // Caller *MUST* encode the characters: spaces (%20) and % (%25)
    // We use the commin filter: Not the hidden documents, not the versions, no the documents
    // in the trash of their container.
    private static String kQUERY_FOR_TITLE = "SELECT * FROM Document"
                                            + " WHERE dc:title ILIKE '%s'"
                                                    + " AND ecm:mixinType != 'HiddenInNavigation'"
                                                    + " AND ecm:isCheckedInVersion = 0"
                                                    + " AND ecm:currentLifeCycleState != 'deleted'";

    /*
     * Overring this one to handle both authentication and pagination
     */
    @Override
    protected JsonNode call(String url) {

        // Adapt the UI if we need a pageSize or not
        WebResource webResource = client.resource(url + (needPageSize ? "&pageSize=" + kPAGE_SIZE : ""));
        ClientResponse response = null;

        response = webResource.accept("application/json")
                              .header("Authorization", kAUTHORIZATION)
                              .get(ClientResponse.class);

        if (response.getStatus() != 200) {
            throw new ClientRuntimeException(
                    "Failed to call remote service : HTTP error code : "
                            + response.getStatus());
        }

        try {
            // This call is the magic of all the plumbing here ;->
            return getMapper().readTree(response.getEntityInputStream());
        } catch (Exception e) {
            throw new ClientRuntimeException(
                    "Error while reading JSON response", e);
        }

    }

    @Override
    public boolean hasEntry(String id) throws ClientException {
        return getEntryMap(id) != null;
    }

    /*
     * Here, Nuxeo is asking for a Map-from-JSON of a single entry
     * given its id, to display the title. This is done when:
     *      - The value must be displayed in a "view" layout for
     *        example, or when the value is first displayed in the
     *        "edit" layout
     *      - And for every value found by queryEntryIds(), to fill
     *        the suggestion widget with the titles
     *
     *  => we use the "/id" pattern, which means:
     *      -> We don't want to add the pageSize parameter
     *      -> We don't need to extract a sub-node from the JSON
     *         response, because we already get the document and its
     *         "title" property is available right away.
     *         The directory handler will do the mapping between the
     *         "title" field (received from the web service) and the
     *         "label" field expected by the code handling the vocabulary.
     *
     * REMINDER: In the xml definition (miscDirectoryConnectorsContrib.xml) we
     *           stated that this vocabulary was to be handled as a regular
     *           vocabulary schema; with "id" and "label" fields, while we
     *           receive in the JSON "uid" and "title".
     */
    @Override
    public Map<String, Object> getEntryMap(String id) {

        String getDataSetUrl = params.get("url") + "id/" + id;
        needPageSize = false;
        JsonNode responseAsJson = call(getDataSetUrl);

        try {
            return readAsMap(responseAsJson);
        } catch (IOException e) {
            log.error("Unable to handle mapping from JSON", e);
            return null;
        }
    }


    @Override
    public List<String> getEntryIds() {
        return new ArrayList<String>();
    }

    /*
     * This one is called every time the user changes the value in the suggestion box
     * (add a character, remove a character, copy/paste, ...). The query starts depending
     * on the suggestion widget's setting about the number of characters required to start
     * the query.
     */
    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter,
            Set<String> fulltext) {

        List<String> ids = new ArrayList<>();

        /* We receive "label", and not "title", because we configured the thing
         * (miscDirectoryConnectorsContrib.xml) so Nuxeo thinks we are using a
         * "standard" (from nuxeo standpoint) vocabulary. So, when the user
         * enters a value in the box, it is put in a "label" field.
         *
         * On the other hand, once the search is done, we received a JSON where
         * the id field is "uid" => we must use this propertye to fill the list.
         */
        String valueToFind = "";
        if(filter.containsKey("label")) {
            valueToFind = (String) filter.get("label");
        }

        if(valueToFind != null && !valueToFind.isEmpty()) {
            String getDataSetUrl = "";

            /* Need to try...catch, we can't throw an exception because we are
             * overriding queryEntryIds() which does not throw any exception
             * while URLEncoder.encode() can throw UnsupportedEncodingException.
             * But we don't do a lot with the exception for sure
             */
            try {
                getDataSetUrl = params.get("url") + "path///@search?query="
                                + URLEncoder.encode(String.format(kQUERY_FOR_TITLE,
                                        "%" + valueToFind + "%"), "utf-8");
            } catch(Exception e) {
                log.error("Failed to encode the URI", e);
            }

            needPageSize = true;
            JsonNode responseAsJson = call(getDataSetUrl);

            /*
             * After this query, the JSON object we received contains this
             * "entries" property, which is and array of documents, each of
             * them having its "uid" property (and "title", "path", etc.)
             */
            JsonNode result = responseAsJson.get("entries");

            for (int i = 0; i < result.size(); i++) {
                ids.add(result.get(i).get("uid").getValueAsText());
            }

            log.warn(ids);
        }

        return ids;
    }
}

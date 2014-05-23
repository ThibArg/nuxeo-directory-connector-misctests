package org.nuxeo.directory.connector.json.nuxeodemo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonNode;
import org.nuxeo.directory.connector.json.JsonInMemoryDirectoryConnector;
import org.nuxeo.ecm.core.api.ClientRuntimeException;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;

/*
 * In this example, we connect to a hard coded REST URL, defined in
 * OSGI-INF/miscDirectoryConnectorsContrib.xml. This is why we are
 * using the JsonInMemoryDirectoryConnector. Basically what happens
 * is:
 *    - The query is done once, and gets all the results.
 *      NOTE: In this example, we increase the default pageSize queryParameter
 *      so we fetch more elements than the default (50). This is done in call(url).
 *
 *    - We override the call() method to handle authorization.
 *
 *    - We override extractResult() to get the "entries" array as returned
 *      by the query
 *
 *    - We override queryEntryIds() to filter the list (when the user enters
 *      something), so we filter on the correct field (in the JSON)
 *
 * Something IMPORTANT TO REMEMBER: This is the "...InMemoryConnector", so:
 *      - The query is static
 *      - The filter occurs only on the pre-fetched values, no new query is made
 *        to the service
 *      - It is "inMemory" => We  increase the "pageSize" parameter in the request,
 *        and, obviously, the bigger the pageSize, the slower the initial request.
 *              => You may want to take care of that in the initial request.
 *              => As we request 200 (instead of 50) documents, we still maybe
 *                 missing some, if the total was > 200 docs found.
 *      - On the other hand, once the result is fetched, doing the sub-query is
 *        pretty fast.
 */
public class NuxeoDemoComInMemoryConnector extends
        JsonInMemoryDirectoryConnector {

    protected Log log = LogFactory.getLog(NuxeoDemoComInMemoryConnector.class);

    // Base64 for Administrator:Administrator
    private static String kAUTHORIZATION = "Basic QWRtaW5pc3RyYXRvcjpBZG1pbmlzdHJhdG9y";
    // Tune the pageSize
    private static int kPAGE_SIZE = 50;

    @Override
    protected JsonNode call(String url) {

        WebResource webResource = client.resource(url + "&pageSize=" + kPAGE_SIZE);
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
            return getMapper().readTree(response.getEntityInputStream());
        } catch (Exception e) {
            throw new ClientRuntimeException(
                    "Error while reading JSON response", e);
        }

    }

    @Override
    protected JsonNode extractResult(JsonNode responseAsJson) {
        if( responseAsJson.get("numberOfPages").getIntValue() > 1) {
            log.warn("Got only " + responseAsJson.get("currentPageSize") + " on " + responseAsJson.get("resultsCount") + " entities");
        }
        return responseAsJson.get("entries");
    }

    @Override
    public Map<String, Object> getEntryMap(String id) {
        Map<String, Object> entry = super.getEntryMap(id);
        // add the obsolete flag so that the default directory filters will work
        entry.put("obsolete", new Long(0));
        return entry;
    }

    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter,
            Set<String> fulltext) {

        List<String> ids = new ArrayList<String>();

        String valueToFind = "";
        if(filter.containsKey("label")) {
            valueToFind = (String) filter.get("label");
            if(valueToFind != null) {
                valueToFind = valueToFind.toLowerCase();
            }
        }

        // do the search
        data_loop: for (String id : getEntryIds()) {

            Map<String, Object> map = getEntryMap(id);
            Object value = map.get("title");
            if(value == null) {
                continue data_loop;
            }

            if( value.toString().toLowerCase().indexOf(valueToFind) < 0) {
                continue data_loop;
            }

            // this entry matches
            ids.add(id);
        }
        return ids;
    }
}

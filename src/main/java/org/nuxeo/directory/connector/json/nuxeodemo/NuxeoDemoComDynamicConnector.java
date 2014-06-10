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
public class NuxeoDemoComDynamicConnector extends BaseJSONDirectoryConnector  {

    protected Log log = LogFactory.getLog(NuxeoDemoComDynamicConnector.class);

    // Base64 for Administrator:Administrator
    private static String kAUTHORIZATION = "Basic QWRtaW5pc3RyYXRvcjpBZG1pbmlzdHJhdG9y";
    // Tune the pageSize
    private static int kPAGE_SIZE = 200;
    // pageSize is not required when getting directly the document with its uid (/api/v1/id/the-uid)
    // (adding it returns a 404, which is normal)
    private boolean needPageSize = false;

    // Caller *MUST* encode the characters: spaces (%20) and % (%25)
    private static String kQUERY_FOR_TITLE = "SELECT * FROM Document"
                                            + " WHERE dc:title ILIKE '%s'"
                                                    + " AND ecm:mixinType != 'HiddenInNavigation'"
                                                    + " AND ecm:isCheckedInVersion = 0"
                                                    + " AND ecm:currentLifeCycleState != 'deleted'";

    @Override
    protected JsonNode call(String url) {

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

    @Override
    public Map<String, Object> getEntryMap(String id) {

        String getDataSetUrl = params.get("url") + "id/" + id;
        needPageSize = false;
        JsonNode responseAsJson = call(getDataSetUrl);

        try {
            return readAsMap(/*result*/responseAsJson);
        } catch (IOException e) {
            log.error("Unable to handle mapping from JSON", e);
            return null;
        }
    }

    @Override
    public List<String> getEntryIds() {
        return new ArrayList<String>();
    }

    @Override
    public List<String> queryEntryIds(Map<String, Serializable> filter,
            Set<String> fulltext) {

        List<String> ids = new ArrayList<>();

        String valueToFind = "";
        if(filter.containsKey("label")) {
            valueToFind = (String) filter.get("label");
        }

        if(valueToFind != null && !valueToFind.isEmpty()) {
            String getDataSetUrl = "";

            // Need to try...catch, we can't throw an exception to override queryEntryIds()
            try {
                getDataSetUrl = params.get("url") + "path///@search?query="
                                + URLEncoder.encode(String.format(kQUERY_FOR_TITLE,
                                        "%" + valueToFind + "%"), "utf-8");
            } catch(Exception e) {
                // . . .
            }

            needPageSize = true;
            JsonNode responseAsJson = call(getDataSetUrl);

            JsonNode result = responseAsJson.get("entries");

            for (int i = 0; i < result.size(); i++) {
                if(result.get(i) == null) {
                    log.warn("result.get(i) == null" );
                }
                if(result.get(i).get("uid") == null) {
                    log.warn("result.get(i).get(\"uid\") == null" );
                }
                ids.add(result.get(i).get("uid").getValueAsText());
            }

            log.warn(ids);
        }

        return ids;
    }
}

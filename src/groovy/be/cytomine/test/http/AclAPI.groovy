package be.cytomine.test.http

/*
* Copyright (c) 2009-2019. Authors: see NOTICE file.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

import be.cytomine.test.Infos

/**
 * User: lrollus
 * Date: 6/12/11
 * This class implement all method to easily get/create/update/delete/manage User to Cytomine with HTTP request during functional test
 */
class AclAPI extends DomainAPI {

//
//    "/api/domain/$domainClassName/$domainIdent/user/$user"(controller:"restACL"){
//        action = [GET:"list",POST:"add",DELETE: "delete"]
//    }

    static def list(String domainClassName, Long domainIdent, Long user, String username, String password) {
        String URL = Infos.CYTOMINEURL + "api/domain/$domainClassName/$domainIdent/user/${(user? user : "")}.json"
        return doGET(URL, username, password)
    }

    static def list(String username, String password) {
        String URL = Infos.CYTOMINEURL + "api/acl.json"
        return doGET(URL, username, password)
    }

    static def create(String domainClassName, Long domainIdent, Long user, String auth, String username, String password) {
        String URL = Infos.CYTOMINEURL + "api/domain/$domainClassName/$domainIdent/user/${user}.json?" + (auth? "auth=$auth" : "")
        def result = doPOST(URL,"",username,password)
        return result
    }

    static def delete(String domainClassName, Long domainIdent, Long user, String auth, String username, String password) {
        String URL = Infos.CYTOMINEURL + "api/domain/$domainClassName/$domainIdent/user/${user}.json?" + (auth? "auth=$auth" : "")
        return doDELETE(URL,username,password)
    }
}

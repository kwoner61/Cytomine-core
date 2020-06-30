package be.cytomine.meta

import be.cytomine.Exception.ServerException
import be.cytomine.command.AddCommand

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

import be.cytomine.command.Command
import be.cytomine.command.DeleteCommand
import be.cytomine.command.Transaction
import be.cytomine.meta.AttachedFile
import be.cytomine.security.SecUser
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task
import org.springframework.web.multipart.MultipartFile

class AttachedFileService extends ModelService {

    static transactional = true

    def currentDomain() {
        return AttachedFile
    }

    /**
     * List all description, Only for admin
     */
    def list() {
        return AttachedFile.list()
    }

    def list(Long domainIdent,String domainClassName) {
        return AttachedFile.findAllByDomainIdentAndDomainClassName(domainIdent,domainClassName)
    }


    def read(def id) {
        AttachedFile.read(id)
    }

    def add(def json, MultipartFile file) {
        SecUser currentUser = cytomineService.getCurrentUser()
        def result = executeCommand(new AddCommand(user: currentUser),null,json)
        AttachedFile af = result?.object

        if (!af) {
            return result
        }

        // Copy file to filesystem
        try {
            file.transferTo(af.getFile())
        }
        catch (IOException e) {
            delete(af)
            throw new ServerException("File cannot be stored")
        }

        return af // TODO: to be consistent with previous API, no command is returned (to be changed and syc with clients)
    }

    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    def delete(AttachedFile domain, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        SecUser currentUser = cytomineService.getCurrentUser()
        Command c = new DeleteCommand(user: currentUser,transaction:transaction)
        return executeCommand(c,domain,null)
    }

    def getStringParamsI18n(def domain) {
        return [domain.id, domain.domainClassName]
    }

}

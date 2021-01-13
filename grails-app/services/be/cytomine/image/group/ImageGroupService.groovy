package be.cytomine.image.group

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

import be.cytomine.command.*
import be.cytomine.ontology.AnnotationGroup
import be.cytomine.project.Project
import be.cytomine.security.SecUser
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task
import grails.converters.JSON

import static org.springframework.security.acls.domain.BasePermission.READ

class ImageGroupService extends ModelService {

    static transactional = true

    def cytomineService
    def imageGroupImageInstanceService
    def securityACLService
    def abstractImageService

    def currentDomain() {
        return ImageGroup
    }

    def read(def id) {
        def group = ImageGroup.read(id)
        if(group) {
            securityACLService.check(group.container(),READ)
            checkDeleted(group)
        }
        group
    }

    def list(Project project) {
        securityACLService.check(project,READ)
        return ImageGroup.findAllByProjectAndDeletedIsNull(project)
    }


    /**
     * Add the new domain with JSON data
     * @param json New domain data
     * @return Response structure (created domain data,..)
     */
    def add(def json) {
        securityACLService.check(json.project,Project,READ)
        securityACLService.checkisNotReadOnly(json.project, Project)
        SecUser currentUser = cytomineService.getCurrentUser()
        json.user = currentUser.id
        synchronized (this.getClass()) {
            Command c = new AddCommand(user: currentUser)
            executeCommand(c,null,json)
        }
    }

    /**
     * Update this domain with new data from json
     * @param domain Domain to update
     * @param jsonNewData New domain datas
     * @return  Response structure (new domain data, old domain data..)
     */
    def update(ImageGroup domain, def jsonNewData) {
        securityACLService.check(domain.container(),READ)
        securityACLService.check(jsonNewData.project,Project,READ)

        SecUser currentUser = cytomineService.getCurrentUser()
        Command c = new EditCommand(user: currentUser)
        executeCommand(c,domain,jsonNewData)
    }

    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    def delete(ImageGroup domain, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        securityACLService.check(domain.container(),READ)
        SecUser currentUser = cytomineService.getCurrentUser()
//        Command c = new DeleteCommand(user: currentUser,transaction:transaction)
//        return executeCommand(c,domain,null)
        //We don't delete domain, we juste change a flag
        def jsonNewData = JSON.parse(domain.encodeAsJSON())
        jsonNewData.deleted = new Date().time
        Command c = new EditCommand(user: currentUser)
        c.delete = true
        return executeCommand(c,domain,jsonNewData)
    }

    def getStringParamsI18n(def domain) {
        return [domain.id,  domain.name, domain.project.name]
    }

    def deleteDependentImageGroupImageInstance(ImageGroup group, Transaction transaction, Task task = null) {
        ImageGroupImageInstance.findAllByGroup(group).each {
            imageGroupImageInstanceService.delete(it,transaction,null,false)
        }
    }

    def annotationGroupService
    def deleteDependentAnnotationGroup(ImageGroup group, Transaction transaction, Task task = null) {
        AnnotationGroup.findAllByImageGroup(group).each {
            annotationGroupService.delete(it,transaction,null,false)
        }
    }
}

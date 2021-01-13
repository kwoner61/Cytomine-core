package be.cytomine.ontology

import be.cytomine.AnnotationDomain
import be.cytomine.Exception.WrongArgumentException

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
import be.cytomine.security.SecUser
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task
import grails.converters.JSON

import static org.springframework.security.acls.domain.BasePermission.READ

class AnnotationLinkService extends ModelService {

    static transactional = true

    def cytomineService
    def annotationGroupService
    def securityACLService
    def abstractImageService

    def currentDomain() {
        return AnnotationLink
    }

    def read(def id) {
        def link = AnnotationLink.read(id)
        if(link) {
            securityACLService.check(link.container(),READ)
            checkDeleted(link)
        }
        link
    }

    def read(AnnotationGroup group, AnnotationDomain annotation) {
        AnnotationLink link = AnnotationLink.findByAnnotationIdentAndGroup(annotation.id, group)
        if(link) {
            securityACLService.check(link.container(),READ)
            checkDeleted(link)
        }
        link
    }

    def list(AnnotationGroup group) {
        securityACLService.check(group,READ)
        return AnnotationLink.findAllByGroupAndDeletedIsNull(group)
    }


    /**
     * Add the new domain with JSON data
     * @param json New domain data
     * @return Response structure (created domain data,..)
     */
    def add(def json) {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(json.annotationIdent, json.annotationClassName)
        if (!annotation) {
            throw new WrongArgumentException("Annotation does not have a valid project.")
        }
        securityACLService.check(annotation.project, READ)
        securityACLService.checkisNotReadOnly(annotation.project)

        AnnotationGroup group = annotationGroupService.read(json.group)
        if (group.project != annotation.project) {
            throw new WrongArgumentException("Group and annotation are not in the same project!")
        }

        json.annotationIdent = annotation.id
        json.annotationClassName = annotation.getClass().getName()
        json.image = annotation.image.id
        SecUser currentUser = cytomineService.getCurrentUser()
        Command c = new AddCommand(user: currentUser)
        executeCommand(c,null,json)
    }

    def addAnnotationLink(def annotationClassName, def annotationIdent, def idGroup, def idImage, SecUser currentUser, Transaction transaction) {
        def json = JSON.parse("{annotationClassName: $annotationClassName, annotationIdent: $annotationIdent, group: $idGroup, image: $idImage}")
        return executeCommand(new AddCommand(user: currentUser, transaction: transaction), null,json)
    }

    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    def delete(AnnotationLink domain, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(domain.annotationIdent, domain.annotationClassName)
        securityACLService.check(annotation.project, READ)
        securityACLService.checkisNotReadOnly(annotation.project)
        SecUser currentUser = cytomineService.getCurrentUser()
        Command c = new DeleteCommand(user: currentUser,transaction:transaction)
        return executeCommand(c,domain,null)
//        //We don't delete domain, we juste change a flag
//        def jsonNewData = JSON.parse(domain.encodeAsJSON())
//        jsonNewData.deleted = new Date().time
//        Command c = new EditCommand(user: currentUser)
//        c.delete = true
//        return executeCommand(c,domain,jsonNewData)
    }

    def retrieve(Map json) {
        AnnotationGroup group = annotationGroupService.read(json.group)
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(json.annotationIdent, json.annotationClassName)

        if (group.project != annotation.project) {
            throw new WrongArgumentException("Group and annotation are not in the same project!")
        }

        return read(group, annotation)
    }

    @Override
    def afterDelete(Object domain, Object response) {
        if (AnnotationLink.countByGroupAndDeletedIsNull(domain.group) < 2) {

            def other = AnnotationLink.findByGroupAndDeletedIsNull(domain.group)
            if (other) {
                other.delete(flush: true)
            }

            domain.group.delete(flush: true)
        }
    }

    @Override
    def afterUpdate(Object domain, Object response) {
        if (domain.deleted != null) {
            afterDelete(domain, response)
        }
    }

    def getStringParamsI18n(def domain) {
        return [domain.id,  domain.group.id]
    }
}

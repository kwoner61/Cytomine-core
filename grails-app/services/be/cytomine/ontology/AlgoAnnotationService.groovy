package be.cytomine.ontology

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

import be.cytomine.AnnotationDomain
import be.cytomine.Exception.WrongArgumentException
import be.cytomine.command.*
import be.cytomine.meta.Property
import be.cytomine.image.ImageInstance
import be.cytomine.image.SliceInstance
import be.cytomine.processing.Job
import be.cytomine.project.Project
import be.cytomine.security.SecUser
import be.cytomine.security.UserJob
import be.cytomine.sql.AlgoAnnotationListing
import be.cytomine.sql.AnnotationListing
import be.cytomine.utils.JSONUtils
import be.cytomine.utils.ModelService
import be.cytomine.utils.Task
import com.vividsolutions.jts.geom.Geometry
import com.vividsolutions.jts.io.ParseException
import com.vividsolutions.jts.io.WKTReader
import com.vividsolutions.jts.io.WKTWriter
import grails.converters.JSON
import grails.transaction.Transactional

import static org.springframework.security.acls.domain.BasePermission.READ

@Transactional
class AlgoAnnotationService extends ModelService {

    static transactional = true
    def propertyService

    def cytomineService
    def transactionService
    def algoAnnotationTermService
    def simplifyGeometryService
    def dataSource
    def reviewedAnnotationService
    def kmeansGeometryService
    def annotationListingService
    def securityACLService
    def sharedAnnotationService
    def imageInstanceService
    def sliceInstanceService
    def annotationLinkService

    def currentDomain() {
        return AlgoAnnotation
    }

    AlgoAnnotation read(def id) {
        def annotation = AlgoAnnotation.read(id)
        if (annotation) {
            securityACLService.check(annotation.container(),READ)
            checkDeleted(annotation)
        }
        annotation
    }

    def countByProject(Project project, Date startDate, Date endDate) {
        String request = "SELECT COUNT(*) FROM AlgoAnnotation WHERE project = $project.id " +
                (startDate ? "AND created > '$startDate' " : "") +
                (endDate ? "AND created < '$endDate' " : "")
        def result = AlgoAnnotation.executeQuery(request)
        return result[0]
    }

    def list(Project project,def propertiesToShow = null) {
        securityACLService.check(project,READ)
        annotationListingService.executeRequest(new AlgoAnnotationListing(
                columnToPrint: propertiesToShow,
                project : project.id
        ))
    }

    def list(Job job,def propertiesToShow = null) {
        securityACLService.check(job.container(),READ)
        List<UserJob> users = UserJob.findAllByJob(job);
        List algoAnnotations = []
        users.each { user ->
            algoAnnotations.addAll(annotationListingService.executeRequest(new AlgoAnnotationListing(
                    columnToPrint: propertiesToShow,
                    user : user.id
            )))
        }
        return algoAnnotations
    }

    def listIncluded(ImageInstance image, String geometry, SecUser user,  List<Long> terms, AnnotationDomain annotation = null,def propertiesToShow = null) {
        securityACLService.check(image.container(),READ)
        annotationListingService.executeRequest(new AlgoAnnotationListing(
                columnToPrint: propertiesToShow,
                image : image.id,
                user : user.id,
                suggestedTerms : terms,
                excludedAnnotation : annotation?.id,
                bbox: geometry
        ))
    }

    /**
     * Add the new domain with JSON data
     * @param json New domain data
     * @return Response structure (created domain data,..)
     */
    def add(def json) {
        SliceInstance slice = null
        ImageInstance image = null
        Project project = null

        if (json.slice) {
            slice = sliceInstanceService.read(json.slice)
            image = slice?.image
            project = slice?.project
        }
        else if (json.image) {
            image = imageInstanceService.read(json.image)
            slice = image?.referenceSlice
            project = image?.project
        }

        if (!project) {
            throw new WrongArgumentException("Annotation does not have a valid project.")
        }

        json.slice = slice.id
        json.image = image.id
        json.project = project.id

        securityACLService.check(project, READ)
        securityACLService.checkisNotReadOnly(project)

        SecUser currentUser = cytomineService.getCurrentUser()
        json.user = currentUser.id
        //TODO: check current user is algo

        def minPoint = json.minPoint
        def maxPoint = json.maxPoint

        Geometry annotationShape
        try {
            annotationShape = new WKTReader().read(json.location)
        }
        catch (Exception ignored) {
            throw new WrongArgumentException("Annotation location is not valid")
        }

        if (!annotationShape.isValid()) {
            throw new WrongArgumentException("Annotation location is not valid")
        }

        //simplify annotation
        try {
            def data = simplifyGeometryService.simplifyPolygon(annotationShape, minPoint, maxPoint)
            json.location = data.geometry
            json.geometryCompression = data.rate
        } catch (Exception e) {
            log.error("Cannot simplify annotation location:" + e)
        }

        if (!json.location) {
            json.location = annotationShape
            json.geometryCompression = 0.0d
        }

        //Start transaction
        Transaction transaction = transactionService.start()
        def result = executeCommand(new AddCommand(user: currentUser, transaction: transaction), null, json)
        def addedAnnotation = result?.object

        if (!addedAnnotation) {
            return result
        }

        // Add annotation-term if any
        def termIds = JSONUtils.getJSONList(json.term) + JSONUtils.getJSONList(json.terms)
        def terms = termIds.collect { id ->
            def r = algoAnnotationTermService.addAlgoAnnotationTerm(addedAnnotation, id, currentUser.id, currentUser, transaction)
            return r?.data?.algoannotationterm?.term
        }
        result.data.annotation.term = terms

        // Add annotation-track if any
        def tracksIds = JSONUtils.getJSONList(json.track) + JSONUtils.getJSONList(json.tracks)
        def annotationTracks = tracksIds.collect { id ->
            def r = annotationTrackService.addAnnotationTrack("be.cytomine.ontology.AlgoAnnotation", addedAnnotation.id, id, addedAnnotation.slice.id, currentUser, transaction)
            return r?.data?.annotationtrack
        }
        result.data.annotation.annotationTrack = annotationTracks
        result.data.annotation.track = annotationTracks.collect { it -> it.track }

        // Add annotation-group/link if any
        def groupId = json.group
        if (groupId) {
            def annotationLinkResult = annotationLinkService.addAnnotationLink("be.cytomine.ontology.AlgoAnnotation",
                    addedAnnotation.id, groupId, addedAnnotation.image.id, currentUser, transaction)
            result.data.annotation.group = groupId
            result.data.annotation.annotationLinks = annotationLinkResult?.data?.annotationlink // Only return created links but not all links !
        }

        // Add properties if any
        def properties = JSONUtils.getJSONList(json.property) + JSONUtils.getJSONList(json.properties)
        properties.each {
            def key = it.key as String
            def value = it.value as String
            propertyService.addProperty("be.cytomine.ontology.AlgoAnnotation", addedAnnotation.id, key, value, currentUser, transaction)
        }

        return result
    }

    /**
     * Update this domain with new data from json
     * @param domain Domain to update
     * @param jsonNewData New domain datas
     * @return  Response structure (new domain data, old domain data..)
     */
    def update(AlgoAnnotation annotation, def jsonNewData) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkFullOrRestrictedForOwner(annotation,annotation.user)

        // TODO: what about image/project ??

        Geometry annotationShape
        try {
            annotationShape = new WKTReader().read(jsonNewData.location)
        }
        catch (ParseException ignored) {
            throw new WrongArgumentException("Annotation location is not valid")
        }

        if (!annotationShape.isValid()) {
            throw new WrongArgumentException("Annotation location is not valid")
        }

        //simplify annotation
        try {
            def data = simplifyGeometryService.simplifyPolygon(annotationShape, jsonNewData.geometryCompression)
            jsonNewData.location = data.geometry
            jsonNewData.geometryCompression = data.rate
        } catch (Exception e) {
            log.error("Cannot simplify annotation location:" + e)
        }

        return executeCommand(new EditCommand(user: currentUser),annotation,jsonNewData)
    }

    /**
     * Delete this domain
     * @param domain Domain to delete
     * @param transaction Transaction link with this command
     * @param task Task for this command
     * @param printMessage Flag if client will print or not confirm message
     * @return Response structure (code, old domain,..)
     */
    def delete(AlgoAnnotation domain, Transaction transaction = null, Task task = null, boolean printMessage = true) {
        SecUser currentUser = cytomineService.getCurrentUser()
        securityACLService.checkIsCreator(domain,currentUser)
        def jsonNewData = JSON.parse(domain.encodeAsJSON())
        jsonNewData.deleted = new Date().time
        Command c = new EditCommand(user: currentUser)
        c.delete = true
        return executeCommand(c,domain,jsonNewData)
    }


    def getStringParamsI18n(def domain) {
        return [cytomineService.getCurrentUser().toString(), domain.image?.getBlindInstanceFilename(), domain.user.toString()]
    }

    def afterAdd(def domain, def response) {
        response.data['annotation'] = response.data.algoannotation
        response.data.remove('algoannotation')
    }

    def afterDelete(def domain, def response) {
        response.data['annotation'] = response.data.algoannotation
        response.data.remove('algoannotation')
    }

    def afterUpdate(def domain, def response) {
        response.data['annotation'] = response.data.algoannotation
        response.data.remove('algoannotation')
    }



    def deleteDependentAlgoAnnotationTerm(AlgoAnnotation ao, Transaction transaction, Task task = null) {
        AlgoAnnotationTerm.findAllByAnnotationIdent(ao.id).each {
            algoAnnotationTermService.delete(it,transaction,null,false)
        }
    }

    def deleteDependentReviewedAnnotation(AlgoAnnotation aa, Transaction transaction, Task task = null) {
        ReviewedAnnotation.findAllByParentIdent(aa.id).each {
            reviewedAnnotationService.delete(it,transaction,null,false)
        }
    }

    def deleteDependentProperty(AlgoAnnotation aa, Transaction transaction, Task task = null) {
        Property.findAllByDomainIdent(aa.id).each {
            propertyService.delete(it,transaction,null,false)
        }

    }
    def deleteDependentSharedAnnotation(AlgoAnnotation aa, Transaction transaction, Task task = null) {
        SharedAnnotation.findAllByAnnotationClassNameAndAnnotationIdent(aa.class.name, aa.id).each {
            sharedAnnotationService.delete(it,transaction,null,false)
        }

    }

    def annotationTrackService
    def deleteDependentAnnotationTrack(AlgoAnnotation ua, Transaction transaction, Task task = null) {
        AnnotationTrack.findAllByAnnotationIdent(ua.id).each {
            annotationTrackService.delete(it, transaction, task)
        }
    }

    def deleteDependentAnnotationLink(AlgoAnnotation ua, Transaction transaction, Task task = null) {
        AnnotationLink.findAllByAnnotationIdent(ua.id).each {
            annotationLinkService.delete(it, transaction, task)
        }
    }

}

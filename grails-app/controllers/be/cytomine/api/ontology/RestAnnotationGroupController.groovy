package be.cytomine.api.ontology

import be.cytomine.Exception.InvalidRequestException
import be.cytomine.api.RestController
import be.cytomine.image.group.ImageGroup
import be.cytomine.ontology.AnnotationGroup
import be.cytomine.project.Project
import grails.converters.JSON
import org.restapidoc.annotation.RestApiMethod
import org.restapidoc.annotation.RestApiParam
import org.restapidoc.annotation.RestApiParams
import org.restapidoc.annotation.RestApiResponseObject
import org.restapidoc.pojo.RestApiParamType

import static org.springframework.security.acls.domain.BasePermission.READ

class RestAnnotationGroupController extends RestController {
    def annotationGroupService
    def projectService
    def imageGroupService
    def securityACLService

    @RestApiMethod(description="Get all annotation groups from a project", listing=true)
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The project id")
    ])
    def listByProject() {
        Project project = projectService.read(params.long('id'))
        if (project) {
            responseSuccess(annotationGroupService.list(project))
        } else {
            responseNotFound("AnnotationGroup", "Project", params.id)
        }
    }

    @RestApiMethod(description="Get all annotation groups from an image group", listing=true)
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The image group id")
    ])
    def listByImageGroup() {
        ImageGroup imageGroup = imageGroupService.read(params.long('id'))
        if (imageGroup) {
            responseSuccess(annotationGroupService.list(imageGroup))
        } else {
            responseNotFound("AnnotationGroup", "ImageGroup", params.id)
        }
    }

    @RestApiMethod(description = "Count the number of annotation groups in the project")
    @RestApiResponseObject(objectIdentifier = "[total:x]")
    @RestApiParams(params = [
            @RestApiParam(name = "project", type = "long", paramType = RestApiParamType.PATH, description = "The project id"),
            @RestApiParam(name = "startDate", type = "long", paramType = RestApiParamType.QUERY, description = "Only count the annotation groups created after this date (optional)"),
            @RestApiParam(name = "endDate", type = "long", paramType = RestApiParamType.QUERY, description = "Only count the annotation groups created before this date (optional)")
    ])
    def countByProject() {
        Project project = projectService.read(params.project)
        securityACLService.check(project, READ)
        Date startDate = params.startDate ? new Date(params.long("startDate")) : null
        Date endDate = params.endDate ? new Date(params.long("endDate")) : null
        responseSuccess([total: annotationGroupService.countByProject(project, startDate, endDate)])
    }

    @RestApiMethod(description="Get a annotation group")
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The annotation group id")
    ])
    def show() {
        AnnotationGroup annotationGroup = annotationGroupService.read(params.long('id'))
        if (annotationGroup) {
            responseSuccess(annotationGroup)
        } else {
            responseNotFound("AnnotationGroup", params.id)
        }
    }

    @RestApiMethod(description="Add a annotation group in an image")
    def add () {
        if(!request.JSON.imageGroup)
            throw new InvalidRequestException("Image Group not set")
        add(annotationGroupService, request.JSON)
    }

    @RestApiMethod(description="Update a annotation group")
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The annotation group id")
    ])
    def update () {
        update(annotationGroupService, request.JSON)
    }

    @RestApiMethod(description="Delete a annotation group")
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The annotation group id")
    ])
    def delete () {
        delete(annotationGroupService, JSON.parse("{id : $params.id}"),null)
    }

}

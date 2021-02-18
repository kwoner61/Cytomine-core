package be.cytomine.api.ontology

import be.cytomine.AnnotationDomain
import be.cytomine.api.RestController
import be.cytomine.ontology.AnnotationLink
import be.cytomine.ontology.AnnotationGroup
import grails.converters.JSON
import org.restapidoc.annotation.RestApiMethod
import org.restapidoc.annotation.RestApiParam
import org.restapidoc.annotation.RestApiParams
import org.restapidoc.pojo.RestApiParamType

class RestAnnotationLinkController extends RestController {

    def annotationLinkService
    def securityACLService
    def annotationGroupService

    @RestApiMethod(description="Get all annotationLink for a annotation group", listing=true)
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The annotation group id"),
    ])
    def listByAnnotationGroup() {
        AnnotationGroup annotationGroup = annotationGroupService.read(params.long('id'))
        if (annotationGroup) {
            responseSuccess(annotationLinkService.list(annotationGroup))
        } else {
            responseNotFound("AnnotationLink", "AnnotationGroup", params.id)
        }
    }

    @RestApiMethod(description="Get all annotationLink for an annotation", listing=true)
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH, description = "The annotation id"),
    ])
    def listByAnnotation() {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(params.long("id"))
        if (annotation) {
            AnnotationLink link = AnnotationLink.findByAnnotationIdentAndDeletedIsNull(annotation.id)
            if (link) {
                responseSuccess(annotationLinkService.list(link.group))
            }
            else {
                responseSuccess([])
            }
        } else {
            responseNotFound("AnnotationLink", "Annotation", params.id)
        }
    }


    @RestApiMethod(description="Get an annotation annotation group")
    @RestApiParams(params=[
            @RestApiParam(name="annotation", type="long", paramType = RestApiParamType.PATH, description = "The annotation id"),
            @RestApiParam(name="annotationGroup", type="long", paramType = RestApiParamType.PATH, description = "The annotation group id"),
    ])
    def show() {
        AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(params.long('annotation'))
        AnnotationGroup annotationGroup = annotationGroupService.read(params.long('annotationGroup'))

        if (!annotation) {
            responseNotFound("Annotation", params.annotation)
        }

        if (!annotationGroup) {
            responseNotFound("AnnotationGroup", params.annotationGroup)
        }

        AnnotationLink at = annotationLinkService.read(annotationGroup, annotation)
        if (at) {
            responseSuccess(at)
        }
        else {
            responseNotFound("AnnotationLink", "Annotation", "AnnotationGroup", annotation.id, annotationGroup.id)
        }
    }

    @RestApiMethod(description="Get an annotation-annotation group relation")
    def add() {
        add(annotationLinkService, request.JSON)
    }


    @RestApiMethod(description="Remove an annotation from a annotation group")
    @RestApiParams(params=[
            @RestApiParam(name="annotation", type="long", paramType = RestApiParamType.PATH, description = "The annotation id"),
            @RestApiParam(name="annotationGroup", type="long", paramType = RestApiParamType.PATH, description = "The annotation group id")
    ])
    def delete() {
        def json = JSON.parse("{annotationIdent: $params.annotation, group: $params.annotationGroup}")
        delete(annotationLinkService, json,null)
    }
}

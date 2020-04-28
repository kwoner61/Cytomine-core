package be.cytomine.api.ontology

import be.cytomine.AnnotationDomain
import be.cytomine.Exception.CytomineException
import be.cytomine.Exception.ObjectNotFoundException
import be.cytomine.api.RestController
import be.cytomine.image.CompanionFile

class RestAnnotationProfileController extends RestController {

    def imageServerService
    def exportService

    def projections() {
        forward(action: "profile", params: [type: "projections", format: params.format])
    }

    def minProjection() {
        forward(action: "profile", params: [type: "image-projection", projection: "min", format: params.format])
    }

    def maxProjection() {
        forward(action: "profile", params: [type: "image-projection", projection: "max", format: params.format])
    }

    def averageProjection() {
        forward(action: "profile", params: [type: "image-projection"], projection: "average", format: params.format)
    }

    def profile() {
        try {
            AnnotationDomain annotation = AnnotationDomain.getAnnotationDomain(params.long('id'))
            if (!annotation) {
                throw new ObjectNotFoundException("Annotation ${params.long('id')} not found!")
            }

            if (!annotation.image.baseImage.hasProfile()) {
                throw new ObjectNotFoundException("No profile for abstract image ${annotation.image.baseImage}")
            }

            CompanionFile cf = CompanionFile.findByImageAndType(annotation.image.baseImage, "HDF5")

            if (params.type == "projections") {
                def projections = imageServerService.profileProjections(cf, annotation, params)
                if (params.format == "csv") {
                    response.contentType = grailsApplication.config.grails.mime.types[params.format]
                    response.setHeader("Content-disposition", "attachment; filename=projections-annotation-${annotation.id}.${params.format}")

                    Map labels = [x: "X", y: "Y", min: "minimum intensity", max: "maximum intensity", average: "average intensity"]
                    def fields = labels.keySet() as List

                    def csvData = projections.collect {
                        [x: it.point[0], y: it.point[1], min: it.min, max: it.max, average: it.average]
                    }

                    exportService.export("csv", response.outputStream, csvData, fields, labels, null, ["csv.encoding": "UTF-8", "separator": ";"])
                } else {
                    responseSuccess(projections)
                }
            }
            else if (params.type == "image-projection") {
                responseByteArray(imageServerService.profileImageProjection(cf, annotation, params))
            }
            else {
                responseSuccess(imageServerService.profile(cf, annotation, params))
            }
        }
        catch (CytomineException e) {
            responseError(e)
        }
    }

}

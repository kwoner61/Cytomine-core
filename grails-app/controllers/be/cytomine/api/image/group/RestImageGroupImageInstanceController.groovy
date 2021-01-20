package be.cytomine.api.image.group

import be.cytomine.api.RestController
import be.cytomine.image.ImageInstance
import be.cytomine.image.group.ImageGroup
import be.cytomine.image.group.ImageGroupImageInstance
import grails.converters.JSON
import org.restapidoc.annotation.RestApiMethod
import org.restapidoc.annotation.RestApiParam
import org.restapidoc.annotation.RestApiParams
import org.restapidoc.pojo.RestApiParamType

class RestImageGroupImageInstanceController extends RestController {

    def imageGroupImageInstanceService
    def imageGroupService
    def imageInstanceService

    @RestApiMethod(description="Get all relations for a group", listing=true)
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The group id"),
    ])
    def listByImageGroup() {
        ImageGroup group = imageGroupService.read(params.long('id'))
        if (group) {
            responseSuccess(imageGroupImageInstanceService.list(group))
        } else {
            responseNotFound("ImageGroupImageInstance", "ImageGroup", params.id)
        }
    }

    @RestApiMethod(description="Get all relations for an image", listing=true)
    @RestApiParams(params=[
            @RestApiParam(name="id", type="long", paramType = RestApiParamType.PATH,description = "The image id"),
    ])
    def listByImageInstance() {
        ImageInstance image = imageInstanceService.read(params.long('id'))
        if (image) {
            responseSuccess(imageGroupImageInstanceService.list(image))
        } else {
            responseNotFound("ImageGroupImageInstance", "ImageInstance", params.id)
        }
    }


    @RestApiMethod(description="Get an image group - image instance relation")
    @RestApiParams(params=[
            @RestApiParam(name="group", type="long", paramType = RestApiParamType.PATH, description = "The group id"),
            @RestApiParam(name="image", type="long", paramType = RestApiParamType.PATH, description = "The image id")
    ])
    def show() {
        ImageGroup group = imageGroupService.read(params.long('group'))
        ImageInstance image = imageInstanceService.read(params.long('image'))

        if (!group) {
            responseNotFound("ImageGroup", params.group)
        }

        if (!image) {
            responseNotFound("ImageInstance", params.image)
        }

        ImageGroupImageInstance igii = imageGroupImageInstanceService.read(group, image)
        if (igii) {
            responseSuccess(igii)
        }
        else {
            responseNotFound("ImageGroupImageInstance", "ImageGroup", "ImageInstance", group.id, image.id)
        }
    }

    @RestApiMethod(description="Get the next image in the group, ordered by image filename")
    @RestApiParams(params=[
            @RestApiParam(name="group", type="long", paramType = RestApiParamType.PATH, description = "The image group id"),
            @RestApiParam(name="image", type="long", paramType = RestApiParamType.PATH, description = "The current image instance id"),
            @RestApiParam(name="circular", type="boolean", paramType = RestApiParamType.PATH, description = "Whether return first image when end is reached"),
    ])
    def next() {
        ImageGroup group = imageGroupService.read(params.long('group'))
        ImageInstance image = imageInstanceService.read(params.long('image'))

        if (!group) {
            responseNotFound("ImageGroup", params.group)
        }

        if (!image) {
            responseNotFound("ImageInstance", params.image)
        }

        ImageGroupImageInstance igii = imageGroupImageInstanceService.read(group, image)
        if (!igii) {
            responseNotFound("ImageGroupImageInstance", "ImageGroup", "ImageInstance", group.id, image.id)
        }

        boolean circular = params.boolean('circular', true)

        def images = ImageGroupImageInstance.findAllByGroupAndDeletedIsNull(group).collect{ x -> x.image}
        images.sort({x, y ->
            x?.blindInstanceFilename?.toLowerCase() <=> y?.blindInstanceFilename?.toLowerCase()
        })
        def index = images.findIndexOf {it.id == image.id}
        def next = index + 1
        if (next == images.size()) {
            if (!circular) {
                return responseSuccess([:])
            }
            next = 0
        }
        return responseSuccess(images[next])
    }

    @RestApiMethod(description="Get the previous image in the group, ordered by image filename")
    @RestApiParams(params=[
            @RestApiParam(name="group", type="long", paramType = RestApiParamType.PATH, description = "The image group id"),
            @RestApiParam(name="image", type="long", paramType = RestApiParamType.PATH, description = "The current image instance id"),
            @RestApiParam(name="circular", type="boolean", paramType = RestApiParamType.PATH, description = "Whether return last image when beginning is reached"),
    ])
    def previous() {
        ImageGroup group = imageGroupService.read(params.long('group'))
        ImageInstance image = imageInstanceService.read(params.long('image'))

        if (!group) {
            responseNotFound("ImageGroup", params.group)
        }

        if (!image) {
            responseNotFound("ImageInstance", params.image)
        }

        ImageGroupImageInstance igii = imageGroupImageInstanceService.read(group, image)
        if (!igii) {
            responseNotFound("ImageGroupImageInstance", "ImageGroup", "ImageInstance", group.id, image.id)
        }

        boolean circular = params.boolean('circular', true)

        def images = ImageGroupImageInstance.findAllByGroupAndDeletedIsNull(group).collect{ x -> x.image}
        images.sort({x, y ->
            x?.blindInstanceFilename?.toLowerCase() <=> y?.blindInstanceFilename?.toLowerCase()
        })
        def index = images.findIndexOf {it.id == image.id}
        def previous = index - 1
        if (previous == -1) {
            if (!circular) {
                return responseSuccess([:])
            }
            previous = images.size() - 1
        }
        return responseSuccess(images[previous])
    }

    @RestApiMethod(description="Add an image instance to an image group")
    def add() {
        def json = request.JSON
        json = JSON.parse("{group: ${params.group ?: json.group}, image: ${params.image ?: json.image}}")
        add(imageGroupImageInstanceService, json)
    }


    @RestApiMethod(description="Remove an image instance from a group")
    @RestApiParams(params=[
            @RestApiParam(name="group", type="long", paramType = RestApiParamType.PATH, description = "The group id"),
            @RestApiParam(name="image", type="long", paramType = RestApiParamType.PATH, description = "The image id")
    ])
    def delete() {
        def json = JSON.parse("{group: $params.group, image: $params.image}")
        delete(imageGroupImageInstanceService, json,null)
    }
}
